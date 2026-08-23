package br.com.redclaw.zelda64player.randomizer.api

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.ArrayDeque

/**
 * Token-bucket rate limiter allowing at most [maxRequests] within any rolling
 * [windowMillis] window.
 *
 * [acquire] suspends (via [delay]) until a request slot is free, so callers
 * never busy-wait. The limiter is safe to share across coroutines: all state
 * mutations are guarded by a [Mutex].
 *
 * @param maxRequests Maximum requests permitted per rolling window.
 * @param windowMillis Length of the rolling window in milliseconds.
 */
class OotrRateLimiter(
    private val maxRequests: Int = 20,
    private val windowMillis: Long = 10_000L
) {
    private val mutex = Mutex()
    private val timestamps = ArrayDeque<Long>()

    /**
     * Suspend until a request slot is available, then reserve it by recording
     * the current timestamp. Respects coroutine cancellation (the underlying
     * [delay] is cancellable).
     */
    suspend fun acquire() {
        while (true) {
            val now = System.currentTimeMillis()
            val waitMillis = mutex.withLock {
                // Drop timestamps that have left the rolling window.
                while (timestamps.isNotEmpty() && now - timestamps.first() >= windowMillis) {
                    timestamps.removeFirst()
                }
                if (timestamps.size < maxRequests) {
                    timestamps.addLast(now)
                    -1L
                } else {
                    // Time until the oldest timestamp leaves the window.
                    timestamps.first() + windowMillis - now
                }
            }
            if (waitMillis < 0) return
            delay(waitMillis)
        }
    }
}
