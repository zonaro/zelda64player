package br.com.redclaw.zelda64player.retroachievements.session

import android.content.Context
import android.util.Log
import br.com.redclaw.zelda64player.retroachievements.api.RaHttpClient
import br.com.redclaw.zelda64player.retroachievements.auth.RaCredentialStore
import br.com.redclaw.zelda64player.retroachievements.data.RaInstallMetadataStore
import br.com.redclaw.zelda64player.retroachievements.jni.RaNativeListener
import br.com.redclaw.zelda64player.retroachievements.jni.RcheevosJni
import br.com.redclaw.zelda64player.retroachievements.jni.executeServerRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** User-facing session lifecycle states surfaced to ViewModels/UI. */
sealed interface RaSessionState {

    /** Session not started (no game running or feature disabled globally). */
    data object Idle : RaSessionState

    /** Feature enabled in settings but the user has no stored credentials. */
    data object NotLoggedIn : RaSessionState

    /** Silent token login in progress after game start. */
    data object LoggingIn : RaSessionState

    /** Hashing/identifying the loaded ROM against the RA database. */
    data object LoadingGame : RaSessionState

    /**
     * Achievements active for [game]. Evaluation ticks per rendered frame.
     */
    data class Running(val game: RaGameSummary) : RaSessionState

    /** Terminal failure for this session; [message] is a log-safe reason. */
    data class Failed(val message: String) : RaSessionState
}

/** Parsed snapshot of the currently loaded RA game (from game info JSON). */
data class RaGameSummary(
    val id: Long,
    val title: String,
    val hash: String,
    val badgeUrl: String?,
    val numCoreAchievements: Int,
    val numUnlockedAchievements: Int,
    val pointsCore: Int,
    val pointsUnlocked: Int
)

/**
 * A client event marshalled from native code, consumed by UI layers (unlock
 * popups, challenge indicators, leaderboard toasts).
 */
data class RaClientEvent(val eventType: Int, val payloadJson: String)

/**
 * Orchestrates the RetroAchievements client lifecycle around a running game.
 *
 * Flow: [start] is invoked once the first frame rendered and the patched ROM
 * exists. If credentials are stored, a silent token login runs, then rhash
 * identifies the ROM and achievements activate. [onFrame] advances evaluation
 * once per rendered frame; [stop] tears everything down (called from the
 * ViewModel dispose path).
 *
 * Threading contract:
 * - [start]/[stop]/[onFrame] run on the main thread (GameActivity lifecycle).
 * - HTTP responses complete on OkHttp worker threads through
 *   [RcheevosJni.nativeCompleteServerRequest]; rcheevos re-enters our event
 *   handler there, which only posts to flows (thread-safe).
 * - Login/load results arrive on arbitrary threads via [onAsyncResult] and
 *   complete pending coroutines.
 *
 * Credentials never appear in logs; failures carry sanitized messages only.
 *
 * @param context Application context.
 * @param http Shared RA HTTP executor.
 * @param credentials Encrypted credential storage.
 * @param metadataStore Per-hack install-time identities (library screens).
 */
