package br.com.redclaw.zelda64player

import android.app.Application
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import br.com.redclaw.zelda64player.randomizer.api.OotrApiClient
import br.com.redclaw.zelda64player.randomizer.api.OotrApiKeyStore
import br.com.redclaw.zelda64player.randomizer.settings.SchemaLoader
import br.com.redclaw.zelda64player.repositories.Storage
import br.com.redclaw.zelda64player.retroachievements.api.RaHttpClient
import br.com.redclaw.zelda64player.retroachievements.api.RaUserAgent
import br.com.redclaw.zelda64player.retroachievements.auth.RaAuthService
import br.com.redclaw.zelda64player.retroachievements.auth.RaCredentialStore
import br.com.redclaw.zelda64player.retroachievements.data.RaCatalogRepository
import br.com.redclaw.zelda64player.retroachievements.data.RaInstallMetadataStore
import br.com.redclaw.zelda64player.retroachievements.jni.RcheevosJni
import br.com.redclaw.zelda64player.shortcuts.GamePlayHistoryStore
import br.com.redclaw.zelda64player.shortcuts.GameShortcutsManager
import br.com.redclaw.zelda64player.views.InstalledLibrary
import br.com.redclaw.zelda64player.work.CatalogRefreshWorker
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Application entry point. Schedules the periodic background catalog refresh on
 * startup so the Library always has reasonably fresh data without requiring the
 * user to open the Store. The periodic work is unique (KEEP policy) so repeated
 * process starts never enqueue duplicates.
 *
 * Also acts as the manual service-locator (DI container) for the OoTR
 * Randomizer singletons: [ootrApiKeyStore] (encrypted key storage) and
 * [ootrApiClient] (rate-limited API client). Both are lazily constructed on
 * first access so no work happens before they are needed.
 */
class Zelda64PlayerApp : Application() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        // Relocate any patched ROMs left in the legacy cache dir by earlier
        // builds into the durable external-files store (idempotent, safe).
        Storage.getInstance(this).migrateLegacyRoms()
        scheduleCatalogRefresh()
        syncGameShortcuts()
        logRcheevosVersion()
    }

    /**
     * Debug-only startup log proving the rcheevos native library links and
     * loads correctly (RetroAchievements foundation smoke test, phase B1).
     */
    private fun logRcheevosVersion() {
        val isDebugBuild = (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (!isDebugBuild) return
        try {
            Log.i(TAG, "rcheevos native runtime loaded: ${RcheevosJni.getVersion()}")
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "Failed to load rcheevos native runtime", e)
        }
    }

    /**
     * Publish dynamic shortcuts for installed games on every cold start so the
     * launcher's long-press menu reflects the current library (and stale pins
     * are disabled if a game is missing).
     */
    private fun syncGameShortcuts() {
        val entries = InstalledLibrary.entries(this)
        if (entries.isEmpty()) return
        val history = GamePlayHistoryStore(File(filesDir, "game_play_history.json"))
        GameShortcutsManager(this, history).sync(entries)
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

    companion object {
        private const val TAG = "Zelda64PlayerApp"

        private lateinit var instance: Zelda64PlayerApp
            private set

        /** Encrypted, secure storage for the user's OoTR API key. */
        val ootrApiKeyStore: OotrApiKeyStore by lazy {
            OotrApiKeyStore(instance.applicationContext)
        }

        /** Shared OoTR API client (rate-limited, 20 requests / 10s). */
        val ootrApiClient: OotrApiClient by lazy {
            OotrApiClient.default()
        }

        /** Shared loader/parser for the bundled OoTR settings schema asset. */
        val randomizerSchemaLoader: SchemaLoader by lazy {
            SchemaLoader(instance.applicationContext)
        }

        /** Encrypted, secure storage for RetroAchievements credentials. */
        val raCredentialStore: RaCredentialStore by lazy {
            RaCredentialStore(instance.applicationContext)
        }

        /** Shared RetroAchievements HTTP executor (User-Agent contract). */
        val raHttpClient: RaHttpClient by lazy {
            RaHttpClient(RaUserAgent.build(instance.applicationContext))
        }

        /** Interactive RA login/logout service (settings screen). */
        val raAuthService: RaAuthService by lazy {
            RaAuthService(raCredentialStore, raHttpClient)
        }

        /** Per-hack install-time RA identities (hash/gameId/title). */
        val raInstallMetadataStore: RaInstallMetadataStore by lazy {
            RaInstallMetadataStore(instance.applicationContext)
        }

        /** RA catalog fetcher (achievement/leaderboard definitions, unlocks). */
        val raCatalogRepository: RaCatalogRepository by lazy {
            RaCatalogRepository(raHttpClient)
        }
    }
}
