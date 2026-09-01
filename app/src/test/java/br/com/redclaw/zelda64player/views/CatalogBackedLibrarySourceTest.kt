package br.com.redclaw.zelda64player.views

import br.com.redclaw.zelda64player.data.local.InstalledHacksRepository
import br.com.redclaw.zelda64player.data.model.BaseRomRef
import br.com.redclaw.zelda64player.data.model.Checksums
import br.com.redclaw.zelda64player.data.model.HackEntry
import br.com.redclaw.zelda64player.ocarina.OcarinaGame
import br.com.redclaw.zelda64player.store.CanonicalIdResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * Tests [CatalogBackedLibrarySource]: the badge's game family is detected from
 * the installed patched ROM header. Fake normalized-z64 header files (first
 * 0x40 bytes) are written to exercise OoT / MM detection, an unknown game code,
 * a too-short/garbage file, and an orphan record with no ROM on disk.
 */
class CatalogBackedLibrarySourceTest {

    private fun writeFakeRom(dir: File, id: String, gameCode: String, title: String = "FAKE ROM") {
        val file = File(dir, "rom_$id")
        val bytes = ByteArray(0x40)
        title.toByteArray(Charsets.US_ASCII).copyInto(bytes, 0x20)
        gameCode.toByteArray(Charsets.US_ASCII).copyInto(bytes, 0x3B)
        bytes[0x3F] = 0
        file.writeBytes(bytes)
    }

    private fun sourceWith(storage: File, romIds: List<String> = emptyList()): CatalogBackedLibrarySource {
        val installed = InstalledHacksRepository(File.createTempFile("installed", ".json"))
        romIds.forEach { installed.markInstalled(it, "1.0", "rom_$it") }
        return CatalogBackedLibrarySource(storage, installed, emptyMap())
    }

    @Test
    fun ootRomYieldsOotFamily() {
        val storage = File.createTempFile("storage", "").also { it.delete(); it.mkdirs() }
        writeFakeRom(storage, "oot1", "CZLE")
        val entries = sourceWith(storage).available()
        assertEquals(1, entries.size)
        assertEquals(OcarinaGame.OOT, entries.first().family)
        assertEquals(BadgeType.HACK, entries.first().badge)
    }

    @Test
    fun mmRomYieldsMmFamily() {
        val storage = File.createTempFile("storage", "").also { it.delete(); it.mkdirs() }
        writeFakeRom(storage, "mm1", "NZSE")
        val entries = sourceWith(storage).available()
        assertEquals(1, entries.size)
        assertEquals(OcarinaGame.MM, entries.first().family)
    }

    @Test
    fun unknownGameCodeYieldsNullFamily() {
        val storage = File.createTempFile("storage", "").also { it.delete(); it.mkdirs() }
        writeFakeRom(storage, "unk", "XXXX")
        val entries = sourceWith(storage).available()
        assertEquals(1, entries.size)
        assertNull(entries.first().family)
    }

    @Test
    fun shortOrGarbageFileYieldsNullFamily() {
        val storage = File.createTempFile("storage", "").also { it.delete(); it.mkdirs() }
        // File is too short to contain a header -> RomHeader throws, caught -> null.
        File(storage, "rom_short").writeBytes(ByteArray(10))
        val entries = sourceWith(storage).available()
        assertEquals(1, entries.size)
        assertNull(entries.first().family)
    }

    @Test
    fun orphanRecordWithoutRomYieldsNullFamily() {
        // Installed record exists but no rom_<id> file on disk.
        val storage = File.createTempFile("storage", "").also { it.delete(); it.mkdirs() }
        val entries = sourceWith(storage, romIds = listOf("orphan")).available()
        assertEquals(1, entries.size)
        assertNull(entries.first().family)
    }

    @Test
    fun groupsCatalogEntriesByCanonicalId() {
        CanonicalIdResolver.reset()
        val storage = File.createTempFile("storage", "").also { it.delete(); it.mkdirs() }
        // Same hack installed under two different store ids (PICKS + Hylian Modding).
        writeFakeRom(storage, "the-missing-link", "CZLE")
        writeFakeRom(storage, "hm_themissinglink", "CZLE")

        val picks = HackEntry(
            id = "the-missing-link", name = "The Missing Link", description = "", author = "x",
            version = "1.0", baseRom = BaseRomRef("OoT", "CZLE", 0, Checksums("abc")),
            storeId = "picks"
        )
        val hm = HackEntry(
            id = "hm_themissinglink", name = "HM The Missing Link", description = "", author = "x",
            version = "1.0", baseRom = BaseRomRef("OoT", "CZLE", 0, Checksums("abc")),
            storeId = "hylianmodding"
        )
        val catalog = mapOf(picks.id to picks, hm.id to hm)
        val installed = InstalledHacksRepository(File.createTempFile("installed", ".json"))
        installed.markInstalled("the-missing-link", "1.0", "rom_the-missing-link")
        installed.markInstalled("hm_themissinglink", "1.0", "rom_hm_themissinglink")

        val entries = CatalogBackedLibrarySource(storage, installed, catalog).available()
        assertEquals(1, entries.size)
        val entry = entries.first()
        assertEquals("themissinglink", entry.id)
        assertEquals("The Missing Link", entry.title) // PICKS representative preferred
        assertEquals("picks", entry.storeId)
        assertEquals(OcarinaGame.OOT, entry.family)
        // romId must be one of the original hack ids (the one with a ROM on disk)
        assert(entry.romId == "the-missing-link" || entry.romId == "hm_themissinglink") {
            "romId should be an original hack id, got ${entry.romId}"
        }
    }
}
