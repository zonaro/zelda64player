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

package br.com.redclaw.zelda64player.tracker.ui

import android.content.Context
import br.com.redclaw.zelda64player.data.local.AppRepositories
import br.com.redclaw.zelda64player.tracker.assets.RomAssetExtractor
import br.com.redclaw.zelda64player.tracker.assets.cache.TrackerAssetCache
import br.com.redclaw.zelda64player.tracker.data.OotItemDatabase
import br.com.redclaw.zelda64player.tracker.data.TrackerRepository
import br.com.redclaw.zelda64player.tracker.logic.HintInterpreter
import br.com.redclaw.zelda64player.tracker.model.TrackerGame
import br.com.redclaw.zelda64player.tracker.model.TrackerHint
import br.com.redclaw.zelda64player.tracker.model.TrackerItem
import br.com.redclaw.zelda64player.tracker.model.TrackerLocation
import br.com.redclaw.zelda64player.tracker.model.TrackerSong
import br.com.redclaw.zelda64player.tracker.model.TrackerState
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Plain (non-Android-ViewModel) state holder shared by the tracker dialog and its tab fragments.
 * Owns the [TrackerState], persists every mutation through [TrackerRepository], and drives the run
 * timer. No core RAM is touched.
 */
class TrackerViewModel(context: Context, val game: TrackerGame, val hackId: String? = null) {

    private val appContext = context.applicationContext
    private val repository = TrackerRepository(appContext)
    private val assetCache = TrackerAssetCache(appContext)
    private val assetExtractor = RomAssetExtractor(appContext, assetCache)

    // Asset extraction state (observed by UI for spinner/progress)
    private val _isExtracting = MutableStateFlow(false)
    val isExtracting: StateFlow<Boolean> = _isExtracting
    private val _assetCrc = MutableStateFlow<String?>(resolveAssetCrc())
    val assetCrc: StateFlow<String?> = _assetCrc

    private fun resolveAssetCrc(): String? {
        val roms = AppRepositories.baseRomRepository(appContext).getAll()
        android.util.Log.d(
                "TrackerAssets",
                "resolveAssetCrc game=$game roms=${roms.map { "${it.gameCode}:${it.crc32.take(8)}" }}"
        )
        // Prefer a ROM matching the current game (OoT vs MM via gameCode)
        val gameCodes =
                when (game) {
                    TrackerGame.OOT -> setOf("CZLE", "CZLP")
                    TrackerGame.MM -> setOf("NZSE", "NZSP")
                }
        val result =
                roms.firstOrNull { it.gameCode in gameCodes }?.crc32 ?: roms.firstOrNull()?.crc32
        android.util.Log.d("TrackerAssets", "resolveAssetCrc result=$result")
        return result
    }

    /**
     * Ensure icons for [game] are extracted from the base ROM if needed. Call from UI (IO-safe).
     */
    suspend fun ensureAssetsExtracted() {
        val crc = _assetCrc.value
        android.util.Log.d(
                "TrackerAssets",
                "ensureAssetsExtracted game=$game crc=$crc expected=${RomAssetExtractor.mappingCount(game)}"
        )
        if (crc == null) {
            android.util.Log.w("TrackerAssets", "No CRC for $game — no base ROM imported?")
            return
        }
        val expectedCount = RomAssetExtractor.mappingCount(game)
        if (assetCache.hasValidCache(crc, expectedCount)) {
            android.util.Log.d("TrackerAssets", "Cache valid for $crc, skipping extraction")
            return
        }
        val romFile =
                AppRepositories.baseRomRepository(appContext)
                        .getAll()
                        .firstOrNull { it.crc32.equals(crc, ignoreCase = true) }
                        ?.let { File(it.path) }
                        ?: run {
                            android.util.Log.w("TrackerAssets", "ROM file not found for CRC $crc")
                            return
                        }
        android.util.Log.d(
                "TrackerAssets",
                "Extracting $expectedCount icons from ${romFile.name} for $game"
        )
        _isExtracting.value = true
        try {
            val result = assetExtractor.extractAll(romFile, game)
            android.util.Log.d("TrackerAssets", "Extraction result: $result")
            if (result.isFailure) {
                android.util.Log.e("TrackerAssets", "Extraction failed", result.exceptionOrNull())
            } else {
                val report = result.getOrNull()
                android.util.Log.d(
                        "TrackerAssets",
                        "Extracted=${report?.extracted} failed=${report?.failed} errors=${report?.errors}"
                )
            }
        } finally {
            _isExtracting.value = false
        }
    }
    val state: TrackerState =
            repository.load(game, hackId).also {
                if (it.hints.isEmpty()) it.hints.addAll(HintInterpreter.defaultHints())
                migrateLegacyUpgrades(it)
            }

    val items: List<TrackerItem>
        get() = OotItemDatabase.forGame(game).items
    val locations: List<TrackerLocation>
        get() = OotItemDatabase.forGame(game).locations
    val songs: List<TrackerSong>
        get() = OotItemDatabase.forGame(game).songs
    // ---- Items ----
    fun isItemObtained(id: String) = (state.obtainedItems[id] ?: 0) > 0
    fun getItemCount(id: String) = state.obtainedItems[id] ?: 0

    /** Cycles an item's obtained count 0 -> 1 -> ... -> maxCount -> 0. */
    fun cycleItem(id: String, maxCount: Int) {
        val current = state.obtainedItems[id] ?: 0
        val next = if (current < maxCount) current + 1 else 0
        if (next == 0) state.obtainedItems.remove(id) else state.obtainedItems[id] = next
        save()
    }

