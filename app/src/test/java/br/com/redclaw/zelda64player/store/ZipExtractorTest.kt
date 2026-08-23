package br.com.redclaw.zelda64player.store

import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipExtractorTest {

    @Test
    fun extractsNamedEntry() {
        val bpsContent = byteArrayOf(1, 2, 3, 4, 5)
        val zipBytes = ByteArrayOutputStream().use { bos ->
            ZipOutputStream(bos).use { zos ->
                zos.putNextEntry(ZipEntry("readme.txt"))
                zos.write("ignore".toByteArray())
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("hack.bps"))
                zos.write(bpsContent)
                zos.closeEntry()
            }
            bos.toByteArray()
        }
        val extracted = ZipExtractor.extractEntry(zipBytes, "hack.bps")
        assertArrayEquals(bpsContent, extracted)
    }

    @Test(expected = StoreException.GenericError::class)
    fun throwsWhenEntryMissing() {
        val zipBytes = ByteArrayOutputStream().use { bos ->
            ZipOutputStream(bos).use { zos ->
                zos.putNextEntry(ZipEntry("other.bps"))
                zos.write(byteArrayOf(9))
                zos.closeEntry()
            }
            bos.toByteArray()
        }
        ZipExtractor.extractEntry(zipBytes, "missing.bps")
    }
}
