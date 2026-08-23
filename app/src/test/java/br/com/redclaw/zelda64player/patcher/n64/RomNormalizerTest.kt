package br.com.redclaw.zelda64player.patcher.n64

import br.com.redclaw.zelda64player.patcher.PatcherException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class RomNormalizerTest {

    private fun buildLogicalRom(size: Int = 4096): ByteArray {
        val bytes = ByteArray(size)
        for (i in bytes.indices) bytes[i] = ((i * 31 + 7) and 0xFF).toByte()
        // z64 magic
        bytes[0] = 0x80.toByte(); bytes[1] = 0x37.toByte(); bytes[2] = 0x12.toByte(); bytes[3] = 0x40.toByte()
        // title at 0x20..0x33 (zero the full field first: leftover pattern bytes
        // would otherwise be parsed as part of the title)
        for (i in 0x20 until 0x34) bytes[i] = 0
        "THE LEGEND OF ZELDA".toByteArray().copyInto(bytes, 0x20)
        // game code at 0x3B..0x3E
        "CZLE".toByteArray().copyInto(bytes, 0x3B)
        // version byte at 0x3F
        bytes[0x3F] = 0x00
        return bytes
    }

    private fun swap16(input: ByteArray): ByteArray {
        val out = input.copyOf()
        var i = 0
        while (i + 1 < out.size) {
            val t = out[i]; out[i] = out[i + 1]; out[i + 1] = t; i += 2
        }
        return out
    }

    private fun swap32(input: ByteArray): ByteArray {
        val out = input.copyOf()
        var i = 0
        while (i + 3 < out.size) {
            val b0 = out[i]; val b1 = out[i + 1]; val b2 = out[i + 2]; val b3 = out[i + 3]
            out[i] = b3; out[i + 1] = b2; out[i + 2] = b1; out[i + 3] = b0; i += 4
        }
        return out
    }

    @Test
    fun detectRecognizesAllFormats() {
        val logical = buildLogicalRom()
        assertEquals(RomNormalizer.Format.Z64, RomNormalizer.detect(RomNormalizer.magicOf(logical)))
        assertEquals(RomNormalizer.Format.V64, RomNormalizer.detect(RomNormalizer.magicOf(swap16(logical))))
        assertEquals(RomNormalizer.Format.N64, RomNormalizer.detect(RomNormalizer.magicOf(swap32(logical))))
    }

    @Test
    fun normalizeAllFormatsToIdenticalZ64() {
        val logical = buildLogicalRom()
        val v64 = swap16(logical)
        val n64 = swap32(logical)
        assertArrayEquals(logical, RomNormalizer.normalize(logical))
        assertArrayEquals(logical, RomNormalizer.normalize(v64))
        assertArrayEquals(logical, RomNormalizer.normalize(n64))
    }

    @Test
    fun streamingNormalizeMatchesByteArray() {
        val logical = buildLogicalRom(5000)
        val v64 = swap16(logical)
        val out = ByteArrayOutputStream()
        RomNormalizer.normalize(ByteArrayInputStream(v64), out)
        assertArrayEquals(logical, out.toByteArray())
    }

    @Test
    fun unknownMagicThrowsRomFormatError() {
        val bad = ByteArray(8) { it.toByte() }
        try {
            RomNormalizer.detect(RomNormalizer.magicOf(bad))
            org.junit.Assert.fail("Expected RomFormatError")
        } catch (e: PatcherException.RomFormatError) {
            // expected
        }
    }

    @Test
    fun headerParsesGameCodeVersionAndTitle() {
        val logical = buildLogicalRom()
        val header = RomHeader.fromNormalizedZ64(logical)
        assertEquals("CZLE", header.gameCode)
        assertEquals(0, header.versionByte)
        assertEquals("THE LEGEND OF ZELDA", header.title)
    }
}
