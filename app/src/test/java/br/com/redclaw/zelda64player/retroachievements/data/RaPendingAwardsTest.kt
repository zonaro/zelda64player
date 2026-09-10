package br.com.redclaw.zelda64player.retroachievements.data

import br.com.redclaw.zelda64player.retroachievements.api.RaHttpResponse
import br.com.redclaw.zelda64player.retroachievements.sync.RaPendingAwards
import org.junit.Assert.*
import org.junit.Test

class RaPendingAwardsTest {
    private val hash = "0123456789abcdef0123456789abcdef"
    private fun request(user: String = "alice", mode: Int = 0) =
        "r=awardachievement&u=$user&t=secret-session-token&a=42&h=$mode&m=$hash"
    private fun response(id: Int = 42) = RaHttpResponse(200,
        """{"Success":true,"AchievementID":$id}""".toByteArray())

    @Test fun `pending award survives recreation without persisting token`() {
        var disk = "[]"
        var clock = 10000L
        val first = RaPendingAwards({ disk }, { disk = it }, { clock })
        val award = first.capture(request())!!
        assertFalse(disk.contains("secret-session-token"))
        clock += 5000L
        val restarted = RaPendingAwards({ disk }, { disk = it }, { clock })
        assertEquals(award, restarted.forUser("ALICE").single())
        assertEquals(10000L, restarted.capture(request())!!.earnedAt)
        assertTrue(restarted.acknowledge(award, response()))
        assertTrue(restarted.forUser("alice").isEmpty())
    }

    @Test fun `failure and mismatched acknowledgment retain award`() {
        var disk = "[]"
        val journal = RaPendingAwards({ disk }, { disk = it })
        val award = journal.capture(request())!!
        assertFalse(journal.acknowledge(award, RaHttpResponse(0, null, "offline")))
        assertFalse(journal.acknowledge(award, response(43)))
        assertFalse(journal.acknowledge(award, RaHttpResponse(200, """{"Success":false}""".toByteArray())))
        assertEquals(listOf(award), journal.forUser("alice"))
    }

    @Test fun `accounts and modes stay separate while retries preserve original timestamp`() {
        var disk = "[]"
        val journal = RaPendingAwards({ disk }, { disk = it }, { 100000L })
        journal.capture(request())
        journal.capture(request(mode = 1))
        journal.capture(request("bob") + "&o=10")
        assertEquals(2, journal.forUser("alice").size)
        assertEquals(90000L, journal.forUser("bob").single().earnedAt)
        assertTrue(journal.forUser("charlie").isEmpty())
        journal.acknowledge(journal.forUser("alice").first(), response())
        assertTrue(journal.forUser("alice").single().hardcore)
        assertEquals(1, journal.forUser("bob").size)
    }

    @Test fun `only validated native awards enter journal`() {
        var disk = "[]"
        val journal = RaPendingAwards({ disk }, { disk = it })
        assertNull(journal.capture("r=login2&u=alice&t=secret"))
        assertNull(journal.capture(request().replace("a=42", "a=0")))
        assertNull(journal.capture(request().replace(hash, "bad-hash")))
        assertNull(journal.capture(request().replace("h=0", "h=9")))
        assertEquals("[]", disk)
    }
    @Test fun `definitive rejection is retained and a fresh login permits retry`() {
        var disk = "[]"
        val journal = RaPendingAwards({ disk }, { disk = it })
        val award = journal.capture(request())!!
        assertFalse(journal.acknowledge(award, RaHttpResponse(200,
            """{"Success":false,"Error":"rejected"}""".toByteArray())))
        assertTrue(journal.forUser("alice").single().rejected)
        journal.retryRejected("bob")
        assertTrue(journal.forUser("alice").single().rejected)
        journal.retryRejected("alice")
        assertFalse(journal.forUser("alice").single().rejected)
    }
}
