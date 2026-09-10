package br.com.redclaw.zelda64player.retroachievements.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class RaSaveStateTest {
    @Test fun `core and hit counts round trip in one checked record`() {
        val original = RaSaveState(byteArrayOf(1, 2, 3), byteArrayOf(4, 5), "exact-rom-hash")
        val restored = RaSaveStateCodec.decode(RaSaveStateCodec.encode(original))
        assertArrayEquals(original.core, restored.core)
        assertArrayEquals(original.progress, restored.progress)
        assertEquals(original.hash, restored.hash)
    }
    @Test fun `legacy core state has no achievement progress`() {
        val bytes = byteArrayOf(1, 2, 3)
        val restored = RaSaveStateCodec.decode(bytes)
        assertArrayEquals(bytes, restored.core)
        assertNull(restored.progress)
        assertEquals("", restored.hash)
    }
    @Test fun `hardcore save can be kept without resumable achievement hit counts`() {
        val restored = RaSaveStateCodec.decode(RaSaveStateCodec.encode(RaSaveState(byteArrayOf(1), null, "hash")))
        assertNull(restored.progress)
        assertArrayEquals(byteArrayOf(1), restored.core)
    }
    @Test fun `corruption or interrupted write cannot restore a mismatched pair`() {
        val bytes = RaSaveStateCodec.encode(RaSaveState(byteArrayOf(1, 2), byteArrayOf(3), "hash"))
        bytes[bytes.lastIndex] = 99
        assertThrows(IllegalArgumentException::class.java) { RaSaveStateCodec.decode(bytes) }
        assertThrows(IllegalArgumentException::class.java) { RaSaveStateCodec.decode(bytes.copyOf(bytes.size - 1)) }
    }
}
