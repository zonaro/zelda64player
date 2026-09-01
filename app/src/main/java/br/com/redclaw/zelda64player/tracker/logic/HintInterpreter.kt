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

package br.com.redclaw.zelda64player.tracker.logic

import androidx.annotation.StringRes
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.tracker.model.TrackerHint
import br.com.redclaw.zelda64player.tracker.model.TrackerHintType

/**
 * Resolves hint labels and builds the default hint slots for the Hints tab. WOTH = Way of the Hero
 * (5 slots), Barren = 3 slots, plus free Gossip Stones.
 */
object HintInterpreter {

    @StringRes
    fun labelRes(type: TrackerHintType): Int =
            when (type) {
                TrackerHintType.WOTH -> R.string.tracker_hint_woth
                TrackerHintType.BARREN -> R.string.tracker_hint_barren
                TrackerHintType.STONE -> R.string.tracker_hint_stone
            }

    /** Builds the initial hint list: 5 WOTH + 3 Barren + 6 free stones. */
    fun defaultHints(): List<TrackerHint> {
        val list = mutableListOf<TrackerHint>()
        repeat(5) { list.add(TrackerHint("woth_$it", TrackerHintType.WOTH, 0)) }
        repeat(3) { list.add(TrackerHint("barren_$it", TrackerHintType.BARREN, 0)) }
        repeat(6) { list.add(TrackerHint("stone_$it", TrackerHintType.STONE, 0)) }
        return list
    }
}
