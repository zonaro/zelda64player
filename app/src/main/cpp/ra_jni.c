/*
 * Zelda 64 Player - JNI bridge to the rcheevos library (RetroAchievements).
 *
 * Architecture notes:
 * - HTTP never happens in C. The server_call callback marshals each request to
 *   Kotlin (RaNativeListener.onServerRequest) which executes it via OkHttp on
 *   its own dispatcher threads, then hands the response back through
 *   nativeCompleteServerRequest. Pending requests are tracked in a mutex
 *   guarded map keyed by an opaque request id.
 * - Emulated memory is exposed to rcheevos through a direct ByteBuffer
 *   captured once per game load (nativeSetMemoryRegion). read_memory then
 *   performs zero-copy reads off that alias. The alias may observe torn reads
 *   while the GL thread runs the frame; this mirrors how other frontends
 *   running evaluation off the emulation thread behave, and rcheevos hit
 *   counting tolerates occasional inconsistency. Never cache the buffer
 *   across game unload/reload.
 * - Async operation results (login, load game) are correlated on the Kotlin
 *   side by an operation id passed as callback userdata.
 * - Events and structured info (user/game/achievements) cross the boundary as
 *   small JSON documents built here, parsed in Kotlin with org.json.
 */

#include <jni.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>

#include "rc_client.h"
#include "rc_api_request.h"
#include "rc_api_runtime.h"
#include "rc_api_user.h"
#include "rc_consoles.h"
#include "rc_error.h"
#include "rc_hash.h"
#include "rc_version.h"

#define RA_JNI_VERSION JNI_VERSION_1_6

/* Console id used for all game identification in this app. */
#define RA_CONSOLE_ID RC_CONSOLE_NINTENDO_64

static JavaVM* g_jvm = NULL;

static rc_client_t* g_client = NULL;
static pthread_mutex_t g_client_mutex = PTHREAD_MUTEX_INITIALIZER;

/* Cached global ref of the Kotlin listener (RaNativeListener implementation). */
static jobject g_listener = NULL;
static jmethodID g_mid_on_server_request = NULL;
static jmethodID g_mid_on_async_result = NULL;
static jmethodID g_mid_on_client_event = NULL;

/* Emulated memory alias captured at game load. */
static void* g_mem_base = NULL;
static size_t g_mem_size = 0;

/* Pending server requests awaiting an OkHttp response. */
typedef struct pending_request {
    int id;
    rc_client_server_callback_t callback;
    void* callback_data;
    struct pending_request* next;
} pending_request_t;

static pending_request_t* g_pending = NULL;
static int g_next_request_id = 1;
static pthread_mutex_t g_pending_mutex = PTHREAD_MUTEX_INITIALIZER;

static pending_request_t* remove_pending_request(int id);

static jobjectArray build_request_array(JNIEnv* env, const rc_api_request_t* request);

/* ---------------------------------------------------------------------------
 * Small helpers
 * ------------------------------------------------------------------------- */

/* Appends a JSON-escaped copy of src into a growable buffer. */
typedef struct {
    char* data;
    size_t len;
    size_t cap;
} strbuf_t;

static void strbuf_init(strbuf_t* sb) {
    sb->cap = 256;
    sb->len = 0;
    sb->data = (char*) malloc(sb->cap);
    sb->data[0] = '\0';
}

static void strbuf_putc_raw(strbuf_t* sb, char c) {
    if (sb->len + 1 >= sb->cap) {
        sb->cap *= 2;
        sb->data = (char*) realloc(sb->data, sb->cap);
    }
    sb->data[sb->len++] = c;
    sb->data[sb->len] = '\0';
}

static void strbuf_puts(strbuf_t* sb, const char* s) {
    while (*s) {
        strbuf_putc_raw(sb, *s++);
    }
}

/* Appends a JSON string literal (with quotes), escaping per RFC 8259. */
static void strbuf_put_json_string(strbuf_t* sb, const char* s) {
    strbuf_putc_raw(sb, '"');
    if (s != NULL) {
        for (; *s; s++) {
            unsigned char c = (unsigned char) *s;
            switch (c) {
                case '"': strbuf_puts(sb, "\\\""); break;
                case '\\': strbuf_puts(sb, "\\\\"); break;
                case '\b': strbuf_puts(sb, "\\b"); break;
                case '\f': strbuf_puts(sb, "\\f"); break;
                case '\n': strbuf_puts(sb, "\\n"); break;
                case '\r': strbuf_puts(sb, "\\r"); break;
                case '\t': strbuf_puts(sb, "\\t"); break;
                default:
                    if (c < 0x20) {
                        char esc[8];
                        snprintf(esc, sizeof(esc), "\\u%04x", c);
                        strbuf_puts(sb, esc);
                    } else {
                        strbuf_putc_raw(sb, (char) c);
                    }
            }
        }
    }
    strbuf_putc_raw(sb, '"');
}

static void strbuf_put_json_int(strbuf_t* sb, long v) {
    char tmp[24];
    snprintf(tmp, sizeof(tmp), "%ld", v);
    strbuf_puts(sb, tmp);
}

/* ---------------------------------------------------------------------------
 * JVM attachment helpers
 * ------------------------------------------------------------------------- */

