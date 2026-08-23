package br.com.redclaw.zelda64player.patcher.ips

import br.com.redclaw.zelda64player.patcher.PatcherException.PatchFormatError
import br.com.redclaw.zelda64player.patcher.PatcherFacade
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IpsApplierTest {

    private fun tempFile(bytes: ByteArray): File =
        File.createTempFile("ips", ".bin").apply { writeBytes(bytes) }

    private fun applyRoundTrip(target: ByteArray, patch: ByteArray): ByteArray {
        val patchFile = tempFile(patch)
        val out = tempFile(ByteArray(0))
        val result = IpsApplier.apply(tempFile(ByteArray(0)), patchFile, out)
        assertTrue("apply failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        return out.readBytes()
    }

    @Test
    fun simpleLiteralRoundTrip() {
        val target = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        val patch = IpsPatchEncoder.encode(byteArrayOf(0), target)
        assertArrayEquals(target, applyRoundTrip(target, patch))
    }

    @Test
    fun rleRecordRoundTrip() {
        val target = ByteArray(200) { 0xAB.toByte() }
        val patch = IpsPatchEncoder.encode(byteArrayOf(0), target)
        assertArrayEquals(target, applyRoundTrip(target, patch))
    }

    @Test
    fun mixedLiteralAndRle() {
        val target = ByteArray(300) { i -> if (i < 100) (i and 0xFF).toByte() else 0xCD.toByte() }
        val patch = IpsPatchEncoder.encode(byteArrayOf(0), target)
        assertArrayEquals(target, applyRoundTrip(target, patch))
    }

    @Test
    fun gapFillPastEndZeroFilled() {
        // Record starts at offset 10 with 5 bytes; bytes 0..9 must be zero-filled.
        val patch = IpsPatchEncoder.encodeRaw(
            listOf(IpsPatchEncoder.Record(offset = 10, length = 5, data = byteArrayOf(9, 8, 7, 6, 5)))
        )
        val expected = ByteArray(15) { i -> if (i in 10..14) (14 - i + 5).toByte() else 0 }
        // expected[10..14] = 9,8,7,6,5
        val out = applyRoundTrip(expected, patch)
        assertArrayEquals(expected, out)
    }

    @Test
    fun missingEofTolerated() {
        val target = byteArrayOf(3, 1, 4, 1, 5, 9, 2, 6)
        val patch = IpsPatchEncoder.encode(byteArrayOf(0), target, includeEof = false)
        assertArrayEquals(target, applyRoundTrip(target, patch))
    }

    @Test
    fun trailingDataAfterEofIgnored() {
        val target = byteArrayOf(1, 2, 3)
        val patchBytes = IpsPatchEncoder.encode(byteArrayOf(0), target)
        // Append junk after EOF; must be ignored.
        val withJunk = patchBytes + byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte())
        assertArrayEquals(target, applyRoundTrip(target, withJunk))
    }

    @Test
    fun badMagicIsTypedError() {
        val patch = tempFile(byteArrayOf(1, 2, 3, 4, 5))
        val out = tempFile(ByteArray(0))
        val result = IpsApplier.apply(tempFile(ByteArray(0)), patch, out)
        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull() is PatchFormatError)
    }

    @Test
    fun truncatedRecordIsTypedError() {
        // "PATCH" + a 3-byte partial offset header, then EOF of file.
        val patch = tempFile("PATCH".toByteArray(Charsets.US_ASCII) + byteArrayOf(0x00, 0x00, 0x00))
        val out = tempFile(ByteArray(0))
        val result = IpsApplier.apply(tempFile(ByteArray(0)), patch, out)
        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull() is PatchFormatError)
    }

    @Test
    fun truncatedDataIsTypedError() {
        // Record claims 10 bytes of literal data but only 3 follow before EOF.
        val patch = tempFile(
            "PATCH".toByteArray(Charsets.US_ASCII) +
                byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x00, 0x0A, 0x01, 0x02, 0x03)
        )
        val out = tempFile(ByteArray(0))
        val result = IpsApplier.apply(tempFile(ByteArray(0)), patch, out)
        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull() is PatchFormatError)
    }

    @Test
    fun facadeDetectsIpsAndBps() {
        val ips = tempFile(IpsPatchEncoder.encode(byteArrayOf(0), byteArrayOf(1, 2, 3)))
        assertEquals(PatcherFacade.PatchFormat.IPS, PatcherFacade.detectPatchFormat(ips))
        val bps = tempFile("BPS1".toByteArray())
        assertEquals(PatcherFacade.PatchFormat.BPS, PatcherFacade.detectPatchFormat(bps))
        val junk = tempFile(byteArrayOf(0x00, 0x01, 0x02, 0x03))
        assertEquals(PatcherFacade.PatchFormat.UNKNOWN, PatcherFacade.detectPatchFormat(junk))
    }

    @Test
    fun facadeDispatchesIpsApply() {
        val target = byteArrayOf(4, 5, 6, 7, 8)
        val patch = tempFile(IpsPatchEncoder.encode(byteArrayOf(0), target))
        val out = tempFile(ByteArray(0))
        val result = PatcherFacade.applyPatchBlocking(tempFile(ByteArray(0)), patch, out)
        assertTrue("facade IPS dispatch failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        assertArrayEquals(target, out.readBytes())
    }

    @Test
    fun facadeRejectsUnknownFormat() {
        val patch = tempFile(byteArrayOf(0x00, 0x01, 0x02, 0x03))
        val out = tempFile(ByteArray(0))
        val result = PatcherFacade.applyPatchBlocking(tempFile(ByteArray(0)), patch, out)
        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull() is PatchFormatError)
    }
}
