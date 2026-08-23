package br.com.redclaw.zelda64player.randomizer.patch

import br.com.redclaw.zelda64player.patcher.n64.RomHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import br.com.redclaw.zelda64player.randomizer.patch.RandomizerPatchTestUtils.assertWordBE
import br.com.redclaw.zelda64player.randomizer.patch.RandomizerPatchTestUtils.deflate
import br.com.redclaw.zelda64player.randomizer.patch.RandomizerPatchTestUtils.tempFile
import br.com.redclaw.zelda64player.randomizer.patch.RandomizerPatchTestUtils.uint16BE
import br.com.redclaw.zelda64player.randomizer.patch.RandomizerPatchTestUtils.uint24BE
import br.com.redclaw.zelda64player.randomizer.patch.RandomizerPatchTestUtils.uint32BE
import java.io.ByteArrayOutputStream
import java.io.File

class RandomizerPatcherFacadeTest {

    private val ROM_SIZE = 0x10000

    private fun buildBaseRom(): ByteArray {
        val rom = ByteArray(ROM_SIZE)
        // Valid OoT 1.0 NTSC-U header: game code CZLE at 0x3B, version 0 at 0x3F.
        val code = "CZLE".toByteArray()
        System.arraycopy(code, 0, rom, 0x3B, 4)
        rom[0x3F] = 0x00
        // Key window [0x1000, 0x10FF].
        for (i in 0x1000..0x10FF) rom[i] = 0x01
        rom[0x1001] = 0x22
        rom[0x1002] = 0x00
        rom[0x1003] = 0x33
        // Source file at 0x3000.
        val src = byteArrayOf(0xAA.toByte(), 0xBB.toByte(), 0xCC.toByte(), 0xDD.toByte(),
            0xEE.toByte(), 0xFF.toByte(), 0x01, 0x02)
        System.arraycopy(src, 0, rom, 0x3000, src.size)
        for (i in 0x2000..0x2003) rom[i] = 0x55
        return rom
    }

    private fun buildPatchBlob(): ByteArray = ByteArrayOutputStream().apply {
        write(0x5A); write(0x50); write(0x46); write(0x76) // 'ZPFv'
        write(0x31)
        write(uint32BE(0x5000)) // dmaTableStart
        write(uint32BE(0x1000)) // keyRangeMin
        write(uint32BE(0x10FF)) // keyRangeMax
        write(uint32BE(0x1000)) // keyAddress
        // DMA update: index 5, from 0x3000, new start 0x4000, size 8
        write(uint16BE(0x0005))
        write(uint32BE(0x3000))
        write(uint32BE(0x4000))
        write(uint24BE(0x000008))
        write(uint16BE(0xFFFF)) // terminator
        // start block at 0x2000, size 4: [0x00, 0xAB, 0xCD, 0x00]
        write(uint32BE(0x2000))
        write(uint16BE(0x0004))
        write(byteArrayOf(0x00, 0xAB.toByte(), 0xCD.toByte(), 0x00))
    }.toByteArray()

    @Test
    fun endToEndAppliesSeedPatch() {
        val baseRom = tempFile(buildBaseRom())
        val patchFile = tempFile(deflate(buildPatchBlob()))
        val outDir = File.createTempFile("zrnd-out", "").apply { delete(); mkdirs() }

        val output = RandomizerPatcherFacade.applySeedPatch(
            baseRom, patchFile, outDir, "patched.z64"
        )

        assertTrue(output.exists())
        assertEquals(ROM_SIZE.toLong(), output.length())

        // Header preserved as accepted OoT 1.0 image.
        val header = RomHeader.fromNormalizedZ64(output)
        assertEquals("CZLE", header.gameCode)
        assertEquals(0, header.versionByte)

        val result = output.readBytes()
        // DMA entry at 0x5000 + 5*0x10 = 0x5050
        assertWordBE(result, 0x5050, 0x4000)
        assertWordBE(result, 0x5054, 0x4008)
        // Copied file at 0x4000
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

    @Test(expected = RandomizerPatchException.BaseRomMissing::class)
    fun missingBaseRomThrows() {
        val patchFile = tempFile(deflate(buildPatchBlob()))
        val outDir = File.createTempFile("zrnd-out2", "").apply { delete(); mkdirs() }
        RandomizerPatcherFacade.applySeedPatch(
            File("/nonexistent/base.z64"), patchFile, outDir, "out.z64"
        )
    }
}
