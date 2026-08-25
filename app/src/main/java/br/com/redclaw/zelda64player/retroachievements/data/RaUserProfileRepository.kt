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
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A safe-to-display field returned by the RetroAchievements user-profile API. */
data class RaUserProfileField(val key: String, val value: String)

/**
 * Complete, safe-to-display snapshot of a RetroAchievements profile.
 *
 * [fields] retains every non-sensitive field returned by the endpoint, including
 * fields added by RetroAchievements in future API releases. This lets the UI be
 * complete without coupling a release of the app to a fixed server schema.
 */
data class RaUserProfile(
    val username: String,
    val avatarUrl: String?,
    val motto: String?,
    val richPresence: String?,
    val fields: List<RaUserProfileField>
)

/**
 * Fetches and caches the signed-in player's full RetroAchievements profile.
 *
 * The cache contains only response fields that are safe to display. In
 * particular, credential-like keys are discarded before a response is written
 * to disk or exposed to UI code. The token is supplied only as an encoded query
 * parameter to the official API and is never logged.
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

    /**
     * Gets a profile, using a short-lived cache unless [forceRefresh] is true.
     * Use [refreshProfile] for an explicit user-requested refresh.
     */
    suspend fun getProfile(forceRefresh: Boolean = false): Result<RaUserProfile> =
        withContext(Dispatchers.IO) {
            val username = credentials.getUsername()?.trim().orEmpty()
            val token = credentials.getToken().orEmpty()
            if (username.isBlank() || token.isBlank()) {
                return@withContext Result.failure(RaUserProfileException("not signed in"))
            }

            val cached = readCached(username)
            if (!forceRefresh && cached != null && isFresh(cached.fetchedAtMillis)) {
                return@withContext Result.success(cached.profile)
            }

            fetchAndCache(username, token)
        }

    /** Forces a network request and updates the cache after a successful response. */
    suspend fun refreshProfile(): Result<RaUserProfile> = getProfile(forceRefresh = true)

    private suspend fun fetchAndCache(username: String, token: String): Result<RaUserProfile> {
        val url = PROFILE_ENDPOINT.toHttpUrl().newBuilder()
            .addQueryParameter("u", username)
            .addQueryParameter("y", token)
            .build()
            .toString()
        val response = http.execute(url)
        if (!response.isSuccessful) {
            return Result.failure(RaUserProfileException("profile request failed"))
        }
        val body = response.bodyAsString()
            ?: return Result.failure(RaUserProfileException("empty profile response"))
        val raw = runCatching { JSONObject(body) }.getOrNull()
            ?: return Result.failure(RaUserProfileException("invalid profile response"))
        if (raw.has("Error") || raw.has("error")) {
            return Result.failure(RaUserProfileException("profile request rejected"))
        }

        val safe = sanitize(raw)
        val profile = safe.toProfile(username)
            ?: return Result.failure(RaUserProfileException("missing profile data"))
        writeCached(username, safe)
        return Result.success(profile)
    }

    private fun readCached(username: String): CachedProfile? = runCatching {
        if (!cacheFile.exists()) return null
        val root = JSONObject(cacheFile.readText())
        val cachedUsername = root.optString(CACHE_USERNAME).trim()
        if (!cachedUsername.equals(username, ignoreCase = true)) return null
        val profile = root.optJSONObject(CACHE_PROFILE)?.toProfile(username) ?: return null
        CachedProfile(profile, root.optLong(CACHE_FETCHED_AT, 0L))
    }.getOrNull()

    private fun writeCached(username: String, profile: JSONObject) {
        val contents = JSONObject()
            .put(CACHE_USERNAME, username)
            .put(CACHE_FETCHED_AT, System.currentTimeMillis())
            .put(CACHE_PROFILE, profile)
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

    /** Drops credential-like response properties before display and caching. */
    private fun sanitize(raw: JSONObject): JSONObject = JSONObject().also { safe ->
        raw.keys().forEach { key ->
            if (!isSensitiveKey(key)) safe.put(key, raw.opt(key))
        }
    }

    private fun JSONObject.toProfile(fallbackUsername: String): RaUserProfile? {
        if (length() == 0) return null
        val username = firstString("User", "Username", "user", "username")
            ?.takeIf { it.isNotBlank() }
            ?: fallbackUsername
        val avatar = firstString(
            "AvatarUrl", "avatarUrl", "UserPic", "userPic", "ImageIcon", "imageIcon", "Avatar", "avatar"
        )?.let(::normalizeAvatarUrl)
        val motto = firstString("Motto", "motto")?.takeIf { it.isNotBlank() }
        val richPresence = firstString(
            "RichPresenceMsg", "richPresenceMsg", "RichPresence", "richPresence"
        )?.takeIf { it.isNotBlank() }
        val fields = keys().asSequence()
            .filterNot(::isSensitiveKey)
            .sorted()
            .map { key -> RaUserProfileField(key, valueForDisplay(opt(key))) }
            .toList()
        return RaUserProfile(username, avatar, motto, richPresence, fields)
    }

    private fun JSONObject.firstString(vararg keys: String): String? = keys.asSequence()
        .mapNotNull { key -> opt(key).takeUnless { it == JSONObject.NULL } as? String }
        .firstOrNull { it.isNotBlank() }

    private fun normalizeAvatarUrl(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return null
        return when {
            trimmed.startsWith("https://", ignoreCase = true) ||
                trimmed.startsWith("http://", ignoreCase = true) -> trimmed
            trimmed.startsWith('/') -> "$MEDIA_HOST$trimmed"
            else -> "$MEDIA_HOST/$trimmed"
        }
    }

    private fun valueForDisplay(value: Any?): String = when (value) {
        null, JSONObject.NULL -> ""
        is JSONObject, is JSONArray -> value.toString()
        else -> value.toString()
    }

    private data class CachedProfile(val profile: RaUserProfile, val fetchedAtMillis: Long)

    companion object {
        /** Official minimal profile endpoint. */
        const val PROFILE_ENDPOINT = "https://retroachievements.org/API/API_GetUserProfile.php"

        private const val MEDIA_HOST = "https://media.retroachievements.org"
        private const val CACHE_FILE_NAME = "ra_user_profile.json"
        private const val CACHE_USERNAME = "username"
        private const val CACHE_FETCHED_AT = "fetched_at"
        private const val CACHE_PROFILE = "profile"
        private const val CACHE_TTL_MS = 10 * 60 * 1000L

        private fun isSensitiveKey(key: String): Boolean {
            val normalized = key.lowercase()
            return normalized.contains("token") || normalized.contains("password") ||
                normalized.contains("credential") || normalized == "y" ||
                normalized.contains("api_key") || normalized.contains("apikey") ||
                normalized.contains("secret")
        }
    }
}

/** A sanitized failure type for the profile repository; never includes credentials. */
class RaUserProfileException(message: String) : Exception(message)
