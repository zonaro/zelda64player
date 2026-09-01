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
            displayTouchControls = CorePrefs.getDisplayTouchControls(context),
            connectedClients = application.attributes.getOrNull(DashboardManager.SERVER_KEY)
                ?.connectedClients ?: 0,
            address = application.attributes.getOrNull(DashboardManager.SERVER_KEY)
                ?.address ?: "unknown"
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
        request.displayTouchControls?.let { CorePrefs.setDisplayTouchControls(context, it) }

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
    val displayTouchControls: String,
    val connectedClients: Int,
    val address: String
)

@Serializable
internal data class DashboardSettingsUpdate(
    val port: Int? = null,
    val password: String? = null,
    val displayOutput: String? = null,
    val displayTouchControls: String? = null
)

@Serializable
internal data class DashboardSettingsUpdateResult(
    val success: Boolean,
    val restartRequired: Boolean,
    val message: String
)