static JNIEnv* attach_current_thread(void) {
    JNIEnv* env = NULL;
    if (g_jvm == NULL) return NULL;
    if ((*g_jvm)->GetEnv(g_jvm, (void**) &env, RA_JNI_VERSION) == JNI_OK) {
        return env;
    }
    if ((*g_jvm)->AttachCurrentThread(g_jvm, &env, NULL) == JNI_OK) {
        return env;
    }
    return NULL;
}

/* ---------------------------------------------------------------------------
 * rc_client callbacks
 * ------------------------------------------------------------------------- */

static uint32_t read_memory(uint32_t address, uint8_t* buffer, uint32_t num_bytes, rc_client_t* client) {
    (void) client;
    if (g_mem_base == NULL || g_mem_size == 0) {
        return 0;
    }
    /* N64 RDRAM is contiguous and RA addresses map directly onto it. */
    if ((uint64_t) address + num_bytes > g_mem_size) {
        return 0;
    }
    memcpy(buffer, (const uint8_t*) g_mem_base + address, num_bytes);
    return num_bytes;
}

static void server_call(const rc_api_request_t* request,
        rc_client_server_callback_t callback, void* callback_data, rc_client_t* client) {
    (void) client;

    JNIEnv* env = attach_current_thread();
    pending_request_t* pending = (pending_request_t*) malloc(sizeof(pending_request_t));

    pthread_mutex_lock(&g_pending_mutex);
    pending->id = g_next_request_id++;
    pthread_mutex_unlock(&g_pending_mutex);
    pending->callback = callback;
    pending->callback_data = callback_data;
    pending->next = NULL;

    pthread_mutex_lock(&g_pending_mutex);
    pending->next = g_pending;
    g_pending = pending;
    int pending_id = pending->id;
    pthread_mutex_unlock(&g_pending_mutex);

    if (env != NULL && g_listener != NULL && g_mid_on_server_request != NULL) {
        jstring url = (*env)->NewStringUTF(env, request->url != NULL ? request->url : "");
        jstring post_data = request->post_data != NULL
                ? (*env)->NewStringUTF(env, request->post_data) : NULL;
        (*env)->CallVoidMethod(env, g_listener, g_mid_on_server_request,
                (jint) pending_id, url, post_data);
        if (url != NULL) (*env)->DeleteLocalRef(env, url);
        if (post_data != NULL) (*env)->DeleteLocalRef(env, post_data);
    } else {
        /* No way to perform HTTP: fail the request so rc_client can surface
           it, and unlink the entry so it does not leak. */
        remove_pending_request(pending_id);
        rc_api_server_response_t response;
        memset(&response, 0, sizeof(response));
        response.body = "no http dispatcher";
        response.body_length = strlen(response.body);
        response.http_status_code = RC_API_SERVER_RESPONSE_CLIENT_ERROR;
        callback(&response, callback_data);
        free(pending);
    }
}

/* Unlinks [id] from the pending map; returns the entry or NULL. */
static pending_request_t* remove_pending_request(int id) {
    pthread_mutex_lock(&g_pending_mutex);
    pending_request_t** link = &g_pending;
    pending_request_t* found = NULL;
    while (*link != NULL) {
        if ((*link)->id == id) {
            found = *link;
            *link = found->next;
            break;
        }
        link = &(*link)->next;
    }
    pthread_mutex_unlock(&g_pending_mutex);
    return found;
}

