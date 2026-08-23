package br.com.redclaw.zelda64player.patcher.bps

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayInputStream

class VarIntTest {

    private val samples = listOf(
        0L, 1L, 2L, 127L, 128L, 16383L, 16384L, 65535L, 65536L,
        0x7FFFFFFFL, 0x80000000L, Long.MAX_VALUE
    )

    @Test
    fun encodeDecodeRoundTrip() {
        for (value in samples) {
            val encoded = VarInt.encode(value)
            val decoded = VarInt.decode(encoded)
            assertEquals("round-trip failed for $value", value, decoded)
            assertEquals("offset decode mismatch for $value", value, VarInt.decode(encoded, 0))
        }
    }

    @Test
    fun decodeFromStreamMatchesArray() {
        for (value in samples) {
            val encoded = VarInt.encode(value)
            val decoded = VarInt.decode(ByteArrayInputStream(encoded))
            assertEquals(value, decoded)
        }
    }

    @Test
    fun knownEncodings() {
        assertEquals(listOf(0x80.toByte()), VarInt.encode(0).toList())
        assertEquals(listOf(0x81.toByte()), VarInt.encode(1).toList())
        assertEquals(listOf(0xFF.toByte()), VarInt.encode(127).toList())
        // 128 -> 0x00 0x80
        assertEquals(listOf(0x00.toByte(), 0x80.toByte()), VarInt.encode(128).toList())
        // 16383 -> 0x7F 0xFE
        assertEquals(listOf(0x7F.toByte(), 0xFE.toByte()), VarInt.encode(16383).toList())
    }

    @Test
    fun signedOffsetRoundTrip() {
        val offsets = listOf(0L, 1L, -1L, 12345L, -12345L, 999999L, -999999L)
        for (off in offsets) {
            val encoded = VarInt.encode(BpsParser.encodeSignedOffset(off))
            val decoded = BpsParser.decodeSignedOffset(VarInt.decode(encoded))
            assertEquals("signed offset round-trip failed for $off", off, decoded)
        }
    }
}