    /** Increments [id] by [step], clamped to [maxCount]. Used by the Rupees stepper (+). */
    fun incrementItem(id: String, maxCount: Int, step: Int = 1) {
        val current = state.obtainedItems[id] ?: 0
        val next = (current + step).coerceAtMost(maxCount)
        if (next == 0) state.obtainedItems.remove(id) else state.obtainedItems[id] = next
        save()
    }

    /** Decrements [id] by [step], clamped to 0. Used by the Rupees stepper (−). */
    fun decrementItem(id: String, step: Int = 1) {
        val current = state.obtainedItems[id] ?: 0
        val next = (current - step).coerceAtLeast(0)
        if (next == 0) state.obtainedItems.remove(id) else state.obtainedItems[id] = next
        save()
    }

    /** Clears [id] to 0. Used by long-press on Rupees. */
    fun clearItem(id: String) {
        if (state.obtainedItems.remove(id) != null) save()
    }

    // ---- Locations ----
    fun isLocationChecked(id: String) = state.checkedLocations.contains(id)
    fun toggleLocation(id: String) {
        if (state.checkedLocations.contains(id)) state.checkedLocations.remove(id)
        else state.checkedLocations.add(id)
        save()
    }

    // ---- Songs ----
    fun isSongFound(id: String) = state.foundSongs.contains(id)
    fun toggleSong(id: String) {
        if (state.foundSongs.contains(id)) state.foundSongs.remove(id) else state.foundSongs.add(id)
        save()
    }

    // ---- Hints ----
    fun getHints(): List<TrackerHint> = state.hints
    fun setHintText(id: String, text: String) {
        state.hints.find { it.id == id }?.text = text
        save()
    }

    // ---- Timer ----
    // The running timer's wall-clock anchor lives in [TrackerState.timerStartMs] so it survives
    // dialog close / app restart and keeps counting while the tracker UI is hidden.

    fun isTimerRunning() = state.timerRunning
    fun getElapsedMs(): Long {
        return if (state.timerRunning)
                state.timerElapsedMs + (System.currentTimeMillis() - state.timerStartMs)
        else state.timerElapsedMs
    }

    fun startTimer() {
        if (state.timerRunning) return
        state.timerRunning = true
        state.timerStartMs = System.currentTimeMillis()
        save()
    }

    fun pauseTimer() {
        if (!state.timerRunning) return
        state.timerElapsedMs += System.currentTimeMillis() - state.timerStartMs
        state.timerRunning = false
        save()
    }

    fun resetTimer() {
        state.timerRunning = false
        state.timerElapsedMs = 0L
        state.timerStartMs = 0L
        save()
    }

    /** Directly sets the elapsed time (ms). Keeps running state; re-anchors if running. */
    fun setElapsedMs(ms: Long) {
        val clamped = ms.coerceAtLeast(0L)
        state.timerElapsedMs = clamped
        if (state.timerRunning) state.timerStartMs = System.currentTimeMillis()
        save()
    }

    fun clearAll() {
        state.obtainedItems.clear()
        state.checkedLocations.clear()
        state.foundSongs.clear()
        state.upgradeLevels.clear()
        state.hints.clear()
        state.hints.addAll(HintInterpreter.defaultHints())
        state.timerElapsedMs = 0L
        state.timerRunning = false
        state.timerStartMs = 0L
        save()
    }

    /** Replaces the current state's contents with [newState] (from an import) and persists. */
    fun importState(newState: TrackerState) {
        state.obtainedItems.clear()
        state.obtainedItems.putAll(newState.obtainedItems)
        state.checkedLocations.clear()
        state.checkedLocations.addAll(newState.checkedLocations)
        state.foundSongs.clear()
        state.foundSongs.addAll(newState.foundSongs)
        state.hints.clear()
        state.hints.addAll(newState.hints)
        state.upgradeLevels.clear()
        state.upgradeLevels.putAll(newState.upgradeLevels)
        state.timerElapsedMs = newState.timerElapsedMs
        state.timerRunning = newState.timerRunning
        // Re-anchor the running timer to "now" on this device so it continues from the imported
        // elapsed time rather than a foreign wall-clock anchor.
        state.timerStartMs = if (newState.timerRunning) System.currentTimeMillis() else 0L
        save()
    }

    private fun save() = repository.save(state, hackId)

    /** Moves the former Evolutions-tab values into their equivalent item cards once. */
    private fun migrateLegacyUpgrades(state: TrackerState) {
        val ids =
                when (game) {
                    TrackerGame.OOT ->
                            mapOf(
                                    "strength" to "strength",
                                    "bombs" to "bomb_bag",
                                    "bow" to "bow",
                                    "wallet" to "rupees",
                                    "scale" to "scale",
                                    "magic" to "magic"
                            )
                    TrackerGame.MM ->
                            mapOf(
                                    "bombs" to "bomb_bag",
                                    "bow" to "bow",
                                    "wallet" to "rupees",
                                    "magic" to "magic_power"
                            )
                }
        var changed = false
        ids.forEach { (upgradeId, itemId) ->
            val level = state.upgradeLevels[upgradeId] ?: return@forEach
            if (level > 0 && !state.obtainedItems.containsKey(itemId)) {
                state.obtainedItems[itemId] = level
            }
            state.upgradeLevels.remove(upgradeId)
            changed = true
        }
        if (changed) repository.save(state, hackId)
    }
}
