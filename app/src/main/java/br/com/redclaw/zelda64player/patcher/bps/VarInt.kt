package br.com.redclaw.zelda64player.patcher.bps

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

/**
 * Variable-length integer (LEB128-like) codec used by the BPS format.
 *
 * Encoding (per the public BPS spec): emit the low 7 bits of the value; if more
 * bits remain after shifting right 7, write the byte with the high bit clear
 * and decrement the remaining value by one (this removes the ambiguity of
 * encoding `1` as either `0x81` or `0x01 0x80`). The final byte has its high
 * bit set.
 *
 * Decoding is the exact inverse.
 */
object VarInt {

    /** Encode [value] to its varint byte representation. */
    fun encode(value: Long): ByteArray {
        require(value >= 0) { "VarInt cannot encode negative values" }
        val out = ByteArrayOutputStream()
        var data = value
        while (true) {
            val x = (data and 0x7FL).toInt()
            data = data ushr 7
            if (data == 0L) {
                out.write(0x80 or x)
                break
            }
            out.write(x)
            data--
        }
        return out.toByteArray()
    }

    /** Decode a varint read from [input]. */
    fun decode(input: InputStream): Long {
        var data = 0L
        var shift = 1L
        while (true) {
            val x = input.read()
            if (x < 0) throw IOException("Unexpected end of stream while decoding varint")
            data += (x and 0x7F) * shift
            if (x and 0x80 != 0) break
            shift = shift shl 7
            data += shift
        }
        return data
    }

    /** Decode a varint from a byte array starting at [offset]. Returns the decoded value. */
    fun decode(bytes: ByteArray, offset: Int = 0): Long {
        var data = 0L
        var shift = 1L
        var i = offset
        while (true) {
            if (i >= bytes.size) throw IOException("Unexpected end of array while decoding varint")
            val x = bytes[i].toInt() and 0xFF
            data += (x and 0x7F) * shift
            i++
            if (x and 0x80 != 0) break
            shift = shift shl 7
            data += shift
        }
        return data
    }
}