static void client_event_handler(const rc_client_event_t* event, rc_client_t* client) {
    (void) client;
    JNIEnv* env = attach_current_thread();
    if (env == NULL || g_listener == NULL || g_mid_on_client_event == NULL) {
        return;
    }

    strbuf_t payload;
    strbuf_init(&payload);

    switch (event->type) {
        case RC_CLIENT_EVENT_ACHIEVEMENT_TRIGGERED:
        case RC_CLIENT_EVENT_ACHIEVEMENT_CHALLENGE_INDICATOR_SHOW:
        case RC_CLIENT_EVENT_ACHIEVEMENT_CHALLENGE_INDICATOR_HIDE:
        case RC_CLIENT_EVENT_ACHIEVEMENT_PROGRESS_INDICATOR_SHOW:
        case RC_CLIENT_EVENT_ACHIEVEMENT_PROGRESS_INDICATOR_UPDATE:
        case RC_CLIENT_EVENT_ACHIEVEMENT_PROGRESS_INDICATOR_HIDE: {
            const rc_client_achievement_t* ach = event->achievement;
            strbuf_puts(&payload, "{\"title\":");
            strbuf_put_json_string(&payload, ach ? ach->title : "");
            strbuf_puts(&payload, ",\"description\":");
            strbuf_put_json_string(&payload, ach ? ach->description : "");
            strbuf_puts(&payload, ",\"badge_url\":");
            strbuf_put_json_string(&payload, ach ? ach->badge_url : "");
            strbuf_puts(&payload, ",\"badge_locked_url\":");
            strbuf_put_json_string(&payload, ach ? ach->badge_locked_url : "");
            strbuf_puts(&payload, ",\"points\":");
            strbuf_put_json_int(&payload, ach ? (long) ach->points : 0);
            strbuf_puts(&payload, ",\"id\":");
            strbuf_put_json_int(&payload, ach ? (long) ach->id : 0);
            strbuf_puts(&payload, ",\"measured_progress\":");
            strbuf_put_json_string(&payload, ach ? ach->measured_progress : "");
            strbuf_puts(&payload, ",\"measured_percent\":");
            {
                char pct[32];
                snprintf(pct, sizeof(pct), "%.3f", ach ? (double) ach->measured_percent : 0.0);
                strbuf_puts(&payload, pct);
            }
            strbuf_puts(&payload, "}");
            break;
        }
        case RC_CLIENT_EVENT_LEADERBOARD_STARTED:
        case RC_CLIENT_EVENT_LEADERBOARD_FAILED:
        case RC_CLIENT_EVENT_LEADERBOARD_SUBMITTED: {
            const rc_client_leaderboard_t* lbd = event->leaderboard;
            strbuf_puts(&payload, "{\"title\":");
            strbuf_put_json_string(&payload, lbd ? lbd->title : "");
            strbuf_puts(&payload, ",\"description\":");
            strbuf_put_json_string(&payload, lbd ? lbd->description : "");
            strbuf_puts(&payload, ",\"tracker_value\":");
            strbuf_put_json_string(&payload, lbd ? lbd->tracker_value : "");
            strbuf_puts(&payload, ",\"id\":");
            strbuf_put_json_int(&payload, lbd ? (long) lbd->id : 0);
            strbuf_puts(&payload, "}");
            break;
        }
        case RC_CLIENT_EVENT_SERVER_ERROR: {
            const rc_client_server_error_t* err = event->server_error;
            strbuf_puts(&payload, "{\"error\":");
            strbuf_put_json_string(&payload, err && err->error_message ? err->error_message : "");
            strbuf_puts(&payload, "}");
            break;
        }
        default:
            strbuf_puts(&payload, "{}");
            break;
    }

    jstring json = (*env)->NewStringUTF(env, payload.data);
    (*env)->CallVoidMethod(env, g_listener, g_mid_on_client_event, (jint) event->type, json);
    (*env)->DeleteLocalRef(env, json);
    free(payload.data);
}

/* ---------------------------------------------------------------------------
 * Native entry points
 * ------------------------------------------------------------------------- */

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    (void) reserved;
    g_jvm = vm;
    return RA_JNI_VERSION;
}

JNIEXPORT void JNICALL
Java_br_com_redclaw_zelda64player_retroachievements_jni_RcheevosJni_nativeCreateClient(
        JNIEnv* env, jobject thiz, jobject listener) {
    (void) thiz;

    jclass cls = (*env)->GetObjectClass(env, listener);
    jobject global_listener = (*env)->NewGlobalRef(env, listener);

    pthread_mutex_lock(&g_client_mutex);
    g_listener = global_listener;
    g_mid_on_server_request = (*env)->GetMethodID(env, cls, "onServerRequest",
            "(ILjava/lang/String;Ljava/lang/String;)V");
    g_mid_on_async_result = (*env)->GetMethodID(env, cls, "onAsyncResult",
            "(IILjava/lang/String;)V");
    g_mid_on_client_event = (*env)->GetMethodID(env, cls, "onClientEvent",
            "(ILjava/lang/String;)V");

    if (g_client == NULL) {
        g_client = rc_client_create(read_memory, server_call);
        if (g_client != NULL) {
            rc_client_set_event_handler(g_client, client_event_handler);
            /* Hardcore stays disabled until the User-Agent is validated by RAdmin. */
            rc_client_set_hardcore_enabled(g_client, 0);
        }
    }
    pthread_mutex_unlock(&g_client_mutex);
}

JNIEXPORT void JNICALL
Java_br_com_redclaw_zelda64player_retroachievements_jni_RcheevosJni_nativeDestroyClient(
        JNIEnv* env, jobject thiz) {
    (void) env;
    (void) thiz;

    pthread_mutex_lock(&g_client_mutex);
    if (g_client != NULL) {
        rc_client_destroy(g_client);
        g_client = NULL;
    }
    if (g_listener != NULL) {
        (*env)->DeleteGlobalRef(env, g_listener);
        g_listener = NULL;
    }
    g_mid_on_server_request = NULL;
    g_mid_on_async_result = NULL;
    g_mid_on_client_event = NULL;
    pthread_mutex_unlock(&g_client_mutex);
}

JNIEXPORT jboolean JNICALL
Java_br_com_redclaw_zelda64player_retroachievements_jni_RcheevosJni_nativeHasClient(
        JNIEnv* env, jobject thiz) {
    (void) env;
    (void) thiz;
    pthread_mutex_lock(&g_client_mutex);
    jboolean has = g_client != NULL;
    pthread_mutex_unlock(&g_client_mutex);
    return has;
}

JNIEXPORT void JNICALL
Java_br_com_redclaw_zelda64player_retroachievements_jni_RcheevosJni_nativeSetHardcoreEnabled(
        JNIEnv* env, jobject thiz, jboolean enabled) {
    (void) env;
    (void) thiz;
    pthread_mutex_lock(&g_client_mutex);
    if (g_client != NULL) {
        rc_client_set_hardcore_enabled(g_client, enabled ? 1 : 0);
    }
    pthread_mutex_unlock(&g_client_mutex);
}

