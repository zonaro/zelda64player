/*
 * Zelda 64 Player - native Android N64 emulator frontend for Zelda ROM hacks.
 * Copyright (C) 2026 RedClaw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package br.com.redclaw.zelda64player.retroachievements.data

import android.content.Context
import android.util.AtomicFile
import br.com.redclaw.zelda64player.retroachievements.api.RaHttpClient
import br.com.redclaw.zelda64player.retroachievements.auth.RaCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** One recently played game in a RetroAchievements profile. */
data class RaRecentlyPlayed(
    val gameId: Long?,
    val title: String?,
    val consoleName: String?,
    val imageIcon: String?,
    val lastPlayed: String?,
    val achievementsTotal: Int?,
    val numAchieved: Int?,
    val scoreAchieved: Int?
)

/** One recently earned achievement in a RetroAchievements profile. */
data class RaRecentAchievement(
    val id: Long?,
    val gameId: Long?,
    val gameTitle: String?,
    val title: String?,
    val description: String?,
    val points: Int?,
    val badgeUrl: String?,
    val dateAwarded: String?,
    val hardcore: Boolean?
)

/** Award counters (mastery/beaten) from API_GetUserAwards. */
data class RaAwards(
    val totalAwardsCount: Int?,
    val masteryAwardsCount: Int?,
    val completionAwardsCount: Int?,
    val beatenHardcoreAwardsCount: Int?,
    val beatenSoftcoreAwardsCount: Int?,
    val eventAwardsCount: Int?,
    val siteAwardsCount: Int?
)

/** Completion progress counters from API_GetUserCompletionProgress. */
data class RaCompletionProgress(val total: Int?, val count: Int?)

/** The last game played, from the summary's LastGame object. */
data class RaLastGame(val title: String?, val console: String?, val imageIcon: String?)

/**
 * Complete, safe-to-display snapshot of a RetroAchievements profile.
 *
 * Mirrors the fields surfaced by the Vigia-ai dashboard: the user summary
 * (API_GetUserSummary) merged with award counters (API_GetUserAwards) and
 * completion progress (API_GetUserCompletionProgress). No credential-like
 * field is ever stored or exposed.
 */
data class RaUserProfile(
    val username: String,
    val avatarUrl: String?,
    val motto: String?,
    val memberSince: String?,
    val status: String?,
    val richPresence: String?,
    val totalPoints: Int?,
    val totalSoftcorePoints: Int?,
    val totalTruePoints: Int?,
    val rank: Int?,
    val totalRanked: Int?,
    val lastGame: RaLastGame?,
    val recentlyPlayed: List<RaRecentlyPlayed>,
    val recentAchievements: List<RaRecentAchievement>,
    val awards: RaAwards?,
    val completionProgress: RaCompletionProgress?,
    val updatedAt: String?
)

/**
 * Fetches and caches the signed-in player's full RetroAchievements profile.
 *
 * The profile uses the public Web API, which authenticates with the Web API
 * key from https://retroachievements.org/controlpanel.php (the `y` query
 * parameter) — not the rcheevos login token. The key is supplied only as an
 * encoded query parameter and is never logged or cached with the profile.
 */
