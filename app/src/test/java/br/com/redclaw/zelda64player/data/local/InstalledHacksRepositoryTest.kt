package br.com.redclaw.zelda64player.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class InstalledHacksRepositoryTest {

    private fun tempFile(): File {
        val f = File.createTempFile("installed", ".json")
        f.deleteOnExit()
        return f
    }

    @Test
    fun roundTripPersistLoad() {
        val file = tempFile()
        val repo = InstalledHacksRepository(file)
        repo.markInstalled("hack_a", "1.0", "hack_a.bps")
        repo.markInstalled("hack_b", "2.3", "hack_b.bps")

        val reloaded = InstalledHacksRepository(file)
        assertEquals(2, reloaded.load().size)
        assertEquals("1.0", reloaded.installedVersion("hack_a"))
        assertEquals("hack_a.bps", reloaded.getInstalled("hack_a")?.fileName)
        assertTrue(reloaded.isInstalled("hack_b"))
        assertFalse(reloaded.isInstalled("hack_c"))
    }

    @Test
    fun overwritesSameHackId() {
        val file = tempFile()
        val repo = InstalledHacksRepository(file)
        repo.markInstalled("hack_a", "1.0", "hack_a.bps")
        repo.markInstalled("hack_a", "1.1", "hack_a.bps")
        assertEquals("1.1", InstalledHacksRepository(file).installedVersion("hack_a"))
    }

    @Test
    fun emptyWhenNoFile() {
        val file = tempFile()
        file.delete()
        val repo = InstalledHacksRepository(file)
        assertTrue(repo.load().isEmpty())
        assertFalse(repo.isInstalled("anything"))
    }
}
