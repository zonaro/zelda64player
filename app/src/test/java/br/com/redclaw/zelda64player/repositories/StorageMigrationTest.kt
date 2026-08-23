package br.com.redclaw.zelda64player.repositories

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Tests the pure ROM-migration helper [migrateLegacyRomFiles] (no Android deps)
 * used by [Storage.migrateLegacyRoms] to relocate patched ROMs out of the
 * legacy cache directory into the durable external-files store.
 */
class StorageMigrationTest {

    private fun newDir(): File =
        File.createTempFile("mig", "").also { it.delete() }.also { it.mkdirs() }

    @Test
    fun movesRomFilesFromCacheToStorage() {
        val cache = newDir()
        val storage = newDir()
        File(cache, "rom_ocarina_of_time_dx").writeBytes(ByteArray(1024))
        File(cache, "rom_the_missing_link").writeBytes(ByteArray(2048))

        migrateLegacyRomFiles(cache, storage)

        assertTrue(File(storage, "rom_ocarina_of_time_dx").exists())
        assertTrue(File(storage, "rom_the_missing_link").exists())
        assertFalse(File(cache, "rom_ocarina_of_time_dx").exists())
        assertFalse(File(cache, "rom_the_missing_link").exists())
    }

    @Test
    fun keepsExistingStorageFileAndDropsLegacyCopy() {
        val cache = newDir()
        val storage = newDir()
        File(cache, "rom_x").writeBytes(ByteArray(10))
        File(storage, "rom_x").writeBytes(ByteArray(20))

        migrateLegacyRomFiles(cache, storage)

        // The durable copy must be left untouched; the legacy copy is discarded.
        assertEquals(20, File(storage, "rom_x").length())
        assertFalse(File(cache, "rom_x").exists())
    }

    @Test
    fun ignoresNonRomFiles() {
        val cache = newDir()
        val storage = newDir()
        File(cache, "patch_x.bps").writeBytes(ByteArray(5))
        File(cache, "rom_y").writeBytes(ByteArray(5))

        migrateLegacyRomFiles(cache, storage)

        assertFalse(File(storage, "patch_x.bps").exists())
        assertTrue(File(storage, "rom_y").exists())
    }

    @Test
    fun noOpWhenCacheEmpty() {
        val cache = newDir()
        val storage = newDir()
        migrateLegacyRomFiles(cache, storage)
        assertEquals(0, storage.listFiles()?.size ?: 0)
    }
}
