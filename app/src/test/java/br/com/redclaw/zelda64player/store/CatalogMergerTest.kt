package br.com.redclaw.zelda64player.store

import br.com.redclaw.zelda64player.data.model.BaseRomRef
import br.com.redclaw.zelda64player.data.model.Checksums
import br.com.redclaw.zelda64player.data.model.HackEntry
import br.com.redclaw.zelda64player.data.model.PatchRef
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogMergerTest {

    private fun hack(id: String, version: String): HackEntry = HackEntry(
        id = id, name = id, description = "", author = "", version = version,
        baseRom = BaseRomRef("r", "CZLE", 0, Checksums("aa")),
        patch = PatchRef("u", "f", 1, Checksums("bb"))
    )

    @Test
    fun duplicateIdLastWins() {
        val merged = CatalogMerger.merge(listOf(listOf(hack("a", "1.0")), listOf(hack("a", "2.0"))))
        assertEquals(1, merged.size)
        assertEquals("2.0", merged[0].version)
    }

    @Test
    fun emptyCatalogsIgnored() {
        val merged = CatalogMerger.merge(listOf(emptyList(), listOf(hack("a", "1.0")), emptyList()))
        assertEquals(1, merged.size)
        assertEquals("a", merged[0].id)
    }

    @Test
    fun orderStabilityPreserved() {
        val merged = CatalogMerger.merge(
            listOf(listOf(hack("a", "1"), hack("b", "1"), hack("c", "1")))
        )
        assertEquals(listOf("a", "b", "c"), merged.map { it.id })
    }

    @Test
    fun laterOverrideKeepsOriginalPosition() {
        val merged = CatalogMerger.merge(
            listOf(listOf(hack("a", "1"), hack("b", "1")), listOf(hack("a", "2")))
        )
        assertEquals(listOf("a", "b"), merged.map { it.id })
        assertEquals("2", merged[0].version)
    }
}
