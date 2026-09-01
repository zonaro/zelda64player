package br.com.redclaw.zelda64player.store

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.map
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.data.local.AppRepositories
import br.com.redclaw.zelda64player.data.local.InstalledHacksRepository
import br.com.redclaw.zelda64player.data.model.HackEntry
import br.com.redclaw.zelda64player.repositories.Storage
import br.com.redclaw.zelda64player.shortcuts.GamePlayHistoryStore
import br.com.redclaw.zelda64player.shortcuts.GameShortcutsManager
import br.com.redclaw.zelda64player.views.HackLibraryEntry
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient

/**
 * Process-lifetime singleton orchestrating the Hack Store download + patch queue. It owns a
 * [DownloadQueueEngine] (pure scheduling) and adapts its events to Android: a [LiveData] of
 * [QueueItemUi] for the UI, system notifications via [DownloadNotificationHelper], a success Toast,
 * and launcher-shortcut publishing on completion.
 *
 * Initialized once from [br.com.redclaw.zelda64player.Zelda64PlayerApp.onCreate] via [init];
 * afterwards the public methods are safe to call from any thread.
 */
object DownloadQueueManager {

    const val MAX_CONCURRENT: Int = 3

    private lateinit var appContext: Context
    private lateinit var installedRepository: InstalledHacksRepository
    private lateinit var engine: DownloadQueueEngine

    private val _queue = MutableLiveData<List<QueueItemUi>>(emptyList())
    val queue: LiveData<List<QueueItemUi>> = _queue

    /** Builds the engine and the real [DownloadManager]. Idempotent. */
    fun init(context: Context) {
        if (::appContext.isInitialized) return
        appContext = context.applicationContext
        installedRepository =
                InstalledHacksRepository(File(appContext.filesDir, "installed_hacks.json"))
        val downloadManager =
                DownloadManager(
                        appContext,
                        OkHttpClient.Builder()
                                .dns(
                                        object : okhttp3.Dns {
                                            override fun lookup(
                                                    hostname: String
                                            ): List<java.net.InetAddress> {
                                                // Resolve DNS with a thread-based timeout to avoid
                                                // hanging
                                                // when Android Private DNS (DNS-over-TLS, e.g.
                                                // dns.adguard.com)
                                                // blocks or takes too long.
                                                // InetAddress.getAllByName() can
                                                // block indefinitely on some devices with DoT
                                                // configured.
                                                val holder = arrayOfNulls<java.net.InetAddress>(1)
                                                val t = Thread {
                                                    runCatching {
                                                        val addrs =
                                                                java.net.InetAddress.getAllByName(
                                                                        hostname
                                                                )
                                                        if (addrs.isNotEmpty()) holder[0] = addrs[0]
                                                    }
                                                }
                                                t.isDaemon = true
                                                t.start()
                                                t.join(8_000) // 8s DNS resolution timeout
                                                if (t.isAlive) {
                                                    t.interrupt()
                                                    throw java.net.UnknownHostException(
                                                            "DNS resolution timed out for $hostname (possible Private DNS issue)"
                                                    )
                                                }
                                                val addr =
                                                        holder[0]
                                                                ?: throw java.net
                                                                        .UnknownHostException(
                                                                                "No addresses for $hostname"
                                                                        )
                                                return listOf(addr)
                                            }
                                        }
                                )
                                .connectTimeout(30, TimeUnit.SECONDS)
                                .readTimeout(30, TimeUnit.SECONDS)
                                .writeTimeout(30, TimeUnit.SECONDS)
                                .build(),
                        AppRepositories.patchRepository(appContext),
                        installedRepository,
                        AppRepositories.baseRomRepository(appContext),
                        Storage.getInstance(appContext)
                )
        engine =
                DownloadQueueEngine(
                        runner =
                                object : DownloadRunner {
                                    override suspend fun run(
                                            hack: HackEntry,
                                            signal: CancelSignal,
                                            onProgress:
                                                    (
                                                            phase: InstallPhase,
                                                            bytesDownloaded: Long,
                                                            totalBytes: Long) -> Unit
                                    ) =
                                            downloadManager.download(hack, onProgress, signal).map {
                                                Unit
                                            }
                                },
                        listener =
                                object : EngineListener {
                                    override fun onQueueChanged(items: List<QueueItemUi>) {
                                        Log.d(
                                                "DownloadQueueManager",
                                                "onQueueChanged: ${items.size} items, phases=${items.map { "${it.hackId}:${it.phase}" }}"
                                        )
                                        // Post to main thread via Handler + setValue for reliable
                                        // observer notification (postValue can be coalesced or
                                        // lost when called rapidly from background threads).
                                        Handler(Looper.getMainLooper()).post {
                                            _queue.value = items
                                        }
                                    }

                                    override fun onSuccess(ui: QueueItemUi, hack: HackEntry) {
                                        publishShortcut(hack)
                                        DownloadNotificationHelper.notifyCompleted(appContext, ui)
                                        showInstalledToast()
                                    }

                                    override fun onCancelled(ui: QueueItemUi, hack: HackEntry) {
                                        DownloadNotificationHelper.notifyCancelled(appContext, ui)
                                    }

                                    override fun onError(ui: QueueItemUi, hack: HackEntry) {
                                        DownloadNotificationHelper.notifyError(appContext, ui)
                                    }

                                    override fun mapError(e: Throwable): String = mapErrorMessage(e)
                                }
                )
        DownloadNotificationHelper.ensureChannel(appContext)
    }

