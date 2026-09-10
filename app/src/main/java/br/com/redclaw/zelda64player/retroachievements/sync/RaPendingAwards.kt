package br.com.redclaw.zelda64player.retroachievements.sync

import br.com.redclaw.zelda64player.retroachievements.api.RaHttpResponse
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.util.Locale

/** Only an award actually emitted by rcheevos can enter the durable journal. No tokens are stored. */
internal data class RaPendingAward(
    val username: String,
    val achievementId: Long,
    val hardcore: Boolean,
    val hash: String,
    val earnedAt: Long,
    val rejected: Boolean = false
) {
    val key: String get() = "${username.lowercase(Locale.ROOT)}|$achievementId|$hardcore|$hash"
    fun toJson(): JSONObject = JSONObject().put("username", username).put("id", achievementId)
        .put("hardcore", hardcore).put("hash", hash).put("earnedAt", earnedAt).put("rejected", rejected)
}

/** Durable account-isolated journal; encrypted persistence is supplied by RaCredentialStore. */
internal class RaPendingAwards(
    private val read: () -> String,
    private val write: (String) -> Unit,
    private val now: () -> Long = System::currentTimeMillis
) {
    @Synchronized
    fun capture(postData: String?): RaPendingAward? {
        val values = runCatching {
            postData?.split('&')?.associate {
                val parts = it.split('=', limit = 2)
                URLDecoder.decode(parts[0], "UTF-8") to URLDecoder.decode(parts.getOrElse(1) { "" }, "UTF-8")
            }
        }.getOrNull() ?: return null
        if (values["r"] != "awardachievement") return null
        val username = values["u"]?.takeIf { it.isNotBlank() } ?: return null
        val id = values["a"]?.toLongOrNull()?.takeIf { it in 1..0xffffffffL } ?: return null
        val hash = values["m"]?.takeIf { it.matches(Regex("[0-9a-fA-F]{32}")) } ?: return null
        val hardcore = when (values["h"]) { "1" -> true; "0" -> false; else -> return null }
        val elapsed = values["o"]?.toLongOrNull()?.coerceIn(0, 0xffffffffL) ?: 0L
        val award = RaPendingAward(username, id, hardcore, hash.lowercase(Locale.ROOT),
            (now() - elapsed * 1000L).coerceAtLeast(0L))
        val entries = entries().toMutableList()
        val previous = entries.firstOrNull { it.key == award.key }
        if (previous != null) return previous
        entries.add(award)
        write(JSONArray(entries.map { it.toJson() }).toString())
        return award
    }

    @Synchronized
    fun forUser(username: String): List<RaPendingAward> = entries().filter {
        it.username.equals(username, ignoreCase = true)
    }

    /** Only confirmed success for the exact id removes a journal entry; errors remain retryable. */
    @Synchronized
    fun acknowledge(award: RaPendingAward, response: RaHttpResponse): Boolean {
        if (!response.isSuccessful) return false
        val accepted = runCatching {
            val body = JSONObject(response.bodyAsString() ?: return false)
            body.optBoolean("Success") && body.optLong("AchievementID") == award.achievementId
        }.getOrDefault(false)
        if (!accepted) {
            // Definitive API rejection must not become an endless background submission loop.
            val rejected = runCatching {
                val body = JSONObject(response.bodyAsString().orEmpty())
                !body.optBoolean("Success", true) && body.optString("Error").isNotBlank()
            }.getOrDefault(false)
            if (rejected) write(JSONArray(entries().map {
                if (it.key == award.key) it.copy(rejected = true).toJson() else it.toJson()
            }).toString())
            return false
        }
        write(JSONArray(entries().filterNot { it.key == award.key }.map { it.toJson() }).toString())
        return true
    }

    /** A fresh login explicitly retries retained server rejections with renewed credentials. */
    @Synchronized
    fun retryRejected(username: String) {
        write(JSONArray(entries().map {
            if (it.username.equals(username, ignoreCase = true)) it.copy(rejected = false).toJson() else it.toJson()
        }).toString())
    }

    private fun entries(): List<RaPendingAward> {
        val array = JSONArray(read())
        return (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            RaPendingAward(obj.getString("username"), obj.getLong("id"), obj.getBoolean("hardcore"),
                obj.getString("hash"), obj.getLong("earnedAt"), obj.optBoolean("rejected"))
        }
    }
}