class RaUserProfileRepository(
    context: Context,
    private val credentials: RaCredentialStore,
    private val http: RaHttpClient
) {
    private val cacheFile = File(context.applicationContext.cacheDir, CACHE_FILE_NAME)

    /** Returns the profile cached for the currently signed-in user, if any. */
    fun getCachedProfile(): RaUserProfile? {
        val username = credentials.getUsername()?.trim().orEmpty()
        if (username.isBlank()) return null
        return readCached(username)?.profile
    }

    /** Small synchronous helper for home-screen avatar binding. */
    fun cachedAvatarUrl(): String? = getCachedProfile()?.avatarUrl

    /** Builds the deterministic RA avatar URL for [username] (no API key needed). */
    fun avatarUrlFor(username: String): String {
        val clean = username.trim()
        return "$MEDIA_HOST/UserPic/$clean.png"
    }

    /** Returns cached avatar or deterministic fallback from stored username. */
    fun cachedAvatarUrlOrFallback(): String? {
        cachedAvatarUrl()?.let { return it }
        val username = credentials.getUsername()?.trim().orEmpty()
        return username.takeIf { it.isNotBlank() }?.let { avatarUrlFor(it) }
    }

    /**
     * Gets a profile, using a short-lived cache unless [forceRefresh] is true.
     * Use [refreshProfile] for an explicit user-requested refresh.
     */
    suspend fun getProfile(forceRefresh: Boolean = false): Result<RaUserProfile> =
        withContext(Dispatchers.IO) {
            val username = credentials.getUsername()?.trim().orEmpty()
            val apiKey = credentials.getApiKey()?.trim().orEmpty()
            if (username.isBlank() || apiKey.isBlank()) {
                return@withContext Result.failure(RaUserProfileException("missing web api key"))
            }

            val cached = readCached(username)
            if (!forceRefresh && cached != null && isFresh(cached.fetchedAtMillis)) {
                return@withContext Result.success(cached.profile)
            }

            fetchAndCache(username, apiKey)
        }

    /** Forces a network request and updates the cache after a successful response. */
    suspend fun refreshProfile(): Result<RaUserProfile> = getProfile(forceRefresh = true)

    private suspend fun fetchAndCache(username: String, apiKey: String): Result<RaUserProfile> {
        val summary = fetchJson(SUMMARY_ENDPOINT, username, apiKey, "g=3&a=5")
            ?: return Result.failure(RaUserProfileException("profile request failed"))
        if (summary.has("Error") || summary.has("error")) {
            return Result.failure(RaUserProfileException("profile request rejected"))
        }

        // Awards and completion are best-effort: a failure there must never
        // hide the summary. Fetched in parallel like the Vigia-ai dashboard.
        val (awards, completion) = coroutineScope {
            val a = async { fetchJson(AWARDS_ENDPOINT, username, apiKey, null) }
            val c = async { fetchJson(COMPLETION_ENDPOINT, username, apiKey, "c=1&o=0") }
            a.await() to c.await()
        }

        val profile = parseProfile(summary, awards, completion, username)
            ?: return Result.failure(RaUserProfileException("missing profile data"))
        writeCached(username, profile)
        return Result.success(profile)
    }

    private suspend fun fetchJson(
        endpoint: String,
        username: String,
        apiKey: String,
        extra: String?
    ): JSONObject? {
        val builder = endpoint.toHttpUrl().newBuilder()
            .addQueryParameter("u", username)
            .addQueryParameter("y", apiKey)
        extra?.split("&")?.forEach { pair ->
            val idx = pair.indexOf('=')
            if (idx > 0) builder.addQueryParameter(pair.substring(0, idx), pair.substring(idx + 1))
        }
        val response = http.execute(builder.build().toString())
        if (!response.isSuccessful) return null
        val body = response.bodyAsString() ?: return null
        return runCatching { JSONObject(body) }.getOrNull()
    }

    private fun readCached(username: String): CachedProfile? = runCatching {
        if (!cacheFile.exists()) return null
        val root = JSONObject(cacheFile.readText())
        val cachedUsername = root.optString(CACHE_USERNAME).trim()
        if (!cachedUsername.equals(username, ignoreCase = true)) return null
        val profile = root.optJSONObject(CACHE_PROFILE)?.toProfile(username) ?: return null
        CachedProfile(profile, root.optLong(CACHE_FETCHED_AT, 0L))
    }.getOrNull()

    private fun writeCached(username: String, profile: RaUserProfile) {
        val contents = JSONObject()
            .put(CACHE_USERNAME, username)
            .put(CACHE_FETCHED_AT, System.currentTimeMillis())
            .put(CACHE_PROFILE, profile.toJson())
            .toString()
            .toByteArray(Charsets.UTF_8)
        val atomicFile = AtomicFile(cacheFile)
        var output = atomicFile.startWrite()
        try {
            output.write(contents)
            atomicFile.finishWrite(output)
        } catch (error: Exception) {
            atomicFile.failWrite(output)
            // A cache failure must never turn a valid network profile into an
            // error. The next request simply has no local snapshot to reuse.
        }
    }

    private fun isFresh(fetchedAtMillis: Long): Boolean =
        fetchedAtMillis > 0 && System.currentTimeMillis() - fetchedAtMillis < CACHE_TTL_MS

    /* ── Parsing (mirrors Vigia-ai's parseRaPayload) ───────────────────── */

    private fun parseProfile(
        summary: JSONObject,
        awards: JSONObject?,
        completion: JSONObject?,
        fallbackUsername: String
    ): RaUserProfile? {
        if (summary.length() == 0) return null
        val username = firstString(summary, "User", "user", "Username", "username")
            ?.takeIf { it.isNotBlank() }
            ?: fallbackUsername
        val avatar = firstString(summary, "UserPic", "userPic", "AvatarUrl", "avatarUrl", "ImageIcon", "imageIcon")
            ?.let(::normalizeImageUrl)
        val motto = firstString(summary, "Motto", "motto")?.takeIf { it.isNotBlank() }
        val memberSince = firstString(summary, "MemberSince", "memberSince")?.takeIf { it.isNotBlank() }
        val status = firstString(summary, "Status", "status")?.takeIf { it.isNotBlank() }
        val richPresence = firstString(summary, "RichPresenceMsg", "richPresenceMsg")?.takeIf { it.isNotBlank() }

        val lastGameRaw = summary.optJSONObject("LastGame") ?: summary.optJSONObject("lastGame")
        val lastGame = lastGameRaw?.let {
            RaLastGame(
                title = firstString(it, "Title", "title"),
                console = firstString(it, "ConsoleName", "consoleName"),
                imageIcon = firstString(it, "ImageIcon", "imageIcon")?.let(::normalizeImageUrl)
            )
        }

        return RaUserProfile(
            username = username,
            avatarUrl = avatar,
            motto = motto,
            memberSince = memberSince,
            status = status,
            richPresence = richPresence,
            totalPoints = optInt(summary, "TotalPoints", "totalPoints"),
            totalSoftcorePoints = optInt(summary, "TotalSoftcorePoints", "totalSoftcorePoints"),
            totalTruePoints = optInt(summary, "TotalTruePoints", "totalTruePoints"),
            rank = optInt(summary, "Rank", "rank"),
            totalRanked = optInt(summary, "TotalRanked", "totalRanked"),
            lastGame = lastGame,
            recentlyPlayed = parseRecentlyPlayed(summary),
            recentAchievements = parseRecentAchievements(summary),
            awards = awards?.let(::parseAwards),
            completionProgress = completion?.let(::parseCompletion),
            updatedAt = System.currentTimeMillis().toString()
        )
    }

    private fun parseRecentlyPlayed(summary: JSONObject): List<RaRecentlyPlayed> {
        val raw = summary.optJSONArray("RecentlyPlayed") ?: summary.optJSONArray("recentlyPlayed")
            ?: return emptyList()
        val awarded = summary.optJSONObject("Awarded") ?: summary.optJSONObject("awarded")
        val out = ArrayList<RaRecentlyPlayed>(raw.length())
        for (i in 0 until raw.length()) {
            val g = raw.optJSONObject(i) ?: continue
            val gid = optLong(g, "GameID", "gameId")
            var numAchieved: Int? = null
            var scoreAchieved: Int? = null
            if (gid != null && awarded != null) {
                val aw = awarded.optJSONObject(gid.toString())
                if (aw != null) {
                    numAchieved = optInt(aw, "NumAchieved", "numAchieved")
                    scoreAchieved = optInt(aw, "ScoreAchieved", "scoreAchieved")
                }
            }
            out.add(
                RaRecentlyPlayed(
                    gameId = gid,
                    title = firstString(g, "Title", "title"),
                    consoleName = firstString(g, "ConsoleName", "consoleName"),
                    imageIcon = firstString(g, "ImageIcon", "imageIcon")?.let(::normalizeImageUrl),
                    lastPlayed = firstString(g, "LastPlayed", "lastPlayed"),
                    achievementsTotal = optInt(g, "AchievementsTotal", "achievementsTotal"),
                    numAchieved = numAchieved,
                    scoreAchieved = scoreAchieved
                )
            )
        }
        return out
    }

    private fun parseRecentAchievements(summary: JSONObject): List<RaRecentAchievement> {
        val raw = summary.optJSONObject("RecentAchievements") ?: summary.optJSONObject("recentAchievements")
            ?: return emptyList()
        val out = ArrayList<RaRecentAchievement>()
        val gameIds = raw.keys()
        while (gameIds.hasNext()) {
            val gameIdKey = gameIds.next()
            val achMap = raw.optJSONObject(gameIdKey) ?: continue
            val gid = gameIdKey.toLongOrNull()
            val achKeys = achMap.keys()
            while (achKeys.hasNext()) {
                val ach = achMap.optJSONObject(achKeys.next()) ?: continue
                out.add(
                    RaRecentAchievement(
                        id = optLong(ach, "ID", "id"),
                        gameId = optLong(ach, "GameID", "gameId") ?: gid,
                        gameTitle = firstString(ach, "GameTitle", "gameTitle"),
                        title = firstString(ach, "Title", "title"),
                        description = firstString(ach, "Description", "description"),
                        points = optInt(ach, "Points", "points"),
                        badgeUrl = firstString(ach, "BadgeName", "badgeName")?.let(::buildBadgeUrl),
                        dateAwarded = firstString(ach, "DateAwarded", "dateAwarded"),
                        hardcore = optBool(ach, "HardcoreAchieved", "hardcoreAchieved")
                    )
                )
            }
        }
        // Most recent first, matching the dashboard ordering.
        out.sortByDescending { it.dateAwarded.orEmpty() }
        return out
    }

    private fun parseAwards(awards: JSONObject): RaAwards = RaAwards(
        totalAwardsCount = optInt(awards, "TotalAwardsCount", "totalAwardsCount"),
        masteryAwardsCount = optInt(awards, "MasteryAwardsCount", "masteryAwardsCount"),
        completionAwardsCount = optInt(awards, "CompletionAwardsCount", "completionAwardsCount"),
        beatenHardcoreAwardsCount = optInt(awards, "BeatenHardcoreAwardsCount", "beatenHardcoreAwardsCount"),
        beatenSoftcoreAwardsCount = optInt(awards, "BeatenSoftcoreAwardsCount", "beatenSoftcoreAwardsCount"),
        eventAwardsCount = optInt(awards, "EventAwardsCount", "eventAwardsCount"),
        siteAwardsCount = optInt(awards, "SiteAwardsCount", "siteAwardsCount")
    )

    private fun parseCompletion(completion: JSONObject): RaCompletionProgress = RaCompletionProgress(
        total = optInt(completion, "Total", "total"),
        count = optInt(completion, "Count", "count")
    )

    /* ── JSON helpers ──────────────────────────────────────────────────── */

    private fun firstString(obj: JSONObject, vararg keys: String): String? = keys.asSequence()
        .mapNotNull { key -> obj.opt(key).takeUnless { it == JSONObject.NULL } as? String }
        .firstOrNull { it.isNotBlank() }

    private fun optInt(obj: JSONObject, vararg keys: String): Int? {
        for (key in keys) {
            if (!obj.has(key)) continue
            val v = obj.opt(key)
            if (v == JSONObject.NULL) continue
            val n = when (v) {
                is Number -> v.toInt()
                is String -> v.toIntOrNull()
                else -> null
            }
            if (n != null) return n
        }
        return null
    }

    private fun optLong(obj: JSONObject, vararg keys: String): Long? {
        for (key in keys) {
            if (!obj.has(key)) continue
            val v = obj.opt(key)
            if (v == JSONObject.NULL) continue
            val n = when (v) {
                is Number -> v.toLong()
                is String -> v.toLongOrNull()
                else -> null
            }
            if (n != null) return n
        }
        return null
    }

    private fun optBool(obj: JSONObject, vararg keys: String): Boolean? {
        for (key in keys) {
            if (!obj.has(key)) continue
            val v = obj.opt(key)
            if (v == JSONObject.NULL) continue
            return when (v) {
                is Boolean -> v
                is Number -> v.toInt() != 0
                is String -> v.equals("true", ignoreCase = true) || v == "1"
                else -> null
            }
        }
        return null
    }

    private fun normalizeImageUrl(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null
        return when {
            trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            trimmed.startsWith('/') -> "$MEDIA_HOST$trimmed"
            else -> "$MEDIA_HOST/$trimmed"
        }
    }

    private fun buildBadgeUrl(badgeName: String): String? {
        val clean = badgeName.trim()
        if (clean.isBlank()) return null
        return "$BADGE_HOST/Badge/$clean.png"
    }

    /* ── Cache serialization ───────────────────────────────────────────── */

    private fun RaUserProfile.toJson(): JSONObject = JSONObject()
        .put("username", username)
        .put("avatarUrl", avatarUrl)
        .put("motto", motto)
        .put("memberSince", memberSince)
        .put("status", status)
        .put("richPresence", richPresence)
        .put("totalPoints", totalPoints)
        .put("totalSoftcorePoints", totalSoftcorePoints)
        .put("totalTruePoints", totalTruePoints)
        .put("rank", rank)
        .put("totalRanked", totalRanked)
        .put("lastGame", lastGame?.let {
            JSONObject()
                .put("title", it.title)
                .put("console", it.console)
                .put("imageIcon", it.imageIcon)
        })
        .put("recentlyPlayed", JSONArray().also { arr ->
            recentlyPlayed.forEach { g ->
                arr.put(
                    JSONObject()
                        .put("gameId", g.gameId)
                        .put("title", g.title)
                        .put("consoleName", g.consoleName)
                        .put("imageIcon", g.imageIcon)
                        .put("lastPlayed", g.lastPlayed)
                        .put("achievementsTotal", g.achievementsTotal)
                        .put("numAchieved", g.numAchieved)
                        .put("scoreAchieved", g.scoreAchieved)
                )
            }
        })
        .put("recentAchievements", JSONArray().also { arr ->
            recentAchievements.forEach { a ->
                arr.put(
                    JSONObject()
                        .put("id", a.id)
                        .put("gameId", a.gameId)
                        .put("gameTitle", a.gameTitle)
                        .put("title", a.title)
                        .put("description", a.description)
                        .put("points", a.points)
                        .put("badgeUrl", a.badgeUrl)
                        .put("dateAwarded", a.dateAwarded)
                        .put("hardcore", a.hardcore)
                )
            }
        })
        .put("awards", awards?.let {
            JSONObject()
                .put("totalAwardsCount", it.totalAwardsCount)
                .put("masteryAwardsCount", it.masteryAwardsCount)
                .put("completionAwardsCount", it.completionAwardsCount)
                .put("beatenHardcoreAwardsCount", it.beatenHardcoreAwardsCount)
                .put("beatenSoftcoreAwardsCount", it.beatenSoftcoreAwardsCount)
                .put("eventAwardsCount", it.eventAwardsCount)
                .put("siteAwardsCount", it.siteAwardsCount)
        })
        .put("completionProgress", completionProgress?.let {
            JSONObject()
                .put("total", it.total)
                .put("count", it.count)
        })
        .put("updatedAt", updatedAt)

    private fun JSONObject.toProfile(fallbackUsername: String): RaUserProfile? {
        if (length() == 0) return null
        val username = optString("username").takeIf { it.isNotBlank() } ?: fallbackUsername
        val lastGame = optJSONObject("lastGame")?.let {
            RaLastGame(
                title = it.optString("title").takeIf { s -> s.isNotBlank() },
                console = it.optString("console").takeIf { s -> s.isNotBlank() },
                imageIcon = it.optString("imageIcon").takeIf { s -> s.isNotBlank() }
            )
        }
        val recentlyPlayed = optJSONArray("recentlyPlayed")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val g = arr.optJSONObject(i) ?: return@mapNotNull null
                RaRecentlyPlayed(
                    gameId = g.optLong("gameId").takeIf { it != 0L },
                    title = g.optString("title").takeIf { it.isNotBlank() },
                    consoleName = g.optString("consoleName").takeIf { it.isNotBlank() },
                    imageIcon = g.optString("imageIcon").takeIf { it.isNotBlank() },
                    lastPlayed = g.optString("lastPlayed").takeIf { it.isNotBlank() },
                    achievementsTotal = g.optInt("achievementsTotal").takeIf { it != 0 },
                    numAchieved = g.optInt("numAchieved").takeIf { it != 0 },
                    scoreAchieved = g.optInt("scoreAchieved").takeIf { it != 0 }
                )
            }
        } ?: emptyList()
        val recentAchievements = optJSONArray("recentAchievements")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val a = arr.optJSONObject(i) ?: return@mapNotNull null
                RaRecentAchievement(
                    id = a.optLong("id").takeIf { it != 0L },
                    gameId = a.optLong("gameId").takeIf { it != 0L },
                    gameTitle = a.optString("gameTitle").takeIf { it.isNotBlank() },
                    title = a.optString("title").takeIf { it.isNotBlank() },
                    description = a.optString("description").takeIf { it.isNotBlank() },
                    points = a.optInt("points").takeIf { it != 0 },
                    badgeUrl = a.optString("badgeUrl").takeIf { it.isNotBlank() },
                    dateAwarded = a.optString("dateAwarded").takeIf { it.isNotBlank() },
                    hardcore = if (a.has("hardcore") && !a.isNull("hardcore")) a.optBoolean("hardcore") else null
                )
            }
        } ?: emptyList()
        val awards = optJSONObject("awards")?.let {
            RaAwards(
                totalAwardsCount = it.optInt("totalAwardsCount").takeIf { v -> v != 0 },
                masteryAwardsCount = it.optInt("masteryAwardsCount").takeIf { v -> v != 0 },
                completionAwardsCount = it.optInt("completionAwardsCount").takeIf { v -> v != 0 },
                beatenHardcoreAwardsCount = it.optInt("beatenHardcoreAwardsCount").takeIf { v -> v != 0 },
                beatenSoftcoreAwardsCount = it.optInt("beatenSoftcoreAwardsCount").takeIf { v -> v != 0 },
                eventAwardsCount = it.optInt("eventAwardsCount").takeIf { v -> v != 0 },
                siteAwardsCount = it.optInt("siteAwardsCount").takeIf { v -> v != 0 }
            )
        }
        val completion = optJSONObject("completionProgress")?.let {
            RaCompletionProgress(
                total = it.optInt("total").takeIf { v -> v != 0 },
                count = it.optInt("count").takeIf { v -> v != 0 }
            )
        }
        return RaUserProfile(
            username = username,
            avatarUrl = optString("avatarUrl").takeIf { it.isNotBlank() },
            motto = optString("motto").takeIf { it.isNotBlank() },
            memberSince = optString("memberSince").takeIf { it.isNotBlank() },
            status = optString("status").takeIf { it.isNotBlank() },
            richPresence = optString("richPresence").takeIf { it.isNotBlank() },
            totalPoints = optInt("totalPoints").takeIf { it != 0 },
            totalSoftcorePoints = optInt("totalSoftcorePoints").takeIf { it != 0 },
            totalTruePoints = optInt("totalTruePoints").takeIf { it != 0 },
            rank = optInt("rank").takeIf { it != 0 },
            totalRanked = optInt("totalRanked").takeIf { it != 0 },
            lastGame = lastGame,
            recentlyPlayed = recentlyPlayed,
            recentAchievements = recentAchievements,
            awards = awards,
            completionProgress = completion,
            updatedAt = optString("updatedAt").takeIf { it.isNotBlank() }
        )
    }

    private data class CachedProfile(val profile: RaUserProfile, val fetchedAtMillis: Long)

    companion object {
        /** Official Web API endpoints (same set used by the Vigia-ai dashboard). */
        const val SUMMARY_ENDPOINT = "https://retroachievements.org/API/API_GetUserSummary.php"
        const val AWARDS_ENDPOINT = "https://retroachievements.org/API/API_GetUserAwards.php"
        const val COMPLETION_ENDPOINT = "https://retroachievements.org/API/API_GetUserCompletionProgress.php"

        private const val MEDIA_HOST = "https://media.retroachievements.org"
        private const val BADGE_HOST = "https://media.retroachievements.org"
        private const val CACHE_FILE_NAME = "ra_user_profile.json"
        private const val CACHE_USERNAME = "username"
        private const val CACHE_FETCHED_AT = "fetched_at"
        private const val CACHE_PROFILE = "profile"
        private const val CACHE_TTL_MS = 10 * 60 * 1000L
    }
}

/** A sanitized failure type for the profile repository; never includes credentials. */
class RaUserProfileException(message: String) : Exception(message)
