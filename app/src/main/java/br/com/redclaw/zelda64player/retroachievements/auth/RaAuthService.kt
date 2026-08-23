package br.com.redclaw.zelda64player.retroachievements.auth

import br.com.redclaw.zelda64player.retroachievements.api.RaHttpClient
import br.com.redclaw.zelda64player.retroachievements.jni.RaNativeListener
import br.com.redclaw.zelda64player.retroachievements.jni.RcheevosJni
import br.com.redclaw.zelda64player.retroachievements.jni.executeServerRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Interactive RetroAchievements login/logout used by the settings screen.
 *
 * Creates a temporary rc_client for the credential exchange, persists the
 * issued token via [RaCredentialStore], then destroys the client again —
 * game sessions create their own client later. Only one native client exists
 * at a time, so this must not run while a game session is active (the
 * settings screen is only reachable outside gameplay, which guarantees it).
 *
 * Credentials are never logged; failures carry sanitized server messages.
 *
 * @param credentials Encrypted credential storage.
 * @param http Shared RA HTTP executor satisfying rc_client requests.
 */
class RaAuthService(
    private val credentials: RaCredentialStore,
    private val http: RaHttpClient
) : RaNativeListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Pending login op; single-flight by construction (UI button). */
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<RaLoginResult>>()
    private val nextOpId = AtomicInteger(1)

    /**
     * Logs [username] in with [password]. On success the username + token are
     * persisted and any previous credentials are replaced.
     */
    suspend fun login(username: String, password: String): Result<Unit> {
        RcheevosJni.nativeCreateClient(this)
        try {
            val opId = nextOpId.getAndIncrement()
            val deferred = CompletableDeferred<RaLoginResult>()
            pending[opId] = deferred
            RcheevosJni.nativeBeginLoginWithPassword(username, password, opId)
            val result = deferred.await()
            return if (result.success) {
                // Persist for silent token re-login during gameplay sessions.
                // The token is secret: stored encrypted, never logged.
                credentials.setCredentials(username, result.token.orEmpty())
                Result.success(Unit)
            } else {
                Result.failure(RaAuthException(result.error ?: "login failed"))
            }
        } catch (e: CancellationException) {
            throw e
        } finally {
            pending.clear()
            RcheevosJni.nativeDestroyClient()
        }
    }

    /** Clears stored credentials and logs the native client out if present. */
    fun logout() {
        credentials.clear()
        if (RcheevosJni.nativeHasClient()) {
            RcheevosJni.nativeLogout()
        }
    }

    override fun onServerRequest(requestId: Int, url: String, postData: String?) {
        executeServerRequest(scope, http, requestId, url, postData)
    }

    override fun onAsyncResult(opId: Int, resultCode: Int, errorMessage: String?) {
        pending.remove(opId)?.complete(
            RaLoginResult(
                success = resultCode == RC_OK,
                token = extractToken(),
                error = errorMessage
            )
        )
    }

    override fun onClientEvent(eventType: Int, payloadJson: String) = Unit

    /**
     * Reads the session token issued by the just-completed login from the
     * user info JSON. Returns null when unavailable; never logged.
     */
    private fun extractToken(): String? = runCatching {
        val raw = RcheevosJni.nativeGetUserInfoJson()
        if (raw == "null") return null
        org.json.JSONObject(raw).optString("token").takeIf { it.isNotBlank() }
    }.getOrNull()

    private data class RaLoginResult(val success: Boolean, val token: String?, val error: String?)

    private companion object {
        const val RC_OK = 0
    }
}

/** Failure surfaced by auth operations; messages are log-safe. */
class RaAuthException(message: String) : Exception(message)
