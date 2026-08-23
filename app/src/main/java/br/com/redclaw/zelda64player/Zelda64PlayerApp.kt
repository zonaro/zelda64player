package br.com.redclaw.zelda64player

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import br.com.redclaw.zelda64player.work.CatalogRefreshWorker
import java.util.concurrent.TimeUnit

/**
 * Application entry point. Schedules the periodic background catalog refresh on
 * startup so the Library always has reasonably fresh data without requiring the
 * user to open the Store. The periodic work is unique (KEEP policy) so repeated
 * process starts never enqueue duplicates.
 */
class Zelda64PlayerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        scheduleCatalogRefresh()
    }

    private fun scheduleCatalogRefresh() {
        val request = PeriodicWorkRequestBuilder<CatalogRefreshWorker>(12, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            CatalogRefreshWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
