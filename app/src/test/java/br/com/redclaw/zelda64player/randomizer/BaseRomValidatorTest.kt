package br.com.redclaw.zelda64player.randomizer

import br.com.redclaw.zelda64player.patcher.n64.RomHeader
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BaseRomValidatorTest {

    @Test
    fun acceptsCzleV0() {
        assertTrue(BaseRomValidator.isAccepted(RomHeader("CZLE", 0, "OoT")))
    }

    @Test
    fun acceptsCzljV0() {
        assertTrue(BaseRomValidator.isAccepted(RomHeader("CZLJ", 0, "OoT")))
    }

    @Test
    fun rejectsCzleV1() {
        assertFalse(BaseRomValidator.isAccepted(RomHeader("CZLE", 1, "OoT")))
    }

    @Test
    fun rejectsOtherGames() {
        assertFalse(BaseRomValidator.isAccepted(RomHeader("NSME", 0, "MM")))
        assertFalse(BaseRomValidator.isAccepted(RomHeader("CZLE", 2, "OoT")))
        assertFalse(BaseRomValidator.isAccepted(RomHeader("CZLJ", 1, "OoT")))
    }

    @Test
    fun parsesAndAcceptsSyntheticCzleV0HeaderFromFile() {
        val file = File.createTempFile("rom", ".z64").apply { deleteOnExit() }
        file.writeBytes(buildHeader(gameCode = "CZLE", versionByte = 0))
        val header = RomHeader.fromNormalizedZ64(file)
        assertEquals("CZLE", header.gameCode)
        assertEquals(0, header.versionByte)
        assertTrue(BaseRomValidator.isAccepted(header))
    }

    @Test
    fun parsesAndRejectsSyntheticCzleV1HeaderFromFile() {
        val file = File.createTempFile("rom", ".z64").apply { deleteOnExit() }
        file.writeBytes(buildHeader(gameCode = "CZLE", versionByte = 1))
        val header = RomHeader.fromNormalizedZ64(file)
        assertEquals("CZLE", header.gameCode)
        assertEquals(1, header.versionByte)
        assertFalse(BaseRomValidator.isAccepted(header))
    }

    /**
     * Build a minimal 64-byte N64 header (z64 big-endian) with the magic word,
     * a blank title region, the game code at 0x3B and the version byte at 0x3F.
     */
    private fun buildHeader(gameCode: String, versionByte: Int): ByteArray {
        val bytes = ByteArray(0x40)
        // Magic word 0x80371240 (z64 big-endian).
        bytes[0] = 0x80.toByte()
        bytes[1] = 0x37.toByte()
        bytes[2] = 0x12.toByte()
        bytes[3] = 0x40.toByte()
        // Title region (0x20..0x33) left blank.
        // Game code at 0x3B.
        gameCode.toByteArray(Charsets.US_ASCII).copyInto(bytes, 0x3B, 0, 4)
        // Version byte at 0x3F.
        bytes[0x3F] = versionByte.toByte()
        return bytes
    }
}
