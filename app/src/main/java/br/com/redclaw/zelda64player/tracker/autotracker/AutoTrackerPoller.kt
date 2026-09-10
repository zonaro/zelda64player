/* Copyright (C) 2026 RedClaw. SPDX-License-Identifier: GPL-3.0-or-later */
package br.com.redclaw.zelda64player.tracker.autotracker

import br.com.redclaw.zelda64player.tracker.autotracker.model.AutoTrackerSnapshot
import br.com.redclaw.zelda64player.tracker.autotracker.parser.SaveContextParser
import br.com.redclaw.zelda64player.tracker.model.TrackerGame
import java.nio.ByteBuffer

/** Single GL-thread reader. The owner obtains RAM outside frameCallback (getMemoryRegion locks
 * coreLock), installs the composite callback, then removes it before dropping this owner/unload.
 * Native RAM never escapes onSnapshot. Time throttling also works with PAL and fast-forward.
 */
class AutoTrackerPoller(
    private val memory: ByteBuffer,
    private val game: TrackerGame,
    private val enabled: () -> Boolean,
    private val onSnapshot: (AutoTrackerSnapshot) -> Unit,
    private val nanoTime: () -> Long = System::nanoTime
) {
    private var lastPoll: Long? = null
    private var lastSnapshot: AutoTrackerSnapshot? = null

    @Volatile private var invalidated = false

    /** Requests a fresh emission on the next poll, including rapid global OFF/ON toggles. */
    fun invalidate() { invalidated = true }

    /** Reads only while the caller owns coreLock; never invokes locking core APIs. */
    fun onFrame() {
        if (invalidated) {
            lastSnapshot = null
            invalidated = false
        }
        val now = nanoTime()
        if (lastPoll?.let { now - it < 100_000_000L } == true) return
        lastPoll = now
        if (!enabled()) {
            lastSnapshot = null
            return
        }
        val snapshot = SaveContextParser.parse(memory, game) ?: return
        if (snapshot == lastSnapshot) return
        lastSnapshot = snapshot
        onSnapshot(snapshot)
    }
}
