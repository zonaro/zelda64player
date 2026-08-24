package br.com.redclaw.zelda64player.store

import br.com.redclaw.zelda64player.data.model.BaseRomRef
import br.com.redclaw.zelda64player.data.model.Checksums
import br.com.redclaw.zelda64player.data.model.HackEntry
import br.com.redclaw.zelda64player.data.model.PatchRef
import kotlinx.coroutines.delay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM unit tests for the download + patch queue scheduling core
 * ([DownloadQueueEngine]). The engine is pure Kotlin (no Android), so it runs
 * directly on the JVM with a fake [DownloadRunner].
 */
class DownloadQueueManagerTest {

    private data class RecordingListener(
        val states: MutableList<List<QueueItemUi>> = mutableListOf(),
        val successes: MutableList<QueueItemUi> = mutableListOf(),
        val cancelled: MutableList<QueueItemUi> = mutableListOf(),
        val errors: MutableList<QueueItemUi> = mutableListOf()
    ) : EngineListener {
        override fun onQueueChanged(items: List<QueueItemUi>) { states.add(items) }
        override fun onSuccess(ui: QueueItemUi, hack: HackEntry) { successes.add(ui) }
        override fun onCancelled(ui: QueueItemUi, hack: HackEntry) { cancelled.add(ui) }
        override fun onError(ui: QueueItemUi, hack: HackEntry) { errors.add(ui) }
        override fun mapError(e: Throwable): String = e.message ?: "error"
    }

    /** A runner that simulates work by delaying; optionally throws on cancel. */
    private class DelayingRunner(
        private val workMs: Long = 400L,
        private val failWith: Throwable? = null
    ) : DownloadRunner {
        val started = mutableListOf<String>()
        override suspend fun run(
            hack: HackEntry,
            signal: CancelSignal,
            onProgress: (phase: InstallPhase, bytesDownloaded: Long, totalBytes: Long) -> Unit
        ): Result<Unit> {
            started.add(hack.id)
            if (failWith != null) return Result.failure(failWith)
            var elapsed = 0L
            while (elapsed < workMs) {
                if (signal.isCancelled) throw StoreException.Cancelled()
                delay(10)
                elapsed += 10
                onProgress(InstallPhase.DOWNLOADING, elapsed, workMs)
            }
            return Result.success(Unit)
        }
    }

    private fun makeHack(id: String, version: String = "1.0"): HackEntry = HackEntry(
        id = id,
        name = id,
        description = "desc",
        author = "author",
        version = version,
        baseRom = BaseRomRef("base", "CZLE", 0, Checksums("abcd")),
        patch = PatchRef("https://example.com/$id", "p.bps", 100, Checksums("efgh"))
    )

    @Test
    fun enqueueMoreThanMaxStartsAtMostMax() {
        val runner = DelayingRunner(workMs = 1000)
        val listener = RecordingListener()
        val engine = DownloadQueueEngine(runner, listener)

        repeat(5) { engine.enqueue(makeHack("h$it")) { false } }

        val snap = engine.snapshot()
        val downloading = snap.count { it.phase == DownloadPhase.DOWNLOADING }
        val queued = snap.count { it.phase == DownloadPhase.QUEUED }
        assertEquals(MAX_CONCURRENT, downloading)
        assertEquals(2, queued)
        assertEquals(5, snap.size)
    }

    @Test
    fun enqueueIsIdempotent() {
        val runner = DelayingRunner(workMs = 1000)
        val listener = RecordingListener()
        val engine = DownloadQueueEngine(runner, listener)

        assertTrue(engine.enqueue(makeHack("h0")) { false })
        assertFalse(engine.enqueue(makeHack("h0")) { false })
        assertEquals(1, engine.snapshot().size)
    }

