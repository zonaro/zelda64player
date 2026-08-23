package br.com.redclaw.zelda64player.patcher.n64

import br.com.redclaw.zelda64player.patcher.PatcherException.RomFormatError
import java.io.InputStream
import java.io.OutputStream

/**
 * Detects the byte order of an N64 ROM and normalizes it to the native
 * big-endian `.z64` layout.
 *
 * | Format | Magic (offset 0, big-endian read) | Transformation            |
 * |--------|-----------------------------------|---------------------------|
 * | z64    | 0x80371240                        | already big-endian (noop) |
 * | v64    | 0x37804012                        | swap every uint16         |
 * | n64    | 0x40123780                        | byte-swap every uint32    |
 *
 * Both a convenience [ByteArray] API and a streaming [normalize] transform are
 * provided so multi-megabyte ROMs are never fully buffered on the heap.
 */
object RomNormalizer {

    private const val MAGIC_Z64 = 0x80371240L
    private const val MAGIC_V64 = 0x37804012L
    private const val MAGIC_N64 = 0x40123780L

    /** The three recognized N64 ROM byte orders. */
    enum class Format { Z64, V64, N64 }

    /**
     * Detect the [Format] from the first four bytes (read big-endian).
     * @throws RomFormatError if the magic word is not recognized.
     */
    fun detect(bytes: ByteArray): Format {
        require(bytes.size >= 4) { throw RomFormatError("ROM too small to contain a magic word") }
        return detect(magicOf(bytes))
    }

    /**
     * Detect the [Format] from a raw magic word.
     * @throws RomFormatError if the magic word is not recognized.
     */
    fun detect(magic: Long): Format = when (magic) {
        MAGIC_Z64 -> Format.Z64
        MAGIC_V64 -> Format.V64
        MAGIC_N64 -> Format.N64
        else -> throw RomFormatError(
            "Unrecognized N64 ROM format (magic 0x%08X)".format(magic)
        )
    }

    /** Read the 4-byte magic word at offset 0 as a big-endian integer. */
    fun magicOf(bytes: ByteArray): Long =
        ((bytes[0].toInt() and 0xFF).toLong() shl 24) or
            ((bytes[1].toInt() and 0xFF).toLong() shl 16) or
            ((bytes[2].toInt() and 0xFF).toLong() shl 8) or
            (bytes[3].toInt() and 0xFF).toLong()

    /**
     * Normalize a full ROM held in memory.
     * @throws RomFormatError if the format is unrecognized.
     */
    fun normalize(input: ByteArray): ByteArray {
        val format = detect(input)
        return when (format) {
            Format.Z64 -> input.copyOf()
            Format.V64 -> swap16(input)
            Format.N64 -> swap32(input)
        }
    }

    /**
     * Streaming normalize from [input] to [output]. The header bytes are swapped
     * along with the rest of the file. Uses a bounded buffer so the full ROM is
     * never held in the heap.
     */
    fun normalize(input: InputStream, output: OutputStream, bufferSize: Int = 64 * 1024) {
        val header = ByteArray(4)
        val read = input.read(header)
        if (read != 4) throw RomFormatError("ROM too small to contain a magic word")
        val format = detect(magicOf(header))
        output.write(swapBytes(format, header))

        if (format == Format.Z64) {
            input.copyTo(output, bufferSize)
            return
        }

        val unit = if (format == Format.V64) 2 else 4
        var leftover = ByteArray(0)
        val buf = ByteArray(bufferSize)
        var count: Int
        while (input.read(buf).also { count = it } != -1) {
            val combined = if (leftover.isEmpty()) buf.copyOf(count) else leftover + buf.copyOf(count)
            val processable = (combined.size / unit) * unit
            swapInPlace(format, combined, processable)
            if (processable > 0) output.write(combined, 0, processable)
            leftover = if (processable < combined.size) combined.copyOfRange(processable, combined.size) else ByteArray(0)
        }
        // Valid cart ROMs are exact multiples of 4 bytes, so `leftover` is empty.
        if (leftover.isNotEmpty()) output.write(leftover)
    }

    private fun swap16(input: ByteArray): ByteArray {
        val out = input.copyOf()
        var i = 0
        while (i + 1 < out.size) {
            val t = out[i]
            out[i] = out[i + 1]
            out[i + 1] = t
            i += 2
        }
        return out
    }

    private fun swap32(input: ByteArray): ByteArray {
        val out = input.copyOf()
        var i = 0
        while (i + 3 < out.size) {
            val b0 = out[i]
            val b1 = out[i + 1]
            val b2 = out[i + 2]
            val b3 = out[i + 3]
            out[i] = b3
            out[i + 1] = b2
            out[i + 2] = b1
            out[i + 3] = b0
            i += 4
        }
        return out
    }

    private fun swapBytes(format: Format, bytes: ByteArray): ByteArray {
        val out = bytes.copyOf()
        return when (format) {
            Format.Z64 -> out
            Format.V64 -> {
                var i = 0
                while (i + 1 < out.size) {
                    val t = out[i]
                    out[i] = out[i + 1]
                    out[i + 1] = t
                    i += 2
                }
                out
            }
            Format.N64 -> {
                var i = 0
                while (i + 3 < out.size) {
                    val b0 = out[i]
                    val b1 = out[i + 1]
                    val b2 = out[i + 2]
                    val b3 = out[i + 3]
                    out[i] = b3
                    out[i + 1] = b2
                    out[i + 2] = b1
                    out[i + 3] = b0
                    i += 4
                }
                out
            }
        }
    }

    private fun swapInPlace(format: Format, bytes: ByteArray, length: Int) {
        when (format) {
            Format.Z64 -> Unit
            Format.V64 -> {
                var i = 0
                while (i + 1 < length) {
                    val t = bytes[i]
                    bytes[i] = bytes[i + 1]
                    bytes[i + 1] = t
                    i += 2
                }
            }
            Format.N64 -> {
                var i = 0
                while (i + 3 < length) {
                    val b0 = bytes[i]
                    val b1 = bytes[i + 1]
                    val b2 = bytes[i + 2]
                    val b3 = bytes[i + 3]
                    bytes[i] = b3
                    bytes[i + 1] = b2
                    bytes[i + 2] = b1
                    bytes[i + 3] = b0
                    i += 4
                }
            }
        }
    }
}
