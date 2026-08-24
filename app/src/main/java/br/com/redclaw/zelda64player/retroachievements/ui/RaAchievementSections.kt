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

package br.com.redclaw.zelda64player.retroachievements.ui

import br.com.redclaw.zelda64player.retroachievements.data.RaGameData
import br.com.redclaw.zelda64player.retroachievements.data.RaGameIdentity
import br.com.redclaw.zelda64player.views.HackLibraryEntry

/**
 * One game's fully resolved achievement payload, ready to be flattened into the
 * all-games list. [title] is the display name (preferring the RA game title,
 * falling back to the installed entry title supplied by the caller).
 */
data class GameAchievements(
    val gameId: Long,
    val title: String,
    val gameData: RaGameData,
    val unlockedIds: Set<Long>
)

/**
 * Flattens a list of resolved games into the single [RaListItem] sequence shown
 * by [RaAchievementAdapter]: one [RaSectionItem] header per game followed by
 * that game's [RaAchievementRow]s (unlocked first, then alphabetical). Pure and
 * JVM-testable — no network, no Android framework.
 */
fun buildSectionedRows(games: List<GameAchievements>): List<RaListItem> {
    val rows = mutableListOf<RaListItem>()
    for (game in games) {
        val achievements = game.gameData.achievements
        val unlockedCount = achievements.count { it.id in game.unlockedIds }
        val totalPoints = achievements.sumOf { it.points }
        val earnedPoints =
            achievements.filter { it.id in game.unlockedIds }.sumOf { it.points }

        rows += RaSectionItem(
            gameId = game.gameId,
            title = game.title.ifBlank { game.gameData.title },
            unlockedCount = unlockedCount,
            totalCount = achievements.size,
            earnedPoints = earnedPoints,
            totalPoints = totalPoints
        )
        rows += achievements
            .map { RaAchievementRow(it, it.id in game.unlockedIds) }
            .sortedWith(
                compareByDescending<RaAchievementRow> { it.unlocked }
                    .thenBy { it.def.title.lowercase() }
            )
    }
    return rows
}

/**
 * Groups installed [entries] by their resolved RetroAchievements game id,
 * returning a map of gameId -> fallback display title (the first matching
 * entry's title). Entries without a resolved identity are skipped. Pure and
 * JVM-testable; the caller performs the network fetches keyed by these gameIds.
 */
fun collectResolvedGames(
    entries: List<HackLibraryEntry>,
    identities: Map<String, RaGameIdentity>
): Map<Long, String> {
    val result = LinkedHashMap<Long, String>()
    for (entry in entries) {
        val identity = identities[entry.id] ?: continue
        if (!identity.isResolved) continue
        result.putIfAbsent(identity.gameId, entry.title)
    }
    return result
}
