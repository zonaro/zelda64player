package br.com.redclaw.zelda64player.repositories

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UninstallHackFilesTest {

    /** Create a unique, empty temporary directory for a single test. */
    private fun tempDir(): File {
        val dir = File(
            System.getProperty("java.io.tmpdir"),
            "uninstall_${System.nanoTime()}_${(0..9999).random()}"
        )
        dir.mkdirs()
        return dir
    }

    @Test
    fun deletesAllThreeFilesWhenPresent() {
        val dir = tempDir()
        try {
            val rom = File(dir, "rom_abc").apply { writeText("rom") }
            val sram = File(dir, "sram_abc").apply { writeText("sram") }
            val state = File(dir, "state_abc").apply { writeText("state") }

            val ok = uninstallHackFiles(dir, "abc")

            assertTrue(ok)
            assertFalse(rom.exists())
            assertFalse(sram.exists())
            assertFalse(state.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun missingFilesAreTreatedAsRemoved() {
        val dir = tempDir()
        try {
            // Only the ROM exists; sram/state were never created.
            val rom = File(dir, "rom_xyz").apply { writeText("rom") }

            val ok = uninstallHackFiles(dir, "xyz")

            assertTrue(ok)
            assertFalse(rom.exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun unrelatedFilesAreLeftUntouched() {
        val dir = tempDir()
        try {
            // Files for the hack being uninstalled ("keep")...
            File(dir, "rom_keep").apply { writeText("rom") }
            File(dir, "sram_keep").apply { writeText("sram") }
            File(dir, "state_keep").apply { writeText("state") }
            // ...and files for a DIFFERENT hack ("other") plus an unrelated file.
            File(dir, "rom_other").apply { writeText("rom") }
            File(dir, "sram_other").apply { writeText("sram") }
            File(dir, "state_other").apply { writeText("state") }
            File(dir, "other.dat").apply { writeText("x") }

            uninstallHackFiles(dir, "keep")

            // The uninstalled hack's files are gone...
            assertFalse(File(dir, "rom_keep").exists())
            assertFalse(File(dir, "sram_keep").exists())
            assertFalse(File(dir, "state_keep").exists())
            // ...but a different hack id's files and unrelated data survive.
            assertTrue(File(dir, "rom_other").exists())
            assertTrue(File(dir, "sram_other").exists())
            assertTrue(File(dir, "state_other").exists())
            assertTrue(File(dir, "other.dat").exists())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun returnsFalseWhenDeletionFails() {
        // A directory named like a save file cannot be deleted by File.delete()
        // while it is non-empty, exercising the failure path.
        val dir = tempDir()
        try {
            val asDir = File(dir, "rom_fail").apply { mkdirs() }
            File(asDir, "child").createNewFile()

            val ok = uninstallHackFiles(dir, "fail")

            assertFalse(ok)
            assertNotNull(asDir.parentFile)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun createTempDir(prefix: String): File {
        val dir = File(System.getProperty("java.io.tmpdir"), "${prefix}_${System.nanoTime()}")
        dir.mkdirs()
        return dir
    }
}
