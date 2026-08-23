package br.com.redclaw.zelda64player.randomizer.patch

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Deflater

/** Shared helpers for the randomizer patch unit tests. */
object RandomizerPatchTestUtils {

    fun tempFile(bytes: ByteArray): File =
        File.createTempFile("zrnd", ".bin").apply { writeBytes(bytes) }

    /** Deflate with the default zlib (RFC 1950) wrapper, matching ZpfzDecoder. */
    fun deflate(data: ByteArray): ByteArray {
        val deflater = Deflater()
        deflater.setInput(data)
        deflater.finish()
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        while (!deflater.finished()) {
            val n = deflater.deflate(buf)
            out.write(buf, 0, n)
        }
        deflater.end()
        return out.toByteArray()
    }

    fun uint16BE(v: Int): ByteArray =
        byteArrayOf((v ushr 8).toByte(), (v and 0xFF).toByte())

    fun uint24BE(v: Int): ByteArray =
        byteArrayOf((v ushr 16).toByte(), ((v ushr 8) and 0xFF).toByte(), (v and 0xFF).toByte())

    fun uint32BE(v: Int): ByteArray =
        byteArrayOf(
            (v ushr 24).toByte(),
            ((v ushr 16) and 0xFF).toByte(),
            ((v ushr 8) and 0xFF).toByte(),
            (v and 0xFF).toByte()
        )

    fun assertWordBE(data: ByteArray, offset: Int, expected: Int) {
        val actual = ((data[offset].toInt() and 0xFF) shl 24) or
            ((data[offset + 1].toInt() and 0xFF) shl 16) or
            ((data[offset + 2].toInt() and 0xFF) shl 8) or
            (data[offset + 3].toInt() and 0xFF)
        org.junit.Assert.assertEquals("word@0x${offset.toString(16)}", expected, actual)
    }
}
