package br.com.redclaw.zelda64player.retroachievements.data

import br.com.redclaw.zelda64player.data.model.RetroAchievementsRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Unit tests for the catalog RetroAchievements reference model. */
class RetroAchievementsRefTest {

    @Test
    fun `round trip keeps game id and title`() {
        val original = RetroAchievementsRef(gameId = 5678L, title = "Zelda Hack")

        val restored = RetroAchievementsRef.fromJson(original.toJson())

        assertEquals(original, restored)
    }

    @Test
    fun `null title survives round trip`() {
        val original = RetroAchievementsRef(gameId = 42L, title = null)

        val restored = RetroAchievementsRef.fromJson(original.toJson())

        assertEquals(42L, restored.gameId)
        assertNull(restored.title)
    }

    @Test
    fun `defaults apply when fields are missing`() {
        val restored = RetroAchievementsRef.fromJson(org.json.JSONObject("{}"))

        assertEquals(0L, restored.gameId)
        assertNull(restored.title)
    }
}
