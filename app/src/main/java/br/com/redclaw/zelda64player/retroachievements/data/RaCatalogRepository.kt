package br.com.redclaw.zelda64player.retroachievements.data

import br.com.redclaw.zelda64player.retroachievements.api.RaHttpClient
import br.com.redclaw.zelda64player.retroachievements.jni.RcheevosJni
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** One achievement definition from the RA database. */
data class RaAchievementDef(
    val id: Long,
    val title: String,
    val description: String,
    val points: Int,
    val badgeUrl: String?,
    val badgeLockedUrl: String?,
    val category: Int,
    val type: Int
)

/** One leaderboard definition from the RA database. */
data class RaLeaderboardDef(
    val id: Long,
    val title: String,
    val description: String,
    val format: Int,
    val lowerIsBetter: Boolean,
    val hidden: Boolean
)

/** Parsed fetch-game-data response for one game. */
data class RaGameData(
    val id: Long,
    val title: String,
    val imageUrl: String?,
    val achievements: List<RaAchievementDef>,
    val leaderboards: List<RaLeaderboardDef>
)

/**
 * Fetches RetroAchievements catalog data (achievement/leaderboard definitions
 * and the user's unlock set) through the standalone rapi helpers.
 *
 * Used by library-facing screens where no live rc_client session exists.
 * Game data is cached in memory per game id for the process lifetime; unlocks
 * are always fetched fresh (they change during play).
 *
 * @param http Shared RA HTTP executor.
 */
class RaCatalogRepository(private val http: RaHttpClient) {

    private val gameDataCache = HashMap<Long, RaGameData>()

    /**
     * Fetches achievement + leaderboard definitions for [gameId]. Returns null
     * on network/parse failure. [username]/[apiToken] may be blank (public data).
     */
    suspend fun fetchGameData(
        gameId: Long,
        username: String = "",
        apiToken: String = ""
    ): RaGameData? = withContext(Dispatchers.IO) {
        synchronized(gameDataCache) { gameDataCache[gameId] }?.let { return@withContext it }

        val parts = RcheevosJni.nativeBuildFetchGameDataRequest(username, apiToken, gameId)
            ?: return@withContext null
        val response = http.execute(parts[0], parts.getOrNull(1))
        if (!response.isSuccessful) return@withContext null
        val body = response.bodyAsString() ?: return@withContext null

        val parsed = parseGameData(body) ?: return@withContext null
        synchronized(gameDataCache) { gameDataCache[gameId] = parsed }
        parsed
    }

    /**
     * Fetches the set of achievement ids [username] has unlocked for [gameId].
     * Empty set on failure or when credentials are missing.
     */
    suspend fun fetchUserUnlocks(
        gameId: Long,
        username: String,
        apiToken: String,
        hardcore: Boolean
    ): Set<Long> = withContext(Dispatchers.IO) {
        if (username.isBlank() || apiToken.isBlank()) return@withContext emptySet()
        val parts = RcheevosJni.nativeBuildFetchUserUnlocksRequest(
            username, apiToken, gameId, hardcore
        ) ?: return@withContext emptySet()
        val response = http.execute(parts[0], parts.getOrNull(1))
        if (!response.isSuccessful) return@withContext emptySet()
        val body = response.bodyAsString() ?: return@withContext emptySet()

        runCatching {
            val array = JSONArray(body)
            buildSet { for (i in 0 until array.length()) add(array.getLong(i)) }
        }.getOrDefault(emptySet())
    }

    private fun parseGameData(body: String): RaGameData? = runCatching {
        if (body == "null") return null
        val root = JSONObject(body)
        val achievements = mutableListOf<RaAchievementDef>()
        val achArray = root.optJSONArray("achievements") ?: JSONArray()
        for (i in 0 until achArray.length()) {
            val a = achArray.getJSONObject(i)
            achievements.add(
                RaAchievementDef(
                    id = a.getLong("id"),
                    title = a.optString("title"),
                    description = a.optString("description"),
                    points = a.optInt("points"),
                    badgeUrl = a.optString("badge_url").takeIf { it.isNotBlank() },
                    badgeLockedUrl = a.optString("badge_locked_url").takeIf { it.isNotBlank() },
                    category = a.optInt("category"),
                    type = a.optInt("type")
                )
            )
        }
        val leaderboards = mutableListOf<RaLeaderboardDef>()
        val lbdArray = root.optJSONArray("leaderboards") ?: JSONArray()
        for (i in 0 until lbdArray.length()) {
            val l = lbdArray.getJSONObject(i)
            leaderboards.add(
                RaLeaderboardDef(
                    id = l.getLong("id"),
                    title = l.optString("title"),
                    description = l.optString("description"),
                    format = l.optInt("format"),
                    lowerIsBetter = l.optInt("lower_is_better") != 0,
                    hidden = l.optInt("hidden") != 0
                )
            )
        }
        RaGameData(
            id = root.getLong("id"),
            title = root.optString("title"),
            imageUrl = root.optString("image_url").takeIf { it.isNotBlank() },
            achievements = achievements,
            leaderboards = leaderboards
        )
    }.getOrNull()
}
