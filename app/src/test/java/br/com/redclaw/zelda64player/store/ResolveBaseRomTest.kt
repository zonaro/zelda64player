package br.com.redclaw.zelda64player.store

import br.com.redclaw.zelda64player.data.model.BaseRom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests the pure base-ROM resolution helper [findBaseRomByCrc] used by
 * [DownloadManager] to match a BPS patch's expected source CRC32 against the
 * imported base ROMs (case-insensitive).
 */
class ResolveBaseRomTest {

    private fun rom(crc32: String) = BaseRom(
        id = crc32,
        displayName = "ROM $crc32",
        path = "/tmp/$crc32.z64",
        gameCode = "CZLE",
        versionByte = 0,
        sizeBytes = 100,
        crc32 = crc32,
        md5 = null,
        sha1 = null
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
}
