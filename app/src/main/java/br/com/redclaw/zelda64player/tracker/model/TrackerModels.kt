/*
 * Zelda 64 Player - native Android N64 emulator frontend for Zelda ROM hacks.
 * Copyright (C) 2026 RedClaw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package br.com.redclaw.zelda64player.tracker.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/**
 * Which game the tracker is tracking. Mirrors [br.com.redclaw.zelda64player.ocarina.OcarinaGame].
 */
enum class TrackerGame {
        OOT,
        MM
}

/** A trackable inventory item. [maxCount] > 1 means it is stackable (e.g. rupees, skulls). */
data class TrackerItem(
        val id: String,
        @StringRes val nameRes: Int,
        @DrawableRes val iconRes: Int = 0,
        val maxCount: Int = 1,
        /** When non-empty, the item cycles through these label/icon variants (0 = off). */
        val cycleLabels: List<Int> = emptyList(),
        val cycleIcons: List<Int> = emptyList()
) {
        val isStackable: Boolean
                get() = maxCount > 1
        val isCyclic: Boolean
                get() = cycleLabels.isNotEmpty()
}

/** A check location, grouped by [regionRes]. */
data class TrackerLocation(
        val id: String,
        @StringRes val regionRes: Int,
        @StringRes val nameRes: Int
)

/** A learnable song. */
data class TrackerSong(
        val id: String,
        @StringRes val nameRes: Int,
        @DrawableRes val iconRes: Int = 0
)

/** Type of hint recorded on a Gossip Stone. */
enum class TrackerHintType {
        WOTH,
        BARREN,
        STONE
}

/** A single hint entry (WOTH / Barren / free Gossip Stone note). */
data class TrackerHint(
        val id: String,
        val type: TrackerHintType,
        @StringRes val labelRes: Int,
        var text: String = ""
)

/** An upgrade chain (e.g. strength 0->3, bottles 0->4). */
data class TrackerUpgrade(val id: String, @StringRes val nameRes: Int, val maxLevel: Int)

/** Full mutable tracker state for one game. Persisted as JSON. */
data class TrackerState(
        val game: TrackerGame,
        val obtainedItems: MutableMap<String, Int> = mutableMapOf(),
        val checkedLocations: MutableSet<String> = mutableSetOf(),
        val foundSongs: MutableSet<String> = mutableSetOf(),
        val hints: MutableList<TrackerHint> = mutableListOf(),
        val upgradeLevels: MutableMap<String, Int> = mutableMapOf(),
        var timerElapsedMs: Long = 0L,
        var timerRunning: Boolean = false,
        /**
         * Wall-clock anchor (System.currentTimeMillis) set when the timer is running. Persisted so
         * the run timer keeps counting across dialog close / app restart.
         */
        var timerStartMs: Long = 0L
)

/** User-facing visibility of the tracker menu entry. */
enum class VisibilityMode {
        ALWAYS,
        NEVER
}

/** Tracker visibility / layout preferences (persisted separately from per-game state). */
data class TrackerSettings(var visibility: VisibilityMode = VisibilityMode.ALWAYS)
