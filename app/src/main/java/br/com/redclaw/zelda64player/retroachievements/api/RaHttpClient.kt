package br.com.redclaw.zelda64player.retroachievements.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Result of a single RetroAchievements web API round trip.
 *
 * [statusCode] is the HTTP status (0 when the transport itself failed, in
 * which case [error] carries the reason). [bodyBytes] is the raw response
 * payload, forwarded to rcheevos untouched.
 */
data class RaHttpResponse(
    val statusCode: Int,
    val bodyBytes: ByteArray?,
    val error: String? = null
) {
    val isSuccessful: Boolean get() = error == null && statusCode in 200..299

    fun bodyAsString(): String? =
        bodyBytes?.toString(Charsets.UTF_8)
}

/**
 * Minimal OkHttp executor for the RetroAchievements bridge.
 *
 * Two consumers share this client:
 * 1. The rc_client server_call dispatcher ([RaSessionManager]) which forwards
 *    requests marshalled from native code.
 * 2. Standalone install-time calls (resolve hash) built by the rapi helpers.
 *
 * The User-Agent follows the rcheevos contract:
 * `<product>/<semver> (<system-info>) <extensions>`. Hardcore mode stays
 * disabled until RAdmin validates this UA for the app.
 *
 * @param userAgent Full User-Agent header value sent with every request.
 * @param client Shared OkHttp instance; defaults to one tuned for the RA API
 *   (30s timeouts, no cookie/session state).
 */
class RaHttpClient(
    private val userAgent: String,
    private val client: OkHttpClient = defaultOkHttp()
) {

    /**
     * Executes [url] as GET (when [postData] is null) or POST.
     * Never throws: transport failures are reported as
     * [RaHttpResponse] with statusCode 0 and a non-null [RaHttpResponse.error].
     */
    suspend fun execute(url: String, postData: String? = null): RaHttpResponse =
        withContext(Dispatchers.IO) {
            try {
                val builder = Request.Builder()
                    .url(url)
                    .header("User-Agent", userAgent)
                if (postData != null) {
                    builder.post(
                        postData.toRequestBody("application/x-www-form-urlencoded".toMediaTypeOrNull())
                    )
                }
                client.newCall(builder.build()).execute().use { response ->
                    val bytes = response.body?.bytes()
                    RaHttpResponse(response.code, bytes)
                }
            } catch (e: IOException) {
                RaHttpResponse(0, null, e.message ?: "network error")
            } catch (e: IllegalArgumentException) {
                RaHttpResponse(0, null, "invalid url")
            }
        }

    private companion object {
        fun defaultOkHttp(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
