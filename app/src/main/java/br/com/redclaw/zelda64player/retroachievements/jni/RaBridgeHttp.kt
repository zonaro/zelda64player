package br.com.redclaw.zelda64player.retroachievements.jni

import br.com.redclaw.zelda64player.retroachievements.api.RaHttpClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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
    postData: String?
) {
    scope.launch {
        val response = http.execute(url, postData)
        RcheevosJni.nativeCompleteServerRequest(
            requestId,
            response.statusCode,
            response.bodyBytes,
            response.error
        )
    }
}
