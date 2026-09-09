package br.com.redclaw.zelda64player.retroachievements.data

import br.com.redclaw.zelda64player.retroachievements.api.RaHttpClient
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Exercises the HTTP/native boundary that normalized-JSON parser tests cannot cover. */
class RaCatalogPipelineTest {
    @Test
    fun `raw authenticated responses are normalized before parsing and caching`() = runBlocking {
        val raw = """{"Success":true,"PatchData":{"ID":101}}"""
        val bridge = FakeApi(raw)
        val repository = repository(raw, bridge)
        assertEquals(101L, repository.fetchGameData(101, "test-user", "test-token")?.id)
        assertEquals(setOf(77L), repository.fetchUserUnlocks(101, "test-user", "test-token", false))
        assertEquals(listOf("test-user", "test-token", "101"), bridge.credentials)
        assertEquals(1, bridge.gameResponses)
        assertEquals(1, bridge.unlockResponses)
        repository.fetchGameData(101, "test-user", "test-token")
        assertEquals(1, bridge.gameResponses)
    }

    @Test
    fun `server errors never become empty unlocks or cached games`() = runBlocking {
        val raw = """{"Success":false,"Error":"invalid credentials"}"""
        val bridge = FakeApi(raw, "null", "null")
        val repository = repository(raw, bridge)
        assertNull(repository.fetchGameData(101, "test-user", "test-token"))
        assertNull(repository.fetchGameData(101, "test-user", "test-token"))
        assertNull(repository.fetchUserUnlocks(101, "test-user", "test-token", false))
        assertEquals(2, bridge.gameResponses)
    }

    private fun repository(raw: String, bridge: RaCatalogApi): RaCatalogRepository {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(raw.toResponseBody()).build()
        }.build()
        return RaCatalogRepository(RaHttpClient("test", client), bridge)
    }

    private class FakeApi(
        private val expectedRaw: String,
        private val game: String = """{"id":101,"title":"Test game","achievements":[],"leaderboards":[]}""",
        private val unlocks: String = "[77]"
    ) : RaCatalogApi {
        var credentials = emptyList<String>()
        var gameResponses = 0
        var unlockResponses = 0
        override fun gameRequest(username: String, token: String, gameId: Long): Array<String> {
            credentials = listOf(username, token, gameId.toString())
            return arrayOf("https://example.test/game", "r=patch")
        }
        override fun gameResponse(body: String): String {
            assertEquals(expectedRaw, body)
            gameResponses++
            return game
        }
        override fun unlockRequest(username: String, token: String, gameId: Long, hardcore: Boolean) =
            arrayOf("https://example.test/unlocks", "r=unlocks")
        override fun unlockResponse(body: String): String {
            assertEquals(expectedRaw, body)
            unlockResponses++
            return unlocks
        }
    }
}
