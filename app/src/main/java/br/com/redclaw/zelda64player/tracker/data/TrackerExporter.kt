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

package br.com.redclaw.zelda64player.tracker.data

import br.com.redclaw.zelda64player.tracker.model.TrackerGame
import br.com.redclaw.zelda64player.tracker.model.TrackerHint
import br.com.redclaw.zelda64player.tracker.model.TrackerHintType
import br.com.redclaw.zelda64player.tracker.model.TrackerState
import org.json.JSONArray
import org.json.JSONObject

/**
 * Simple JSON export / import for a tracker state. The format is a self-contained object (game +
 * obtained items + checks + songs + hints + upgrades + timer) that can be shared between devices.
 * Not compatible with the EmoTracker pack format.
 */
object TrackerExporter {

    fun exportToJson(state: TrackerState): String {
        val items = JSONObject()
        state.obtainedItems.forEach { (k, v) -> items.put(k, v) }
        val checks = JSONArray().apply { state.checkedLocations.forEach { put(it) } }
        val songs = JSONArray().apply { state.foundSongs.forEach { put(it) } }
        val hints =
                JSONArray().apply {
                    state.hints.forEach { h ->
                        put(
                                JSONObject().apply {
                                    put("id", h.id)
                                    put("type", h.type.name)
                                    put("text", h.text)
                                }
                        )
                    }
                }
        val upgrades = JSONObject()
        state.upgradeLevels.forEach { (k, v) -> upgrades.put(k, v) }
        return JSONObject()
                .apply {
                    put("format", "zelda64player-tracker")
                    put("version", 1)
                    put("game", state.game.name)
                    put("obtainedItems", items)
                    put("checkedLocations", checks)
                    put("foundSongs", songs)
                    put("hints", hints)
                    put("upgradeLevels", upgrades)
                    put("timerElapsedMs", state.timerElapsedMs)
                    put("timerStartMs", state.timerStartMs)
                }
                .toString(2)
    }

    fun importFromJson(json: String): TrackerState? {
        return runCatching {
                    val obj = JSONObject(json)
                    val game = TrackerGame.valueOf(obj.getString("game"))
                    val state = TrackerState(game)
                    obj.optJSONObject("obtainedItems")?.let {
                        it.keys().forEach { k -> state.obtainedItems[k] = it.getInt(k) }
                    }
                    obj.optJSONArray("checkedLocations")?.let {
                        for (i in 0 until it.length()) state.checkedLocations.add(it.getString(i))
                    }
                    obj.optJSONArray("foundSongs")?.let {
                        for (i in 0 until it.length()) state.foundSongs.add(it.getString(i))
                    }
                    obj.optJSONArray("hints")?.let { arr ->
                        for (i in 0 until arr.length()) {
                            val h = arr.getJSONObject(i)
                            state.hints.add(
                                    TrackerHint(
                                            h.getString("id"),
                                            TrackerHintType.valueOf(h.getString("type")),
                                            0,
                                            h.optString("text", "")
                                    )
                            )
                        }
                    }
                    obj.optJSONObject("upgradeLevels")?.let {
                        it.keys().forEach { k -> state.upgradeLevels[k] = it.getInt(k) }
                    }
                    state.timerElapsedMs = obj.optLong("timerElapsedMs", 0L)
                    state.timerStartMs = obj.optLong("timerStartMs", 0L)
                    state
                }
                .getOrNull()
    }
}
