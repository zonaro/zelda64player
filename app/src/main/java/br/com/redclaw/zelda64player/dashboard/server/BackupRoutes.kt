/*
 *     Copyright (C) 2026 RedClaw
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package br.com.redclaw.zelda64player.dashboard.server

import br.com.redclaw.zelda64player.data.local.SaveBackupManager
import br.com.redclaw.zelda64player.repositories.Storage
import br.com.redclaw.zelda64player.views.InstalledLibrary
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File
import java.time.Instant
import kotlinx.serialization.Serializable

/**
 * REST API routes for backup export/import.
 *
 * Provides a complete backup of all game saves (SRAM + states) as a ZIP file, and allows restoring
 * from a previously exported backup.
 */
internal fun Route.backupRoutes() {

    val context = application.attributes[DashboardManager.CONTEXT_KEY]

    /**
     * POST /api/backup/export — Export all saves as a ZIP file.
     *
     * The ZIP contains:
     * - manifest.json (appVersion, exportDate, per-hack checksums)
     * - <hackId>/sram_<hackId> and <hackId>/state_<hackId> per hack
     */
    // A browser download is necessarily a GET navigation. Keep POST as well for API clients,
    // but make both verbs use the same export implementation.
    get("/backup/export") { exportBackup(call, context) }
    post("/backup/export") { exportBackup(call, context) }

    post("/backup/import") {
        val multipart = call.receiveMultipart()
        var summary: SaveBackupManager.BackupSummary? = null
        val errors = mutableListOf<String>()

        multipart.forEachPart { part ->
            if (part is PartData.FileItem) {
                try {
                    part.streamProvider().use { input ->
                        val storage = Storage.getInstance(context)
                        summary = SaveBackupManager.restore(input) { hackId, fileName ->
                            when {
                                fileName.startsWith("sram_") -> storage.sram(hackId)
                                fileName.startsWith("state_") -> storage.state(hackId)
                                else -> null // Never restore arbitrary paths from an archive.
                            }
                        }
                    }
                } catch (e: Exception) {
                    errors += "Failed to process backup: ${e.message ?: "unknown error"}"
                }
            }
            part.dispose()
        }

        val result = summary
        if (result == null) {
            return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No backup file uploaded"))
        }
        errors += result.errors
        val success = result.files > 0 && errors.isEmpty()
        call.respond(
                if (errors.isEmpty()) HttpStatusCode.OK else HttpStatusCode.BadRequest,
                BackupImportResult(success = success, imported = result.files, errors = errors)
        )
    }
}

private suspend fun exportBackup(call: ApplicationCall, context: android.content.Context) {
        val storage = Storage.getInstance(context)
        // InstalledLibrary is the app's canonical collection. The old repository excluded
        // vanilla entries and aliases, causing apparently successful backups to omit saves.
        val installed = InstalledLibrary.entries(context)

        // Collect saves from all installed hacks.
        val saves = mutableMapOf<String, List<File>>()
        for (hack in installed) {
            val hackSaves = mutableListOf<File>()
            val sramFile = storage.sram(hack.romId)
            if (sramFile.exists()) hackSaves.add(sramFile)
            val stateFile = storage.state(hack.romId)
            if (stateFile.exists()) hackSaves.add(stateFile)
            if (hackSaves.isNotEmpty()) {
                saves[hack.romId] = hackSaves
            }
        }

        if (saves.isEmpty()) {
            return call.respond(
                    HttpStatusCode.NotFound,
                    mapOf("error" to "No saves to export")
            )
        }

        call.response.header(HttpHeaders.ContentType, ContentType.Application.Zip.toString())
        call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(
                                ContentDisposition.Parameters.FileName,
                                "zelda64_backup_${Instant.now().epochSecond}.zip"
                        )
                        .toString()
        )
        call.respondOutputStream { SaveBackupManager.export(this, saves, context.packageName) }
}

@Serializable
internal data class BackupImportResult(
        val success: Boolean,
        val imported: Int,
        val errors: List<String> = emptyList()
)
