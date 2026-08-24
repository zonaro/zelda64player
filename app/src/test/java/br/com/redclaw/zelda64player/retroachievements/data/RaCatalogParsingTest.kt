package br.com.redclaw.zelda64player.retroachievements.data

import br.com.redclaw.zelda64player.retroachievements.api.RaHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for parsing the compact fetch-game-data JSON produced by the
 * native rapi bridge (ra_jni.c). The fixture mirrors the exact field names
 * emitted by nativeProcessFetchGameDataResponse.
 */
class RaCatalogParsingTest {

    // parseGameData is pure; the HTTP client is never touched by these tests.
    private val repository = RaCatalogRepository(http = RaHttpClient("zelda64player-tests"))

    @Test
    fun `parses achievements and leaderboards`() {
        val body = """
            {
              "id": 1234,
              "title": "The Legend of Zelda: Ocarina of Time",
              "image_url": "https://media.retroachievements.org/1234.png",
              "achievements": [
                {
                  "id": 111,
                  "title": "It's a Secret to Everybody",
                  "description": "Collect 10 rupees.",
                  "points": 5,
                  "badge_url": "https://badge/111.png",
                  "badge_locked_url": "https://badge/111_lock.png",
                  "category": 3,
                  "type": 0
                },
                {
                  "id": 112,
                  "title": "Hyrule Hero",
                  "description": "Finish the game.",
                  "points": 25,
                  "badge_url": "",
                  "badge_locked_url": "",
                  "category": 3,
                  "type": 1
                }
              ],
              "leaderboards": [
                {
                  "id": 222,
                  "title": "Child Dungeon Sprint",
                  "description": "Fastest Deku Tree clear.",
                  "format": 3,
                  "lower_is_better": 1,
                  "hidden": 0
                }
              ]
            }
        """.trimIndent()

        val game = repository.parseGameData(body)

        assertNotNull(game)
        assertEquals(1234L, game!!.id)
        assertEquals("The Legend of Zelda: Ocarina of Time", game.title)
        assertEquals("https://media.retroachievements.org/1234.png", game.imageUrl)

        assertEquals(2, game.achievements.size)
        val first = game.achievements[0]
        assertEquals(111L, first.id)
        assertEquals(5, first.points)
        assertEquals("https://badge/111.png", first.badgeUrl)
        assertEquals("https://badge/111_lock.png", first.badgeLockedUrl)
        // Blank badge urls must normalize to null.
        assertNull(game.achievements[1].badgeUrl)
        assertNull(game.achievements[1].badgeLockedUrl)

        assertEquals(1, game.leaderboards.size)
        val leaderboard = game.leaderboards[0]
        assertEquals(222L, leaderboard.id)
        assertTrue(leaderboard.lowerIsBetter)
        assertFalse(leaderboard.hidden)
    }

    @Test
    fun `empty collections are tolerated`() {
        val body = """{"id": 7, "title": "Tiny Hack"}"""

        val game = repository.parseGameData(body)

        assertNotNull(game)
        assertEquals(7L, game!!.id)
        assertTrue(game.achievements.isEmpty())
        assertTrue(game.leaderboards.isEmpty())
        assertNull(game.imageUrl)
    }

    @Test
    fun `literal null body yields null`() {
        assertNull(repository.parseGameData("null"))
    }

    @Test
    fun `malformed body yields null instead of throwing`() {
        assertNull(repository.parseGameData("not json at all"))
    }

    @Test
    fun `missing required id field yields null`() {
        assertNull(repository.parseGameData("""{"title": "No id here"}"""))
    }
}
