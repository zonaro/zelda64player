package br.com.redclaw.zelda64player.work

import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import br.com.redclaw.zelda64player.store.CatalogRefresher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Periodic background refresh of the merged hack catalog.
 *
 * Delegates to [CatalogRefresher] — the exact same fetch/merge/persist path
 * used by the Store — so the Library shows fresh data even if the user has not
 * opened the Store recently. It is fully resilient: any failure is swallowed
 * (logged) and reported as a failure so WorkManager applies its default
 * (non-aggressive) backoff; the worker never throws into the app process.
 */
class CatalogRefreshWorker(
    appContext: android.content.Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val WORK_NAME = "catalog_refresh"
        private const val TAG = "CatalogRefreshWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val result = CatalogRefresher(applicationContext).refresh()
            if (result.isFailure) {
                Log.w(TAG, "Catalog refresh failed: ${result.exceptionOrNull()?.message}")
                return@withContext Result.failure()
            }
            Result.success()
        } catch (e: Exception) {
            // Never crash the app; just skip this refresh and let WorkManager
            // retry with its default backoff.
            Log.w(TAG, "Catalog refresh skipped due to error: ${e.message}")
            Result.failure()
        }
    }
}
