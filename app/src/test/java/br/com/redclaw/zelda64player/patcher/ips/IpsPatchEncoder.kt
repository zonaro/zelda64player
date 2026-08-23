package br.com.redclaw.zelda64player.patcher.ips

import java.io.ByteArrayOutputStream

/**
 * Test-only IPS encoder. Builds a valid IPS patch from a (source, target) pair.
 * The [source] is ignored (IPS is self-contained); only [target] matters.
 *
 * It splits the target into runs of identical bytes (emitted as RLE records)
 * and short differing runs (emitted as literal records), exercising both
 * record types and round-tripping through [IpsApplier]. It is NOT production
 * code.
 */
object IpsPatchEncoder {

    fun encode(source: ByteArray, target: ByteArray, includeEof: Boolean = true): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("PATCH".toByteArray(Charsets.US_ASCII))
        var i = 0
        while (i < target.size) {
            val b = target[i]
            var run = 1
            while (i + run < target.size && target[i + run] == b) run++
            if (run >= 4) {
                writeUint32Be(out, i.toLong())
                writeUint16Be(out, 0) // length 0 marks an RLE record
                writeUint16Be(out, run)
                out.write(b.toInt() and 0xFF)
                i += run
            } else {
                writeUint32Be(out, i.toLong())
                writeUint16Be(out, run)
                out.write(target, i, run)
                i += run
            }
        }
        if (includeEof) out.write("EOF".toByteArray(Charsets.US_ASCII))
        return out.toByteArray()
    }

    /** Build a raw IPS patch from explicit records (for structural tests). */
    fun encodeRaw(records: List<Record>, includeEof: Boolean = true): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("PATCH".toByteArray(Charsets.US_ASCII))
        for (r in records) {
            writeUint32Be(out, r.offset)
            writeUint16Be(out, r.length)
            if (r.length == 0) {
                writeUint16Be(out, r.runLength)
                out.write(r.value)
            } else {
                out.write(r.data)
            }
        }
        if (includeEof) out.write("EOF".toByteArray(Charsets.US_ASCII))
        return out.toByteArray()
    }

    data class Record(
        val offset: Long,
        val length: Int,
        val data: ByteArray = ByteArray(0),
        val runLength: Int = 0,
        val value: Int = 0
    )

    private fun writeUint32Be(out: ByteArrayOutputStream, value: Long) {
        out.write(((value ushr 24) and 0xFF).toInt())
        out.write(((value ushr 16) and 0xFF).toInt())
        out.write(((value ushr 8) and 0xFF).toInt())
        out.write((value and 0xFF).toInt())
    }

    private fun writeUint16Be(out: ByteArrayOutputStream, value: Int) {
        out.write(((value ushr 8) and 0xFF).toInt())
        out.write((value and 0xFF).toInt())
    }
}
