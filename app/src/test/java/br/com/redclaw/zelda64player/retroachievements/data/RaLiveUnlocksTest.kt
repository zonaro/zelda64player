package br.com.redclaw.zelda64player.retroachievements.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RaLiveUnlocksTest {
    private val identity = RaGameIdentity("rom-hash", 101, "Test")
    private val game = """{"id":101,"hash":"rom-hash","num_core_achievements":2}"""
    private val rows = """[{"id":1,"category":1,"unlocked":1},{"id":2,"category":1,"unlocked":0},{"id":3,"category":2,"unlocked":1}]"""

    @Test fun `uses live unlocks including pending submission and excludes unofficial`() {
        assertEquals(setOf(1L), matchingLiveUnlocks(identity, game, rows, game))
    }
    @Test fun `rejects other game or other rom version and transition`() {
        assertNull(matchingLiveUnlocks(identity.copy(gameId = 102), game, rows, game))
        assertNull(matchingLiveUnlocks(identity.copy(raHash = "another-rom"), game, rows, game))
        assertNull(matchingLiveUnlocks(identity, game, rows, game.replace("101", "102")))
        assertNull(matchingLiveUnlocks(identity, game, "[]", game))
    }
    @Test fun `confirmed zero unlocks stays empty instead of requesting stale network state`() {
        assertEquals(emptySet<Long>(), matchingLiveUnlocks(identity, game, rows.replace("\"unlocked\":1", "\"unlocked\":0"), game))
    }
}