    @Test
    fun cancelQueuedItemRemovesIt() {
        val runner = DelayingRunner(workMs = 1000)
        val listener = RecordingListener()
        val engine = DownloadQueueEngine(runner, listener)

        // Fill the 3 concurrent slots, then a 4th stays QUEUED.
        repeat(3) { engine.enqueue(makeHack("h$it")) { false } }
        engine.enqueue(makeHack("h3")) { false }

        assertTrue(
            engine.snapshot().any { it.hackId == "h3" && it.phase == DownloadPhase.QUEUED }
        )
        engine.cancel("h3")
        assertFalse(engine.snapshot().any { it.hackId == "h3" })
        assertEquals(1, listener.cancelled.size)
    }

    @Test
    fun cancelRunningItemEndsCancelled() {
        val runner = DelayingRunner(workMs = 2000)
        val listener = RecordingListener()
        val engine = DownloadQueueEngine(runner, listener)

        engine.enqueue(makeHack("h0")) { false }
        // Wait until it is actually running.
        val deadline = System.currentTimeMillis() + 2000
        while (engine.snapshot().firstOrNull { it.hackId == "h0" }?.phase != DownloadPhase.DOWNLOADING) {
            if (System.currentTimeMillis() > deadline) break
            Thread.sleep(10)
        }
        engine.cancel("h0")

        val done = System.currentTimeMillis() + 2000
        while (engine.snapshot().firstOrNull { it.hackId == "h0" }?.phase != DownloadPhase.CANCELLED) {
            if (System.currentTimeMillis() > done) break
            Thread.sleep(10)
        }
        assertEquals(DownloadPhase.CANCELLED, engine.snapshot().firstOrNull { it.hackId == "h0" }?.phase)
        assertEquals(1, listener.cancelled.size)
    }

    @Test
    fun finishedItemCanBeDismissed() {
        val runner = DelayingRunner(workMs = 100)
        val listener = RecordingListener()
        val engine = DownloadQueueEngine(runner, listener)

        engine.enqueue(makeHack("h0")) { false }
        val deadline = System.currentTimeMillis() + 2000
        while (engine.snapshot().firstOrNull { it.hackId == "h0" }?.phase != DownloadPhase.SUCCESS) {
            if (System.currentTimeMillis() > deadline) break
            Thread.sleep(10)
        }
        assertEquals(DownloadPhase.SUCCESS, engine.snapshot().firstOrNull { it.hackId == "h0" }?.phase)
        engine.dismiss("h0")
        assertTrue(engine.snapshot().isEmpty())
    }

    @Test
    fun clearFinishedRemovesOnlyFinished() {
        val runner = DelayingRunner(workMs = 100)
        val listener = RecordingListener()
        val engine = DownloadQueueEngine(runner, listener)

        engine.enqueue(makeHack("h0")) { false }
        val deadline = System.currentTimeMillis() + 2000
        while (engine.snapshot().firstOrNull { it.hackId == "h0" }?.phase != DownloadPhase.SUCCESS) {
            if (System.currentTimeMillis() > deadline) break
            Thread.sleep(10)
        }
        engine.clearFinished()
        assertTrue(engine.snapshot().isEmpty())
    }

    @Test
    fun cancelSignalWorks() {
        val signal = CancelSignal()
        assertFalse(signal.isCancelled)
        signal.cancel()
        assertTrue(signal.isCancelled)
    }

    @Test
    fun errorMapsToErrorPhase() {
        val runner = object : DownloadRunner {
            override suspend fun run(
                hack: HackEntry,
                signal: CancelSignal,
                onProgress: (phase: InstallPhase, bytesDownloaded: Long, totalBytes: Long) -> Unit
            ): Result<Unit> = Result.failure(StoreException.NetworkError("boom"))
        }
        val listener = RecordingListener()
        val engine = DownloadQueueEngine(runner, listener)

        engine.enqueue(makeHack("h0")) { false }
        val deadline = System.currentTimeMillis() + 2000
        while (engine.snapshot().firstOrNull { it.hackId == "h0" }?.phase != DownloadPhase.ERROR) {
            if (System.currentTimeMillis() > deadline) break
            Thread.sleep(10)
        }
        val ui = engine.snapshot().firstOrNull { it.hackId == "h0" }
        assertEquals(DownloadPhase.ERROR, ui?.phase)
        assertEquals("boom", ui?.error)
    }
}
