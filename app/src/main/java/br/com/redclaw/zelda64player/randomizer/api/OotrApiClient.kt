package br.com.redclaw.zelda64player.randomizer.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit

/**
 * Thin coroutine-friendly HTTP client for the OoTR (Ocarina of Time
 * Randomizer) web API.
 *
 * Every request is rate-limited through [rateLimiter] (20 requests / 10s) and
 * executed on [Dispatchers.IO]. The API key is sent only as a query parameter
 * to the OoTR endpoint and is **never** written to logs or exception messages
 * (it is masked as "***" wherever a value must be shown).
 *
 * @param client Underlying OkHttp client (timeouts configured by the caller).
 * @param rateLimiter Token-bucket limiter shared across all endpoints.
 * @param baseUrl Root of the OoTR API (no trailing slash).
 */
class OotrApiClient(
    private val client: OkHttpClient,
    private val rateLimiter: OotrRateLimiter = OotrRateLimiter(),
    private val baseUrl: String = "https://ootrandomizer.com/api"
) {
    private val baseHttpUrl = baseUrl.toHttpUrl()

    /**
     * Create a new seed.
     *
     * @param settingsJson Full settings object sent as the JSON request body.
     * @param apiKey User's OoTR API key (masked in logs/errors).
     * @param version Optional OoTR version pin; `null` lets the server choose.
     * @param locked When true, the seed settings are locked (not re-rollable).
     * @param seed Optional fixed seed string for reproducible generation; `null`
     *   lets the server generate a random seed.
     * @return [SeedCreateResponse] with the new seed id.
     */
    suspend fun createSeed(
        settingsJson: JSONObject,
        apiKey: String,
        version: String? = null,
        locked: Boolean = false,
        seed: String? = null,
        plandomizerJson: JSONObject? = null
    ): SeedCreateResponse = withContext(Dispatchers.IO) {
        requireKey(apiKey)
        val url = apiUrl("v2/seed/create") {
            addQueryParameter("key", apiKey)
            if (version != null) addQueryParameter("version", version)
            if (seed != null) addQueryParameter("seed", seed)
            addQueryParameter("locked", locked.toString())
        }
        // Merge the Plandomizer placement file into the settings body. This is
        // our best-guess transport for the (undocumented) distribution-file
        // feature; isolated here so it is trivial to adjust after live testing.
        val bodyJson = attachPlandomizer(settingsJson, plandomizerJson)
        val body = bodyJson.toString().toRequestBody(JSON)
        val request = Request.Builder().url(url).post(body).build()
        val response = execute(request)
        if (response.code == HttpURLConnection.HTTP_NO_CONTENT) {
            response.close()
            throw OotrApiException.StillGenerating
        }
        if (!response.isSuccessful) {
            val text = response.body?.string().orEmpty()
            response.close()
            throw mapError(response.code, text)
        }
        val json = JSONObject(response.body?.string().orEmpty())
        response.close()
        SeedCreateResponse(
            id = json.getString("id"),
            version = json.getString("version"),
            spoilers = json.optBoolean("spoilers", false)
        )
    }

    /**
     * Poll the generation status of a seed.
     *
     * @return [SeedStatus]; a `204 No Content` response maps to
     *   [OotrApiException.StillGenerating].
     */
    suspend fun getSeedStatus(apiKey: String, seedId: String): SeedStatus =
        withContext(Dispatchers.IO) {
            requireKey(apiKey)
            val url = apiUrl("v2/seed/status") {
                addQueryParameter("key", apiKey)
                addQueryParameter("id", seedId)
            }
            val request = Request.Builder().url(url).get().build()
            val response = execute(request)
            if (response.code == HttpURLConnection.HTTP_NO_CONTENT) {
                response.close()
                throw OotrApiException.StillGenerating
            }
            if (!response.isSuccessful) {
                val text = response.body?.string().orEmpty()
                response.close()
                throw mapError(response.code, text)
            }
            val json = JSONObject(response.body?.string().orEmpty())
            response.close()
            SeedStatus(
                status = json.getInt("status"),
                progress = json.getInt("progress"),
                positionQueue = json.optInt("positionQueue", -1).let { if (it < 0) null else it },
                maxWaitTime = json.optInt("maxWaitTime", -1).let { if (it < 0) null else it }
            )
        }

    /**
     * Download the generated patch (ZPF/ZPFZ) for a seed, streaming the body
     * directly to [targetFile] without buffering it entirely in memory.
     *
     * @return [targetFile] after the stream has been fully written.
     */
    suspend fun downloadPatch(apiKey: String, seedId: String, targetFile: File): File =
        withContext(Dispatchers.IO) {
            requireKey(apiKey)
            val url = apiUrl("v2/seed/patch") {
                addQueryParameter("key", apiKey)
                addQueryParameter("id", seedId)
            }
            val request = Request.Builder().url(url).get().build()
            val response = execute(request)
            if (response.code == HttpURLConnection.HTTP_NO_CONTENT) {
                response.close()
                throw OotrApiException.StillGenerating
            }
            if (!response.isSuccessful) {
                val text = response.body?.string().orEmpty()
                response.close()
                throw mapError(response.code, text)
            }
            try {
                targetFile.outputStream().use { out ->
                    response.body?.byteStream()?.use { input ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            out.write(buffer, 0, read)
                        }
                    } ?: throw OotrApiException.ServerError(response.code)
                }
            } finally {
                response.close()
            }
            targetFile
        }

    /**
     * Fetch the OoTR versions available for a given build [branch].
     *
     * @param branch Build branch to query (defaults to "master").
     * @return List of available version strings.
     */
    suspend fun fetchAvailableVersions(branch: String = "master"): List<String> =
        withContext(Dispatchers.IO) {
            val url = apiUrl("version") {
                addQueryParameter("branch", branch)
            }
            val request = Request.Builder().url(url).get().build()
            val response = execute(request)
            if (response.code == HttpURLConnection.HTTP_NO_CONTENT) {
                response.close()
                throw OotrApiException.StillGenerating
            }
            if (!response.isSuccessful) {
                val text = response.body?.string().orEmpty()
                response.close()
                throw mapError(response.code, text)
            }
            val json = JSONObject(response.body?.string().orEmpty())
            response.close()
            val arr: JSONArray = json.optJSONArray("availableVersions") ?: JSONArray()
            (0 until arr.length()).map { arr.getString(it) }
        }

    /**
     * Acquire a rate-limit slot, execute the [request], and translate IO
     * failures into [OotrApiException.NetworkError]. The caller is responsible
     * for consuming and closing the returned [okhttp3.Response].
     */
    private suspend fun execute(request: Request): okhttp3.Response {
        rateLimiter.acquire()
        return try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw OotrApiException.NetworkError(e)
        }
    }

    private fun apiUrl(path: String, block: okhttp3.HttpUrl.Builder.() -> Unit): okhttp3.HttpUrl =
        baseHttpUrl.newBuilder().addPathSegments(path).apply(block).build()

    private fun requireKey(apiKey: String) {
        if (apiKey.isBlank()) throw OotrApiException.MissingApiKey
    }

    /**
     * Merge a Plandomizer placement file into the settings body.
     *
     * When [plandomizer] is non-null we enable the distribution file and inline
     * the placement object. The settings map is copied first so the caller's
     * original object is never mutated.
     */
    private fun attachPlandomizer(settings: JSONObject, plandomizer: JSONObject?): JSONObject {
        if (plandomizer == null) return settings
        val merged = JSONObject(settings.toString())
        merged.put("enable_distribution_file", true)
        merged.put("distribution_file", plandomizer)
        return merged
    }

    private fun mapError(code: Int, body: String?): OotrApiException = when (code) {
        HttpURLConnection.HTTP_BAD_REQUEST ->
            OotrApiException.InvalidSettings(body?.takeIf { it.isNotBlank() })
        HttpURLConnection.HTTP_NOT_FOUND -> OotrApiException.SeedNotFound
        HttpURLConnection.HTTP_CONFLICT -> OotrApiException.VersionNotAvailable
        423 -> OotrApiException.QueueFull
        429 -> OotrApiException.RateLimited
        in 500..599 -> OotrApiException.ServerError(code)
        else -> OotrApiException.ServerError(code)
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()

        /**
         * Build a client with the project-standard timeouts (15s connect,
         * 60s read) and a fresh [OotrRateLimiter].
         */
        fun default(baseUrl: String = "https://ootrandomizer.com/api"): OotrApiClient {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            return OotrApiClient(client, OotrRateLimiter(), baseUrl)
        }
    }
}
