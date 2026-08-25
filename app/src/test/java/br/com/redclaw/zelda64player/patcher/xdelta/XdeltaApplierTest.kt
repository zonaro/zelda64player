package br.com.redclaw.zelda64player.patcher.xdelta

import br.com.redclaw.zelda64player.patcher.PatcherException
import br.com.redclaw.zelda64player.patcher.PatcherFacade
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class XdeltaApplierTest {

    private val cliPath = System.getProperty("zelda64.xdelta.cli.path")
    private val jniPath = System.getProperty("zelda64.xdelta.jni.path")

    private fun tempFile(bytes: ByteArray): File =
        File.createTempFile("xdelta", ".bin").apply { writeBytes(bytes) }

    @Test
    fun detectPatchFormatIdentifiesVcdiffMagic() {
        val patch = tempFile(
            byteArrayOf(0xD6.toByte(), 0xC3.toByte(), 0xC4.toByte(), 0x00.toByte(), 1, 2, 3)
        )
        assertEquals(PatcherFacade.PatchFormat.XDELTA, PatcherFacade.detectPatchFormat(patch))
    }

    @Test
    fun expectedSourceCrc32RejectsXdelta() {
        val patch = tempFile(
            byteArrayOf(0xD6.toByte(), 0xC3.toByte(), 0xC4.toByte(), 0x00.toByte())
        )
        val result = PatcherFacade.expectedSourceCrc32(patch)
        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull() is PatcherException.PatchFormatError)
    }

    @Test
    fun applyRoundTripViaNative() {
        assumeTrue("xdelta3 CLI unavailable (zelda64.xdelta.cli.path)", !cliPath.isNullOrBlank())
        assumeTrue("xdelta3 JNI lib unavailable (zelda64.xdelta.jni.path)", !jniPath.isNullOrBlank())

        val rnd = java.util.Random(42)
        val source = ByteArray(76800) { (it and 0xFF).toByte() }
        val target = source.copyOf()
        for (i in 1000 until 5000) target[i] = (target[i].toInt() xor 0x55).toByte()
        val extra = ByteArray(5000) { rnd.nextInt(256).toByte() }
        val targetFull = target.copyOf(65000) + extra + source.copyOfRange(65000, 76800)

        val aFile = tempFile(source)
        val bFile = tempFile(targetFull)
        val patchFile = File.createTempFile("xdpatch", ".vcdiff")
        val outFile = tempFile(ByteArray(0))

        val enc = ProcessBuilder(cliPath, "-e", "-f", "-s", aFile.absolutePath, bFile.absolutePath, patchFile.absolutePath)
            .redirectErrorStream(true)
            .start()
        val encOut = enc.inputStream.bufferedReader().readText()
        assertEquals("encode failed: $encOut", 0, enc.waitFor())

        val result = XdeltaApplier.apply(aFile, patchFile, outFile)
        assertTrue("apply failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        assertArrayEquals(targetFull, outFile.readBytes())
    }

    @Test
    fun wrongSourceReportsSourceMismatch() {
        assumeTrue("xdelta3 CLI unavailable", !cliPath.isNullOrBlank())
        assumeTrue("xdelta3 JNI lib unavailable", !jniPath.isNullOrBlank())

        val source = ByteArray(4096) { (it and 0xFF).toByte() }
        val target = source.copyOf()
        for (i in 0 until 100) target[i] = (target[i].toInt() xor 0xAA).toByte()
        val aFile = tempFile(source)
        val bFile = tempFile(target)
        val patchFile = File.createTempFile("xdpatch", ".vcdiff")
        val enc = ProcessBuilder(cliPath, "-e", "-f", "-s", aFile.absolutePath, bFile.absolutePath, patchFile.absolutePath)
            .redirectErrorStream(true)
            .start()
        enc.inputStream.bufferedReader().readText()
        enc.waitFor()

        // Corrupt the ENTIRE source so any SourceCopy region referenced by the
        // patch fails xdelta3's internal source validation (flipping a single
        // byte can be missed if that byte was encoded as literal data).
        val wrong = tempFile(ByteArray(source.size) { (source[it].toInt() xor 0xFF).toByte() })
        val outFile = tempFile(ByteArray(0))
        val result = XdeltaApplier.apply(wrong, patchFile, outFile)
        assertFalse(result.isSuccess)
        assertTrue(
            "expected SourceChecksumMismatch, got ${result.exceptionOrNull()}",
            result.exceptionOrNull() is PatcherException.SourceChecksumMismatch
        )
    }
}