/*
 * Async operation results (login, load game) funnel back to Kotlin through
 * onAsyncResult(opId, resultCode, errorMessage). The trampoline lives here so
 * both login variants share one code path.
 */
static void async_result_trampoline(int result, const char* error_message,
        rc_client_t* client, void* userdata) {
    (void) client;
    int op_id = (int) (intptr_t) userdata;
    JNIEnv* env = attach_current_thread();
    if (env == NULL || g_listener == NULL || g_mid_on_async_result == NULL) {
        return;
    }
    jstring error = error_message != NULL ? (*env)->NewStringUTF(env, error_message) : NULL;
    (*env)->CallVoidMethod(env, g_listener, g_mid_on_async_result,
            (jint) op_id, (jint) result, error);
    if (error != NULL) (*env)->DeleteLocalRef(env, error);
}

JNIEXPORT void JNICALL
Java_br_com_redclaw_zelda64player_retroachievements_jni_RcheevosJni_nativeBeginLoginWithPassword(
        JNIEnv* env, jobject thiz, jstring username, jstring password, jint op_id) {
    (void) thiz;
    pthread_mutex_lock(&g_client_mutex);
    if (g_client == NULL) {
        pthread_mutex_unlock(&g_client_mutex);
        return;
    }
    const char* user = (*env)->GetStringUTFChars(env, username, NULL);
    const char* pass = (*env)->GetStringUTFChars(env, password, NULL);
    rc_client_begin_login_with_password(g_client, user, pass,
            async_result_trampoline, (void*) (intptr_t) op_id);
    (*env)->ReleaseStringUTFChars(env, username, user);
    (*env)->ReleaseStringUTFChars(env, password, pass);
    pthread_mutex_unlock(&g_client_mutex);
}

JNIEXPORT void JNICALL
Java_br_com_redclaw_zelda64player_retroachievements_jni_RcheevosJni_nativeBeginLoginWithToken(
        JNIEnv* env, jobject thiz, jstring username, jstring token, jint op_id) {
    (void) thiz;
    pthread_mutex_lock(&g_client_mutex);
    if (g_client == NULL) {
        pthread_mutex_unlock(&g_client_mutex);
        return;
    }
    const char* user = (*env)->GetStringUTFChars(env, username, NULL);
    const char* tok = (*env)->GetStringUTFChars(env, token, NULL);
    rc_client_begin_login_with_token(g_client, user, tok,
            async_result_trampoline, (void*) (intptr_t) op_id);
    (*env)->ReleaseStringUTFChars(env, username, user);
    (*env)->ReleaseStringUTFChars(env, token, tok);
    pthread_mutex_unlock(&g_client_mutex);
}

JNIEXPORT void JNICALL
Java_br_com_redclaw_zelda64player_retroachievements_jni_RcheevosJni_nativeLogout(
        JNIEnv* env, jobject thiz) {
    (void) env;
    (void) thiz;
    pthread_mutex_lock(&g_client_mutex);
    if (g_client != NULL) {
        rc_client_logout(g_client);
    }
    pthread_mutex_unlock(&g_client_mutex);
}

JNIEXPORT void JNICALL
Java_br_com_redclaw_zelda64player_retroachievements_jni_RcheevosJni_nativeIdentifyAndLoadGame(
        JNIEnv* env, jobject thiz, jstring file_path, jint op_id) {
    (void) thiz;
    pthread_mutex_lock(&g_client_mutex);
    if (g_client == NULL) {
        pthread_mutex_unlock(&g_client_mutex);
        return;
    }
    const char* path = (*env)->GetStringUTFChars(env, file_path, NULL);
#ifdef RC_CLIENT_SUPPORTS_HASH
    rc_client_begin_identify_and_load_game(g_client, RA_CONSOLE_ID, path, NULL, 0,
            async_result_trampoline, (void*) (intptr_t) op_id);
#else
    (void) async_result_trampoline;
#endif
    (*env)->ReleaseStringUTFChars(env, file_path, path);
    pthread_mutex_unlock(&g_client_mutex);
}

JNIEXPORT void JNICALL
Java_br_com_redclaw_zelda64player_retroachievements_jni_RcheevosJni_nativeUnloadGame(
        JNIEnv* env, jobject thiz) {
    (void) env;
    (void) thiz;
    pthread_mutex_lock(&g_client_mutex);
    if (g_client != NULL && rc_client_is_game_loaded(g_client)) {
        rc_client_unload_game(g_client);
    }
    pthread_mutex_unlock(&g_client_mutex);
}

JNIEXPORT void JNICALL
Java_br_com_redclaw_zelda64player_retroachievements_jni_RcheevosJni_nativeSetMemoryRegion(
        JNIEnv* env, jobject thiz, jobject byte_buffer) {
    (void) thiz;
    if (byte_buffer == NULL) {
        g_mem_base = NULL;
        g_mem_size = 0;
        return;
    }
    g_mem_base = (*env)->GetDirectBufferAddress(env, byte_buffer);
    g_mem_size = (size_t) (*env)->GetDirectBufferCapacity(env, byte_buffer);
}

