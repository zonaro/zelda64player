package br.com.redclaw.zelda64player.utils

import android.content.Context

/**
 * Shared read/write access to the selected LibRetro core index.
 *
 * Used by both [LibraryActivity] (settings core dialog) and [GameActivityViewModel] (to pick the
 * core .so at launch) so the choice lives in exactly one place.
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
        private const val PREF_SWITCH_ACCENT = "pref_switch_accent"

        // Automatic cloud sync (incremental, per-save) preference keys.
        private const val PREF_CLOUD_SYNC_ENABLED = "pref_cloud_sync_enabled"
        private const val PREF_CLOUD_SYNC_WIFI_ONLY = "pref_cloud_sync_wifi_only"
        private const val PREF_CLOUD_SYNC_NOTIFICATIONS = "pref_cloud_sync_notifications"
        private const val PREF_CLOUD_SYNC_LAST_SYNC = "pref_cloud_sync_last_sync"

        // Google Drive cloud backup preference keys.
        private const val PREF_GDRIVE_ENABLED = "pref_gdrive_enabled"
        private const val PREF_GDRIVE_BACKUP_SAVES = "pref_gdrive_backup_saves"
        private const val PREF_GDRIVE_BACKUP_IMAGES = "pref_gdrive_backup_images"
        private const val PREF_GDRIVE_BACKUP_VIDEOS = "pref_gdrive_backup_videos"
        private const val PREF_GDRIVE_ACCOUNT_NAME = "pref_gdrive_account_name"
        private const val PREF_GDRIVE_LAST_BACKUP = "pref_gdrive_last_backup"
        private const val PREF_GDRIVE_AUTO_BACKUP = "pref_gdrive_auto_backup"
        private const val PREF_GDRIVE_BACKUP_FREQUENCY =
                "pref_gdrive_backup_frequency" // daily, weekly, manual
        private const val PREF_GDRIVE_FOLDER_ID = "pref_gdrive_folder_id"

        // "Todos os Jogos" grid sort mode (values: last_played | download_date | alpha).
        private const val PREF_GRID_SORT = "pref_grid_sort"

        // Gameplay recording. Emulator PCM is always recorded; this opt-in
        // adds the device microphone to that same recording.
        private const val PREF_CAPTURE_INCLUDE_MICROPHONE = "pref_capture_include_microphone"

        // ---- Gamepad Overlay ----
        private const val PREF_BUTTON_STICK_ENABLED = "button_stick_enabled"
        private const val PREF_OVERLAY_SCALE = "overlay_scale"
        private const val PREF_RIGHT_TAP_ACTION = "right_tap_action"

        val options = arrayOf("Mupen64Plus Next (GLES3)", "Parallel N64")
        val libNames = arrayOf("libcore_mupen_gles3.so", "libcore_parallel.so")

        fun getSelectedCoreIndex(context: Context): Int =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY, 0)

        fun getSelectedCoreLib(context: Context): String {
                val index = getSelectedCoreIndex(context)
                return libNames.getOrElse(index) { libNames[0] }
        }

        fun setSelectedCoreIndex(context: Context, index: Int) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putInt(KEY, index)
                        .apply()
        }

        // ---- RetroAchievements ----

        /** Master switch for the RetroAchievements integration (default off). */
        fun getRetroAchievementsEnabled(context: Context): Boolean =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getBoolean(PREF_RA_ENABLED, false)

        fun setRetroAchievementsEnabled(context: Context, enabled: Boolean) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(PREF_RA_ENABLED, enabled)
                        .apply()
        }

        /**
         * Hardcore mode. Defaults OFF until the app's User-Agent is validated with RAdmin; the
         * setting exists but softcore is the shipped default.
         */
        fun getRaHardcore(context: Context): Boolean =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getBoolean(PREF_RA_HARDCORE, false)

        fun setRaHardcore(context: Context, enabled: Boolean) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(PREF_RA_HARDCORE, enabled)
                        .apply()
        }

        /** System notifications for unlocks (opt-in default ON per project rules). */
        fun getRaSystemNotifications(context: Context): Boolean =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getBoolean(PREF_RA_SYSTEM_NOTIFICATIONS, true)

        fun setRaSystemNotifications(context: Context, enabled: Boolean) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(PREF_RA_SYSTEM_NOTIFICATIONS, enabled)
                        .apply()
        }

        /** In-game challenge indicator popups (default on). */
        fun getRaShowChallengeIndicators(context: Context): Boolean =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getBoolean(PREF_RA_CHALLENGE_INDICATORS, true)

        fun setRaShowChallengeIndicators(context: Context, enabled: Boolean) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(PREF_RA_CHALLENGE_INDICATORS, enabled)
                        .apply()
        }

        /** In-game progress indicator popups (default on). */
        fun getRaShowProgressIndicators(context: Context): Boolean =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getBoolean(PREF_RA_PROGRESS_INDICATORS, true)

        fun setRaShowProgressIndicators(context: Context, enabled: Boolean) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(PREF_RA_PROGRESS_INDICATORS, enabled)
                        .apply()
        }

        /**
         * Auto-Open Ocarina: when enabled and the running game is a verified OoT/MM build, the app
         * polls emulated RAM and auto-opens the song list when Link draws the Ocarina. Default off.
         */
        fun getOcarinaAutoOpen(context: Context): Boolean =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getBoolean(PREF_OCARINA_AUTO_OPEN, false)

        fun setOcarinaAutoOpen(context: Context, enabled: Boolean) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(PREF_OCARINA_AUTO_OPEN, enabled)
                        .apply()
        }

        // Learned ocarina flag addresses, per game key (hackId / vanilla id):
        // JSON object mapping physical RAM offset (hex string) -> vote count.
        private const val PREF_OCARINA_LEARNED_VOTES = "pref_ocarina_learned_votes"

        /**
         * Load the persisted learning votes for [gameKey]. Returns an empty map when nothing was
         * stored or the JSON is malformed (tolerant by design).
         */
        fun getOcarinaLearningVotes(context: Context, gameKey: String): Map<Long, Int> {
                val all =
                        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                .getString(PREF_OCARINA_LEARNED_VOTES, null)
                                ?: return emptyMap()
                return runCatching {
                                val root = org.json.JSONObject(all)
                                val node = root.optJSONObject(gameKey) ?: return emptyMap()
                                buildMap {
                                        node.keys().forEach { key ->
                                                val offset = key.toLongOrNull(16) ?: return@forEach
                                                this[offset] = node.optInt(key, 0)
                                        }
                                }
                                        .filterValues { it > 0 }
                        }
                        .getOrDefault(emptyMap())
        }

        /** Merge-persist [votes] for [gameKey] (replaces that key's entry only). */
        fun saveOcarinaLearningVotes(context: Context, gameKey: String, votes: Map<Long, Int>) {
                val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val root =
                        runCatching {
                                        org.json.JSONObject(
                                                prefs.getString(PREF_OCARINA_LEARNED_VOTES, null)
                                                        ?: "{}"
                                        )
                                }
                                .getOrDefault(org.json.JSONObject())
                val node = org.json.JSONObject()
                votes.forEach { (offset, count) ->
                        if (count > 0) node.put(offset.toString(16), count)
                }
                root.put(gameKey, node)
                prefs.edit().putString(PREF_OCARINA_LEARNED_VOTES, root.toString()).apply()
        }

        /**
         * The learned flag address for [gameKey], once enough votes accumulated; null while
         * confidence is insufficient.
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
         * Selected Switch UI theme. Returns [THEME_DARK] (the project default) or [THEME_LIGHT].
         * Persisted as a string so the value is self-describing.
         */
        fun getSwitchTheme(context: Context): String =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getString(PREF_SWITCH_THEME, THEME_DARK)
                        ?: THEME_DARK

        fun setSwitchTheme(context: Context, theme: String) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(PREF_SWITCH_THEME, theme)
                        .apply()
        }

        /** Switch UI sound effects enabled (default on). */
        fun getSwitchSfxEnabled(context: Context): Boolean =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getBoolean(PREF_SWITCH_SFX_ENABLED, true)

        fun setSwitchSfxEnabled(context: Context, enabled: Boolean) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(PREF_SWITCH_SFX_ENABLED, enabled)
                        .apply()
        }

        /**
         * Selected Switch UI accent color. Returns the accent key (e.g., "cyan", "green_light").
         */
        fun getSwitchAccent(context: Context): String =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getString(PREF_SWITCH_ACCENT, ACCENT_DEFAULT)
                        ?: ACCENT_DEFAULT

        fun setSwitchAccent(context: Context, accentKey: String) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(PREF_SWITCH_ACCENT, accentKey)
                        .apply()
        }

        /** Default accent color key. */
        const val ACCENT_DEFAULT = "cyan"

        /**
         * Selected "Todos os Jogos" grid sort mode, persisted as its [GridSortMode.prefValue]
         * string. Defaults to [br.com.redclaw.zelda64player.views.GridSortMode.ALPHA]
         * (alphabetical) for a fresh install.
         */
        fun getGridSort(context: Context): String =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getString(PREF_GRID_SORT, "alpha")
                        ?: "alpha"

        fun setGridSort(context: Context, mode: String) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(PREF_GRID_SORT, mode)
                        .apply()
        }

        /** Include the device microphone in gameplay recordings (default off). */
        fun getCaptureIncludeMicrophone(context: Context): Boolean =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getBoolean(PREF_CAPTURE_INCLUDE_MICROPHONE, false)

        fun setCaptureIncludeMicrophone(context: Context, enabled: Boolean) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(PREF_CAPTURE_INCLUDE_MICROPHONE, enabled)
                        .apply()
        }

        // ---- Google Drive cloud backup ----

        /** Master switch for Google Drive backup (default off). */
        fun getGdriveEnabled(context: Context): Boolean =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getBoolean(PREF_GDRIVE_ENABLED, false)

        fun setGdriveEnabled(context: Context, enabled: Boolean) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(PREF_GDRIVE_ENABLED, enabled)
                        .apply()
        }

        /** Back up SRAM + save-states when true (default on). */
        fun getGdriveBackupSaves(context: Context): Boolean =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getBoolean(PREF_GDRIVE_BACKUP_SAVES, true)

        fun setGdriveBackupSaves(context: Context, enabled: Boolean) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(PREF_GDRIVE_BACKUP_SAVES, enabled)
                        .apply()
        }

        /** Back up screenshots when true (default on). */
        fun getGdriveBackupImages(context: Context): Boolean =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getBoolean(PREF_GDRIVE_BACKUP_IMAGES, true)

        fun setGdriveBackupImages(context: Context, enabled: Boolean) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(PREF_GDRIVE_BACKUP_IMAGES, enabled)
                        .apply()
        }

        /** Back up screen recordings when true (default on). */
        fun getGdriveBackupVideos(context: Context): Boolean =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getBoolean(PREF_GDRIVE_BACKUP_VIDEOS, true)

        fun setGdriveBackupVideos(context: Context, enabled: Boolean) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(PREF_GDRIVE_BACKUP_VIDEOS, enabled)
                        .apply()
        }

        /** Connected Google account name, or null when not connected. */
        fun getGdriveAccountName(context: Context): String? {
                val name =
                        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                .getString(PREF_GDRIVE_ACCOUNT_NAME, null)
                return if (name.isNullOrBlank()) null else name
        }

        fun setGdriveAccountName(context: Context, name: String?) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(PREF_GDRIVE_ACCOUNT_NAME, name)
                        .apply()
        }

        /** Epoch millis of the last successful backup, or 0 when never. */
        fun getGdriveLastBackup(context: Context): Long =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getLong(PREF_GDRIVE_LAST_BACKUP, 0L)

        fun setGdriveLastBackup(context: Context, epochMillis: Long) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putLong(PREF_GDRIVE_LAST_BACKUP, epochMillis)
                        .apply()
        }

        /** Automatic periodic backup switch (default off). */
        fun getGdriveAutoBackup(context: Context): Boolean =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getBoolean(PREF_GDRIVE_AUTO_BACKUP, false)

        fun setGdriveAutoBackup(context: Context, enabled: Boolean) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(PREF_GDRIVE_AUTO_BACKUP, enabled)
                        .apply()
        }

        /**
         * Backup frequency. One of [GDRIVE_FREQ_DAILY], [GDRIVE_FREQ_WEEKLY] or
         * [GDRIVE_FREQ_MANUAL]. Defaults to [GDRIVE_FREQ_DAILY].
         */
        fun getGdriveBackupFrequency(context: Context): String =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getString(PREF_GDRIVE_BACKUP_FREQUENCY, GDRIVE_FREQ_DAILY)
                        ?: GDRIVE_FREQ_DAILY

        fun setGdriveBackupFrequency(context: Context, frequency: String) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(PREF_GDRIVE_BACKUP_FREQUENCY, frequency)
                        .apply()
        }

        /** Drive folder id for the app backup folder, or null until first created. */
        fun getGdriveFolderId(context: Context): String? {
                val id =
                        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                                .getString(PREF_GDRIVE_FOLDER_ID, null)
                return if (id.isNullOrBlank()) null else id
        }

        fun setGdriveFolderId(context: Context, folderId: String?) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(PREF_GDRIVE_FOLDER_ID, folderId)
                        .apply()
        }

        const val GDRIVE_FREQ_DAILY = "daily"
        const val GDRIVE_FREQ_WEEKLY = "weekly"
        const val GDRIVE_FREQ_MANUAL = "manual"

        // ---- Automatic cloud sync (incremental, per-save) ----

        /** Master switch for automatic cloud sync of saves (default off). */
        fun getCloudSyncEnabled(context: Context): Boolean =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getBoolean(PREF_CLOUD_SYNC_ENABLED, false)

        fun setCloudSyncEnabled(context: Context, enabled: Boolean) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(PREF_CLOUD_SYNC_ENABLED, enabled)
                        .apply()
        }

        /** Only sync while on an unmetered (Wi-Fi) network (default off). */
        fun getCloudSyncWifiOnly(context: Context): Boolean =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getBoolean(PREF_CLOUD_SYNC_WIFI_ONLY, false)

        fun setCloudSyncWifiOnly(context: Context, wifiOnly: Boolean) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(PREF_CLOUD_SYNC_WIFI_ONLY, wifiOnly)
                        .apply()
        }

        /** Raise a notification when a sync conflict is detected (default on). */
        fun getCloudSyncNotifications(context: Context): Boolean =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getBoolean(PREF_CLOUD_SYNC_NOTIFICATIONS, true)

        fun setCloudSyncNotifications(context: Context, notify: Boolean) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(PREF_CLOUD_SYNC_NOTIFICATIONS, notify)
                        .apply()
        }

        /** Epoch millis of the last successful sync run, or 0 when never. */
        fun getCloudSyncLastSync(context: Context): Long =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getLong(PREF_CLOUD_SYNC_LAST_SYNC, 0L)

        fun setCloudSyncLastSync(context: Context, epochMillis: Long) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putLong(PREF_CLOUD_SYNC_LAST_SYNC, epochMillis)
                        .apply()
        }

        // ---- Self-Hosted Dashboard ----

        /** Master switch for the embedded Ktor web server (default off). */
        private const val PREF_DASHBOARD_ENABLED = "pref_dashboard_enabled"
        fun getDashboardEnabled(context: Context): Boolean =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getBoolean(PREF_DASHBOARD_ENABLED, false)

        /** Convenience alias for [getDashboardEnabled] (boolean property style). */
        fun isDashboardEnabled(context: Context): Boolean = getDashboardEnabled(context)

        fun setDashboardEnabled(context: Context, enabled: Boolean) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(PREF_DASHBOARD_ENABLED, enabled)
                        .apply()
        }

        /** TCP port for the embedded Ktor server (default 8080). */
        private const val PREF_DASHBOARD_PORT = "pref_dashboard_port"
        fun getDashboardPort(context: Context): Int =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getInt(PREF_DASHBOARD_PORT, 8080)

        fun setDashboardPort(context: Context, port: Int) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putInt(PREF_DASHBOARD_PORT, port)
                        .apply()
        }

        /** Optional password to protect the dashboard (empty = no auth). */
        private const val PREF_DASHBOARD_PASSWORD = "pref_dashboard_password"
        fun getDashboardPassword(context: Context): String =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getString(PREF_DASHBOARD_PASSWORD, "")
                        ?: ""

        fun setDashboardPassword(context: Context, password: String) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(PREF_DASHBOARD_PASSWORD, password)
                        .apply()
        }

        // ---- Multi-Monitor Display ----

        /**
         * Display output mode: "auto" (secondary if available), "primary", "secondary", or a
         * specific display ID as string.
         */
        private const val PREF_DISPLAY_OUTPUT = "pref_display_output"
        fun getDisplayOutput(context: Context): String =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getString(PREF_DISPLAY_OUTPUT, DISPLAY_AUTO)
                        ?: DISPLAY_AUTO

        fun setDisplayOutput(context: Context, mode: String) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(PREF_DISPLAY_OUTPUT, mode)
                        .apply()
        }

        /** Specific display ID when using a custom output (numeric display id). */
        private const val PREF_DISPLAY_ID = "pref_display_id"
        fun getDisplayId(context: Context): Int =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getInt(PREF_DISPLAY_ID, -1)

        fun setDisplayId(context: Context, displayId: Int) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putInt(PREF_DISPLAY_ID, displayId)
                        .apply()
        }

        /** Where to show touch controls in multi-monitor mode: "primary", "secondary", "both". */
        private const val PREF_DISPLAY_TOUCH_CONTROLS = "pref_display_touch_controls"
        fun getDisplayTouchControls(context: Context): String =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getString(PREF_DISPLAY_TOUCH_CONTROLS, TOUCH_CONTROLS_PRIMARY)
                        ?: TOUCH_CONTROLS_PRIMARY

        fun setDisplayTouchControls(context: Context, mode: String) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(PREF_DISPLAY_TOUCH_CONTROLS, mode)
                        .apply()
        }

        const val DISPLAY_AUTO = "auto"
        const val DISPLAY_PRIMARY = "primary"
        const val DISPLAY_SECONDARY = "secondary"
        const val TOUCH_CONTROLS_PRIMARY = "primary"
        const val TOUCH_CONTROLS_SECONDARY = "secondary"
        const val TOUCH_CONTROLS_BOTH = "both"

        private const val THEME_DARK = "dark"
        private const val THEME_LIGHT = "light"

        // ---- Gamepad Overlay (ButtonStick toggle + scale + right-tap) ----

        /** ButtonStick toggle: true = enabled (C/A/B buttons act as sticks), false = off. */
        fun getButtonStickEnabled(context: Context): Boolean =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getBoolean(PREF_BUTTON_STICK_ENABLED, true)

        fun setButtonStickEnabled(context: Context, enabled: Boolean) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(PREF_BUTTON_STICK_ENABLED, enabled)
                        .apply()
        }

        /** Overlay button scale: "small" (1.0x), "medium" (1.25x), "large" (1.5x). */
        fun getOverlayScale(context: Context): String =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getString(PREF_OVERLAY_SCALE, OVERLAY_SCALE_SMALL)
                        ?: OVERLAY_SCALE_SMALL

        fun setOverlayScale(context: Context, scale: String) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(PREF_OVERLAY_SCALE, scale)
                        .apply()
        }

        fun getOverlayScaleFactor(context: Context): Float =
                when (getOverlayScale(context)) {
                        OVERLAY_SCALE_MEDIUM -> 1.25f
                        OVERLAY_SCALE_LARGE -> 1.5f
                        else -> 1f
                }

        const val OVERLAY_SCALE_SMALL = "small"
        const val OVERLAY_SCALE_MEDIUM = "medium"
        const val OVERLAY_SCALE_LARGE = "large"

        /** Right-side free-area tap action: "off", "a", "b", "r". */
        fun getRightTapAction(context: Context): String =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .getString(PREF_RIGHT_TAP_ACTION, RIGHT_TAP_OFF)
                        ?: RIGHT_TAP_OFF

        fun setRightTapAction(context: Context, action: String) {
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        .edit()
                        .putString(PREF_RIGHT_TAP_ACTION, action)
                        .apply()
        }

        fun getRightTapKeyCode(context: Context): Int? =
                when (getRightTapAction(context)) {
                        RIGHT_TAP_A -> android.view.KeyEvent.KEYCODE_BUTTON_A
                        RIGHT_TAP_B -> android.view.KeyEvent.KEYCODE_BUTTON_B
                        RIGHT_TAP_R -> android.view.KeyEvent.KEYCODE_BUTTON_R2
                        else -> null
                }

        const val RIGHT_TAP_OFF = "off"
        const val RIGHT_TAP_A = "a"
        const val RIGHT_TAP_B = "b"
        const val RIGHT_TAP_R = "r"
}
