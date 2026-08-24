package br.com.redclaw.zelda64player.views

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests the pure ordering helpers in [LibraryOrdering] (no Android Context needed):
 * the recent-played cap/fallback and the three grid sort modes, including tie and
 * missing-data behavior.
 */
class LibraryOrderingTest {

    private fun entry(id: String, title: String) = HackLibraryEntry(id, title)

    @Test
    fun recentPlayedOrdersByRecencyAndCapsAtLimit() {
        val entries = listOf(
            entry("a", "Alpha"),
            entry("b", "Beta"),
            entry("c", "Gamma"),
            entry("d", "Delta"),
            entry("e", "Epsilon"),
            entry("f", "Zeta")
        )
        val history = mapOf(
            "c" to 300L,
            "a" to 100L,
            "e" to 200L,
            "b" to 400L
        )
        val result = LibraryOrdering.recentPlayed(entries, history, 5)
        assertEquals(listOf("b", "c", "e", "a"), result.map { it.id })
        assertEquals(4, result.size) // only played entries, capped at 5
    }

    @Test
    fun recentPlayedDropsUnplayedEntries() {
        val entries = listOf(entry("a", "A"), entry("b", "B"))
        // "x" is in history but not in entries -> ignored; "b" unplayed -> dropped.
        val history = mapOf("a" to 10L, "x" to 20L)
        val result = LibraryOrdering.recentPlayed(entries, history, 5)
        assertEquals(listOf("a"), result.map { it.id })
    }

    @Test
    fun recentPlayedReturnsEmptyWhenNothingPlayed() {
        // Empty history -> no played entries -> caller falls back to default order.
        val entries = listOf(entry("a", "A"), entry("b", "B"), entry("c", "C"))
        val result = LibraryOrdering.recentPlayed(entries, emptyMap(), 5)
        assertEquals(emptyList<String>(), result.map { it.id })
    }

    @Test
    fun alphaSortIsLocaleAwareAndCaseInsensitive() {
        val entries = listOf(
            entry("1", "zelda"),
            entry("2", "Gamma"),
            entry("3", "Alpha"),
            entry("4", "beta")
        )
        val result = LibraryOrdering.sortByMode(entries, GridSortMode.ALPHA, emptyMap()) { 0L }
        // Case-insensitive: "Alpha" < "beta" < "Gamma" < "zelda".
        assertEquals(listOf("3", "4", "2", "1"), result.map { it.id })
    }

    @Test
    fun lastPlayedSortPutsUnplayedLastAlphabetically() {
        val entries = listOf(
            entry("a", "Alpha"),
            entry("b", "Beta"),
            entry("c", "Gamma")
        )
        val history = mapOf("b" to 200L, "c" to 100L) // "a" never played
        val result = LibraryOrdering.sortByMode(entries, GridSortMode.LAST_PLAYED, history) { 0L }
        assertEquals(listOf("b", "c", "a"), result.map { it.id })
    }

    @Test
    fun downloadDateSortPutsMissingFilesLast() {
        val entries = listOf(
            entry("a", "Alpha"),
            entry("b", "Beta"),
            entry("c", "Gamma")
        )
        // "a" missing (0) -> sorts last; "c" newest.
        val times = mapOf("b" to 100L, "c" to 300L, "a" to 0L)
        val result = LibraryOrdering.sortByMode(entries, GridSortMode.DOWNLOAD_DATE, emptyMap()) { times[it] ?: 0L }
        assertEquals(listOf("c", "b", "a"), result.map { it.id })
    }

    @Test
    fun gridSortModePrefRoundTrip() {
        assertEquals(GridSortMode.ALPHA, GridSortMode.fromPref(null))
        assertEquals(GridSortMode.ALPHA, GridSortMode.fromPref("bogus"))
        assertEquals(GridSortMode.LAST_PLAYED, GridSortMode.fromPref("last_played"))
        assertEquals(GridSortMode.DOWNLOAD_DATE, GridSortMode.fromPref("download_date"))
        assertEquals(GridSortMode.ALPHA, GridSortMode.fromPref("alpha"))
    }
}
