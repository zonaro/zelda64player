package br.com.redclaw.zelda64player.randomizer.patch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import br.com.redclaw.zelda64player.randomizer.patch.RandomizerPatchTestUtils.deflate

class ZpfzDecoderTest {

    @Test
    fun decodesConcatenatedStreamsInOrder() {
        val blobA = "hello".toByteArray()
        val blobB = "world!".toByteArray()
        val concatenated = deflate(blobA) + deflate(blobB)

        val file = RandomizerPatchTestUtils.tempFile(concatenated)
        val decoded = ZpfzDecoder.decode(file)

        assertEquals(2, decoded.size)
        assertArrayEquals(blobA, decoded[0])
        assertArrayEquals(blobB, decoded[1])
    }

    @Test
    fun decodesThreeStreams() {
        val blobs = listOf("a", "bb", "ccc").map { it.toByteArray() }
        var acc = ByteArray(0)
        for (b in blobs) acc += deflate(b)
        val file = RandomizerPatchTestUtils.tempFile(acc)
        val decoded = ZpfzDecoder.decode(file)
        assertEquals(3, decoded.size)
        blobs.forEachIndexed { i, b -> assertArrayEquals(b, decoded[i]) }
    }

    @Test(expected = RandomizerPatchException.NoPatchStreams::class)
    fun emptyFileThrows() {
        val file = RandomizerPatchTestUtils.tempFile(ByteArray(0))
        ZpfzDecoder.decode(file)
    }

    @Test(expected = RandomizerPatchException.CorruptPatchStream::class)
    fun garbageThrows() {
        // Random non-zlib bytes cannot be inflated.
        val file = RandomizerPatchTestUtils.tempFile(ByteArray(64) { it.toByte() })
        ZpfzDecoder.decode(file)
    }

    private operator fun ByteArray.plus(other: ByteArray): ByteArray {
        val out = ByteArray(this.size + other.size)
        System.arraycopy(this, 0, out, 0, this.size)
        System.arraycopy(other, 0, out, this.size, other.size)
        return out
    }
}
