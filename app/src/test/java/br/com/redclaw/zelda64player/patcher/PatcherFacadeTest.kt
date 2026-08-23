package br.com.redclaw.zelda64player.patcher

import br.com.redclaw.zelda64player.patcher.bps.BpsPatchEncoder
import br.com.redclaw.zelda64player.patcher.bps.BpsValidator
import br.com.redclaw.zelda64player.patcher.n64.ChecksumCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PatcherFacadeTest {

    private fun tempFile(bytes: ByteArray): File =
        File.createTempFile("pftest", ".bin").apply { writeBytes(bytes) }

    @Test
    fun applyPatchBlockingRoundTrip() {
        val source = byteArrayOf(1, 2, 3, 4, 5, 6)
        val target = byteArrayOf(6, 5, 4, 3, 2, 1, 9, 9)
        val patch = BpsPatchEncoder.encode(source, target)
        val out = tempFile(ByteArray(0))
        val result = PatcherFacade.applyPatchBlocking(tempFile(source), tempFile(patch), out)
        assertTrue("applyPatch failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        assertEquals(target.toList(), out.readBytes().toList())
    }

    @Test
    fun expectedSourceCrc32MatchesSource() {
        val source = byteArrayOf(1, 2, 3, 4, 5, 6)
        val target = byteArrayOf(6, 5, 4, 3, 2, 1, 9, 9)
        val patch = BpsPatchEncoder.encode(source, target)
        val expected = PatcherFacade.expectedSourceCrc32(tempFile(patch))
        assertTrue(expected.isSuccess)
        assertEquals(ChecksumCalculator.toHex(ChecksumCalculator.crc32Raw(tempFile(source))), expected.getOrNull())
    }

    @Test
    fun expectedSourceCrc32RejectsNonBps() {
        val notBps = tempFile(byteArrayOf(1, 2, 3, 4))
        val result = PatcherFacade.expectedSourceCrc32(notBps)
        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull() is PatcherException.PatchFormatError)
    }

    @Test
    fun validateSourceMatchesAndMismatches() {
        val source = byteArrayOf(1, 2, 3, 4, 5)
        val crc = ChecksumCalculator.toHex(ChecksumCalculator.crc32Raw(tempFile(source)))
        assertTrue(BpsValidator.validateSource(crc, crc).isSuccess)
        val mismatch = BpsValidator.validateSource("deadbeef", crc)
        assertFalse(mismatch.isSuccess)
        assertTrue(mismatch.exceptionOrNull() is PatcherException.SourceChecksumMismatch)
    }
}
