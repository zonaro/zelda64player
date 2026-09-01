/*
 *     Copyright (C) 2026 RedClaw
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package br.com.redclaw.zelda64player.dashboard.server

import android.content.Context
import br.com.redclaw.zelda64player.utils.CorePrefs
import br.com.redclaw.zelda64player.utils.LanguageManager
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

/**
 * REST API routes for dashboard server configuration.
 *
 * Allows the web dashboard to read and update server settings such as port,
 * password, and display preferences.
 */
internal fun Route.settingsRoutes() {

    val context = application.attributes[DashboardManager.CONTEXT_KEY]

    /**
     * GET /api/settings — Retrieve current server and display settings.
     */
    get("/settings") {
        call.respond(DashboardSettingsResponse(
            port = CorePrefs.getDashboardPort(context),
            hasPassword = CorePrefs.getDashboardPassword(context).isNotBlank(),
            displayOutput = CorePrefs.getDisplayOutput(context),
            displayId = CorePrefs.getDisplayId(context),
            displayTouchControls = CorePrefs.getDisplayTouchControls(context),
            connectedClients = application.attributes.getOrNull(DashboardManager.SERVER_KEY)
                ?.connectedClients ?: 0,
            address = application.attributes.getOrNull(DashboardManager.SERVER_KEY)
                ?.address ?: "unknown",
            preferences = appSettings(context)
        ))
    }

    /**
     * PUT /api/settings — Update server settings.
     *
     * Note: port and password changes require a server restart to take effect.
     * The response includes a `restartRequired` flag.
     */
    put("/settings") {
        val request = call.receive<DashboardSettingsUpdate>()

        var restartRequired = false

        request.port?.let { newPort ->
            if (newPort in 1024..65535 && newPort != CorePrefs.getDashboardPort(context)) {
                CorePrefs.setDashboardPort(context, newPort)
                restartRequired = true
            }
        }

        request.password?.let { newPassword ->
            if (newPassword != CorePrefs.getDashboardPassword(context)) {
                CorePrefs.setDashboardPassword(context, newPassword)
                restartRequired = true
            }
        }

        request.displayOutput?.let { CorePrefs.setDisplayOutput(context, it) }
        request.displayId?.let { if (it >= 0) CorePrefs.setDisplayId(context, it) }
        request.displayTouchControls?.let { CorePrefs.setDisplayTouchControls(context, it) }
        request.preferences.forEach { (key, value) -> applySetting(context, key, value) }

        call.respond(DashboardSettingsUpdateResult(
            success = true,
            restartRequired = restartRequired,
            message = if (restartRequired)
                "Settings saved. Restart the server for port/password changes to take effect."
            else
                "Settings saved."
        ))
    }
}

@Serializable
internal data class DashboardSettingsResponse(
    val port: Int,
    val hasPassword: Boolean,
    val displayOutput: String,
    val displayId: Int,
    val displayTouchControls: String,
    val connectedClients: Int,
    val address: String,
    val preferences: List<DashboardPreference>
)

@Serializable
internal data class DashboardSettingsUpdate(
    val port: Int? = null,
    val password: String? = null,
    val displayOutput: String? = null,
    val displayId: Int? = null,
    val displayTouchControls: String? = null,
    val preferences: Map<String, String> = emptyMap()
)

@Serializable
internal data class DashboardSettingsUpdateResult(
    val success: Boolean,
    val restartRequired: Boolean,
    val message: String
)

/** Settings exposed by the native Settings screen, excluding credentials and activity timestamps. */
private fun appSettings(context: Context): List<DashboardPreference> = listOf(
    bool("ra_enabled", CorePrefs.getRetroAchievementsEnabled(context)),
    bool("ra_hardcore", CorePrefs.getRaHardcore(context)),
    bool("ra_system_notifications", CorePrefs.getRaSystemNotifications(context)),
    bool("ra_challenge_indicators", CorePrefs.getRaShowChallengeIndicators(context)),
    bool("ra_progress_indicators", CorePrefs.getRaShowProgressIndicators(context)),
    bool("ocarina_auto_open", CorePrefs.getOcarinaAutoOpen(context)),
    choice("language", LanguageManager.getLanguage(context), LanguageManager.CODES),
    choice("core", CorePrefs.getSelectedCoreIndex(context).toString(), listOf("0", "1")),
    choice("switch_theme", CorePrefs.getSwitchTheme(context), listOf("dark", "light")),
    choice("switch_accent", CorePrefs.getSwitchAccent(context), listOf("cyan", "green_light", "green_dark", "blue", "yellow", "pink", "red", "violet", "teal", "orange", "purple", "indigo")),
    bool("switch_sfx", CorePrefs.getSwitchSfxEnabled(context)),
    choice("grid_sort", CorePrefs.getGridSort(context), listOf("alpha", "last_played", "download_date")),
    bool("capture_microphone", CorePrefs.getCaptureIncludeMicrophone(context)),
    bool("gdrive_enabled", CorePrefs.getGdriveEnabled(context)),
    bool("gdrive_saves", CorePrefs.getGdriveBackupSaves(context)),
    bool("gdrive_images", CorePrefs.getGdriveBackupImages(context)),
    bool("gdrive_videos", CorePrefs.getGdriveBackupVideos(context)),
    bool("gdrive_auto", CorePrefs.getGdriveAutoBackup(context)),
    choice("gdrive_frequency", CorePrefs.getGdriveBackupFrequency(context), listOf("daily", "weekly", "manual")),
    bool("cloud_sync", CorePrefs.getCloudSyncEnabled(context)),
    bool("cloud_sync_wifi", CorePrefs.getCloudSyncWifiOnly(context)),
    bool("cloud_sync_notifications", CorePrefs.getCloudSyncNotifications(context)),
    bool("button_stick", CorePrefs.getButtonStickEnabled(context)),
    choice("overlay_scale", CorePrefs.getOverlayScale(context), listOf("small", "medium", "large")),
    choice("right_tap", CorePrefs.getRightTapAction(context), listOf("off", "a", "b", "r"))
)

