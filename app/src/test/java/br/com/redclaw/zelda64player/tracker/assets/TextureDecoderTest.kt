package br.com.redclaw.zelda64player.tracker.assets

import br.com.redclaw.zelda64player.tracker.assets.graphics.TextureDecoder
import org.junit.Assert.assertEquals
import org.junit.Test

class TextureDecoderTest {

    @Test
    fun `decodeRGBA16 white opaque pixel`() {
        // RGBA16 5551: R=31 G=31 B=31 A=1 => 0xFFFF
        val data = byteArrayOf(0xFF.toByte(), 0xFF.toByte())
        val pixels = TextureDecoder.decodeRGBA16(data, 0, 1, 1)
        assertEquals(1, pixels.size)
        // 31*255/31=255 for each channel, A=255
        assertEquals(0xFFFFFFFF.toInt(), pixels[0])
    }

    @Test
    fun `decodeRGBA16 black transparent pixel`() {
        // 0x0000 => R=0 G=0 B=0 A=0
        val data = byteArrayOf(0x00, 0x00)
        val pixels = TextureDecoder.decodeRGBA16(data, 0, 1, 1)
        assertEquals(0x00000000, pixels[0])
    }

    @Test
    fun `decodeRGBA16 red opaque`() {
        // R=31 G=0 B=0 A=1 => bits: 11111 00000 00000 1 = 0xF801
        val data = byteArrayOf(0xF8.toByte(), 0x01)
        val pixels = TextureDecoder.decodeRGBA16(data, 0, 1, 1)
        val argb = pixels[0]
        val r = (argb ushr 16) and 0xFF
        val g = (argb ushr 8) and 0xFF
        val b = argb and 0xFF
        val a = (argb ushr 24) and 0xFF
        assertEquals(255, r)
        assertEquals(0, g)
        assertEquals(0, b)
        assertEquals(255, a)
    }

    @Test
    fun `decodeCI8 via palette`() {
        // TLUT: 2 entries — index 0 = black transparent, index 1 = white opaque
        val tlut = ByteArray(512)
        // entry 0: 0x0000 (already zero)
        // entry 1: 0xFFFF
        tlut[2] = 0xFF.toByte(); tlut[3] = 0xFF.toByte()
        val pixels = byteArrayOf(0x00, 0x01, 0x01, 0x00) // 2x2
        val out = TextureDecoder.decodeCI8(pixels, 0, 2, 2, tlut, 0)
        assertEquals(4, out.size)
        assertEquals(0x00000000, out[0])
        assertEquals(0xFFFFFFFF.toInt(), out[1])
        assertEquals(0xFFFFFFFF.toInt(), out[2])
        assertEquals(0x00000000, out[3])
    }

    @Test
    fun `decodeRGBA16 2x2 image`() {
        // 4 pixels: white, black, red, transparent black
        val data = byteArrayOf(
            0xFF.toByte(), 0xFF.toByte(), // white
            0x00, 0x00, // black transparent
            0xF8.toByte(), 0x01, // red
            0x00, 0x00, // black transparent
        )
        val pixels = TextureDecoder.decodeRGBA16(data, 0, 2, 2)
        assertEquals(4, pixels.size)
        assertEquals(0xFFFFFFFF.toInt(), pixels[0])
        assertEquals(0x00000000, pixels[1])
    }
}
