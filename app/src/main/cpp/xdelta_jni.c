/*
 * Zelda 64 Player - JNI bridge to the xdelta3 library (VCDIFF / xdelta3 patch
 * decoding).
 *
 * xdelta3 is vendored unmodified from upstream tag release3_2_apl
 * (https://github.com/jmacd/xdelta, Apache License 2.0). Do not edit the
 * vendored C sources locally; upgrade by replacing the snapshot with a newer
 * release tag.
 *
 * Integration strategy: xdelta3's command-line entry point `main()` is invoked
 * directly with a constructed argv (`xdelta3 -d -f -s <src> <patch> <out>`).
 * The decode path does NOT call exit() (the only _exit() calls live in the
 * external-compression subprocess helper, which is compiled out because
 * EXTERNAL_COMPRESSION is left undefined), so it is safe to call from JNI and
 * it returns an integer status code.
 *
 * xdelta3 validates the source ROM internally during decode (it fails with
 * XD3_INVALID_INPUT when the source does not match the patch). To surface a
 * meaningful error to Kotlin we capture xdelta3's stderr into a buffer and let
 * the Kotlin side classify it (source mismatch vs. malformed patch).
 *
 * The native call is NOT thread-safe (xdelta3's main() uses static state), so
 * XdeltaApplier serializes every apply() through a single lock.
 */

#include <jni.h>
#include <unistd.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

/* Make POSIX unsetenv() visible (needed on glibc/linux host builds). */
#ifndef _POSIX_C_SOURCE
#define _POSIX_C_SOURCE 200809L
#endif

/* xdelta3's command-line entry point, defined in xdelta3.c when XD3_MAIN=1. */
extern int main(int argc, char **argv);

#define XDELTA_JNI_VERSION JNI_VERSION_1_6

/* Last diagnostic text captured from xdelta3's stderr. Read by getLastError().
 * Safe because XdeltaApplier serializes apply() calls. */
static char g_last_error[2048];

JNIEXPORT jint JNICALL
Java_br_com_redclaw_zelda64player_patcher_xdelta_XdeltaApplier_applyNative(
        JNIEnv* env, jclass clazz,
        jstring src, jstring patch, jstring out) {
    (void) clazz;
    g_last_error[0] = '\0';

    const char* src_c = (*env)->GetStringUTFChars(env, src, NULL);
    const char* patch_c = (*env)->GetStringUTFChars(env, patch, NULL);
    const char* out_c = (*env)->GetStringUTFChars(env, out, NULL);
    if (src_c == NULL || patch_c == NULL || out_c == NULL) {
        if (src_c) (*env)->ReleaseStringUTFChars(env, src, src_c);
        if (patch_c) (*env)->ReleaseStringUTFChars(env, patch, patch_c);
        if (out_c) (*env)->ReleaseStringUTFChars(env, out, out_c);
        return -1;
    }

    /* Redirect stderr to a pipe so we can classify failures from xdelta3's
     * diagnostic output (e.g. "source file ... mismatch"). */
    int pipefd[2];
    int saved_stderr = -1;
    int use_capture = (pipe(pipefd) == 0);
    if (use_capture) {
        saved_stderr = dup(STDERR_FILENO);
        dup2(pipefd[1], STDERR_FILENO);
        close(pipefd[1]);
    }

    /* Avoid inheriting a process-wide XDELTA environment variable that would
     * inject unexpected arguments into the command line. */
#ifdef __unix__
    unsetenv("XDELTA");
#endif

    char* argv[8];
    argv[0] = "xdelta3";
    argv[1] = "-d";   /* decode */
    argv[2] = "-f";   /* force overwrite of the output file */
    argv[3] = "-s";   /* source file */
    argv[4] = (char*) src_c;
    argv[5] = (char*) patch_c;
    argv[6] = (char*) out_c;
    argv[7] = NULL;

    int ret = main(7, argv);

    if (use_capture) {
        fflush(stderr);
        /* Restore the original stderr, then drain the pipe. */
        close(STDERR_FILENO);
        dup2(saved_stderr, STDERR_FILENO);
        close(saved_stderr);
        char buf[1024];
        ssize_t n;
        size_t total = 0;
        while ((n = read(pipefd[0], buf, sizeof(buf))) > 0 && total < sizeof(g_last_error) - 1) {
            size_t take = (size_t) n;
            if (total + take > sizeof(g_last_error) - 1) {
                take = sizeof(g_last_error) - 1 - total;
            }
            memcpy(g_last_error + total, buf, take);
            total += take;
        }
        g_last_error[total] = '\0';
        close(pipefd[0]);
    }

    (*env)->ReleaseStringUTFChars(env, src, src_c);
    (*env)->ReleaseStringUTFChars(env, patch, patch_c);
    (*env)->ReleaseStringUTFChars(env, out, out_c);

    return ret;
}

JNIEXPORT jstring JNICALL
Java_br_com_redclaw_zelda64player_patcher_xdelta_XdeltaApplier_getLastError(
        JNIEnv* env, jclass clazz) {
    (void) clazz;
    return (*env)->NewStringUTF(env, g_last_error);
}
