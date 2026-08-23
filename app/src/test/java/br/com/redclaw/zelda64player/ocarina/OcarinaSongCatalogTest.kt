package br.com.redclaw.zelda64player.ocarina

import br.com.redclaw.zelda64player.patcher.n64.RomHeader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OcarinaSongCatalogTest {

    @Test
    fun detectGame_ootFamily() {
        assertEquals(OcarinaGame.OOT, OcarinaSongCatalog.detectGame(RomHeader("CZLE", 0, "T")))
        assertEquals(OcarinaGame.OOT, OcarinaSongCatalog.detectGame(RomHeader("CZLJ", 0, "T")))
    }

    @Test
    fun detectGame_mmFamily() {
        assertEquals(OcarinaGame.MM, OcarinaSongCatalog.detectGame(RomHeader("NZLE", 0, "T")))
        assertEquals(OcarinaGame.MM, OcarinaSongCatalog.detectGame(RomHeader("NSME", 0, "T")))
    }

    @Test
    fun detectGame_unsupported() {
        assertNull(OcarinaSongCatalog.detectGame(RomHeader("XXXX", 0, "T")))
        assertNull(OcarinaSongCatalog.detectGame(RomHeader("MMLE", 0, "T")))
    }

    @Test
    fun builtInSongs_counts() {
        assertEquals(12, OcarinaSongCatalog.builtInSongs(OcarinaGame.OOT).size)
        assertEquals(11, OcarinaSongCatalog.builtInSongs(OcarinaGame.MM).size)
    }

    @Test
    fun getSongs_appendsCustomAfterBuiltIns() {
        val custom = listOf(
            OcarinaSong("c1", rawName = "Custom 1", notes = listOf(OcarinaNote.A)),
            OcarinaSong("c2", rawName = "Custom 2", notes = listOf(OcarinaNote.C_UP))
        )
        val songs = OcarinaSongCatalog.getSongs(OcarinaGame.OOT, custom)
        assertEquals(14, songs.size)
        // Built-ins come first, custom songs are appended after.
        assertEquals("oot_zeldas_lullaby", songs[0].id)
        assertEquals("c1", songs[12].id)
        assertEquals("c2", songs[13].id)
    }

    @Test
    fun getSongs_noCustomEqualsBuiltIns() {
        val songs = OcarinaSongCatalog.getSongs(OcarinaGame.MM, emptyList())
        assertEquals(11, songs.size)
        assertEquals("mm_song_of_time", songs[0].id)
    }
}
