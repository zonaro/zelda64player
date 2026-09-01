package br.com.redclaw.zelda64player.store

import br.com.redclaw.zelda64player.data.model.BaseRom
import br.com.redclaw.zelda64player.data.model.BaseRomRef
import br.com.redclaw.zelda64player.data.model.Checksums
import br.com.redclaw.zelda64player.data.model.HackEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests the pure base-ROM resolution helpers used by [DownloadManager] to match a hack's required
 * base ROM against the imported base ROMs (case-insensitive).
 */
class ResolveBaseRomTest {

    private fun rom(crc32: String, gameCode: String = "CZLE") =
            BaseRom(
                    id = crc32,
                    displayName = "ROM $crc32",
                    path = "/tmp/$crc32.z64",
                    gameCode = gameCode,
                    versionByte = 0,
                    sizeBytes = 100,
                    crc32 = crc32,
                    md5 = null,
                    sha1 = null
            )

    private fun hack(crc32: String, gameCode: String = "CZLE") =
            HackEntry(
                    id = "h",
                    name = "h",
                    description = "",
                    author = "x",
                    version = "1.0",
                    baseRom = BaseRomRef("OoT", gameCode, 0, Checksums(crc32))
            )

    @Test
    fun matchesCaseInsensitively() {
        val roms = listOf(rom("ABCDEF01"), rom("12345678"))
        assertEquals("ABCDEF01", findBaseRomByCrc(roms, "abcdef01")?.crc32)
    }

    @Test
    fun returnsNullWhenNoMatch() {
        val roms = listOf(rom("ABCDEF01"))
        assertNull(findBaseRomByCrc(roms, "FFFFFFFF"))
    }

    @Test
    fun returnsNullForEmptyList() {
        assertNull(findBaseRomByCrc(emptyList(), "ABCDEF01"))
    }

    @Test
    fun findBaseRomByGameCodeMatchesCaseInsensitively() {
        val roms = listOf(rom("ABCDEF01", "CZLE"), rom("12345678", "NSME"))
        assertEquals("NSME", findBaseRomByGameCode(roms, "nsme")?.gameCode)
    }

    @Test
    fun findBaseRomForHackMatchesByCrcWhenPresent() {
        val roms = listOf(rom("ABCDEF01"), rom("12345678"))
        assertEquals("ABCDEF01", findBaseRomForHack(roms, hack("ABCDEF01"))?.crc32)
    }

    @Test
    fun findBaseRomForHackFallsBackToGameCodeWhenCrcEmpty() {
        // Hylian Modding entries declare only the target game (empty CRC).
        val roms = listOf(rom("ABCDEF01", "CZLE"))
        assertEquals("CZLE", findBaseRomForHack(roms, hack("", "CZLE"))?.gameCode)
    }

    @Test
    fun findBaseRomForHackReturnsNullWhenOnlyGameCodeDiffers() {
        val roms = listOf(rom("ABCDEF01", "CZLE"))
        assertNull(findBaseRomForHack(roms, hack("", "NSME")))
    }

    @Test
    fun findBaseRomForHackReturnsNullWhenBothCrcAndGameCodeEmpty() {
        val roms = listOf(rom("ABCDEF01", "CZLE"))
        assertNull(findBaseRomForHack(roms, hack("", "")))
    }
}
