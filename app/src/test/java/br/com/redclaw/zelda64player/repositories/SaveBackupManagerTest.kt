package br.com.redclaw.zelda64player.repositories

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class SaveBackupManagerTest {

    private fun tempFile(bytes: ByteArray): File =
        File.createTempFile("sav", ".bin").apply { writeBytes(bytes) }

    @Test
    fun exportThenImportRoundTrip() {
        val sram = tempFile(byteArrayOf(1, 2, 3, 4, 5))
        val state = tempFile(byteArrayOf(9, 8, 7, 6))

        val zipBytes = ByteArrayOutputStream().use { out ->
            SaveBackupManager.exportToStream(out, sram, state)
            out.toByteArray()
        }

        val outSram = tempFile(ByteArray(0))
        val outState = tempFile(ByteArray(0))
        val summary = ByteArrayInputStream(zipBytes).use { input ->
            SaveBackupManager.importFromStream(input, outSram, outState)
        }

        assertTrue(summary.ok)
        assertEquals(2, summary.files)
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5), outSram.readBytes())
        assertArrayEquals(byteArrayOf(9, 8, 7, 6), outState.readBytes())
    }

    @Test
    fun exportSkipsMissingFiles() {
        // Only sram exists; state is a non-existent file.
        val sram = tempFile(byteArrayOf(42))
        val missingState = File.createTempFile("state", ".bin").apply { delete() }

        val zipBytes = ByteArrayOutputStream().use { out ->
            SaveBackupManager.exportToStream(out, sram, missingState)
            out.toByteArray()
        }

        val outSram = tempFile(ByteArray(0))
        val outState = tempFile(ByteArray(0))
        val summary = ByteArrayInputStream(zipBytes).use { input ->
            SaveBackupManager.importFromStream(input, outSram, outState)
        }

        assertTrue(summary.ok)
        assertEquals(1, summary.files)
        assertArrayEquals(byteArrayOf(42), outSram.readBytes())
        // State was never in the archive, so the target stays empty.
        assertEquals(0, outState.readBytes().size)
    }

    @Test
    fun importRejectsArchiveWithNoValidEntries() {
        // A ZIP containing only an unrelated entry must be rejected.
        val zipBytes = ByteArrayOutputStream().use { bos ->
            java.util.zip.ZipOutputStream(bos).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("readme.txt"))
                zip.write("not a save".toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            bos.toByteArray()
        }

        val outSram = tempFile(ByteArray(0))
        val outState = tempFile(ByteArray(0))
        val summary = ByteArrayInputStream(zipBytes).use { input ->
            SaveBackupManager.importFromStream(input, outSram, outState)
        }

        assertFalse(summary.ok)
        assertEquals(0, summary.files)
        // Targets must remain untouched.
        assertEquals(0, outSram.readBytes().size)
        assertEquals(0, outState.readBytes().size)
    }

    @Test
    fun importIgnoresUnknownEntriesAndWritesOnlyKnownNames() {
        // Even if a malicious entry name appears, only sram.bin/state.bin are
        // read, so no path traversal or stray write is possible.
        val sram = tempFile(byteArrayOf(11, 22))
        val zipBytes = ByteArrayOutputStream().use { bos ->
            java.util.zip.ZipOutputStream(bos).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("../../evil.bin"))
                zip.write(byteArrayOf(99))
                zip.closeEntry()
                zip.putNextEntry(java.util.zip.ZipEntry(SAVE_ENTRY_SRAM))
                zip.write(sram.readBytes())
                zip.closeEntry()
            }
            bos.toByteArray()
        }

        val outSram = tempFile(ByteArray(0))
        val outState = tempFile(ByteArray(0))
        val summary = ByteArrayInputStream(zipBytes).use { input ->
            SaveBackupManager.importFromStream(input, outSram, outState)
        }

        assertTrue(summary.ok)
        assertEquals(1, summary.files)
        assertArrayEquals(byteArrayOf(11, 22), outSram.readBytes())
    }
}
