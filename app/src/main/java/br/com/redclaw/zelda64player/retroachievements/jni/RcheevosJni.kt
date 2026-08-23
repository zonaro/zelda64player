package br.com.redclaw.zelda64player.retroachievements.jni

/**
 * Callbacks invoked from native code (possibly on non-main threads).
 *
 * Implementations must be cheap and must not block: heavy work belongs in
 * coroutines dispatched by the caller. All callbacks carry small JSON
 * payloads parsed with org.json on the Kotlin side.
 */
interface RaNativeListener {

    /**
     * rcheevos needs an HTTP round trip. [requestId] correlates the response;
     * [postData] is null for GET requests. Execute via OkHttp off the calling
     * thread, then call [RcheevosJni.nativeCompleteServerRequest].
     */
    fun onServerRequest(requestId: Int, url: String, postData: String?)

    /**
     * An async operation (login, identify/load game) finished.
     * [resultCode] is an RC_ error code (RC_OK == 0 on success);
     * [errorMessage] is a server/native message or null.
     */
    fun onAsyncResult(opId: Int, resultCode: Int, errorMessage: String?)

    /**
     * A client event fired (achievement unlocked, challenge indicator,
     * leaderboard start/submit, server error). [eventType] matches the
     * RC_CLIENT_EVENT_* values; [payloadJson] carries event details.
     */
    fun onClientEvent(eventType: Int, payloadJson: String)
}

/**
 * Thin JNI bridge to the vendored rcheevos library (libra_jni.so).
 *
 * Owns the rc_client lifecycle: creation binds a single [RaNativeListener];
 * HTTP stays in Kotlin (see [RaNativeListener.onServerRequest]); emulated
 * memory is aliased through [nativeSetMemoryRegion] once per game load.
 *
 * Threading: session calls (doFrame, login, loadGame) are main-thread entry
 * points; [nativeCompleteServerRequest] may be called from any OkHttp worker
 * thread. Structured getters return small JSON documents.
 */
object RcheevosJni {

    /** RC_CLIENT_EVENT_* values mirrored for Kotlin switch statements. */
    object Events {
        const val ACHIEVEMENT_TRIGGERED = 1
        const val LEADERBOARD_STARTED = 2
        const val LEADERBOARD_FAILED = 3
        const val LEADERBOARD_SUBMITTED = 4
        const val CHALLENGE_INDICATOR_SHOW = 5
        const val CHALLENGE_INDICATOR_HIDE = 6
        const val PROGRESS_INDICATOR_SHOW = 7
        const val PROGRESS_INDICATOR_HIDE = 8
        const val PROGRESS_INDICATOR_UPDATE = 9
        const val LEADERBOARD_TRACKER_SHOW = 10
        const val LEADERBOARD_TRACKER_HIDE = 11
        const val LEADERBOARD_TRACKER_UPDATE = 12
        const val SERVER_ERROR = 16
        const val DISCONNECTED = 17
    }

    init {
        System.loadLibrary("ra_jni")
    }

    /** Returns the rcheevos version string (e.g. "12.4.0"). */
    external fun getVersion(): String

    /** Creates the rc_client and binds [listener]. No-op if already created. */
    external fun nativeCreateClient(listener: RaNativeListener)

    /** Destroys the rc_client and releases the listener global ref. */
    external fun nativeDestroyClient()

    /** True while a client exists (between create/destroy). */
    external fun nativeHasClient(): Boolean

    /** Toggles hardcore mode. Defaults to disabled until UA validation. */
    external fun nativeSetHardcoreEnabled(enabled: Boolean)

    /** Starts password login; result arrives via onAsyncResult(opId, ...). */
    external fun nativeBeginLoginWithPassword(username: String, password: String, opId: Int)

    /** Starts token login; result arrives via onAsyncResult(opId, ...). */
    external fun nativeBeginLoginWithToken(username: String, token: String, opId: Int)

    /** Clears the logged-in user. */
    external fun nativeLogout()

    /**
     * Hashes [filePath] with rhash and identifies it against the RA database,
     * loading achievements when matched. Result via onAsyncResult(opId, ...).
     */
    external fun nativeIdentifyAndLoadGame(filePath: String, opId: Int)

    /** Unloads the current game if one is loaded. */
    external fun nativeUnloadGame()

    /**
     * Aliases emulated memory for rcheevos reads. Pass null to detach.
     * The buffer MUST be a direct ByteBuffer obtained from
     * GLRetroView.getMemoryRegion and must remain valid until replaced.
     */
    external fun nativeSetMemoryRegion(buffer: java.nio.ByteBuffer?)

    /** Advances achievement/leaderboard evaluation by one frame. */
    external fun nativeDoFrame()

    /**
     * Delivers an HTTP response previously requested via
     * [RaNativeListener.onServerRequest]. Call from any thread.
     * [statusCode] 0 with a non-null [errorMessage] marks a retryable
     * transport failure. Body bytes must be UTF-8 encoded.
     */
    external fun nativeCompleteServerRequest(
        requestId: Int,
        statusCode: Int,
        body: ByteArray?,
        errorMessage: String?
    )

    /** Logged-in user info as JSON, or "null" when logged out. */
    external fun nativeGetUserInfoJson(): String

    /** Loaded game info + unlock summary as JSON, or "null". */
    external fun nativeGetGameInfoJson(): String

    /**
     * All achievements grouped by bucket as a JSON array; empty array when no
     * game is loaded. Buckets follow lock-state grouping.
     */
    external fun nativeGetAchievementListJson(): String

    /**
     * Builds the standalone resolve-hash request for [gameHash].
     * Returns [url, postData] where postData may be null (GET), or null on
     * failure. Used at install time without a live session.
     */
    external fun nativeBuildResolveHashRequest(gameHash: String): Array<String>?

    /** Parses a resolve-hash response body; returns gameId or 0 if unmatched. */
    external fun nativeProcessResolveHashResponse(responseBody: String): Long

    /**
     * Computes the RetroAchievements hash of [filePath] using rhash with the
     * N64 console id. Returns "" on failure. Blocking file I/O: call off the
     * main thread.
     */
    external fun nativeComputeRomHash(filePath: String): String

    /**
     * Builds the fetch-game-data request for [gameId]. [username]/[apiToken]
     * may be blank (game data is public). Returns [url, postData] or null.
     */
    external fun nativeBuildFetchGameDataRequest(
        username: String,
        apiToken: String,
        gameId: Long
    ): Array<String>?

    /**
     * Parses a fetch-game-data response into a compact JSON document:
     * `{id,title,image_url,achievements:[...],leaderboards:[...]}`, or "null"
     * on failure. Blocking parse of a potentially large body: call off the
     * main thread.
     */
    external fun nativeProcessFetchGameDataResponse(responseBody: String): String

    /**
     * Builds the fetch-user-unlocks request for [gameId]. Credentials are
     * required by the server. Returns [url, postData] or null.
     */
    external fun nativeBuildFetchUserUnlocksRequest(
        username: String,
        apiToken: String,
        gameId: Long,
        hardcore: Boolean
    ): Array<String>?

    /** Parses a fetch-user-unlocks response into a JSON id array ("[]" on failure). */
    external fun nativeProcessFetchUserUnlocksResponse(responseBody: String): String
}
