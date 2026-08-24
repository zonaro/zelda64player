package br.com.redclaw.zelda64player.retroachievements.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Unit tests for the pure-Kotlin RA identity model and JSON round-trips. */
class RaGameIdentityTest {

    @Test
    fun `round trip keeps hash, game id and title`() {
        val original = RaGameIdentity(
            raHash = "abc123def456",
            gameId = 1234L,
            title = "The Legend of Zelda: Ocarina of Time"
        )

        val restored = RaGameIdentity.fromJson(original.toJson())

        assertEquals(original, restored)
        assertTrue(restored.isResolved)
    }

    @Test
    fun `null title survives round trip as JSON null`() {
        val original = RaGameIdentity(raHash = "hash", gameId = 0L, title = null)

        val json = original.toJson()
        assertTrue(json.isNull(RaGameIdentity.KEY_TITLE))

        val restored = RaGameIdentity.fromJson(json)
        assertEquals(original, restored)
        assertNull(restored.title)
    }

    @Test
    fun `missing fields fall back to defaults`() {
        val restored = RaGameIdentity.fromJson(JSONObject("{}"))

        assertEquals("", restored.raHash)
        assertEquals(0L, restored.gameId)
        assertNull(restored.title)
        assertFalse(restored.isResolved)
    }

    @Test
    fun `unresolved identity is not considered resolved`() {
        val identity = RaGameIdentity(raHash = "hash", gameId = 0L, title = null)
        assertFalse(identity.isResolved)
    }
}
