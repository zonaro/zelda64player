package br.com.redclaw.zelda64player.randomizer.patch

import br.com.redclaw.zelda64player.randomizer.patch.ZpfModels.DmaUpdate
import br.com.redclaw.zelda64player.randomizer.patch.ZpfModels.EditBlock
import br.com.redclaw.zelda64player.randomizer.patch.ZpfModels.ParsedZpf
import br.com.redclaw.zelda64player.randomizer.patch.ZpfModels.ZpfHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import br.com.redclaw.zelda64player.randomizer.patch.RandomizerPatchTestUtils.assertWordBE
import br.com.redclaw.zelda64player.randomizer.patch.RandomizerPatchTestUtils.tempFile
import java.io.File

class ZpfApplierTest {

    private val ROM_SIZE = 0x10000
    private val DMA_TABLE_BASE = 0x5000

    /** Write a big-endian 32-bit word into [rom] at [offset]. */
    private fun writeWordBE(rom: ByteArray, offset: Int, value: Int) {
        rom[offset] = (value ushr 24).toByte()
        rom[offset + 1] = ((value ushr 16) and 0xFF).toByte()
        rom[offset + 2] = ((value ushr 8) and 0xFF).toByte()
        rom[offset + 3] = (value and 0xFF).toByte()
    }

