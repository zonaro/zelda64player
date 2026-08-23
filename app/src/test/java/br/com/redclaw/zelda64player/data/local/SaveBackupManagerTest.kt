package br.com.redclaw.zelda64player.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SaveBackupManagerTest {

    private fun tempFile(bytes: ByteArray): File =
        File.createTempFile("sav", ".bin").apply { writeBytes(bytes) }

    @Test
    fun manifestSerializationRoundTrip() {
        val manifest = SaveBackupManager.Manifest(
            appVersion = "1.0",
            exportDate = "2026-01-01T00:00:00Z",
            hacks = listOf(
                SaveBackupManager.HackEntry(
                    hackId = "hack_a",
                    files = listOf(
                        SaveBackupManager.FileEntry("hack_a/sram_hack_a", 32, "deadbeef"),
                        SaveBackupManager.FileEntry("hack_a/state_hack_a", 64, "cafebabe")
                    )
                )
            )
        )
        val json = manifest.toJson().toString()
        val restored = SaveBackupManager.Manifest.fromJson(org.json.JSONObject(json))
        assertEquals(manifest, restored)
    }

    @Test
    fun exportThenRestoreAcceptsValidArchive() {
        // Mirror the real Storage filenames (sram_<hackId> / state_<hackId>) so
        // the resolver's prefix check behaves like the app.
        val sram = File.createTempFile("sram_hack_x", ".bin").apply { writeBytes(byteArrayOf(1, 2, 3, 4, 5)) }
        val state = File.createTempFile("state_hack_x", ".bin").apply { writeBytes(byteArrayOf(9, 8, 7, 6)) }
        val saves = mapOf("hack_x" to listOf(sram, state))

        val zipBytes = ByteArrayOutputStream().use { out ->
            SaveBackupManager.export(out, saves, "1.0")
            out.toByteArray()
        }

        // Restore into fresh target files.
        val outSram = tempFile(ByteArray(0))
        val outState = tempFile(ByteArray(0))
        val resolver: (String, String) -> File? = { _, fileName ->
            when {
                fileName.startsWith("sram_") -> outSram
                fileName.startsWith("state_") -> outState
                else -> null
            }
        }
        val summary = ByteArrayInputStream(zipBytes).use { input ->
            SaveBackupManager.restore(input, resolver)
        }

        assertTrue(summary.ok)
        assertEquals(1, summary.hacks)
        assertEquals(2, summary.files)
        assertEquals(0, summary.skipped)
        assertEquals(1, outSram.readBytes()[0].toInt())
        assertEquals(9, outState.readBytes()[0].toInt())
    }

    @Test
    fun restoreRejectsCrcMismatch() {
        // Build a ZIP whose manifest claims crc "00000000" but the entry bytes
        // actually have a different CRC. Restore must skip that entry.
        val realBytes = byteArrayOf(1, 2, 3, 4)
        val badManifest = SaveBackupManager.Manifest(
            appVersion = "1.0",
            exportDate = "2026-01-01T00:00:00Z",
            hacks = listOf(
                SaveBackupManager.HackEntry(
                    hackId = "hack_y",
                    files = listOf(SaveBackupManager.FileEntry("hack_y/sram_hack_y", realBytes.size.toLong(), "00000000"))
                )
            )
        )
        val zipBytes = ByteArrayOutputStream().use { bos ->
            ZipOutputStream(bos).use { zip ->
                zip.putNextEntry(ZipEntry("hack_y/sram_hack_y"))
                zip.write(realBytes)
                zip.closeEntry()
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(badManifest.toJson().toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            bos.toByteArray()
        }

        val outSram = tempFile(ByteArray(0))
        val resolver: (String, String) -> File? = { _, fileName ->
            if (fileName == "sram_hack_y") outSram else null
        }
        val summary = ByteArrayInputStream(zipBytes).use { input ->
            SaveBackupManager.restore(input, resolver)
        }

        assertEquals(0, summary.hacks)
        assertEquals(0, summary.files)
        assertEquals(1, summary.skipped)
        assertFalse(summary.ok)
        // Target must remain untouched (empty).
        assertEquals(0, outSram.readBytes().size)
    }

    @Test
    fun restoreSkipsMissingEntry() {
        // Manifest references a file that is absent from the archive.
        val manifest = SaveBackupManager.Manifest(
            appVersion = "1.0",
            exportDate = "2026-01-01T00:00:00Z",
            hacks = listOf(
                SaveBackupManager.HackEntry(
                    hackId = "hack_z",
                    files = listOf(SaveBackupManager.FileEntry("hack_z/sram_hack_z", 4, "00000000"))
                )
            )
        )
        val zipBytes = ByteArrayOutputStream().use { bos ->
            ZipOutputStream(bos).use { zip ->
                zip.putNextEntry(ZipEntry("manifest.json"))
                zip.write(manifest.toJson().toString().toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
            bos.toByteArray()
        }
        val resolver: (String, String) -> File? = { _, _ -> tempFile(ByteArray(0)) }
        val summary = ByteArrayInputStream(zipBytes).use { input ->
            SaveBackupManager.restore(input, resolver)
        }
        assertEquals(1, summary.skipped)
        assertEquals(0, summary.files)
    }
}
