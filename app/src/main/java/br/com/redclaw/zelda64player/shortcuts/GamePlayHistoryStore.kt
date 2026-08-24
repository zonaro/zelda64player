package br.com.redclaw.zelda64player.shortcuts

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists the last-played timestamp for each installed hack so dynamic
 * shortcuts can be ranked by most-recently-played (matching Android's shortcut
 * ranking expectations). Kept separate from
 * [br.com.redclaw.zelda64player.data.local.InstalledHacksRepository] to respect
 * single responsibility: install state is one concern, play recency another.
 *
 * Stored as JSON at [file] (`filesDir/game_play_history.json`), one object per
 * hack: `{ "hackId": "...", "lastPlayed": 1234567890 }`. Takes an explicit file
 * so it is unit-testable on the JVM with a temp file (mirrors the repository
 * style used elsewhere in the project).
 */
class GamePlayHistoryStore(private val file: File) {
    init {
        file.parentFile?.mkdirs()
    }

    /** Record (or refresh) the last-played time for [hackId] as "now". */
    fun markPlayed(hackId: String) {
        val all = load().toMutableMap()
        all[hackId] = System.currentTimeMillis()
        save(all)
    }

    /** Last-played epoch millis for [hackId], or null if never played. */
    fun lastPlayed(hackId: String): Long? = load()[hackId]

    /** Whole history map (hackId -> last-played epoch millis), for bulk ordering. */
    fun all(): Map<String, Long> = load()

    /**
     * Remove [hackId] from the play-history store (e.g. after uninstalling the
     * game). Safe to call when the hack was never played.
     */
    fun remove(hackId: String) {
        val all = load().toMutableMap()
        all.remove(hackId)
        save(all)
    }

    /**
     * Return [ids] ordered by most-recently-played first. Hacks never played
     * (or with no record) sort after played ones, preserving their original
     * relative order for determinism.
     */
    fun recencyRanked(ids: List<String>): List<String> {
        val times = load()
        return ids.sortedByDescending { times[it] ?: Long.MIN_VALUE }
    }

    private fun load(): Map<String, Long> {
        if (!file.exists()) return emptyMap()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { i ->
                runCatching {
                    val o = arr.getJSONObject(i)
                    o.getString("hackId") to o.getLong("lastPlayed")
                }.getOrNull()
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    private fun save(all: Map<String, Long>) {
        val arr = JSONArray()
        all.forEach { (id, time) ->
            arr.put(
                JSONObject().apply {
                    put("hackId", id)
                    put("lastPlayed", time)
                }
            )
        }
        file.writeText(arr.toString(2))
    }
}
