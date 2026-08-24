package br.com.redclaw.zelda64player.store

/**
 * Lightweight, thread-safe cancellation token shared between the queue manager
 * and the [DownloadManager] network/patch loops. The download coroutine polls
 * [isCancelled] and aborts by throwing [StoreException.Cancelled].
 */
class CancelSignal {
    @Volatile
    var isCancelled: Boolean = false
        private set

    fun cancel() {
        isCancelled = true
    }
}
