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

import android.content.Context
import br.com.redclaw.zelda64player.tracker.model.TrackerGame
import br.com.redclaw.zelda64player.tracker.model.TrackerHint
import br.com.redclaw.zelda64player.tracker.model.TrackerHintType
import br.com.redclaw.zelda64player.tracker.model.TrackerSettings
import br.com.redclaw.zelda64player.tracker.model.TrackerState
import br.com.redclaw.zelda64player.tracker.model.VisibilityMode
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists per-hack tracker state and global settings as JSON files in the app's private
 * [Context.getFilesDir]. Keyed by hack so each ROM hack keeps an independent checklist and run
 * timer; when no hack id is available it falls back to the game name (OoT / MM). No core RAM is
 * read — this is a manual tracker.
 */
class TrackerRepository(private val context: Context) {

    /** Resolves the storage key: the hack id when known, otherwise the game name. */
    private fun storageKey(game: TrackerGame, hackId: String?): String =
            hackId?.takeIf { it.isNotBlank() }?.lowercase() ?: game.name.lowercase()

    private fun stateFile(game: TrackerGame, hackId: String?): File =
            File(context.filesDir, "tracker_state_${storageKey(game, hackId)}.json")

    private fun settingsFile(): File = File(context.filesDir, "tracker_settings.json")

    fun load(game: TrackerGame, hackId: String? = null): TrackerState {
        val file = stateFile(game, hackId)
        if (!file.exists()) return TrackerState(game)
        return try {
            parseState(game, JSONObject(file.readText()))
        } catch (_: Exception) {
            TrackerState(game)
        }
    }

    fun save(state: TrackerState, hackId: String? = null) {
        runCatching { stateFile(state.game, hackId).writeText(serializeState(state).toString()) }
    }

    fun loadSettings(): TrackerSettings {
        val file = settingsFile()
        if (!file.exists()) return TrackerSettings()
        return try {
            val obj = JSONObject(file.readText())
            TrackerSettings(
                    visibility =
                            VisibilityMode.valueOf(
                                    obj.optString("visibility", VisibilityMode.ALWAYS.name)
                            )
            )
        } catch (_: Exception) {
            TrackerSettings()
        }
    }

    fun saveSettings(settings: TrackerSettings) {
        runCatching {
            val obj = JSONObject().apply { put("visibility", settings.visibility.name) }
            settingsFile().writeText(obj.toString())
        }
    }

    private fun parseState(game: TrackerGame, obj: JSONObject): TrackerState {
        val state = TrackerState(game)
        obj.optJSONObject("obtainedItems")?.let { items ->
            items.keys().forEach { key -> state.obtainedItems[key] = items.getInt(key) }
        }
        obj.optJSONArray("checkedLocations")?.let { arr ->
            for (i in 0 until arr.length()) state.checkedLocations.add(arr.getString(i))
        }
        obj.optJSONArray("foundSongs")?.let { arr ->
            for (i in 0 until arr.length()) state.foundSongs.add(arr.getString(i))
        }
        obj.optJSONArray("hints")?.let { arr ->
            for (i in 0 until arr.length()) {
                val h = arr.getJSONObject(i)
                state.hints.add(
                        TrackerHint(
                                id = h.getString("id"),
                                type = TrackerHintType.valueOf(h.getString("type")),
                                labelRes = 0,
                                text = h.optString("text", "")
                        )
                )
            }
        }
        obj.optJSONObject("upgradeLevels")?.let { up ->
            up.keys().forEach { key -> state.upgradeLevels[key] = up.getInt(key) }
        }
        state.timerElapsedMs = obj.optLong("timerElapsedMs", 0L)
        state.timerRunning = obj.optBoolean("timerRunning", false)
        state.timerStartMs = obj.optLong("timerStartMs", 0L)
        return state
    }

    private fun serializeState(state: TrackerState): JSONObject {
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
        return JSONObject().apply {
            put("game", state.game.name)
            put("obtainedItems", items)
            put("checkedLocations", checks)
            put("foundSongs", songs)
            put("hints", hints)
            put("upgradeLevels", upgrades)
            put("timerElapsedMs", state.timerElapsedMs)
            put("timerRunning", state.timerRunning)
            put("timerStartMs", state.timerStartMs)
        }
    }
}
