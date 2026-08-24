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
    fun detectGame_nzsVariantIsMM() {
        // Real-world Majora's Mask dump with gameCode "NZSE" (title "ZELDA MAJORA'S MASK").
        assertEquals(OcarinaGame.MM, OcarinaSongCatalog.detectGame(RomHeader("NZSE", 0, "ZELDA MAJORA'S MASK")))
    }

    @Test
    fun detectGame_titleFallbackMajora() {
        // Unknown code + title containing "Majora" (any casing) -> MM.
        assertEquals(OcarinaGame.MM, OcarinaSongCatalog.detectGame(RomHeader("ZZZZ", 0, "ZELDA MAJORA'S MASK")))
        assertEquals(OcarinaGame.MM, OcarinaSongCatalog.detectGame(RomHeader("ZZZZ", 0, "zelda majora's mask")))
    }

    @Test
    fun detectGame_titleFallbackOcarina() {
        // Unknown code + title containing "Ocarina" -> OoT.
        assertEquals(OcarinaGame.OOT, OcarinaSongCatalog.detectGame(RomHeader("ZZZZ", 0, "ZELDA OCARINA OF TIME")))
        assertEquals(OcarinaGame.OOT, OcarinaSongCatalog.detectGame(RomHeader("ZZZZ", 0, "zelda ocarina of time")))
    }

    @Test
    fun detectGame_titleFallbackUnrelatedIsNull() {
        // Unknown code + unrelated title -> null.
        assertNull(OcarinaSongCatalog.detectGame(RomHeader("ZZZZ", 0, "SUPER MARIO 64")))
    }

    @Test
    fun detectGame_canonicalWinsOverMisleadingTitle() {
        // Canonical codes always win even with a misleading title.
        assertEquals(OcarinaGame.OOT, OcarinaSongCatalog.detectGame(RomHeader("CZLE", 0, "ZELDA MAJORA'S MASK")))
        assertEquals(OcarinaGame.MM, OcarinaSongCatalog.detectGame(RomHeader("NZLE", 0, "ZELDA OCARINA OF TIME")))
    }

    @Test
    fun builtInSongs_counts() {
        assertEquals(12, OcarinaSongCatalog.builtInSongs(OcarinaGame.OOT).size)
        assertEquals(12, OcarinaSongCatalog.builtInSongs(OcarinaGame.MM).size)
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
        assertEquals(12, songs.size)
        assertEquals("mm_song_of_time", songs[0].id)
    }

    @Test
    fun sunsSong_usesAuthoritativeSequence() {
        // Verified against Wikibooks/ZeldaDungeon: C-Right, C-Down, C-Up x2.
        val suns = OcarinaSongCatalog.builtInSongs(OcarinaGame.OOT)
            .first { it.id == "oot_suns_song" }
        assertEquals(
            listOf(
                OcarinaNote.C_RIGHT, OcarinaNote.C_DOWN, OcarinaNote.C_UP,
                OcarinaNote.C_RIGHT, OcarinaNote.C_DOWN, OcarinaNote.C_UP
            ),
            suns.notes
        )
    }

    @Test
    fun mm_includesEponasSong() {
        val epona = OcarinaSongCatalog.builtInSongs(OcarinaGame.MM)
            .firstOrNull { it.id == "mm_eponas_song" }
        assertEquals(
            listOf(
                OcarinaNote.C_UP, OcarinaNote.C_LEFT, OcarinaNote.C_RIGHT,
                OcarinaNote.C_UP, OcarinaNote.C_LEFT, OcarinaNote.C_RIGHT
            ),
            epona?.notes
        )
    }
}