JNIEXPORT void JNICALL
Java_br_com_redclaw_zelda64player_retroachievements_jni_RcheevosJni_nativeDoFrame(
        JNIEnv* env, jobject thiz) {
    (void) env;
    (void) thiz;
    /* Lock-free fast path: rc_client_do_frame only touches client state owned
       by the calling thread's evaluation cycle; g_client teardown happens on
       the same (main) thread in practice. */
    if (g_client != NULL) {
        rc_client_do_frame(g_client);
    }
}

JNIEXPORT void JNICALL
Java_br_com_redclaw_zelda64player_retroachievements_jni_RcheevosJni_nativeCompleteServerRequest(
        JNIEnv* env, jobject thiz, jint request_id, jint status_code,
        jbyteArray body, jstring error_message) {
    (void) thiz;

    pending_request_t* found = remove_pending_request((int) request_id);
    if (found == NULL) {
        return;
    }

    rc_api_server_response_t response;
    memset(&response, 0, sizeof(response));

    jbyte* body_bytes = NULL;
    jsize body_len = 0;
    const char* error_cstr = NULL;

    if (body != NULL) {
        body_len = (*env)->GetArrayLength(env, body);
        body_bytes = body_len > 0 ? (*env)->GetByteArrayElements(env, body, NULL) : NULL;
        response.body = (const char*) body_bytes;
        response.body_length = (size_t) body_len;
    }
    if (error_message != NULL) {
        error_cstr = (*env)->GetStringUTFChars(env, error_message, NULL);
    }

    response.http_status_code = (int) status_code;
    if (status_code == 0 && error_cstr != NULL) {
        /* Transport-level failure: mark retryable so rc_client may retry. */
        response.body = error_cstr;
        response.body_length = strlen(error_cstr);
        response.http_status_code = RC_API_SERVER_RESPONSE_RETRYABLE_CLIENT_ERROR;
    }

    found->callback(&response, found->callback_data);

    if (body_bytes != NULL) {
        (*env)->ReleaseByteArrayElements(env, body, body_bytes, JNI_ABORT);
    }
    if (error_cstr != NULL) {
        (*env)->ReleaseStringUTFChars(env, error_message, error_cstr);
    }
    free(found);
}

/* ---------------------------------------------------------------------------
 * Structured info getters (JSON)
 * ------------------------------------------------------------------------- */

JNIEXPORT jstring JNICALL
Java_br_com_redclaw_zelda64player_retroachievements_jni_RcheevosJni_nativeGetUserInfoJson(
        JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_client_mutex);
    const rc_client_user_t* user = g_client != NULL ? rc_client_get_user_info(g_client) : NULL;
    strbuf_t sb;
    strbuf_init(&sb);
    if (user != NULL) {
        char avatar[512];
        avatar[0] = '\0';
        rc_client_user_get_image_url(user, avatar, sizeof(avatar));
        strbuf_puts(&sb, "{\"username\":");
        strbuf_put_json_string(&sb, user->username);
        /* The session token is secret; it is exposed here only for the
           in-process credential exchange, which persists it encrypted and
           never logs it. */
        strbuf_puts(&sb, ",\"token\":");
        strbuf_put_json_string(&sb, user->token != NULL ? user->token : "");
        strbuf_puts(&sb, ",\"display_name\":");
        strbuf_put_json_string(&sb, user->display_name);
        strbuf_puts(&sb, ",\"avatar_url\":");
        strbuf_put_json_string(&sb, avatar);
        strbuf_puts(&sb, ",\"score\":");
        strbuf_put_json_int(&sb, (long) user->score);
        strbuf_puts(&sb, ",\"score_softcore\":");
        strbuf_put_json_int(&sb, (long) user->score_softcore);
        strbuf_puts(&sb, "}");
    } else {
        strbuf_puts(&sb, "null");
    }
    pthread_mutex_unlock(&g_client_mutex);
    jstring result = (*env)->NewStringUTF(env, sb.data);
    free(sb.data);
    return result;
}

JNIEXPORT jstring JNICALL
Java_br_com_redclaw_zelda64player_retroachievements_jni_RcheevosJni_nativeGetGameInfoJson(
        JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_client_mutex);
    const rc_client_game_t* game = g_client != NULL ? rc_client_get_game_info(g_client) : NULL;
    strbuf_t sb;
    strbuf_init(&sb);
    if (game != NULL && game->id != 0) {
        rc_client_user_game_summary_t summary;
        memset(&summary, 0, sizeof(summary));
        if (g_client != NULL) {
            rc_client_get_user_game_summary(g_client, &summary);
        }
        strbuf_puts(&sb, "{\"id\":");
        strbuf_put_json_int(&sb, (long) game->id);
        strbuf_puts(&sb, ",\"title\":");
        strbuf_put_json_string(&sb, game->title);
        strbuf_puts(&sb, ",\"hash\":");
        strbuf_put_json_string(&sb, game->hash);
        strbuf_puts(&sb, ",\"badge_url\":");
        strbuf_put_json_string(&sb, game->badge_url);
        strbuf_puts(&sb, ",\"num_core_achievements\":");
        strbuf_put_json_int(&sb, (long) summary.num_core_achievements);
        strbuf_puts(&sb, ",\"num_unlocked_achievements\":");
        strbuf_put_json_int(&sb, (long) summary.num_unlocked_achievements);
        strbuf_puts(&sb, ",\"points_core\":");
        strbuf_put_json_int(&sb, (long) summary.points_core);
        strbuf_puts(&sb, ",\"points_unlocked\":");
        strbuf_put_json_int(&sb, (long) summary.points_unlocked);
        strbuf_puts(&sb, "}");
    } else {
        strbuf_puts(&sb, "null");
    }
    pthread_mutex_unlock(&g_client_mutex);
    jstring result = (*env)->NewStringUTF(env, sb.data);
    free(sb.data);
    return result;
}

