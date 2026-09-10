package br.com.redclaw.zelda64player.retroachievements.jni

import br.com.redclaw.zelda64player.retroachievements.api.RaHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

/**
 * Shared bridge between rc_client's server_call callback and OkHttp.
 *
 * Both the gameplay session ([br.com.redclaw.zelda64player.retroachievements.session.RaSessionManager])
 * and interactive login ([br.com.redclaw.zelda64player.retroachievements.auth.RaAuthService])
 * satisfy rcheevos HTTP requests the same way: execute on [RaHttpClient]'s IO
 * context, then hand the raw response back to native code.
 */
fun executeServerRequest(
    scope: CoroutineScope,
    http: RaHttpClient,
    requestId: Int,
    url: String,
    postData: String?,
    outbox: br.com.redclaw.zelda64player.retroachievements.sync.RaAwardOutbox? = null
) {
    // Only awards are journaled, synchronously before the native unlock callback can return.
    val award = try { outbox?.capture(postData) } catch (_: Exception) {
        android.util.Log.w("RaBridgeHttp", "Unable to persist pending award")
        null
    }
    scope.launch(Dispatchers.IO) {
        val response = http.execute(url, postData)
        try { outbox?.acknowledge(award, response) } catch (_: Exception) {
            android.util.Log.w("RaBridgeHttp", "Unable to persist award acknowledgment")
        }
        RcheevosJni.nativeCompleteServerRequest(
            requestId,
            response.statusCode,
            response.bodyBytes,
            response.error
        )
    }
}
