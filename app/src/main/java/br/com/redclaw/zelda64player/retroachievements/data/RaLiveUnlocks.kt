package br.com.redclaw.zelda64player.retroachievements.data

import br.com.redclaw.zelda64player.retroachievements.jni.RcheevosJni
import org.json.JSONArray
import org.json.JSONObject

/** Reads pending and confirmed unlocks without waiting for a server submission round trip. */
internal fun liveUnlocks(identity: RaGameIdentity): Set<Long>? {
    val before = RcheevosJni.nativeGetGameInfoJson()
    val achievements = RcheevosJni.nativeGetAchievementListJson()
    val after = RcheevosJni.nativeGetGameInfoJson()
    return matchingLiveUnlocks(identity, before, achievements, after)
}

/** Rejects snapshots belonging to another ROM or spanning a game transition. */
internal fun matchingLiveUnlocks(
    identity: RaGameIdentity,
    before: String,
    achievements: String,
    after: String
): Set<Long>? = runCatching {
    if (identity.raHash.isBlank() || !identity.isResolved) return null
    val first = JSONObject(before)
    val last = JSONObject(after)
    if (listOf(first, last).any {
        it.optLong("id") != identity.gameId || it.optString("hash") != identity.raHash
    }) return null
    val rows = JSONArray(achievements)
    val core = (0 until rows.length()).map { rows.getJSONObject(it) }
        .filter { it.optInt("category") == 1 } // rc_client category, not rapi category 3.
    if (core.size != last.optInt("num_core_achievements")) return null
    core.filter { it.optInt("unlocked") != 0 }.map { it.getLong("id") }.toSet()
}.getOrNull()