JNIEXPORT jstring JNICALL
Java_br_com_redclaw_zelda64player_retroachievements_jni_RcheevosJni_nativeGetAchievementListJson(
        JNIEnv* env, jobject thiz) {
    (void) thiz;
    pthread_mutex_lock(&g_client_mutex);
    strbuf_t sb;
    strbuf_init(&sb);
    strbuf_puts(&sb, "[");

    if (g_client != NULL && rc_client_is_game_loaded(g_client)) {
        rc_client_achievement_list_t* list = rc_client_create_achievement_list(g_client,
                RC_CLIENT_ACHIEVEMENT_CATEGORY_CORE_AND_UNOFFICIAL,
                RC_CLIENT_ACHIEVEMENT_LIST_GROUPING_LOCK_STATE);
        if (list != NULL) {
            int first_bucket = 1;
            for (uint32_t b = 0; b < list->num_buckets; b++) {
                const rc_client_achievement_bucket_t* bucket = &list->buckets[b];
                for (uint32_t i = 0; i < bucket->num_achievements; i++) {
                    const rc_client_achievement_t* ach = bucket->achievements[i];
                    if (!first_bucket) strbuf_puts(&sb, ",");
                    first_bucket = 0;

                    strbuf_puts(&sb, "{\"bucket_label\":");
                    strbuf_put_json_string(&sb, bucket->label);
                    strbuf_puts(&sb, ",\"bucket_type\":");
                    strbuf_put_json_int(&sb, (long) bucket->bucket_type);
                    strbuf_puts(&sb, ",\"id\":");
                    strbuf_put_json_int(&sb, (long) ach->id);
                    strbuf_puts(&sb, ",\"title\":");
                    strbuf_put_json_string(&sb, ach->title);
                    strbuf_puts(&sb, ",\"description\":");
                    strbuf_put_json_string(&sb, ach->description);
                    strbuf_puts(&sb, ",\"points\":");
                    strbuf_put_json_int(&sb, (long) ach->points);
                    strbuf_puts(&sb, ",\"badge_url\":");
                    strbuf_put_json_string(&sb, ach->badge_url);
                    strbuf_puts(&sb, ",\"badge_locked_url\":");
                    strbuf_put_json_string(&sb, ach->badge_locked_url);
                    strbuf_puts(&sb, ",\"unlocked\":");
                    strbuf_put_json_int(&sb, (long) ach->unlocked);
                    strbuf_puts(&sb, ",\"category\":");
                    strbuf_put_json_int(&sb, (long) ach->category);
                    strbuf_puts(&sb, ",\"measured_progress\":");
                    strbuf_put_json_string(&sb, ach->measured_progress);
                    strbuf_puts(&sb, ",\"measured_percent\":");
                    {
                        char pct[32];
                        snprintf(pct, sizeof(pct), "%.3f", (double) ach->measured_percent);
                        strbuf_puts(&sb, pct);
                    }
                    strbuf_puts(&sb, "}");
                }
            }
            rc_client_destroy_achievement_list(list);
        }
    }

    strbuf_puts(&sb, "]");
    pthread_mutex_unlock(&g_client_mutex);
    jstring result = (*env)->NewStringUTF(env, sb.data);
    free(sb.data);
    return result;
}

/* ---------------------------------------------------------------------------
 * Standalone rapi helpers (used outside a live session)
 * ------------------------------------------------------------------------- */

JNIEXPORT jobjectArray JNICALL
Java_br_com_redclaw_zelda64player_retroachievements_jni_RcheevosJni_nativeBuildResolveHashRequest(
        JNIEnv* env, jobject thiz, jstring game_hash) {
    (void) thiz;
    rc_api_resolve_hash_request_t params;
    memset(&params, 0, sizeof(params));
    params.game_hash = (*env)->GetStringUTFChars(env, game_hash, NULL);

    rc_api_request_t request;
    memset(&request, 0, sizeof(request));
    int rc = rc_api_init_resolve_hash_request(&request, &params);
    (*env)->ReleaseStringUTFChars(env, game_hash, params.game_hash);
    if (rc != RC_OK) {
        return NULL;
    }
    jobjectArray result = build_request_array(env, &request);
    rc_buffer_destroy(&request.buffer);
    return result;
}

