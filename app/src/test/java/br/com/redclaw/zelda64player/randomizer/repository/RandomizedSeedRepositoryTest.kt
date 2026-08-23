package br.com.redclaw.zelda64player.randomizer.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RandomizedSeedRepositoryTest {

    private fun tempDir(prefix: String): File {
        val dir = File.createTempFile(prefix, "").apply { delete() }
        dir.mkdirs()
        dir.deleteOnExit()
        return dir
    }

    private fun sampleEntry(id: String, name: String): RandomizedSeedEntry = RandomizedSeedEntry(
        id = id,
        name = name,
        ootrSeedId = "server-$id",
        ootrVersion = "6.2",
        createdAt = 1700000000000L,
        hasPlandomizer = false,
        romFileName = "rom_$id",
        baseRomLabel = "CZLE v0"
    )

    @Test
    fun addPersistsEntryAndMovesRomFile() {
        val romsDir = tempDir("roms")
        val index = File(tempDir("idx"), "seeds.json")
        val repo = RandomizedSeedRepository(romsDir, index)

        val romSource = File(romsDir, "temp_rom.z64").apply { writeText("ROMDATA") }
        val entry = sampleEntry("ootr_abc123", "My Seed")

        repo.add(entry, romSource)

        // Index now has one entry
        assertEquals(1, repo.list().size)
        assertEquals(entry.id, repo.list().first().id)

        // ROM moved into romsDir/rom_<id> and original temp removed
        val romTarget = File(romsDir, "rom_ootr_abc123")
        assertTrue(romTarget.exists())
        assertEquals("ROMDATA", romTarget.readText())
        assertFalse(romSource.exists())

        // get() resolves by id
        assertNotNull(repo.get("ootr_abc123"))
        assertNull(repo.get("does_not_exist"))
    }

    @Test
    fun removeDeletesIndexEntryAndAllPerHackFiles() {
        val romsDir = tempDir("roms")
        val index = File(tempDir("idx"), "seeds.json")
        val repo = RandomizedSeedRepository(romsDir, index)

        val romSource = File(romsDir, "temp_rom.z64").apply { writeText("ROMDATA") }
        val entry = sampleEntry("ootr_xyz", "Seed X")
        repo.add(entry, romSource)

        // Simulate saves created for this seed (sram/state live in romsDir).
        File(romsDir, "sram_ootr_xyz").apply { writeText("SRAM") }
        File(romsDir, "state_ootr_xyz").apply { writeText("STATE") }

        val ok = repo.remove("ootr_xyz")

        assertTrue(ok)
        assertTrue(repo.list().isEmpty())
        assertFalse(File(romsDir, "rom_ootr_xyz").exists())
        assertFalse(File(romsDir, "sram_ootr_xyz").exists())
        assertFalse(File(romsDir, "state_ootr_xyz").exists())
    }

    @Test
    fun listReturnsAllPersistedSeeds() {
        val romsDir = tempDir("roms")
        val index = File(tempDir("idx"), "seeds.json")
        val repo = RandomizedSeedRepository(romsDir, index)

        repo.add(sampleEntry("ootr_a", "A"), File(romsDir, "t_a.z64").apply { writeText("a") })
        repo.add(sampleEntry("ootr_b", "B"), File(romsDir, "t_b.z64").apply { writeText("b") })

        val all = repo.list()
        assertEquals(2, all.size)
        assertTrue(all.any { it.id == "ootr_a" })
        assertTrue(all.any { it.id == "ootr_b" })
    }

    @Test
    fun emptyRepositoryReturnsNoEntries() {
        val repo = RandomizedSeedRepository(tempDir("roms"), File(tempDir("idx"), "seeds.json"))
        assertTrue(repo.list().isEmpty())
        assertNull(repo.get("anything"))
    }
}
