package br.com.redclaw.zelda64player.randomizer.patch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import br.com.redclaw.zelda64player.randomizer.patch.RandomizerPatchTestUtils.assertWordBE
import br.com.redclaw.zelda64player.randomizer.patch.RandomizerPatchTestUtils.tempFile
import java.io.RandomAccessFile

class N64BootCrcCalculatorTest {

    private val ROM_SIZE = 0x101000

    /** Deterministic pseudo-random ROM content. */
    private fun buildRom(): ByteArray =
        ByteArray(ROM_SIZE) { ((it * 2654435761L) and 0xFF).toByte() }

    private fun wordReader(rom: ByteArray): N64BootCrcCalculator.RomWordReader =
        N64BootCrcCalculator.RomWordReader { offset ->
            ((rom[offset].toInt() and 0xFF) shl 24) or
                ((rom[offset + 1].toInt() and 0xFF) shl 16) or
                ((rom[offset + 2].toInt() and 0xFF) shl 8) or
                (rom[offset + 3].toInt() and 0xFF)
        }

    /**
     * Independent reimplementation of the CIC 6105 checksum using `Long`
     * arithmetic (instead of `UInt`), to cross-validate the production code.
     */
    private fun referenceCompute(rom: ByteArray): Pair<Int, Int> {
        val reader = wordReader(rom)
        var t1 = 0xDF26F436L
        var t2 = 0xDF26F436L
        var t3 = 0xDF26F436L
        var t4 = 0xDF26F436L
        var t5 = 0xDF26F436L
        var t6 = 0xDF26F436L
        var i = 0x1000
        while (i < 0x101000) {
            val d = reader.readWord(i).toLong() and 0xFFFFFFFFL
            t6 = (t6 + d) and 0xFFFFFFFFL
            if (t6 < d) t4 = (t4 + 1) and 0xFFFFFFFFL
            t3 = t3 xor d
            val r = rotateLeftLong(d, (d and 0x1F).toInt())
            t5 = (t5 + r) and 0xFFFFFFFFL
            t2 = if (t2 > d) (t2 xor r) else (t2 xor ((t6 xor d) and 0xFFFFFFFFL))
            val seed = reader.readWord(0x750 + (i and 0xFF)).toLong() and 0xFFFFFFFFL
            t1 = (t1 + (seed xor d)) and 0xFFFFFFFFL
            i += 4
        }
        val crc1 = ((t6 xor t4 xor t3) and 0xFFFFFFFFL).toInt()
        val crc2 = ((t5 xor t2 xor t1) and 0xFFFFFFFFL).toInt()
        return Pair(crc1, crc2)
    }

    private fun rotateLeftLong(value: Long, bits: Int): Long {
        val b = bits and 0x1F
        return ((value shl b) or (value ushr (32 - b))) and 0xFFFFFFFFL
    }

    @Test
    fun deterministic() {
        val rom = buildRom()
        val (a1, a2) = N64BootCrcCalculator.compute(wordReader(rom))
        val (b1, b2) = N64BootCrcCalculator.compute(wordReader(rom))
        assertEquals(a1, b1)
        assertEquals(a2, b2)
    }

    @Test
    fun matchesIndependentReference() {
        val rom = buildRom()
        val (c1, c2) = N64BootCrcCalculator.compute(wordReader(rom))
        val (r1, r2) = referenceCompute(rom)
        assertEquals(r1, c1)
        assertEquals(r2, c2)
    }

    @Test
    fun fixBootCrcIsIdempotent() {
        val rom = buildRom()
        val file = tempFile(rom)
        N64BootCrcCalculator.fixBootCrc(file)
        val first = file.readBytes()
        N64BootCrcCalculator.fixBootCrc(file)
        val second = file.readBytes()
        assertArrayEquals(first, second)
    }

    @Test
    fun fixBootCrcWritesComputedValues() {
        val rom = buildRom()
        val file = tempFile(rom)
        val (crc1, crc2) = N64BootCrcCalculator.compute(wordReader(rom))
        N64BootCrcCalculator.fixBootCrc(file)
        val bytes = file.readBytes()
        assertWordBE(bytes, 0x10, crc1)
        assertWordBE(bytes, 0x14, crc2)
    }

    @Test
    fun differentRomProducesDifferentCrc() {
        val romA = buildRom()
        val romB = buildRom()
        romB[0x2000] = (romB[0x2000].toInt() xor 0xFF).toByte()
        val (a1, a2) = N64BootCrcCalculator.compute(wordReader(romA))
        val (b1, b2) = N64BootCrcCalculator.compute(wordReader(romB))
        // Extremely unlikely to collide for a single flipped byte.
        assert(!(a1 == b1 && a2 == b2)) { "CRC collision for a single-bit change" }
    }
}
