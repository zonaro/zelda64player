package br.com.redclaw.zelda64player.views

import br.com.redclaw.zelda64player.data.model.BaseRom
import br.com.redclaw.zelda64player.ocarina.OcarinaGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tests [BaseRomLibrarySource]: id/title/badge/isVanilla mapping, per-family cover
 * URL selection, null cover for unknown families, and defensive skipping of entries
 * whose ROM file is missing on disk.
 */
class BaseRomLibrarySourceTest {

    private fun rom(
        id: String,
        gameCode: String,
        versionByte: Int = 0,
        displayName: String = "ROM $id",
        path: String = File.createTempFile("rom_$id", ".z64").apply { writeText("x") }.absolutePath
    ) = BaseRom(
        id = id,
        displayName = displayName,
        path = path,
        gameCode = gameCode,
        versionByte = versionByte,
        sizeBytes = 1,
        crc32 = id,
        md5 = null,
        sha1 = null
    )

    @Test
    fun mapsIdTitleBadgeAndVanillaFlag() {
        val source = BaseRomLibrarySource(listOf(rom("ABCDEF01", "CZLE")))
        val entries = source.available()
        assertEquals(1, entries.size)
        val entry = entries.first()
        assertEquals("vanilla_ABCDEF01", entry.id)
        assertEquals("ROM ABCDEF01", entry.title)
        assertEquals(BadgeType.VANILLA, entry.badge)
        assertTrue(entry.isVanilla)
        assertFalse(entry.isRandomizer)
    }

    @Test
    fun ootFamilyUsesOotCoverUrl() {
        val source = BaseRomLibrarySource(listOf(rom("AAA", "CZLE"), rom("BBB", "CZLJ")))
        val entries = source.available()
        assertEquals(2, entries.size)
        entries.forEach { entry ->
            assertTrue(entry.coverUrl?.contains("Ocarina%20of%20Time") == true)
        }
    }

    @Test
    fun mmFamilyUsesMmCoverUrl() {
        val source = BaseRomLibrarySource(listOf(rom("CCC", "NSME"), rom("DDD", "NZLE")))
        val entries = source.available()
        assertEquals(2, entries.size)
        entries.forEach { entry ->
            assertTrue(entry.coverUrl?.contains("Majora%27s%20Mask") == true)
        }
    }

    @Test
    fun unknownFamilyHasNullCover() {
        val source = BaseRomLibrarySource(listOf(rom("ZZZ", "XXXX")))
        val entries = source.available()
        assertEquals(1, entries.size)
        assertNull(entries.first().coverUrl)
    }

    @Test
    fun ootFamilyPropagatedAsFamily() {
        val source = BaseRomLibrarySource(listOf(rom("AAA", "CZLE"), rom("BBB", "CZLJ")))
        val entries = source.available()
        assertEquals(2, entries.size)
        entries.forEach { assertEquals(OcarinaGame.OOT, it.family) }
    }

    @Test
    fun mmFamilyPropagatedAsFamily() {
        val source = BaseRomLibrarySource(listOf(rom("CCC", "NSME"), rom("DDD", "NZLE")))
        val entries = source.available()
        assertEquals(2, entries.size)
        entries.forEach { assertEquals(OcarinaGame.MM, it.family) }
    }

    @Test
    fun unknownFamilyPropagatedAsNull() {
        val source = BaseRomLibrarySource(listOf(rom("ZZZ", "XXXX")))
        val entries = source.available()
        assertEquals(1, entries.size)
        assertNull(entries.first().family)
    }

    @Test
    fun missingFileEntriesAreSkipped() {
        // Path points at a file that does not exist on disk.
        val missing = rom("MISS", "CZLE", path = "/no/such/dir/MISS.z64")
        val present = rom("OK", "CZLE")
        val source = BaseRomLibrarySource(listOf(missing, present))
        val entries = source.available()
        assertEquals(1, entries.size)
        assertEquals("vanilla_OK", entries.first().id)
    }

    @Test
    fun emptyInputYieldsEmptyList() {
        assertTrue(BaseRomLibrarySource(emptyList()).available().isEmpty())
    }
}
