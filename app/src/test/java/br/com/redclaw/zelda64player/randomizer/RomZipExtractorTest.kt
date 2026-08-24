package br.com.redclaw.zelda64player.randomizer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class RomZipExtractorTest {

    private fun tempDir(prefix: String): File {
        val dir = File.createTempFile(prefix, "").apply { delete() }
        dir.mkdirs()
        dir.deleteOnExit()
        return dir
    }

    private fun writeZip(file: File, entries: Map<String, ByteArray>) {
        ZipOutputStream(file.outputStream()).use { zos ->
            for ((name, bytes) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
    }

    @Test
    fun extractZ64ReturnsExtractedFileWhenZipContainsZ64() {
        val zip = File(tempDir("zip"), "rom.zip").apply { deleteOnExit() }
        writeZip(zip, mapOf("game.z64" to "ROMBYTES".toByteArray()))

        val outDir = tempDir("out")
        val result = RomZipExtractor.extractZ64(zip, outDir)

        assertTrue(result != null)
        assertTrue(result!!.exists())
        assertEquals("ROMBYTES", result.readText())
    }

    @Test
    fun extractZ64ReturnsNullWhenNoRomEntry() {
        val zip = File(tempDir("zip"), "rom.zip").apply { deleteOnExit() }
        writeZip(zip, mapOf("readme.txt" to "not a rom".toByteArray()))

        val outDir = tempDir("out")
        val result = RomZipExtractor.extractZ64(zip, outDir)

        assertNull(result)
    }

    @Test
    fun extractZ64HandlesNestedEntryPath() {
        val zip = File(tempDir("zip"), "rom.zip").apply { deleteOnExit() }
        writeZip(zip, mapOf("sub/dir/game.n64" to "N64DATA".toByteArray()))

        val outDir = tempDir("out")
        val result = RomZipExtractor.extractZ64(zip, outDir)

        assertTrue(result != null)
        assertEquals("game.n64", result!!.name)
        assertEquals("N64DATA", result.readText())
    }

    @Test
    fun extractZ64ReturnsNullForMissingZip() {
        val missing = File(tempDir("zip"), "does_not_exist.zip")
        val outDir = tempDir("out")
        assertNull(RomZipExtractor.extractZ64(missing, outDir))
    }
}
