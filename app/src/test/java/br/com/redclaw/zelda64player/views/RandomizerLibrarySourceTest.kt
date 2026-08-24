package br.com.redclaw.zelda64player.views

import br.com.redclaw.zelda64player.ocarina.OcarinaGame
import br.com.redclaw.zelda64player.randomizer.repository.RandomizedSeedEntry
import br.com.redclaw.zelda64player.randomizer.repository.RandomizedSeedRepository
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Tests [RandomizerLibrarySource]: every seed tile is flagged RANDOMIZER and
 * carries the OoT game family (AGENTS.md Rule 17 only accepts OoT 1.0 as the
 * randomizer base ROM, so seeds are always OoT).
 */
class RandomizerLibrarySourceTest {

    @Test
    fun seedsAreAlwaysOotFamily() {
        val romsDir = File.createTempFile("roms", "").also { it.delete(); it.mkdirs() }
        val index = File.createTempFile("seeds", ".json")
        val repo = RandomizedSeedRepository(romsDir, index)
        val entry = RandomizedSeedEntry(
            id = "ootr_test",
            name = "Test Seed",
            ootrSeedId = "123",
            ootrVersion = "7.1",
            createdAt = 0L,
            hasPlandomizer = false,
            romFileName = "rom_ootr_test",
            baseRomLabel = "OoT"
        )
        val romFile = File.createTempFile("seedrom", ".z64")
        romFile.writeBytes(ByteArray(0x40))
        repo.add(entry, romFile)

        val source = RandomizerLibrarySource(repo)
        val entries = source.available()
        assertEquals(1, entries.size)
        assertEquals(OcarinaGame.OOT, entries.first().family)
        assertEquals(BadgeType.RANDOMIZER, entries.first().badge)
        assertEquals(true, entries.first().isRandomizer)
    }
}
