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

package br.com.redclaw.zelda64player.views

/**
 * Sort modes available in the "Todos os Jogos" grid. [prefValue] is the stable
 * string persisted by CorePrefs (`pref_grid_sort`); it must never be renamed,
 * or stored selections would silently fall back to alphabetical.
 */
enum class GridSortMode(val prefValue: String) {
    /** Most-recently-played first (never-played entries keep their default order at the end). */
    LAST_PLAYED("last_played"),

    /** Newest ROM file first (entries whose ROM is missing sort last). */
    DOWNLOAD_DATE("download_date"),

    /** Alphabetical by title (default). */
    ALPHA("alpha");

    companion object {
        /**
         * Parses a persisted preference value; unknown/blank values fall back to
         * [ALPHA] so a schema change can never break the grid.
         */
        fun fromPref(value: String?): GridSortMode =
            values().firstOrNull { it.prefValue == value } ?: ALPHA
    }
}

/**
 * Pure ordering helpers shared by the Library home row and the "Todos os Jogos"
 * grid. Kept free of Android dependencies so the ordering rules are
 * JVM-unit-testable; callers supply the play history map and a ROM
 * last-modified lookup.
 */
object LibraryOrdering {

    /**
     * [entries] ordered by most-recently-played first, capped at [limit].
     * Only entries present in [history] are returned — an empty result means
     * nothing was ever played and the caller should fall back to the default
     * order (see InstalledLibrary.recentEntries).
     */
    fun recentPlayed(
        entries: List<HackLibraryEntry>,
        history: Map<String, Long>,
        limit: Int
    ): List<HackLibraryEntry> = entries
        .filter { history.containsKey(it.id) }
        .sortedByDescending { history[it.id] ?: Long.MIN_VALUE }
        .take(limit)

    /**
     * [entries] sorted by [mode]:
     * - [GridSortMode.ALPHA]: title alphabetically (case-insensitive).
     * - [GridSortMode.LAST_PLAYED]: most-recently-played first; never-played
     *   entries sort after played ones preserving their original relative order.
     * - [GridSortMode.DOWNLOAD_DATE]: newest ROM file first via [romLastModified];
     *   a missing ROM file (0L) sorts last.
     *
     * All branches are stable sorts, so ties preserve the source order produced
     * by [InstalledLibrary.entries] (vanilla -> store hacks).
     */
    fun sortByMode(
        entries: List<HackLibraryEntry>,
        mode: GridSortMode,
        history: Map<String, Long>,
        romLastModified: (String) -> Long
    ): List<HackLibraryEntry> = when (mode) {
        GridSortMode.ALPHA ->
            entries.sortedBy { it.title.lowercase() }
        GridSortMode.LAST_PLAYED ->
            entries.sortedByDescending { history[it.id] ?: Long.MIN_VALUE }
        GridSortMode.DOWNLOAD_DATE ->
            entries.sortedByDescending { romLastModified(it.id) }
    }
}
