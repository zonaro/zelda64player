package br.com.redclaw.zelda64player.store

import br.com.redclaw.zelda64player.data.model.HackCatalog
import br.com.redclaw.zelda64player.data.model.HackEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

/** Per-source result metadata for a catalog fetch. */
data class CatalogSourceInfo(
    val url: String,
    val fromCache: Boolean,
    val etag: String?,
    val error: String? = null
)

/** Merged result of fetching one or more catalog URLs. */
data class CatalogFetchResult(
    val hacks: List<HackEntry>,
    val sources: List<CatalogSourceInfo>
)

/**
 * Fetches hack catalogs over HTTP with ETag/If-None-Match conditional GETs.
 *
 * Each URL is cached on disk (`cacheDir/catalog_<urlHash>.json` plus an `.etag`
 * sidecar). A `304 Not Modified` reuses the cache; a network failure falls back
 * to the last cached copy so the store still works offline. Results from all
 * URLs are merged by id (later catalog wins) via [CatalogMerger].
 *
 * The network layer is intentionally thin and the [OkHttpClient] is injected so
 * the merge/cache logic can be exercised without real network calls.
 */
class CatalogFetcher(
    private val client: OkHttpClient,
    private val cacheDir: File,
    private val defaultUrls: List<String> = listOf(DEFAULT_CATALOG_URL)
) {
    init {
        cacheDir.mkdirs()
    }

    suspend fun fetch(urls: List<String> = defaultUrls): Result<CatalogFetchResult> =
        withContext(Dispatchers.IO) {
            val sources = mutableListOf<CatalogSourceInfo>()
            val perCatalog = mutableListOf<List<HackEntry>>()

            for (url in urls) {
                val cacheFile = cacheFileFor(url)
                val cachedEtag = readCachedEtag(cacheFile)
                try {
                    val requestBuilder = Request.Builder().url(url)
                    if (cachedEtag != null) requestBuilder.header("If-None-Match", cachedEtag)
                    val response = client.newCall(requestBuilder.build()).execute()
                    try {
                        val etag = response.header("ETag")
                        when {
                            response.code == 304 && cacheFile.exists() -> {
                                perCatalog.add(HackCatalog.parse(cacheFile.readText()).hacks)
                                sources.add(CatalogSourceInfo(url, fromCache = true, etag = cachedEtag))
                            }
                            response.isSuccessful -> {
                                val body = response.body?.string().orEmpty()
                                writeCache(cacheFile, body, etag)
                                perCatalog.add(HackCatalog.parse(body).hacks)
                                sources.add(CatalogSourceInfo(url, fromCache = false, etag = etag))
                            }
                            else -> {
                                if (cacheFile.exists()) {
                                    perCatalog.add(HackCatalog.parse(cacheFile.readText()).hacks)
                                    sources.add(
                                        CatalogSourceInfo(
                                            url, fromCache = true, etag = cachedEtag,
                                            error = "HTTP ${response.code}"
                                        )
                                    )
                                } else {
                                    sources.add(
                                        CatalogSourceInfo(
                                            url, fromCache = false, etag = null,
                                            error = "HTTP ${response.code}"
                                        )
                                    )
                                }
                            }
                        }
                    } finally {
                        response.close()
                    }
                } catch (e: Exception) {
                    // Network failure: fall back to the cached copy if we have one.
                    if (cacheFile.exists()) {
                        perCatalog.add(HackCatalog.parse(cacheFile.readText()).hacks)
                        sources.add(
                            CatalogSourceInfo(
                                url, fromCache = true, etag = cachedEtag, error = e.message
                            )
                        )
                    } else {
                        sources.add(
                            CatalogSourceInfo(url, fromCache = false, etag = null, error = e.message)
                        )
                    }
                }
            }

            Result.success(CatalogFetchResult(CatalogMerger.merge(perCatalog), sources))
        }

    private fun cacheFileFor(url: String): File =
        File(cacheDir, "catalog_${urlHash(url)}.json")

    private fun urlHash(url: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(url.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun readCachedEtag(cacheFile: File): String? {
        if (!cacheFile.exists()) return null
        val etagFile = File(cacheFile.parentFile, "${cacheFile.name}.etag")
        return if (etagFile.exists()) etagFile.readText().ifBlank { null } else null
    }

    private fun writeCache(cacheFile: File, body: String, etag: String?) {
        cacheFile.writeText(body)
        etag?.let {
            val etagFile = File(cacheFile.parentFile, "${cacheFile.name}.etag")
            etagFile.writeText(it)
        }
    }

    companion object {
        const val DEFAULT_CATALOG_URL =
            "https://raw.githubusercontent.com/zonaro/zelda64player/main/catalog/catalog.json"
    }
}
