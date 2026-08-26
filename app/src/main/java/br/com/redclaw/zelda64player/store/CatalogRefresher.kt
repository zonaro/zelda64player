package br.com.redclaw.zelda64player.store

import android.content.Context
import br.com.redclaw.zelda64player.data.local.MergedCatalogRepository
import br.com.redclaw.zelda64player.data.model.HackEntry
import br.com.redclaw.zelda64player.settings.CatalogUrlStore
import br.com.redclaw.zelda64player.settings.SharedPreferencesStore
import br.com.redclaw.zelda64player.store.BuiltInStores
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File

/**
 * Shared catalog refresh logic, used by both [StoreViewModel] (user-initiated
 * refresh) and the background [br.com.redclaw.zelda64player.work.CatalogRefreshWorker]
 * (periodic refresh). Fetches the default catalog URL plus any custom URLs
 * configured in Settings, merges them, and persists the result to the merged
 * catalog cache so the Library shows fresh data.
 */
class CatalogRefresher(context: Context) {
    private val appContext = context.applicationContext
    private val okHttpClient = OkHttpClient.Builder().build()
    private val cache = appContext.externalCacheDir ?: appContext.cacheDir
    private val mergedCatalogRepository =
        MergedCatalogRepository(File(appContext.filesDir, "merged_catalog.json"))
    private val catalogFetcher = CatalogFetcher(okHttpClient, File(cache, "catalog"))
    private val catalogUrlStore = CatalogUrlStore(
        SharedPreferencesStore(
            appContext.getSharedPreferences(CatalogUrlStore.PREFS_NAME, Context.MODE_PRIVATE)
        ),
        CatalogUrlStore.KEY
    )

    /**
     * Fetch, merge, and persist the catalog across all built-in stores (Hylian
     * Modding + Zelda 64 Picks) plus any user-added custom catalog URLs (merged
     * into the PICKS store for backward compatibility). Each hack is tagged with
     * its store/source so the Store UI can filter without re-fetching.
     *
     * @return the merged [CatalogFetchResult] (tagged hacks + per-source status)
     *   on success, or a failure carrying the error.
     */
    suspend fun refresh(): Result<CatalogFetchResult> = withContext(Dispatchers.IO) {
        val customUrls = catalogUrlStore.getUrls()
        val sources = BuiltInStores.all(customUrls).flatMap { it.sources }
        catalogFetcher.fetchCatalogs(sources).mapCatching { fetchResult ->
            mergedCatalogRepository.save(fetchResult.hacks)
            fetchResult
        }
    }
}
