package br.com.redclaw.zelda64player.store

import br.com.redclaw.zelda64player.data.model.HackEntry
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Per-source result metadata for a catalog fetch. */
data class CatalogSourceInfo(
        val storeId: String,
        val sourceId: String,
        val displayName: String,
        val fromCache: Boolean,
        val etag: String?,
        val error: String? = null
)

/** Merged result of fetching one or more catalog sources. */
data class CatalogFetchResult(val hacks: List<HackEntry>, val sources: List<CatalogSourceInfo>)

/**
 * Fetches hack catalogs over HTTP with ETag/If-None-Match conditional GETs.
 *
 * Two entry points:
 * - [fetch] — legacy single-or-multi URL fetch of Picks-style catalogs.
 * - [fetchCatalogs] — fetches the default Main Store catalog and any
 * ```
 *    user-added Main Store catalogs described by [CatalogSourceMeta].
 * ```
 * Each URL is cached on disk (`cacheDir/catalog_<urlHash>.json` plus an `.etag` sidecar). A `304
 * Not Modified` reuses the cache; a network failure falls back to the last cached copy so the store
 * still works offline.
 */
class CatalogFetcher(
        private val client: OkHttpClient,
        private val cacheDir: File,
        private val defaultUrls: List<String> = listOf(DEFAULT_CATALOG_URL)
) {
    init {
        cacheDir.mkdirs()
    }

    // ------------------------------------------------------------------
    // Legacy PICKS-style fetch (kept for backward compatibility).
    // ------------------------------------------------------------------
    suspend fun fetch(urls: List<String> = defaultUrls): Result<CatalogFetchResult> =
            withContext(Dispatchers.IO) {
                val sources = mutableListOf<CatalogSourceInfo>()
                val perCatalog = mutableListOf<List<HackEntry>>()
                for (url in urls) {
                    val info = fetchUrl(url)
                    val parser = PicksCatalogParser()
                    when (info) {
                        is FetchResult.Ok -> {
                            runCatching { parser.parse(info.body) }.onSuccess { perCatalog.add(it) }
                            sources.add(
                                    CatalogSourceInfo(
                                            storeId = "picks",
                                            sourceId = "picks",
                                            displayName = url,
                                            fromCache = info.fromCache,
                                            etag = info.etag
                                    )
                            )
                        }
                        is FetchResult.Error -> {
                            val cached = info.cachedBody
                            if (cached != null) {
                                runCatching { parser.parse(cached) }.onSuccess {
                                    perCatalog.add(it)
                                }
                                sources.add(
                                        CatalogSourceInfo(
                                                storeId = "picks",
                                                sourceId = "picks",
                                                displayName = url,
                                                fromCache = true,
                                                etag = null,
                                                error = info.message
                                        )
                                )
                            } else {
                                sources.add(
                                        CatalogSourceInfo(
                                                storeId = "picks",
                                                sourceId = "picks",
                                                displayName = url,
                                                fromCache = false,
                                                etag = null,
                                                error = info.message
                                        )
                                )
                            }
                        }
                    }
                }
                Result.success(CatalogFetchResult(CatalogMerger.merge(perCatalog), sources))
            }

    /** Fetches and merges the Main Store catalog sources. */
    suspend fun fetchCatalogs(sources: List<CatalogSourceMeta>): Result<CatalogFetchResult> =
            withContext(Dispatchers.IO) {
                val sourceInfos = mutableListOf<CatalogSourceInfo>()
                val perCatalog = mutableListOf<List<HackEntry>>()

                for (meta in sources) {
                    fetchPicksSource(meta, sourceInfos, perCatalog)
                }
                Result.success(CatalogFetchResult(CatalogMerger.merge(perCatalog), sourceInfos))
            }

    private fun fetchPicksSource(
            meta: CatalogSourceMeta,
            sourceInfos: MutableList<CatalogSourceInfo>,
            perCatalog: MutableList<List<HackEntry>>
    ) {
        val info = fetchUrl(meta.url)
        val parser = PicksCatalogParser()
        when (info) {
            is FetchResult.Ok -> {
                runCatching { parser.parse(info.body) }.onSuccess { perCatalog.add(it) }
                sourceInfos.add(
                        CatalogSourceInfo(
                                storeId = BuiltInStores.STORE_PICKS,
                                sourceId = meta.id,
                                displayName = meta.displayName,
                                fromCache = info.fromCache,
                                etag = info.etag
                        )
                )
            }
            is FetchResult.Error -> {
                val cached = info.cachedBody
                if (cached != null) {
                    runCatching { parser.parse(cached) }.onSuccess { perCatalog.add(it) }
                    sourceInfos.add(
                            CatalogSourceInfo(
                                    storeId = BuiltInStores.STORE_PICKS,
                                    sourceId = meta.id,
                                    displayName = meta.displayName,
                                    fromCache = true,
                                    etag = null,
                                    error = info.message
                            )
                    )
                } else {
                    sourceInfos.add(
                            CatalogSourceInfo(
                                    storeId = BuiltInStores.STORE_PICKS,
                                    sourceId = meta.id,
                                    displayName = meta.displayName,
                                    fromCache = false,
                                    etag = null,
                                    error = info.message
                            )
                    )
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Shared HTTP + disk-cache primitive.
    // ------------------------------------------------------------------
    private sealed class FetchResult {
        data class Ok(val body: String, val etag: String?, val fromCache: Boolean) : FetchResult()
        data class Error(val message: String?, val cachedBody: String?) : FetchResult()
    }

    private fun fetchUrl(url: String): FetchResult {
        val cacheFile = cacheFileFor(url)
        val cachedEtag = readCachedEtag(cacheFile)
        return try {
            val requestBuilder = Request.Builder().url(url)
            if (cachedEtag != null) requestBuilder.header("If-None-Match", cachedEtag)
            val response = client.newCall(requestBuilder.build()).execute()
            try {
                val etag = response.header("ETag")
                when {
                    response.code == 304 && cacheFile.exists() -> {
                        FetchResult.Ok(cacheFile.readText(), cachedEtag, fromCache = true)
                    }
                    response.isSuccessful -> {
                        val body = response.body?.string().orEmpty()
                        writeCache(cacheFile, body, etag)
                        FetchResult.Ok(body, etag, fromCache = false)
                    }
                    else -> {
                        if (cacheFile.exists()) {
                            FetchResult.Error("HTTP ${response.code}", cacheFile.readText())
                        } else {
                            FetchResult.Error("HTTP ${response.code}", null)
                        }
                    }
                }
            } finally {
                response.close()
            }
        } catch (e: Exception) {
            if (cacheFile.exists()) {
                FetchResult.Error(e.message, cacheFile.readText())
            } else {
                FetchResult.Error(e.message, null)
            }
        }
    }

    private fun cacheFileFor(url: String): File = File(cacheDir, "catalog_${urlHash(url)}.json")

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
