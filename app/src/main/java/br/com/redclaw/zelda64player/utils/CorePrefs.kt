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
}