    /**
     * Build a synthetic ROM with a known key window, a source file region, and
     * an original DMA table entry describing that source file.
     *
     * @param originalFileSize size recorded in the original DMA table for the
     *   file starting at 0x3000 (used by the applier's size lookup).
     */
    private fun buildRom(originalFileSize: Int = 8): ByteArray {
        val rom = ByteArray(ROM_SIZE)
        // Key window [0x1000, 0x10FF]: fill with 0x01, then plant specific keys.
        for (i in 0x1000..0x10FF) rom[i] = 0x01
        rom[0x1001] = 0x22 // first key byte (cursor starts at 0x1000, first read at +1)
        rom[0x1002] = 0x00 // must be skipped by key_next
        rom[0x1003] = 0x33 // second key byte
        // Source file to be copied by a DMA update, at 0x3000 (8 bytes of data).
        val src = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte(),
            0xEE.toByte(), 0xFF.toByte(), 0x01, 0x02)
        System.arraycopy(src, 0, rom, 0x3000, src.size)
        // Original DMA table entry at 0x5000 describing the file at 0x3000.
        writeWordBE(rom, DMA_TABLE_BASE + 0, 0x3000)       // start
        writeWordBE(rom, DMA_TABLE_BASE + 4, 0x3000 + originalFileSize) // end
        writeWordBE(rom, DMA_TABLE_BASE + 8, 0x3000)       // start (again)
        writeWordBE(rom, DMA_TABLE_BASE + 12, 0)           // 0
        // Initial (will-be-overwritten) content at the XOR target 0x2000.
        for (i in 0x2000..0x2003) rom[i] = 0x55
        return rom
    }

    @Test
    fun appliesDmaRelocationAndStartBlock() {
        val rom = buildRom()
        val baseFile = tempFile(rom)
        val outFile = tempFile(rom.copyOf())

        val header = ZpfHeader(
            dmaTableStart = DMA_TABLE_BASE,
            keyRangeMin = 0x1000,
            keyRangeMax = 0x10FF,
            keyAddress = 0x1000
        )
        val dma = listOf(DmaUpdate(index = 5, fromFileAddress = 0x3000, newStart = 0x4000, size = 8))
        // start block: [0x00, 0xAB, 0xCD, 0x00] at 0x2000
        //   0x00 -> write 0x00
        //   0xAB -> key 0x22 -> 0x22 xor 0xAB = 0x89
        //   0xCD -> key 0x33 -> 0x33 xor 0xCD = 0xFE
        //   0x00 -> write 0x00
        val blocks = listOf(
            EditBlock(address = 0x2000, data = byteArrayOf(0x00, 0xAB.toByte(), 0xCD.toByte(), 0x00),
                isContinue = false, keySkip = 0)
        )
        ZpfApplier.applyWorld(outFile, baseFile, ParsedZpf(header, dma, blocks))

        val result = outFile.readBytes()
        // DMA entry written at 0x5000 + 5*0x10 = 0x5050
        assertWordBE(result, 0x5050, 0x4000) // start
        assertWordBE(result, 0x5054, 0x4008) // end = start + size
        assertWordBE(result, 0x5058, 0x4000) // start (again)
        assertWordBE(result, 0x505C, 0)      // 0
        // Copied file at 0x4000 (original file size == patch size -> no zero-fill)
        assertArrayEquals(
            byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte(),
                0xEE.toByte(), 0xFF.toByte(), 0x01, 0x02),
            result.copyOfRange(0x4000, 0x4008)
        )
        // XOR edits at 0x2000
        assertEquals(0x00, result[0x2000].toInt() and 0xFF)
        assertEquals(0x89, result[0x2001].toInt() and 0xFF)
        assertEquals(0xFE, result[0x2002].toInt() and 0xFF)
        assertEquals(0x00, result[0x2003].toInt() and 0xFF)
    }

    @Test
    fun appliesContinueBlockWithKeySkip() {
        val rom = buildRom()
        val baseFile = tempFile(rom)
        val outFile = tempFile(rom.copyOf())

        val header = ZpfHeader(
            dmaTableStart = DMA_TABLE_BASE,
            keyRangeMin = 0x1000,
            keyRangeMax = 0x10FF,
            keyAddress = 0x1000
        )
        // Start block consumes keys 0x22 (at 0x1001) and 0x33 (at 0x1003);
        // cursor ends at 0x1003.
        val start = EditBlock(
            address = 0x2000,
            data = byteArrayOf(0x00, 0xAB.toByte(), 0xCD.toByte(), 0x00),
            isContinue = false, keySkip = 0
        )
        // Continue block: keySkip=1 advances past one key (0x01 at 0x1004),
        // then each non-zero byte consumes the next 0x01 key.
        //   0x11 -> key 0x01 -> 0x01 xor 0x11 = 0x10
        //   0x22 -> key 0x01 -> 0x01 xor 0x22 = 0x23
        val cont = EditBlock(
            address = 0x2004,
            data = byteArrayOf(0x11, 0x22),
            isContinue = true, keySkip = 1
        )
        ZpfApplier.applyWorld(outFile, baseFile, ParsedZpf(header, emptyList(), listOf(start, cont)))

        val result = outFile.readBytes()
        assertEquals(0x10, result[0x2004].toInt() and 0xFF)
        assertEquals(0x23, result[0x2005].toInt() and 0xFF)
    }

    @Test
    fun zeroFillNewFileWhenFromAddressIsNull() {
        val rom = buildRom()
        val baseFile = tempFile(rom)
        val outFile = tempFile(rom.copyOf())

        val header = ZpfHeader(
            dmaTableStart = DMA_TABLE_BASE,
            keyRangeMin = 0x1000,
            keyRangeMax = 0x10FF,
            keyAddress = 0x1000
        )
        // fromFileAddress = 0xFFFFFFFF -> zero-fill destination.
        val dma = listOf(DmaUpdate(index = 2, fromFileAddress = 0xFFFFFFFF.toInt(),
            newStart = 0x4000, size = 8))
        ZpfApplier.applyWorld(outFile, baseFile, ParsedZpf(header, dma, emptyList()))

        val result = outFile.readBytes()
        assertWordBE(result, 0x5000 + 2 * 0x10, 0x4000)
        assertWordBE(result, 0x5000 + 2 * 0x10 + 4, 0x4008)
        // Destination region must be all zeros.
        assertEquals(ByteArray(8).contentHashCode(), result.copyOfRange(0x4000, 0x4008).contentHashCode())
    }

    @Test
    fun dmaCopyUsesOriginalFileSizeAndZeroFills() {
        // Original DMA table records the file at 0x3000 as only 4 bytes long,
        // but the patch relocates it to a new slot of size 8. Per N64Patch.py the
        // applier must copy min(size, originalSize)=4 bytes then zero-fill.
        val rom = buildRom(originalFileSize = 4)
        val baseFile = tempFile(rom)
        val outFile = tempFile(rom.copyOf())

        val header = ZpfHeader(
            dmaTableStart = DMA_TABLE_BASE,
            keyRangeMin = 0x1000,
            keyRangeMax = 0x10FF,
            keyAddress = 0x1000
        )
        val dma = listOf(DmaUpdate(index = 5, fromFileAddress = 0x3000, newStart = 0x4000, size = 8))
        ZpfApplier.applyWorld(outFile, baseFile, ParsedZpf(header, dma, emptyList()))

        val result = outFile.readBytes()
        // First 4 bytes are the original file content.
        assertArrayEquals(
            byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte()),
            result.copyOfRange(0x4000, 0x4004)
        )
        // Remaining 4 bytes must be zero-filled.
        assertEquals(ByteArray(4).contentHashCode(), result.copyOfRange(0x4004, 0x4008).contentHashCode())
    }
}