private fun bool(key: String, value: Boolean) = DashboardPreference(key, "boolean", value.toString())
private fun choice(key: String, value: String, options: List<String>) = DashboardPreference(key, "choice", value, options)

private fun applySetting(context: Context, key: String, value: String) {
    val bool = value.toBooleanStrictOrNull()
    when (key) {
        "ra_enabled" -> bool?.let { CorePrefs.setRetroAchievementsEnabled(context, it) }
        "ra_hardcore" -> bool?.let { CorePrefs.setRaHardcore(context, it) }
        "ra_system_notifications" -> bool?.let { CorePrefs.setRaSystemNotifications(context, it) }
        "ra_challenge_indicators" -> bool?.let { CorePrefs.setRaShowChallengeIndicators(context, it) }
        "ra_progress_indicators" -> bool?.let { CorePrefs.setRaShowProgressIndicators(context, it) }
        "ocarina_auto_open" -> bool?.let { CorePrefs.setOcarinaAutoOpen(context, it) }
        "language" -> if (value in LanguageManager.CODES) LanguageManager.setLanguage(context, value)
        "core" -> value.toIntOrNull()?.takeIf { it in CorePrefs.libNames.indices }?.let { CorePrefs.setSelectedCoreIndex(context, it) }
        "switch_theme" -> if (value in setOf("dark", "light")) CorePrefs.setSwitchTheme(context, value)
        "switch_accent" -> if (value in setOf("cyan", "green_light", "green_dark", "blue", "yellow", "pink", "red", "violet", "teal", "orange", "purple", "indigo")) CorePrefs.setSwitchAccent(context, value)
        "switch_sfx" -> bool?.let { CorePrefs.setSwitchSfxEnabled(context, it) }
        "grid_sort" -> if (value in setOf("alpha", "last_played", "download_date")) CorePrefs.setGridSort(context, value)
        "capture_microphone" -> bool?.let { CorePrefs.setCaptureIncludeMicrophone(context, it) }
        "gdrive_enabled" -> bool?.let { CorePrefs.setGdriveEnabled(context, it) }
        "gdrive_saves" -> bool?.let { CorePrefs.setGdriveBackupSaves(context, it) }
        "gdrive_images" -> bool?.let { CorePrefs.setGdriveBackupImages(context, it) }
        "gdrive_videos" -> bool?.let { CorePrefs.setGdriveBackupVideos(context, it) }
        "gdrive_auto" -> bool?.let { CorePrefs.setGdriveAutoBackup(context, it) }
        "gdrive_frequency" -> if (value in setOf("daily", "weekly", "manual")) CorePrefs.setGdriveBackupFrequency(context, value)
        "cloud_sync" -> bool?.let { CorePrefs.setCloudSyncEnabled(context, it) }
        "cloud_sync_wifi" -> bool?.let { CorePrefs.setCloudSyncWifiOnly(context, it) }
        "cloud_sync_notifications" -> bool?.let { CorePrefs.setCloudSyncNotifications(context, it) }
        "button_stick" -> bool?.let { CorePrefs.setButtonStickEnabled(context, it) }
        "overlay_scale" -> if (value in setOf("small", "medium", "large")) CorePrefs.setOverlayScale(context, value)
        "right_tap" -> if (value in setOf("off", "a", "b", "r")) CorePrefs.setRightTapAction(context, value)
    }
}

@Serializable internal data class DashboardPreference(val key: String, val type: String, val value: String, val options: List<String> = emptyList())
