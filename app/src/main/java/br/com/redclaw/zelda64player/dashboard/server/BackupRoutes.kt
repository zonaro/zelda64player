/*
 *     Copyright (C) 2026 RedClaw
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package br.com.redclaw.zelda64player.dashboard.server

import br.com.redclaw.zelda64player.data.local.InstalledHacksRepository
import br.com.redclaw.zelda64player.data.local.SaveBackupManager
import br.com.redclaw.zelda64player.repositories.Storage
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
    post("/backup/export") {
        val storage = Storage.getInstance(context)
        val installed =
                InstalledHacksRepository(File(context.filesDir, "installed_hacks.json")).load()

        // Collect saves from all installed hacks.
        val saves = mutableMapOf<String, List<File>>()
        for (hack in installed.values) {
            val hackSaves = mutableListOf<File>()
            val sramFile = storage.sram(hack.hackId)
            if (sramFile.exists()) hackSaves.add(sramFile)
            val stateFile = storage.state(hack.hackId)
            if (stateFile.exists()) hackSaves.add(stateFile)
            if (hackSaves.isNotEmpty()) {
                saves[hack.hackId] = hackSaves
            }
        }

        if (saves.isEmpty()) {
            return@post call.respond(
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

    /**
     * POST /api/backup/import — Import saves from an uploaded ZIP file.
     *
     * The ZIP is validated against the manifest (CRC32 per entry) before any files are written.
     * Invalid or tampered backups are rejected.
     */
    post("/backup/import") {
        val multipart = call.receiveMultipart()
        var imported = 0
        var errors = mutableListOf<String>()

        multipart.forEachPart { part ->
            if (part is PartData.FileItem) {
                try {
                    part.streamProvider().use { input ->
                        val storage = Storage.getInstance(context)
                        val result =
                                SaveBackupManager.restore(
                                        input,
                                        targetResolver = { hackId, fileName ->
                                            // Route sram/state files to the correct storage path.
                                            when {
                                                fileName.startsWith("sram_") -> storage.sram(hackId)
                                                fileName.startsWith("state_") ->
                                                        storage.state(hackId)
                                                else ->
                                                        File(
                                                                storage.storagePath,
                                                                "${hackId}_$fileName"
                                                        )
                                            }
                                        }
                                )
                        imported = result.files
                        errors = result.errors.toMutableList()
                    }
                } catch (e: Exception) {
                    errors.add("Failed to process backup: ${e.message}")
                }
            }
            part.dispose()
        }

        if (errors.isEmpty()) {
            call.respond(
                    mapOf(
                            "success" to true,
                            "imported" to imported,
                            "message" to "Backup restored successfully"
                    )
            )
        } else {
            call.respond(
                    mapOf("success" to (imported > 0), "imported" to imported, "errors" to errors)
            )
        }
    }
}

@Serializable
internal data class BackupImportResult(
        val success: Boolean,
        val imported: Int,
        val errors: List<String> = emptyList()
)
