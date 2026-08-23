package br.com.redclaw.zelda64player.patcher.bps

import java.io.ByteArrayOutputStream
import kotlin.math.abs

/**
 * Test-only BPS encoder. Builds a valid BPS patch from a (source, target) pair
 * using a greedy command selection that exercises all four command types
 * (SourceRead, TargetRead, SourceCopy with positive/negative offsets,
 * TargetCopy). It is used to validate the [BpsApplier] round-trip; it is NOT
 * production code.
 */
object BpsPatchEncoder {

    /** How far back a TargetCopy back-reference may point (validity needs no optimality). */
    private const val TARGET_WINDOW = 64

    fun encode(source: ByteArray, target: ByteArray): ByteArray {
        var sourceRelative = 0L
        var targetRelative = 0L
        val segments = mutableListOf<Seg>()

        // First occurrence of each byte value in the source, precomputed once
        // (O(source) instead of an O(n^2) indexOf per emitted byte).
        val sourceFirst = IntArray(256) { -1 }
        for (i in source.indices) {
            val b = source[i].toInt() and 0xFF
            if (sourceFirst[b] < 0) sourceFirst[b] = i
        }

        for (i in target.indices) {
            val b = target[i]
            val canSourceRead = i < source.size && source[i] == b
            val srcCopyAbs = sourceFirst[b.toInt() and 0xFF].takeIf { it >= 0 }
            // Bounded backward scan over the ORIGINAL array (no per-byte copy).
            var tgtCopyAbs = -1
            val limit = if (i - TARGET_WINDOW > 0) i - TARGET_WINDOW else 0
            var j = i - 1
            while (j >= limit) {
                if (target[j] == b) { tgtCopyAbs = j; break }
                j--
            }

            val (action, absPos, offset) = when {
                canSourceRead -> Triple(0, null, 0L)
                srcCopyAbs != null && tgtCopyAbs >= 0 -> {
                    val offS = (srcCopyAbs - sourceRelative)
                    val offT = (tgtCopyAbs - targetRelative)
                    if (abs(offT) <= abs(offS)) {
                        Triple(3, tgtCopyAbs.toLong(), offT.toLong())
                    } else {
                        Triple(2, srcCopyAbs.toLong(), offS.toLong())
                    }
                }
                srcCopyAbs != null -> Triple(2, srcCopyAbs.toLong(), (srcCopyAbs - sourceRelative).toLong())
                tgtCopyAbs >= 0 -> Triple(3, tgtCopyAbs.toLong(), (tgtCopyAbs - targetRelative).toLong())
                else -> Triple(1, null, 0L)
            }

            val cur = segments.lastOrNull()
            val canMerge = cur != null && cur.action == action && when (action) {
                0 -> true
                1 -> true
                else -> absPos != null && absPos == cur.absStart + cur.length
            }

            if (canMerge && cur != null) {
                cur.length++
                if (action == 1) cur.bytes!!.add(b)
                if (action == 2) sourceRelative = cur.absStart + cur.length
                if (action == 3) targetRelative = cur.absStart + cur.length
            } else {
                when (action) {
                    0 -> segments.add(Seg(0, 1))
                    1 -> segments.add(Seg(1, 1, bytes = mutableListOf(b)))
                    2 -> {
                        sourceRelative = absPos!! + 1
                        segments.add(Seg(2, 1, offset = offset, absStart = absPos))
                    }
                    3 -> {
                        targetRelative = absPos!! + 1
                        segments.add(Seg(3, 1, offset = offset, absStart = absPos))
                    }
                }
            }
        }

        val body = ByteArrayOutputStream()
        body.write("BPS1".toByteArray())
        body.write(VarInt.encode(source.size.toLong()))
        body.write(VarInt.encode(target.size.toLong()))
        body.write(VarInt.encode(0)) // metadata size
        for (seg in segments) {
            val data = (seg.action.toLong()) or ((seg.length - 1) shl 2)
            body.write(VarInt.encode(data))
            when (seg.action) {
                1 -> body.write(seg.bytes!!.toByteArray())
                2, 3 -> body.write(VarInt.encode(BpsParser.encodeSignedOffset(seg.offset)))
            }
        }
        val sourceCrc = crc32(source)
        val targetCrc = crc32(target)
        body.write(uint32Le(sourceCrc))
        body.write(uint32Le(targetCrc))
        val patchCrc = crc32(body.toByteArray())
        body.write(uint32Le(patchCrc))
        return body.toByteArray()
    }

    private data class Seg(
        val action: Int,
        var length: Long,
        val offset: Long = 0,
        val absStart: Long = 0,
        val bytes: MutableList<Byte>? = null
    )

    private fun crc32(data: ByteArray): Long {
        val c = java.util.zip.CRC32()
        c.update(data)
        return c.value
    }

    private fun uint32Le(value: Long): ByteArray = byteArrayOf(
        (value and 0xFF).toByte(),
        ((value ushr 8) and 0xFF).toByte(),
        ((value ushr 16) and 0xFF).toByte(),
        ((value ushr 24) and 0xFF).toByte()
    )
}
