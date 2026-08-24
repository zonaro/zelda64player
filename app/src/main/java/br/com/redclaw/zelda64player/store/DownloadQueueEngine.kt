package br.com.redclaw.zelda64player.store

import br.com.redclaw.zelda64player.data.model.HackEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Maximum number of hacks downloaded + patched at the same time. */
const val MAX_CONCURRENT: Int = 3

/**
 * Pure-Kotlin scheduling core for the download+patch queue. It holds no Android
 * references (no [android.content.Context], no LiveData, no notifications) so it
 * can be unit-tested on the JVM. [DownloadQueueManager] wires it to the Android
 * world: it supplies the [DownloadRunner] (the real [DownloadManager]) and an
 * [EngineListener] that mirrors state into LiveData, posts notifications and
 * publishes launcher shortcuts.
 *
 * Concurrency model: a single [CoroutineScope] runs each item's work. [pump]
 * promotes at most [maxConcurrent] QUEUED items to DOWNLOADING and launches
 * them; when one finishes it calls [pump] again to start the next QUEUED item.
 * All shared-state access is guarded by a plain [lock] (we never suspend while
 * holding it), which keeps the scheduling deterministic and testable.
 */
class DownloadQueueEngine(
    private val runner: DownloadRunner,
    private val listener: EngineListener,
    private val maxConcurrent: Int = MAX_CONCURRENT,
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val lock = Any()
    private val items = LinkedHashMap<String, EngineItem>()
    private val engineScope = scope

    /**
     * Adds [hack] to the queue. Idempotent: if it is already present (any
     * phase) or already installed at the same version, this is a no-op and
     * returns false. Otherwise returns true and starts pumping.
     */
    fun enqueue(hack: HackEntry, isInstalled: (HackEntry) -> Boolean): Boolean {
        synchronized(lock) {
            if (items.containsKey(hack.id)) return false
            if (isInstalled(hack)) return false
            items[hack.id] = EngineItem(
                hack,
                CancelSignal(),
                QueueItemUi(hack.id, hack.name, hack.coverImageUrl, DownloadPhase.QUEUED, 0, 0, 0)
            )
        }
        listener.onQueueChanged(snapshot())
        pump()
        return true
    }

    /**
     * Cancels an active (QUEUED/DOWNLOADING/PATCHING) item. A still-QUEUED item
     * is removed immediately and reported as CANCELLED; a running item keeps its
     * slot until the runner observes the [CancelSignal] and throws
     * [StoreException.Cancelled], after which [runItem] finalizes it.
     */
    fun cancel(hackId: String) {
        val item = synchronized(lock) { items[hackId] } ?: return
        item.signal.cancel()
        val wasQueued = synchronized(lock) { item.ui.phase == DownloadPhase.QUEUED }
        if (wasQueued) {
            synchronized(lock) { items.remove(hackId) }
            listener.onQueueChanged(snapshot())
            listener.onCancelled(item.ui, item.hack)
        }
    }

    /** Removes a finished (SUCCESS/ERROR/CANCELLED) item from the list. */
    fun dismiss(hackId: String) {
        synchronized(lock) { items.remove(hackId) }
        listener.onQueueChanged(snapshot())
    }

    /** Removes every finished item. */
    fun clearFinished() {
        synchronized(lock) {
            val it = items.entries.iterator()
            while (it.hasNext()) {
                val (_, v) = it.next()
                if (v.ui.phase == DownloadPhase.SUCCESS ||
                    v.ui.phase == DownloadPhase.ERROR ||
                    v.ui.phase == DownloadPhase.CANCELLED
                ) {
                    it.remove()
                }
            }
        }
        listener.onQueueChanged(snapshot())
    }

    /** Current snapshot of all items, in insertion order. */
    fun snapshot(): List<QueueItemUi> = synchronized(lock) { items.values.map { it.ui } }

    /** Promotes QUEUED items up to the concurrency limit and launches them. */
    private fun pump() {
        val toStart = synchronized(lock) {
            val started = mutableListOf<EngineItem>()
            var active = items.values.count {
                it.ui.phase == DownloadPhase.DOWNLOADING || it.ui.phase == DownloadPhase.PATCHING
            }
            for (item in items.values) {
                if (active >= maxConcurrent) break
                if (item.ui.phase == DownloadPhase.QUEUED) {
                    item.ui = item.ui.copy(phase = DownloadPhase.DOWNLOADING)
                    active++
                    started.add(item)
                }
            }
            started
        }
        toStart.forEach { engineScope.launch { runItem(it) } }
        listener.onQueueChanged(snapshot())
    }

    private fun runItem(item: EngineItem) {
        engineScope.launch {
            val result = try {
                runner.run(item.hack, item.signal) { phase, downloaded, total ->
                    val dp = if (phase == InstallPhase.PATCHING) {
                        DownloadPhase.PATCHING
                    } else {
                        DownloadPhase.DOWNLOADING
                    }
                    val percent = if (total > 0) (downloaded * 100 / total).toInt() else 0
                    synchronized(lock) {
                        item.ui = item.ui.copy(
                            phase = dp,
                            progressPercent = percent,
                            downloaded = downloaded,
                            total = total
                        )
                    }
                    listener.onQueueChanged(snapshot())
                }
            } catch (e: Throwable) {
                Result.failure<Unit>(e)
            }

            when {
                result.isSuccess -> {
                    synchronized(lock) { item.ui = item.ui.copy(phase = DownloadPhase.SUCCESS) }
                    listener.onSuccess(item.ui, item.hack)
                    listener.onQueueChanged(snapshot())
                    pump()
                }
                result.isFailure -> {
                    val e = result.exceptionOrNull()
                    if (e is StoreException.Cancelled) {
                        synchronized(lock) { item.ui = item.ui.copy(phase = DownloadPhase.CANCELLED) }
                        listener.onCancelled(item.ui, item.hack)
                    } else {
                        val msg = listener.mapError(e ?: Exception("unknown error"))
                        synchronized(lock) {
                            item.ui = item.ui.copy(phase = DownloadPhase.ERROR, error = msg)
                        }
                        listener.onError(item.ui, item.hack)
                    }
                    listener.onQueueChanged(snapshot())
                    pump()
                }
            }
        }
    }

    /** Internal mutable queue entry. */
    private data class EngineItem(
        val hack: HackEntry,
        val signal: CancelSignal,
        var ui: QueueItemUi
    )
}

/**
 * Executes a single hack's download+patch. Implemented by [DownloadQueueManager]
 * on top of the real [DownloadManager]; faked in tests.
 */
interface DownloadRunner {
    suspend fun run(
        hack: HackEntry,
        signal: CancelSignal,
        onProgress: (phase: InstallPhase, bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Result<Unit>
}

/**
 * Side-effect hooks the engine invokes as state changes. Implemented by
 * [DownloadQueueManager] to update LiveData, post notifications and publish
 * shortcuts. [mapError] turns a throwable into a user-facing message without
 * dragging Android string resources into the pure engine.
 */
interface EngineListener {
    fun onQueueChanged(items: List<QueueItemUi>)
    fun onSuccess(ui: QueueItemUi, hack: HackEntry)
    fun onCancelled(ui: QueueItemUi, hack: HackEntry)
    fun onError(ui: QueueItemUi, hack: HackEntry)
    fun mapError(e: Throwable): String
}
