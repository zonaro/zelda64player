package br.com.redclaw.zelda64player.tracker.assets

import br.com.redclaw.zelda64player.tracker.assets.compression.YarDecompressor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class YarDecompressorTest {

        @Test
        fun `decompresses and concatenates every Yaz0 block`() {
                val first = yaz0LiteralOnly("first".toByteArray())
                val second = yaz0LiteralOnly("-second".toByteArray())
                val headerSize = 12
                val header = ByteBuffer.allocate(headerSize).order(ByteOrder.BIG_ENDIAN)
                header.putInt(headerSize)
                header.putInt(first.size)
                header.putInt(first.size + second.size)

                val result = YarDecompressor.decompress(header.array() + first + second)

                assertArrayEquals("first-second".toByteArray(), result)
        }

        @Test(expected = IllegalArgumentException::class)
        fun `rejects block boundary outside archive`() {
                val header = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN)
                header.putInt(8)
                header.putInt(100)

                YarDecompressor.decompress(header.array())
        }

        private fun yaz0LiteralOnly(payload: ByteArray): ByteArray {
                val header = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
                header.put("Yaz0".toByteArray(Charsets.US_ASCII))
                header.putInt(payload.size)
                header.putLong(0L)
                val body = mutableListOf<Byte>()
                var index = 0
                while (index < payload.size) {
                        val chunkSize = minOf(8, payload.size - index)
                        body.add(((0xFF shl (8 - chunkSize)) and 0xFF).toByte())
                        repeat(chunkSize) { body.add(payload[index++]) }
                }
                return header.array() + body.toByteArray()
        }
}
