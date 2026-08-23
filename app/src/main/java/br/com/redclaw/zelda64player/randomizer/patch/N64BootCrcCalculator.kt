package br.com.redclaw.zelda64player.randomizer.patch

import java.io.File
import java.io.RandomAccessFile

/**
 * N64 boot CRC calculator for the CIC 6105 (a.k.a. 6105 / 7105) bootcode, used
 * by Ocarina of Time. Computes the two 32-bit checksums written at ROM header
 * offsets `0x10` (CRC1) and `0x14` (CRC2).
 *
 * Algorithm (the canonical CIC 6105 checksum, cross-checked against the N64
 * homebrew reference and consistent with OoTRandomizer's `Rom.py`):
 *  - All six working registers are seeded with `0xDF26F436`.
 *  - For each big-endian 32-bit word `d` at ROM offsets `0x1000 .. 0x101000`
 *    (exclusive), stepping by 4:
 *      - `t6 += d`; on unsigned carry `t4 += 1`.
 *      - `t3 ^= d`.
 *      - `r = rotateLeft(d, d & 0x1F)`.
 *      - `t5 += r`.
 *      - `t2 = if (t2 > d) t2 xor r else t2 xor (t6 xor d)` (unsigned compare).
 *      - `t1 += readWord(0x750 + (i & 0xFF)) xor d` (6105-specific seed table).
 *  - `crc1 = t6 xor t4 xor t3`; `crc2 = t5 xor t2 xor t1`.
 *
 * The computation never reads the existing CRC bytes at `0x10/0x14`, so
 * [fixBootCrc] is idempotent: fixing an already-correct ROM leaves it unchanged.
 *
 * All arithmetic uses unsigned 32-bit semantics ([UInt]) so the carry and
 * greater-than comparisons match the reference exactly.
 *
 * Licensing: clean-room implementation from the public CIC 6105 specification.
 */
object N64BootCrcCalculator {

    private const val SEED = 0xDF26F436u
    private const val CRC1_OFFSET = 0x10L
    private const val CRC2_OFFSET = 0x14L
    private const val SCAN_START = 0x1000
    private const val SCAN_END = 0x101000
    private const val SEED_TABLE_BASE = 0x750

    /** Reads a big-endian 32-bit word from the ROM at [offset]. */
    fun interface RomWordReader {
        fun readWord(offset: Int): Int
    }

    /**
     * Compute `(crc1, crc2)` over the ROM exposed by [reader].
     * @return the two CRC values as signed-[Int] bit patterns (use `.toUInt()`
     *   when writing them back as big-endian words).
     */
    fun compute(reader: RomWordReader): Pair<Int, Int> {
        var t1 = SEED
        var t2 = SEED
        var t3 = SEED
        var t4 = SEED
        var t5 = SEED
        var t6 = SEED

        var i = SCAN_START
        while (i < SCAN_END) {
            val d = reader.readWord(i).toUInt()
            t6 += d
            if (t6 < d) t4 += 1u
            t3 = t3 xor d
            val r = rotateLeft(d, (d and 0x1Fu).toInt())
            t5 += r
            t2 = if (t2 > d) (t2 xor r) else (t2 xor (t6 xor d))
            val seedWord = reader.readWord(SEED_TABLE_BASE + (i and 0xFF)).toUInt()
            t1 += seedWord xor d
            i += 4
        }

        val crc1 = (t6 xor t4 xor t3).toInt()
        val crc2 = (t5 xor t2 xor t1).toInt()
        return Pair(crc1, crc2)
    }

    /**
     * Recompute and overwrite the boot CRCs of [file] in place (big-endian at
     * `0x10`/`0x14`). Idempotent.
     */
    fun fixBootCrc(file: File) {
        RandomAccessFile(file, "rw").use { raf ->
            val reader = RomWordReader { offset ->
                raf.seek(offset.toLong())
                val b0 = raf.read().let { if (it < 0) 0 else it }
                val b1 = raf.read().let { if (it < 0) 0 else it }
                val b2 = raf.read().let { if (it < 0) 0 else it }
                val b3 = raf.read().let { if (it < 0) 0 else it }
                (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
            }
            val (crc1, crc2) = compute(reader)
            writeWordBE(raf, CRC1_OFFSET, crc1.toUInt())
            writeWordBE(raf, CRC2_OFFSET, crc2.toUInt())
        }
    }

    /** Rotate [value] left by [bits] (0..31), unsigned. */
    private fun rotateLeft(value: UInt, bits: Int): UInt {
        val b = bits and 0x1F
        // For UInt, shr is already a correct logical (unsigned) right shift.
        return (value shl b) or (value shr (32 - b))
    }

    private fun writeWordBE(raf: RandomAccessFile, offset: Long, value: UInt) {
        raf.seek(offset)
        raf.write(((value shr 24) and 0xFFu).toInt())
        raf.write(((value shr 16) and 0xFFu).toInt())
        raf.write(((value shr 8) and 0xFFu).toInt())
        raf.write((value and 0xFFu).toInt())
    }
}
