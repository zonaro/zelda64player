package br.com.redclaw.zelda64player.store.ui

import br.com.redclaw.zelda64player.data.model.BaseRomRef
import br.com.redclaw.zelda64player.data.model.Checksums
import br.com.redclaw.zelda64player.data.model.HackEntry
import br.com.redclaw.zelda64player.data.model.PatchRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorePagerTest {

    private fun hack(
        id: String,
        name: String = id,
        author: String = "",
        description: String = "",
        tags: List<String> = emptyList()
    ): HackEntry = HackEntry(
        id = id,
        name = name,
        description = description,
        author = author,
        version = "1.0",
        baseRom = BaseRomRef("r", "CZLE", 0, Checksums("aa")),
        patch = PatchRef("u", "f", 1, Checksums("bb")),
        tags = tags
    )

    @Test
    fun emptyQueryReturnsAll() {
        val hacks = listOf(hack("a"), hack("b"), hack("c"))
        assertEquals(hacks, StorePager.filter(hacks, ""))
        assertEquals(hacks, StorePager.filter(hacks, "   "))
    }

    @Test
    fun filterByName() {
        val hacks = listOf(
            hack("a", name = "Master Quest"),
            hack("b", name = "Beta Quest")
        )
        assertEquals(listOf(hacks[0]), StorePager.filter(hacks, "master"))
        assertTrue(StorePager.filter(hacks, "quest").size == 2)
    }

    @Test
    fun filterByAuthor() {
        val hacks = listOf(
            hack("a", author = "ZeldaTeam"),
            hack("b", author = "OtherDev")
        )
        assertEquals(listOf(hacks[0]), StorePager.filter(hacks, "zeldateam"))
    }

    @Test
    fun filterByDescription() {
        val hacks = listOf(
            hack("a", description = "A dark dungeon hack"),
            hack("b", description = "Speedrun friendly")
        )
        assertEquals(listOf(hacks[0]), StorePager.filter(hacks, "dungeon"))
    }

    @Test
    fun filterByTag() {
        val hacks = listOf(
            hack("a", tags = listOf("adventure", "hard")),
            hack("b", tags = listOf("easy"))
        )
        assertEquals(listOf(hacks[0]), StorePager.filter(hacks, "hard"))
        assertEquals(listOf(hacks[1]), StorePager.filter(hacks, "easy"))
    }

    @Test
    fun caseInsensitive() {
        val hacks = listOf(hack("a", name = "Pokemon"))
        assertEquals(listOf(hacks[0]), StorePager.filter(hacks, "POKEMON"))
        assertEquals(listOf(hacks[0]), StorePager.filter(hacks, "pokemon"))
    }

    @Test
    fun accentInsensitive() {
        val hacks = listOf(hack("a", name = "Pokémon"))
        assertEquals(listOf(hacks[0]), StorePager.filter(hacks, "pokemon"))
        assertEquals(listOf(hacks[0]), StorePager.filter(hacks, "POKEMON"))
    }

    @Test
    fun paginationSlicesTwelvePerPage() {
        val hacks = (1..25).map { hack("h$it", name = "Hack $it") }
        val page0 = StorePager.page(hacks, 0)
        assertEquals(12, page0.items.size)
        assertEquals(0, page0.pageIndex)
        assertEquals(3, page0.totalPages)

        val page1 = StorePager.page(hacks, 1)
        assertEquals(12, page1.items.size)
        assertEquals(1, page1.pageIndex)

        val page2 = StorePager.page(hacks, 2)
        assertEquals(1, page2.items.size)
        assertEquals(2, page2.pageIndex)
        assertEquals("Hack 25", page2.items[0].name)
    }

    @Test
    fun pageOutOfBoundsClamps() {
        val hacks = (1..25).map { hack("h$it", name = "Hack $it") }
        val last = StorePager.page(hacks, 99)
        assertEquals(2, last.pageIndex)
        assertEquals(1, last.items.size)

        val first = StorePager.page(hacks, -5)
        assertEquals(0, first.pageIndex)
        assertEquals(12, first.items.size)
    }

    @Test
    fun emptyListHasZeroPages() {
        assertEquals(0, StorePager.totalPages(0))
        val result = StorePager.page(emptyList(), 0)
        assertEquals(0, result.totalPages)
        assertEquals(0, result.pageIndex)
        assertTrue(result.items.isEmpty())
    }

    @Test
    fun totalPagesComputation() {
        assertEquals(1, StorePager.totalPages(1))
        assertEquals(1, StorePager.totalPages(12))
        assertEquals(2, StorePager.totalPages(13))
        assertEquals(3, StorePager.totalPages(25))
    }
}
