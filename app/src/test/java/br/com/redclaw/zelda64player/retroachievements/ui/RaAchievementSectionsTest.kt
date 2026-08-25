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

import br.com.redclaw.zelda64player.retroachievements.data.RaAchievementDef
import br.com.redclaw.zelda64player.retroachievements.data.RaGameData
import br.com.redclaw.zelda64player.retroachievements.data.RaGameIdentity
import br.com.redclaw.zelda64player.views.HackLibraryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the pure achievements-list aggregation helpers. */
class RaAchievementSectionsTest {

    private fun ach(id: Long, title: String, points: Int, unlocked: Boolean) =
        RaAchievementDef(
            id = id,
            title = title,
            description = "desc $id",
            points = points,
            badgeUrl = "badge$id.png",
            badgeLockedUrl = "badge${id}_lock.png",
            category = 3,
            type = 0
        ) to unlocked

    private fun gameData(id: Long, title: String, defs: List<RaAchievementDef>) = RaGameData(
        id = id,
        title = title,
        imageUrl = null,
        achievements = defs,
        leaderboards = emptyList()
    )

    @Test
    fun `buildSectionedRows emits one header then its rows, unlocked first`() {
        val (a, aUnlocked) = ach(1, "Alpha", 5, unlocked = false)
        val (b, bUnlocked) = ach(2, "Beta", 10, unlocked = true)
        val (c, cUnlocked) = ach(3, "Gamma", 1, unlocked = true)
        val data = gameData(100, "Game One", listOf(a, b, c))

        val rows = buildSectionedRows(
            listOf(GameAchievements(100, "Game One", data, setOf(2L, 3L)))
        )

        // Header + 3 rows.
        assertEquals(4, rows.size)
        assertTrue(rows[0] is RaSectionItem)
        val header = rows[0] as RaSectionItem
        assertEquals(100L, header.gameId)
        assertEquals("Game One", header.title)
        assertEquals(2, header.unlockedCount)
        assertEquals(3, header.totalCount)
        assertEquals(11, header.earnedPoints) // 10 + 1
        assertEquals(16, header.totalPoints) // 5 + 10 + 1

        // Unlocked achievements (Beta, Gamma) come before the locked one (Alpha),
        // each group alphabetical (already the case here).
        val achRows = rows.drop(1).filterIsInstance<RaAchievementRow>()
        assertEquals(listOf(2L, 3L, 1L), achRows.map { it.def.id })
        assertEquals(listOf(true, true, false), achRows.map { it.unlocked })
    }

    @Test
    fun `buildSectionedRows handles multiple games in order`() {
        val dataA = gameData(10, "A", listOf(ach(1, "x", 1, true).first))
        val dataB = gameData(20, "B", listOf(ach(2, "y", 2, false).first))

        val rows = buildSectionedRows(
            listOf(
                GameAchievements(10, "A", dataA, setOf(1L)),
                GameAchievements(20, "B", dataB, emptySet())
            )
        )

        assertEquals(4, rows.size)
        assertTrue(rows[0] is RaSectionItem)
        assertTrue(rows[2] is RaSectionItem)
        assertEquals(10L, (rows[0] as RaSectionItem).gameId)
        assertEquals(20L, (rows[2] as RaSectionItem).gameId)
    }

    @Test
    fun `collectResolvedGames dedupes by gameId and skips unresolved`() {
        val entries = listOf(
            HackLibraryEntry("vanilla_aaa", "OoT Vanilla"),
            HackLibraryEntry("hack_bbb", "Some Hack"),
            HackLibraryEntry("hack_ccc", "A Hack"),
            HackLibraryEntry("hack_ddd", "Untracked Hack")
        )
        val identities = mapOf(
            "vanilla_aaa" to RaGameIdentity("h1", 100L, "OoT"),
            "hack_bbb" to RaGameIdentity("h2", 100L, "OoT"), // same game id as above
            "hack_ccc" to RaGameIdentity("h3", 200L, "MM"),
            "hack_ddd" to RaGameIdentity("h4", 0L, null) // unresolved
        )

        val resolved = collectResolvedGames(entries, identities)

        // Two unique game ids; first entry title wins for the duplicated id.
        assertEquals(2, resolved.size)
        assertEquals("OoT Vanilla", resolved[100L])
        assertEquals("A Seed", resolved[200L])
        assertFalse(resolved.containsKey(0L))
    }

    @Test
    fun `row models expose stable unique keys`() {
        val section = RaSectionItem(42L, "T", 1, 2, 3, 4)
        val row = RaAchievementRow(ach(7, "z", 1, true).first, true)

        assertEquals("section:42", section.key)
        assertEquals("ach:7", row.key)
        // Keys are stable across equal instances.
        assertEquals("section:42", RaSectionItem(42L, "Other", 9, 9, 9, 9).key)
        assertEquals("ach:7", RaAchievementRow(ach(7, "z", 1, true).first, false).key)
    }
}