JNIEXPORT jlong JNICALL
Java_br_com_redclaw_zelda64player_retroachievements_jni_RcheevosJni_nativeProcessResolveHashResponse(
        JNIEnv* env, jobject thiz, jstring response_body) {
    (void) thiz;
    const char* body = (*env)->GetStringUTFChars(env, response_body, NULL);

    rc_api_server_response_t server_response;
    memset(&server_response, 0, sizeof(server_response));
    server_response.body = body;
    server_response.body_length = strlen(body);
    server_response.http_status_code = 200;

    rc_api_resolve_hash_response_t api_response;
    memset(&api_response, 0, sizeof(api_response));
    int rc = rc_api_process_resolve_hash_server_response(&api_response, &server_response);
    jlong game_id = (rc == RC_OK) ? (jlong) api_response.game_id : 0;
    rc_api_destroy_resolve_hash_response(&api_response);
    (*env)->ReleaseStringUTFChars(env, response_body, body);
    return game_id;
}

JNIEXPORT jstring JNICALL
Java_br_com_redclaw_zelda64player_retroachievements_jni_RcheevosJni_nativeComputeRomHash(
        JNIEnv* env, jobject thiz, jstring file_path) {
    (void) thiz;
    const char* path = (*env)->GetStringUTFChars(env, file_path, NULL);

    rc_hash_iterator_t iterator;
    rc_hash_initialize_iterator(&iterator, path, NULL, 0);

    char hash[33];
    memset(hash, 0, sizeof(hash));
    int rc = rc_hash_generate(hash, RA_CONSOLE_ID, &iterator);
    rc_hash_destroy_iterator(&iterator);
    (*env)->ReleaseStringUTFChars(env, file_path, path);

    if (rc != RC_OK) {
        return (*env)->NewStringUTF(env, "");
    }
    return (*env)->NewStringUTF(env, hash);
}

/* ---------------------------------------------------------------------------
 * Standalone rapi helpers: game data + user unlocks (library screens)
 * ------------------------------------------------------------------------- */

/* Shared builder: returns [url, postData] from an initialized request. */
static jobjectArray build_request_array(JNIEnv* env, const rc_api_request_t* request) {
    jclass string_class = (*env)->FindClass(env, "java/lang/String");
    jobjectArray result = (*env)->NewObjectArray(env, 2, string_class, NULL);
    jstring url = (*env)->NewStringUTF(env, request->url != NULL ? request->url : "");
    (*env)->SetObjectArrayElement(env, result, 0, url);
    (*env)->DeleteLocalRef(env, url);
    if (request->post_data != NULL) {
        jstring post = (*env)->NewStringUTF(env, request->post_data);
        (*env)->SetObjectArrayElement(env, result, 1, post);
        (*env)->DeleteLocalRef(env, post);
    }
    return result;
}

JNIEXPORT jobjectArray JNICALL
Java_br_com_redclaw_zelda64player_retroachievements_jni_RcheevosJni_nativeBuildFetchGameDataRequest(
        JNIEnv* env, jobject thiz, jstring username, jstring api_token, jlong game_id) {
    (void) thiz;
    rc_api_fetch_game_data_request_t params;
    memset(&params, 0, sizeof(params));
    params.username = (*env)->GetStringUTFChars(env, username, NULL);
    params.api_token = (*env)->GetStringUTFChars(env, api_token, NULL);
    params.game_id = (uint32_t) game_id;

    rc_api_request_t request;
    memset(&request, 0, sizeof(request));
    int rc = rc_api_init_fetch_game_data_request(&request, &params);
    (*env)->ReleaseStringUTFChars(env, username, params.username);
    (*env)->ReleaseStringUTFChars(env, api_token, params.api_token);
    if (rc != RC_OK) {
        return NULL;
    }
    jobjectArray result = build_request_array(env, &request);
    rc_buffer_destroy(&request.buffer);
    return result;
}

/* Serializes parsed game data into a compact JSON document for Kotlin. */
static void append_achievement_json(strbuf_t* sb, const rc_api_achievement_definition_t* a) {
    strbuf_puts(sb, "{\"id\":");
    strbuf_put_json_int(sb, (long) a->id);
    strbuf_puts(sb, ",\"title\":");
    strbuf_put_json_string(sb, a->title);
    strbuf_puts(sb, ",\"description\":");
    strbuf_put_json_string(sb, a->description);
    strbuf_puts(sb, ",\"points\":");
    strbuf_put_json_int(sb, (long) a->points);
    strbuf_puts(sb, ",\"badge_url\":");
    strbuf_put_json_string(sb, a->badge_url);
    strbuf_puts(sb, ",\"badge_locked_url\":");
    strbuf_put_json_string(sb, a->badge_locked_url);
    strbuf_puts(sb, ",\"category\":");
    strbuf_put_json_int(sb, (long) a->category);
    strbuf_puts(sb, ",\"type\":");
    strbuf_put_json_int(sb, (long) a->type);
    strbuf_puts(sb, "}");
}