class RaSessionManager(
    private val context: Context,
    private val http: RaHttpClient,
    private val credentials: RaCredentialStore,
    @Suppress("unused") private val metadataStore: RaInstallMetadataStore
) : RaNativeListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<RaSessionState>(RaSessionState.Idle)
    val state: StateFlow<RaSessionState> = _state

    private val _events = MutableSharedFlow<RaClientEvent>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<RaClientEvent> = _events.asSharedFlow()

    /** Pending async operations keyed by opId (login / identify+load). */
    private val pendingOps = ConcurrentHashMap<Int, PendingOp>()
    private val nextOpId = AtomicInteger(1)

    /** Memory region provider installed at start; nulled at stop. */
    private var memoryRegionProvider: (() -> ByteBuffer?)? = null

    /** True between successful start() and stop(). */
    @Volatile
    private var sessionActive = false

    /** True while a game is loaded client-side (gates doFrame work). */
    @Volatile
    private var gameLoaded = false

    private data class PendingOp(val deferred: kotlinx.coroutines.CompletableDeferred<RaOpResult>)

    // ------------------------------------------------------------------ //
    // Lifecycle                                                           //
    // ------------------------------------------------------------------ //

    /**
     * Starts the session for [romFile]. Must be called on the main thread
     * after the first frame rendered. [memoryRegionProvider] supplies the
     * direct ByteBuffer aliasing core SYSTEM_RAM; it is consulted lazily once
     * the game loads (the region is only valid while the core holds the ROM).
     * [hardcoreEnabled] mirrors the user preference; hardcore stays off by
     * default until the app's User-Agent is validated with RAdmin.
     */
    fun start(
        romFile: File,
        hardcoreEnabled: Boolean = false,
        memoryRegionProvider: () -> ByteBuffer?
    ) {
        if (sessionActive) return
        if (!romFile.exists()) {
            _state.value = RaSessionState.Failed("ROM missing: ${romFile.name}")
            return
        }

        this.memoryRegionProvider = memoryRegionProvider
        sessionActive = true

        val username = credentials.getUsername()
        val token = credentials.getToken()
        if (username.isNullOrBlank() || token.isNullOrBlank()) {
            _state.value = RaSessionState.NotLoggedIn
            return
        }

        RcheevosJni.nativeCreateClient(this)
        RcheevosJni.nativeSetHardcoreEnabled(hardcoreEnabled)
        loginWithToken(username, token, romFile)
    }

    /**
     * Advances achievement/leaderboard evaluation by one frame. Cheap no-op
     * unless a game is loaded; safe to call for every rendered frame.
     */
    fun onFrame() {
        if (gameLoaded) {
            RcheevosJni.nativeDoFrame()
        }
    }

    /**
     * Tears the session down. Idempotent; must be called before the emulated
     * core is destroyed (memory alias becomes invalid afterwards).
     */
    fun stop() {
        if (!sessionActive) return
        sessionActive = false
        gameLoaded = false
        memoryRegionProvider = null

        try {
            RcheevosJni.nativeSetMemoryRegion(null)
            RcheevosJni.nativeUnloadGame()
            RcheevosJni.nativeDestroyClient()
        } catch (e: Exception) {
            Log.w(TAG, "RA teardown issue", e)
        }
        _state.value = RaSessionState.Idle
    }

    /** Silent token re-login, then game identification/loading. */
    private fun loginWithToken(username: String, token: String, romFile: File) {
        _state.value = RaSessionState.LoggingIn
        scope.launch {
            val result = awaitOp { opId ->
                RcheevosJni.nativeBeginLoginWithToken(username, token, opId)
            }
            if (!sessionActive) return@launch
            if (result.isSuccess) {
                identifyAndLoad(romFile)
            } else {
                Log.w(TAG, "RA token login failed: ${result.error}")
                _state.value = RaSessionState.Failed(result.error ?: "login failed")
            }
        }
    }

    private fun identifyAndLoad(romFile: File) {
        _state.value = RaSessionState.LoadingGame
        scope.launch {
            val result = awaitOp { opId ->
                RcheevosJni.nativeIdentifyAndLoadGame(romFile.absolutePath, opId)
            }
            if (!sessionActive) return@launch
            if (result.isSuccess) {
                attachMemoryAndRun()
            } else {
                Log.w(TAG, "RA identify/load failed: ${result.error}")
                _state.value = RaSessionState.Failed(result.error ?: "load failed")
            }
        }
    }

    private fun attachMemoryAndRun() {
        val buffer = memoryRegionProvider?.invoke()
        RcheevosJni.nativeSetMemoryRegion(buffer)
        gameLoaded = buffer != null
        _state.value = parseGameInfo()?.let { RaSessionState.Running(it) }
            ?: RaSessionState.Failed("game info unavailable")
    }

    // ------------------------------------------------------------------ //
    // Structured getters                                                  //
    // ------------------------------------------------------------------ //

    /** Parsed user info, or null when logged out. */
    fun userInfoJson(): JSONObject? = jsonOrNull(RcheevosJni.nativeGetUserInfoJson())

    /** Parsed game info + unlock summary, or null when no game is loaded. */
    fun gameInfoJson(): JSONObject? = jsonOrNull(RcheevosJni.nativeGetGameInfoJson())

    /**
     * All achievements grouped by bucket as a parsed JSON array; empty when
     * no game is loaded. Blocking native call: invoke off the main thread for
     * large sets.
     */
    fun achievementListJson(): JSONArray =
        runCatching { JSONArray(RcheevosJni.nativeGetAchievementListJson()) }
            .getOrDefault(JSONArray())

    private fun jsonOrNull(raw: String): JSONObject? = runCatching {
        if (raw == "null") null else JSONObject(raw)
    }.getOrNull()

    // ------------------------------------------------------------------ //
    // RaNativeListener                                                    //
    // ------------------------------------------------------------------ //

    override fun onServerRequest(requestId: Int, url: String, postData: String?) {
        executeServerRequest(scope, http, requestId, url, postData)
    }

    override fun onAsyncResult(opId: Int, resultCode: Int, errorMessage: String?) {
        pendingOps.remove(opId)?.deferred?.complete(
            RaOpResult(isSuccess = resultCode == RC_OK, error = errorMessage)
        )
    }

    override fun onClientEvent(eventType: Int, payloadJson: String) {
        _events.tryEmit(RaClientEvent(eventType, payloadJson))
    }

    // ------------------------------------------------------------------ //
    // Internals                                                           //
    // ------------------------------------------------------------------ //

    private data class RaOpResult(val isSuccess: Boolean, val error: String?)

    /** Registers a deferred op, invokes [begin], and suspends until completion. */
    private suspend fun awaitOp(begin: (Int) -> Unit): RaOpResult {
        val opId = nextOpId.getAndIncrement()
        val deferred = CompletableDeferred<RaOpResult>()
        pendingOps[opId] = PendingOp(deferred)
        begin(opId)
        return try {
            deferred.await()
        } catch (e: CancellationException) {
            pendingOps.remove(opId)
            throw e
        }
    }

    private fun parseGameInfo(): RaGameSummary? {
        val json = gameInfoJson() ?: return null
        return runCatching {
            RaGameSummary(
                id = json.getLong("id"),
                title = json.optString("title"),
                hash = json.optString("hash"),
                badgeUrl = json.optString("badge_url").takeIf { it.isNotBlank() },
                numCoreAchievements = json.optInt("num_core_achievements"),
                numUnlockedAchievements = json.optInt("num_unlocked_achievements"),
                pointsCore = json.optInt("points_core"),
                pointsUnlocked = json.optInt("points_unlocked")
            )
        }.getOrNull()
    }

    private companion object {
        const val TAG = "RaSessionManager"

        /** RC_OK from rc_error.h; success code of all async operations. */
        const val RC_OK = 0
    }
}
