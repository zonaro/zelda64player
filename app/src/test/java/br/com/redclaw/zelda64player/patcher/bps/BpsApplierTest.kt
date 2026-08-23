package br.com.redclaw.zelda64player.patcher.bps

import br.com.redclaw.zelda64player.patcher.PatcherException.PatchChecksumMismatch
import br.com.redclaw.zelda64player.patcher.PatcherException.PatchFormatError
import br.com.redclaw.zelda64player.patcher.PatcherException.SourceChecksumMismatch
import br.com.redclaw.zelda64player.patcher.PatcherFacade
import br.com.redclaw.zelda64player.patcher.n64.ChecksumCalculator
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

class BpsApplierTest {

    private fun tempFile(bytes: ByteArray): File =
        File.createTempFile("bps", ".bin").apply { writeBytes(bytes) }

    private fun applyRoundTrip(source: ByteArray, target: ByteArray): ByteArray {
        val patch = BpsPatchEncoder.encode(source, target)
        val sourceFile = tempFile(source)
        val patchFile = tempFile(patch)
        val outFile = tempFile(ByteArray(0))
        val result = BpsApplier.apply(sourceFile, patchFile, outFile)
        assertTrue("apply failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        return outFile.readBytes()
    }

    @Test
    fun identicalFiles() {
        val data = ByteArray(2048) { (it * 7).toByte() }
        assertArrayEquals(data, applyRoundTrip(data, data))
    }

    @Test
    fun targetGrowth() {
        val source = ByteArray(16) { it.toByte() }
        val target = ByteArray(64) { (it * 3).toByte() }
        assertArrayEquals(target, applyRoundTrip(source, target))
    }

    @Test
    fun longRepeatedByteRun() {
        val source = ByteArray(0)
        val target = ByteArray(200_000) { 0xAB.toByte() }
        assertArrayEquals(target, applyRoundTrip(source, target))
    }

    @Test
    fun sourceCopyPositiveOffset() {
        val source = byteArrayOf(10, 20, 30, 40)
        val target = byteArrayOf(20, 30, 40, 10)
        assertArrayEquals(target, applyRoundTrip(source, target))
    }

    @Test
    fun sourceCopyNegativeOffset() {
        val source = byteArrayOf(10, 20, 30, 40)
        val target = byteArrayOf(30, 40, 10, 20)
        assertArrayEquals(target, applyRoundTrip(source, target))
    }

    @Test
    fun targetCopyRun() {
        val source = byteArrayOf(1, 2, 3, 4)
        val target = byteArrayOf(1, 2, 3, 4, 1, 2, 3, 4, 9, 9, 9)
        assertArrayEquals(target, applyRoundTrip(source, target))
    }

    @Test
    fun multiMegabyteStreaming() {
        val rnd = java.util.Random(42)
        val source = ByteArray(4 * 1024 * 1024) { rnd.nextInt(256).toByte() }
        val target = source.copyOf()
        for (i in 1_000_000 until 1_500_000) target[i] = (target[i] + 1).toByte()
        assertArrayEquals(target, applyRoundTrip(source, target))
    }

    @Test
    fun manualRleTargetCopyLargeLength() {
        val n = 50_000
        val target = ByteArray(n) { 0x5A.toByte() }
        val body = ByteArrayOutputStream()
        body.write("BPS1".toByteArray())
        body.write(VarInt.encode(0))
        body.write(VarInt.encode(n.toLong()))
        body.write(VarInt.encode(0))
        body.write(VarInt.encode((1L) or ((1L - 1) shl 2)))
        body.write(0x5A)
        body.write(VarInt.encode((3L) or ((n.toLong() - 1 - 1) shl 2)))
        body.write(VarInt.encode(BpsParser.encodeSignedOffset(0)))
        body.write(uint32Le(0L))
        body.write(uint32Le(crc32(target)))
        body.write(uint32Le(crc32(body.toByteArray())))

        val out = tempFile(ByteArray(0))
        val result = BpsApplier.apply(tempFile(ByteArray(0)), tempFile(body.toByteArray()), out)
        assertTrue("manual RLE apply failed: ${result.exceptionOrNull()?.message}", result.isSuccess)
        assertArrayEquals(target, out.readBytes())
    }

    @Test
    fun flippedPatchChecksumField() {
        val source = byteArrayOf(1, 2, 3, 4, 5)
        val target = byteArrayOf(5, 4, 3, 2, 1)
        val patch = BpsPatchEncoder.encode(source, target)
        val corrupted = patch.copyOf()
        corrupted[corrupted.lastIndex] = (corrupted.lastIndex + 1).toByte()
        // Checksum enforcement lives in the public facade (BpsValidator), not in
        // the measuring BpsApplier — so corruption tests go through PatcherFacade.
        val result = PatcherFacade.applyPatchBlocking(
            tempFile(source), tempFile(corrupted), tempFile(ByteArray(0))
        )
        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull() is PatchChecksumMismatch)
    }

    @Test
    fun flippedSourceChecksumField() {
        val source = byteArrayOf(1, 2, 3, 4, 5)
        val target = byteArrayOf(5, 4, 3, 2, 1)
        val patch = BpsPatchEncoder.encode(source, target)
        val corrupted = patch.copyOf()
        corrupted[corrupted.size - 12] = (corrupted[corrupted.size - 12] + 1).toByte()
        val result = PatcherFacade.applyPatchBlocking(
            tempFile(source), tempFile(corrupted), tempFile(ByteArray(0))
        )
        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull() is SourceChecksumMismatch)
    }

    @Test
    fun truncatedFileIsTypedError() {
        val source = byteArrayOf(1, 2, 3, 4, 5)
        val target = byteArrayOf(5, 4, 3, 2, 1)
        val patch = BpsPatchEncoder.encode(source, target)
        val truncated = patch.copyOf(5)
        val result = BpsApplier.apply(tempFile(source), tempFile(truncated), tempFile(ByteArray(0)))
        assertFalse(result.isSuccess)
        assertTrue(result.exceptionOrNull() is PatchFormatError)
    }

    @Test
    fun wrongSourceRom() {
        val sourceA = byteArrayOf(1, 2, 3, 4, 5)
        val target = byteArrayOf(5, 4, 3, 2, 1)
        val patch = BpsPatchEncoder.encode(sourceA, target)
        val sourceB = byteArrayOf(9, 9, 9, 9, 9)
        val result = PatcherFacade.applyPatchBlocking(
            tempFile(sourceB), tempFile(patch), tempFile(ByteArray(0))
        )
        assertFalse(result.isSuccess)
        val ex = result.exceptionOrNull()
        assertTrue(ex is SourceChecksumMismatch)
        assertEquals(
            ChecksumCalculator.toHex(ChecksumCalculator.crc32Raw(tempFile(sourceB))),
            (ex as SourceChecksumMismatch).foundCrc32
        )
    }

    private fun crc32(data: ByteArray): Long {
        val c = java.util.zip.CRC32()
        c.update(data)
        return c.value
    }

    private fun uint32Le(value: Long): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 24) and 0xFF).toByte()
    )
}