JNIEXPORT jstring JNICALL
Java_br_com_redclaw_zelda64player_retroachievements_jni_RcheevosJni_nativeProcessFetchGameDataResponse(
        JNIEnv* env, jobject thiz, jstring response_body) {
    (void) thiz;
    const char* body = (*env)->GetStringUTFChars(env, response_body, NULL);

    rc_api_server_response_t server_response;
    memset(&server_response, 0, sizeof(server_response));
    server_response.body = body;
    server_response.body_length = strlen(body);
    server_response.http_status_code = 200;

    rc_api_fetch_game_data_response_t response;
    memset(&response, 0, sizeof(response));
    int rc = rc_api_process_fetch_game_data_server_response(&response, &server_response);
    (*env)->ReleaseStringUTFChars(env, response_body, body);

    if (rc != RC_OK || !response.response.succeeded) {
        rc_api_destroy_fetch_game_data_response(&response);
        return (*env)->NewStringUTF(env, "null");
    }

    strbuf_t sb;
    strbuf_init(&sb);
    strbuf_puts(&sb, "{\"id\":");
    strbuf_put_json_int(&sb, (long) response.id);
    strbuf_puts(&sb, ",\"title\":");
    strbuf_put_json_string(&sb, response.title);
    strbuf_puts(&sb, ",\"image_url\":");
    strbuf_put_json_string(&sb, response.image_url);
    strbuf_puts(&sb, ",\"achievements\":[");
    for (uint32_t i = 0; i < response.num_achievements; i++) {
        if (i > 0) strbuf_puts(&sb, ",");
        append_achievement_json(&sb, &response.achievements[i]);
    }
    strbuf_puts(&sb, "],\"leaderboards\":[");
    for (uint32_t i = 0; i < response.num_leaderboards; i++) {
        const rc_api_leaderboard_definition_t* lbd = &response.leaderboards[i];
        if (i > 0) strbuf_puts(&sb, ",");
        strbuf_puts(&sb, "{\"id\":");
        strbuf_put_json_int(&sb, (long) lbd->id);
        strbuf_puts(&sb, ",\"title\":");
        strbuf_put_json_string(&sb, lbd->title);
        strbuf_puts(&sb, ",\"description\":");
        strbuf_put_json_string(&sb, lbd->description);
        strbuf_puts(&sb, ",\"format\":");
        strbuf_put_json_int(&sb, (long) lbd->format);
        strbuf_puts(&sb, ",\"lower_is_better\":");
        strbuf_put_json_int(&sb, (long) lbd->lower_is_better);
        strbuf_puts(&sb, ",\"hidden\":");
        strbuf_put_json_int(&sb, (long) lbd->hidden);
        strbuf_puts(&sb, "}");
    }
    strbuf_puts(&sb, "]}");

    rc_api_destroy_fetch_game_data_response(&response);
    jstring result = (*env)->NewStringUTF(env, sb.data);
    free(sb.data);
    return result;
}

JNIEXPORT jobjectArray JNICALL
Java_br_com_redclaw_zelda64player_retroachievements_jni_RcheevosJni_nativeBuildFetchUserUnlocksRequest(
        JNIEnv* env, jobject thiz, jstring username, jstring api_token,
        jlong game_id, jboolean hardcore) {
    (void) thiz;
    rc_api_fetch_user_unlocks_request_t params;
    memset(&params, 0, sizeof(params));
    params.username = (*env)->GetStringUTFChars(env, username, NULL);
    params.api_token = (*env)->GetStringUTFChars(env, api_token, NULL);
    params.game_id = (uint32_t) game_id;
    params.hardcore = hardcore ? 1 : 0;

    rc_api_request_t request;
    memset(&request, 0, sizeof(request));
    int rc = rc_api_init_fetch_user_unlocks_request(&request, &params);
    (*env)->ReleaseStringUTFChars(env, username, params.username);
    (*env)->ReleaseStringUTFChars(env, api_token, params.api_token);
    if (rc != RC_OK) {
        return NULL;
    }
    jobjectArray result = build_request_array(env, &request);
    rc_buffer_destroy(&request.buffer);
    return result;
}

JNIEXPORT jstring JNICALL
Java_br_com_redclaw_zelda64player_retroachievements_jni_RcheevosJni_nativeProcessFetchUserUnlocksResponse(
        JNIEnv* env, jobject thiz, jstring response_body) {
    (void) thiz;
    const char* body = (*env)->GetStringUTFChars(env, response_body, NULL);

    rc_api_server_response_t server_response;
    memset(&server_response, 0, sizeof(server_response));
    server_response.body = body;
    server_response.body_length = strlen(body);
    server_response.http_status_code = 200;

    rc_api_fetch_user_unlocks_response_t response;
    memset(&response, 0, sizeof(response));
    int rc = rc_api_process_fetch_user_unlocks_server_response(&response, &server_response);
    (*env)->ReleaseStringUTFChars(env, response_body, body);

    if (rc != RC_OK || !response.response.succeeded) {
        rc_api_destroy_fetch_user_unlocks_response(&response);
        return (*env)->NewStringUTF(env, "[]");
    }

    strbuf_t sb;
    strbuf_init(&sb);
    strbuf_puts(&sb, "[");
    for (uint32_t i = 0; i < response.num_achievement_ids; i++) {
        if (i > 0) strbuf_puts(&sb, ",");
        strbuf_put_json_int(&sb, (long) response.achievement_ids[i]);
    }
    strbuf_puts(&sb, "]");

    rc_api_destroy_fetch_user_unlocks_response(&response);
    jstring result = (*env)->NewStringUTF(env, sb.data);
    free(sb.data);
    return result;
}
