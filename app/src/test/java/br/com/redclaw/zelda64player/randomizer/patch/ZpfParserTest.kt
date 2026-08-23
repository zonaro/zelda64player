package br.com.redclaw.zelda64player.randomizer.patch

import br.com.redclaw.zelda64player.randomizer.patch.ZpfModels.DmaUpdate
import br.com.redclaw.zelda64player.randomizer.patch.ZpfModels.EditBlock
import br.com.redclaw.zelda64player.randomizer.patch.ZpfModels.ParsedZpf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import br.com.redclaw.zelda64player.randomizer.patch.RandomizerPatchTestUtils.uint16BE
import br.com.redclaw.zelda64player.randomizer.patch.RandomizerPatchTestUtils.uint24BE
import br.com.redclaw.zelda64player.randomizer.patch.RandomizerPatchTestUtils.uint32BE

class ZpfParserTest {

    /** Build a minimal `.zpf` blob: header + 1 DMA update + 1 start + 1 continue block. */
    private fun buildBlob(): ByteArray = ByteArrayOutputStream().apply {
        write(0x5A); write(0x50); write(0x46); write(0x76) // 'ZPFv'
        write(0x31) // version '1'
        write(uint32BE(0x5000)) // dmaTableStart (ROM offset)
        write(uint32BE(0x1000)) // keyRangeMin
        write(uint32BE(0x10FF)) // keyRangeMax
        write(uint32BE(0x1000)) // keyAddress
        // DMA update
        write(uint16BE(0x0005))
        write(uint32BE(0x3000))
        write(uint32BE(0x4000))
        write(uint24BE(0x000008))
        // terminator
        write(uint16BE(0xFFFF))
        // start block at 0x2000, size 4
        write(uint32BE(0x2000))
        write(uint16BE(0x0004))
        write(byteArrayOf(0x00, 0xAB.toByte(), 0xCD.toByte(), 0x00))
        // continue block: marker 0xFF, keySkip 1, size 2
        write(0xFF)
        write(0x01)
        write(uint16BE(0x0002))
        write(byteArrayOf(0x11, 0x22))
    }.toByteArray()

    @Test
    fun parsesHeaderAndStructure() {
        val parsed = ZpfParser.parse(buildBlob())

        assertEquals(0x5000, parsed.header.dmaTableStart)
        assertEquals(0x1000, parsed.header.keyRangeMin)
        assertEquals(0x10FF, parsed.header.keyRangeMax)
        assertEquals(0x1000, parsed.header.keyAddress)

        assertEquals(1, parsed.dmaUpdates.size)
        val dma = parsed.dmaUpdates[0]
        assertEquals(DmaUpdate(5, 0x3000, 0x4000, 8), dma)

        assertEquals(2, parsed.editBlocks.size)
        val start = parsed.editBlocks[0]
        assertFalse(start.isContinue)
        assertEquals(0, start.keySkip)
        assertEquals(0x2000, start.address)
        assertArrayEquals(byteArrayOf(0x00, 0xAB.toByte(), 0xCD.toByte(), 0x00), start.data)

        val cont = parsed.editBlocks[1]
        assertTrue(cont.isContinue)
        assertEquals(1, cont.keySkip)
        // continuation address = previous block end = 0x2000 + 4
        assertEquals(0x2004, cont.address)
        assertArrayEquals(byteArrayOf(0x11, 0x22), cont.data)
    }

    @Test(expected = RandomizerPatchException.BadMagic::class)
    fun badMagicThrows() {
        val blob = buildBlob()
        blob[0] = 0x00
        ZpfParser.parse(blob)
    }

    @Test(expected = RandomizerPatchException.UnsupportedVersion::class)
    fun badVersionThrows() {
        val blob = buildBlob()
        blob[4] = 0x32 // '2'
        ZpfParser.parse(blob)
    }

    @Test(expected = RandomizerPatchException.TruncatedPatch::class)
    fun truncatedThrows() {
        val blob = buildBlob().copyOfRange(0, 21) // header only, no DMA terminator
        ZpfParser.parse(blob)
    }
}
