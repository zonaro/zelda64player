package br.com.redclaw.zelda64player.utils

import android.content.Context

/**
 * Shared read/write access to the selected LibRetro core index.
 *
 * Used by both [LibraryActivity] (settings core dialog) and
 * [GameActivityViewModel] (to pick the core .so at launch) so the choice
 * lives in exactly one place.
 */
object CorePrefs {
    private const val PREFS_NAME = "ludere_prefs"
    private const val KEY = "selected_core_index"

    // RetroAchievements preference keys.
    private const val PREF_RA_ENABLED = "pref_ra_enabled"
    private const val PREF_RA_HARDCORE = "pref_ra_hardcore"
    private const val PREF_RA_SYSTEM_NOTIFICATIONS = "pref_ra_system_notifications"
    private const val PREF_RA_CHALLENGE_INDICATORS = "pref_ra_show_challenge_indicators"
    private const val PREF_RA_PROGRESS_INDICATORS = "pref_ra_show_progress_indicators"

    // Auto-Ocarina auto-open preference (default off).
    private const val PREF_OCARINA_AUTO_OPEN = "pref_ocarina_auto_open"

    // Nintendo Switch UI preferences.
    private const val PREF_SWITCH_THEME = "pref_switch_theme"
    private const val PREF_SWITCH_SFX_ENABLED = "pref_switch_sfx_enabled"

    // "Todos os Jogos" grid sort mode (values: last_played | download_date | alpha).
    private const val PREF_GRID_SORT = "pref_grid_sort"

    val options = arrayOf(
        "Mupen64Plus Next (GLES3)",
        "Parallel N64"
    )
    val libNames = arrayOf(
        "libcore_mupen_gles3.so",
        "libcore_parallel.so"
    )

    fun getSelectedCoreIndex(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY, 0)

    fun getSelectedCoreLib(context: Context): String {
        val index = getSelectedCoreIndex(context)
        return libNames.getOrElse(index) { libNames[0] }
    }

    fun setSelectedCoreIndex(context: Context, index: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY, index).apply()
    }

    // ---- RetroAchievements ----

    /** Master switch for the RetroAchievements integration (default off). */
    fun getRetroAchievementsEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_RA_ENABLED, false)

    fun setRetroAchievementsEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_RA_ENABLED, enabled).apply()
    }

    /**
     * Hardcore mode. Defaults OFF until the app's User-Agent is validated
     * with RAdmin; the setting exists but softcore is the shipped default.
     */
    fun getRaHardcore(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_RA_HARDCORE, false)

    fun setRaHardcore(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_RA_HARDCORE, enabled).apply()
    }

    /** System notifications for unlocks (opt-in default ON per project rules). */
    fun getRaSystemNotifications(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_RA_SYSTEM_NOTIFICATIONS, true)

    fun setRaSystemNotifications(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_RA_SYSTEM_NOTIFICATIONS, enabled).apply()
    }

    /** In-game challenge indicator popups (default on). */
    fun getRaShowChallengeIndicators(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_RA_CHALLENGE_INDICATORS, true)

    fun setRaShowChallengeIndicators(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_RA_CHALLENGE_INDICATORS, enabled).apply()
    }

    /** In-game progress indicator popups (default on). */
    fun getRaShowProgressIndicators(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_RA_PROGRESS_INDICATORS, true)

    fun setRaShowProgressIndicators(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_RA_PROGRESS_INDICATORS, enabled).apply()
    }

    /**
     * Auto-Open Ocarina: when enabled and the running game is a verified OoT/MM
     * build, the app polls emulated RAM and auto-opens the song list when Link
     * draws the Ocarina. Default off.
     */
    fun getOcarinaAutoOpen(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_OCARINA_AUTO_OPEN, false)

    fun setOcarinaAutoOpen(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_OCARINA_AUTO_OPEN, enabled).apply()
    }

    // Learned ocarina flag addresses, per game key (hackId / vanilla id):
    // JSON object mapping physical RAM offset (hex string) -> vote count.
    private const val PREF_OCARINA_LEARNED_VOTES = "pref_ocarina_learned_votes"

    /**
     * Load the persisted learning votes for [gameKey]. Returns an empty map
     * when nothing was stored or the JSON is malformed (tolerant by design).
     */
    fun getOcarinaLearningVotes(context: Context, gameKey: String): Map<Long, Int> {
        val all = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_OCARINA_LEARNED_VOTES, null) ?: return emptyMap()
        return runCatching {
            val root = org.json.JSONObject(all)
            val node = root.optJSONObject(gameKey) ?: return emptyMap()
            buildMap {
                node.keys().forEach { key ->
                    val offset = key.toLongOrNull(16) ?: return@forEach
                    this[offset] = node.optInt(key, 0)
                }
            }.filterValues { it > 0 }
        }.getOrDefault(emptyMap())
    }

    /** Merge-persist [votes] for [gameKey] (replaces that key's entry only). */
    fun saveOcarinaLearningVotes(
        context: Context,
        gameKey: String,
        votes: Map<Long, Int>
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val root = runCatching {
            org.json.JSONObject(
                prefs.getString(PREF_OCARINA_LEARNED_VOTES, null) ?: "{}"
            )
        }.getOrDefault(org.json.JSONObject())
        val node = org.json.JSONObject()
        votes.forEach { (offset, count) -> if (count > 0) node.put(offset.toString(16), count) }
        root.put(gameKey, node)
        prefs.edit().putString(PREF_OCARINA_LEARNED_VOTES, root.toString()).apply()
    }

    /**
     * The learned flag address for [gameKey], once enough votes accumulated;
     * null while confidence is insufficient.
     */
    fun getOcarinaLearnedAddress(context: Context, gameKey: String): Long? =
        getOcarinaLearningVotes(context, gameKey)
            .filterValues { it >= OCARINA_LEARN_MIN_VOTES }
            .maxByOrNull { it.value }
            ?.key

    /** Votes required before a learned address is trusted. */
    const val OCARINA_LEARN_MIN_VOTES = 3

    // ---- Nintendo Switch UI ----

    /**
     * Selected Switch UI theme. Returns [THEME_DARK] (the project default) or
     * [THEME_LIGHT]. Persisted as a string so the value is self-describing.
     */
    fun getSwitchTheme(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_SWITCH_THEME, THEME_DARK) ?: THEME_DARK

    fun setSwitchTheme(context: Context, theme: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(PREF_SWITCH_THEME, theme).apply()
    }

    /** Switch UI sound effects enabled (default on). */
    fun getSwitchSfxEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_SWITCH_SFX_ENABLED, true)

    fun setSwitchSfxEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(PREF_SWITCH_SFX_ENABLED, enabled).apply()
    }

    /**
     * Selected "Todos os Jogos" grid sort mode, persisted as its [GridSortMode.prefValue]
     * string. Defaults to [br.com.redclaw.zelda64player.views.GridSortMode.ALPHA]
     * (alphabetical) for a fresh install.
     */
    fun getGridSort(context: Context): String =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(PREF_GRID_SORT, "alpha") ?: "alpha"

    fun setGridSort(context: Context, mode: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(PREF_GRID_SORT, mode).apply()
    }

    private const val THEME_DARK = "dark"
    private const val THEME_LIGHT = "light"
}