    /** Enqueues [hack]; ignored if already queued or installed at the same version. */
    fun enqueue(hack: HackEntry) {
        Log.d(
                "DownloadQueueManager",
                "enqueue called for hack=${hack.id} version=${hack.version} patch=${hack.patch?.url}"
        )
        engine.enqueue(hack) { installedRepository.installedVersion(it.id) == it.version }
    }

    /** Cancels an active item, or finalizes a QUEUED item immediately. */
    fun cancel(hackId: String) {
        engine.cancel(hackId)
    }

    /** Removes a finished item from the list. */
    fun dismiss(hackId: String) {
        engine.dismiss(hackId)
    }

    /** Removes all finished items. */
    fun clearFinished() {
        engine.clearFinished()
    }

    /** LiveData of a single item's UI state (or null when not in the queue). */
    fun stateFor(hackId: String): LiveData<QueueItemUi?> =
            queue.map { list -> list.firstOrNull { it.hackId == hackId } }

    /** Synchronous read of the current phase for [hackId], or null. */
    fun queuePhaseFor(hackId: String): DownloadPhase? =
            queue.value?.firstOrNull { it.hackId == hackId }?.phase

    /**
     * Publishes or updates the launcher shortcut for [hack] right after a successful install,
     * mirroring the previous [StoreViewModel.publishShortcut] behavior (now centralized in the
     * queue so every completion path shares it).
     */
    private fun publishShortcut(hack: HackEntry) {
        val entry = HackLibraryEntry(hack.id, hack.name, hack.coverImageUrl)
        val history = GamePlayHistoryStore(File(appContext.filesDir, "game_play_history.json"))
        GameShortcutsManager(appContext, history).publishOrUpdate(entry)
    }

    private fun showInstalledToast() {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(appContext, R.string.detail_installed_toast, Toast.LENGTH_SHORT).show()
        }
    }

    private fun mapErrorMessage(e: Throwable): String =
            when (e) {
                is StoreException.NetworkError ->
                        appContext.getString(R.string.detail_error_network)
                is StoreException.ChecksumMismatch ->
                        appContext.getString(R.string.detail_error_checksum)
                is StoreException.InvalidPatch ->
                        appContext.getString(R.string.detail_error_patch_invalid)
                is StoreException.BaseRomMissing ->
                        appContext.getString(
                                R.string.detail_error_base_rom_missing,
                                e.expectedCrc32
                        )
                is java.net.UnknownHostException ->
                        appContext.getString(R.string.detail_error_network)
                is java.net.SocketTimeoutException ->
                        appContext.getString(R.string.detail_error_network)
                is javax.net.ssl.SSLHandshakeException ->
                        appContext.getString(R.string.detail_error_network)
                else -> appContext.getString(R.string.detail_error_generic)
            }
}
