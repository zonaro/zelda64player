package br.com.redclaw.zelda64player.store

import br.com.redclaw.zelda64player.ocarina.OcarinaGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KnownBaseRomTableTest {

    @Test
    fun ootUsa10() {
        assertEquals(OcarinaGame.OOT, KnownBaseRomTable.infoFor("cd16c529")?.game)
    }

    @Test
    fun ootUsa10AltDump() {
        assertEquals(OcarinaGame.OOT, KnownBaseRomTable.infoFor("ec95702d")?.game)
    }

    @Test
    fun mmUsa10() {
        assertEquals(OcarinaGame.MM, KnownBaseRomTable.infoFor("b428d8a7")?.game)
    }

    @Test
    fun unknownReturnsNull() {
        assertNull(KnownBaseRomTable.infoFor("deadbeef"))
    }

    @Test
    fun lookupIsCaseInsensitive() {
        assertEquals(OcarinaGame.OOT, KnownBaseRomTable.infoFor("CD16C529")?.game)
    }
}
