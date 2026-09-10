/* Copyright (C) 2026 RedClaw. SPDX-License-Identifier: GPL-3.0-or-later */
package br.com.redclaw.zelda64player.tracker.autotracker.model

import br.com.redclaw.zelda64player.tracker.model.TrackerGame

/** Immutable scalar copy; never retains native memory beyond the core callback. */
data class AutoTrackerSnapshot(
    val game: TrackerGame,
    val inventory: List<Int>,
    val equipment: Int,
    val upgrades: Int,
    val questFlags: Int,
    val magicLevel: Int,
    val biggoronOwned: Boolean = false
) {
    /** Whether a game-specific quest bit is set. */
    fun hasQuestFlag(bit: Int): Boolean = questFlags and (1 shl bit) != 0
    /** Extracts a packed upgrade level using its game-specific shift and mask. */
    fun upgrade(shift: Int, mask: Int = 7): Int = (upgrades ushr shift) and mask
}
