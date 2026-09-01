/*
 *     Copyright (C) 2026 RedClaw
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package br.com.redclaw.zelda64player.dashboard.server

import br.com.redclaw.zelda64player.repositories.GameRomResolver
import br.com.redclaw.zelda64player.repositories.Storage
import br.com.redclaw.zelda64player.views.InstalledLibrary
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File
import android.util.Base64
import kotlinx.serialization.Serializable

/**
 * REST API routes for collection management — Switch UI edition.
 *
 * The collection is now the single source of truth for the dashboard's unified "Coleção / Jogar"
 * grid. It reuses [InstalledLibrary.entries] (vanilla base ROMs + store hacks merged via
 * [br.com.redclaw.zelda64player.views.CompositeLibrarySource]) so the dashboard shows exactly the
 * same tiles as the native Library, with the same titles and cover URLs. Covers themselves are
 * served through [StaticFiles] (`/covers/{hackId}`) which proxies the remote artwork downloaded on
 * the phone and caches it locally — the browser never fetches remote URLs directly.
 */
internal fun Route.collectionRoutes() {

    val context = application.attributes[DashboardManager.CONTEXT_KEY]

    /** GET /api/collection — List all installed games with metadata for the Switch grid. */
    get("/collection") {
        val entries = InstalledLibrary.entries(context)
        val storage = Storage.getInstance(context)
        val games =
                entries.map { entry ->
                    val romFile = GameRomResolver.resolveRomFile(context, entry.id)
                    val sramFile = storage.sram(entry.romId)
                    val stateFile = storage.state(entry.romId)
                    CollectionGame(
                            hackId = entry.id,
                            romId = entry.romId,
                            name = entry.title,
                            coverUrl = "/covers/${entry.id}",
                            hasRom = romFile?.exists() == true,
                            hasSram = sramFile.exists(),
                            hasState = stateFile.exists(),
                            isVanilla = entry.isVanilla,
                            canPlay = romFile?.exists() == true,
                            sramSize = if (sramFile.exists()) sramFile.length() else 0L,
                            stateSize = if (stateFile.exists()) stateFile.length() else 0L,
                            lastModified =
                                    maxOf(
                                            if (sramFile.exists()) sramFile.lastModified() else 0L,
                                            if (stateFile.exists()) stateFile.lastModified()
                                            else 0L,
                                            romFile?.lastModified() ?: 0L
                                    )
                    )
                }
        call.respond(CollectionResponse(games = games, total = games.size))
    }

    /** GET /api/collection/{hackId} — Get details of a specific game. */
    get("/collection/{hackId}") {
        val hackId =
                call.parameters["hackId"]
                        ?: return@get call.respond(
                                HttpStatusCode.BadRequest,
                                mapOf("error" to "Missing hackId")
                        )

        val entry = InstalledLibrary.entries(context).firstOrNull { it.id == hackId }
        val storage = Storage.getInstance(context)
        val romFile = GameRomResolver.resolveRomFile(context, hackId)
        val sramFile = storage.sram(entry?.romId ?: hackId)
        val stateFile = storage.state(entry?.romId ?: hackId)

        if (romFile?.exists() != true && !sramFile.exists() && !stateFile.exists() && entry == null
        ) {
            return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "Game not found"))
        }

        call.respond(
                CollectionGame(
                        hackId = hackId,
                        romId = entry?.romId ?: hackId,
                        name = entry?.title
                                        ?: hackId.replace("_", " ").replaceFirstChar {
                                            it.uppercase()
                                        },
                        coverUrl = "/covers/$hackId",
                        hasRom = romFile?.exists() == true,
                        hasSram = sramFile.exists(),
                        hasState = stateFile.exists(),
                        isVanilla = entry?.isVanilla ?: false,
                        canPlay = romFile?.exists() == true,
                        sramSize = if (sramFile.exists()) sramFile.length() else 0L,
                        stateSize = if (stateFile.exists()) stateFile.length() else 0L,
                        lastModified =
                                maxOf(
                                        if (sramFile.exists()) sramFile.lastModified() else 0L,
                                        if (stateFile.exists()) stateFile.lastModified() else 0L,
                                        romFile?.lastModified() ?: 0L
                                )
                )
        )
    }

    /** GET /api/collection/{hackId}/rom — Download the patched ROM file (or vanilla base ROM). */
    get("/collection/{hackId}/rom") {
        val hackId =
                call.parameters["hackId"]
                        ?: return@get call.respond(
                                HttpStatusCode.BadRequest,
                                mapOf("error" to "Missing hackId")
                        )

        val romFile = GameRomResolver.resolveRomFile(context, hackId)
        if (romFile == null || !romFile.exists()) {
            return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "ROM not installed"))
        }

        call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(
                                ContentDisposition.Parameters.FileName,
                                "$hackId.z64"
                        )
                        .toString()
        )
        call.respondFile(romFile)
    }

    /**
     * GET /api/collection/{hackId}/play/rom — ROM stream for EmulatorJS.
     *
     * Unlike the download route this deliberately has no attachment header: EmulatorJS fetches
     * this URL directly and must receive the patched hack (or the selected vanilla ROM) as a
     * playable byte stream.
     */
    get("/collection/{hackId}/play/rom") {
        val hackId = call.parameters["hackId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing hackId"))
        val romFile = GameRomResolver.resolveRomFile(context, hackId)
                ?: return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "ROM not installed"))
        if (!romFile.exists()) {
            return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "ROM not installed"))
        }
        call.response.header(HttpHeaders.CacheControl, "no-store")
        call.respondFile(romFile)
    }

    /** Current SRAM snapshot for a browser EmulatorJS session. */
    get("/collection/{hackId}/play/sram") {
        val hackId = call.parameters["hackId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing hackId"))
        val entry = InstalledLibrary.entries(context).firstOrNull { it.id == hackId }
        val sram = Storage.getInstance(context).sram(entry?.romId ?: hackId)
        call.respond(
                EmulatorSaveResponse(
                        data = if (sram.exists()) Base64.encodeToString(sram.readBytes(), Base64.NO_WRAP) else null,
                        modifiedAt = if (sram.exists()) sram.lastModified() else 0L
                )
        )
    }

    /** Raw SRAM file mounted by EmulatorJS before the core starts. */
    get("/collection/{hackId}/play/sram-file") {
        val hackId = call.parameters["hackId"]
                ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing hackId"))
        val entry = InstalledLibrary.entries(context).firstOrNull { it.id == hackId }
        val sram = Storage.getInstance(context).sram(entry?.romId ?: hackId)
        if (!sram.exists()) return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "No SRAM save"))
        call.response.header(HttpHeaders.CacheControl, "no-store")
        call.respondFile(sram)
    }

    /** Persist an SRAM snapshot uploaded by the browser EmulatorJS session. */
    post("/collection/{hackId}/play/sram") {
        val hackId = call.parameters["hackId"]
                ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing hackId"))
        val snapshot = call.receive<EmulatorSaveRequest>()
        val bytes = try {
            Base64.decode(snapshot.data, Base64.DEFAULT)
        } catch (_: IllegalArgumentException) {
            return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Invalid SRAM data"))
        }
        // SRAM is small (normally 32 KiB). A hard limit prevents the dashboard from becoming an
        // unauthenticated arbitrary-storage endpoint when no password is configured.
        if (bytes.size > MAX_SRAM_BYTES) {
            return@post call.respond(HttpStatusCode.PayloadTooLarge, mapOf("error" to "SRAM is too large"))
        }
        val entry = InstalledLibrary.entries(context).firstOrNull { it.id == hackId }
        val target = Storage.getInstance(context).sram(entry?.romId ?: hackId)
        target.parentFile?.mkdirs()
        target.outputStream().use { it.write(bytes) }
        DashboardManager.notifySramChanged(entry?.romId ?: hackId, bytes)
        call.respond(mapOf("success" to true, "modifiedAt" to target.lastModified()))
    }

    /** GET /api/collection/{hackId}/sram — Download the SRAM save file. */
    get("/collection/{hackId}/sram") {
        val hackId =
                call.parameters["hackId"]
                        ?: return@get call.respond(
                                HttpStatusCode.BadRequest,
                                mapOf("error" to "Missing hackId")
                        )

        val entry = InstalledLibrary.entries(context).firstOrNull { it.id == hackId }
        val sramFile = Storage.getInstance(context).sram(entry?.romId ?: hackId)
        if (!sramFile.exists()) {
            return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "No SRAM save"))
        }

        call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(
                                ContentDisposition.Parameters.FileName,
                                "sram_$hackId.bin"
                        )
                        .toString()
        )
        call.respondFile(sramFile)
    }

    /** POST /api/collection/{hackId}/sram — Upload/restore an SRAM save file. */
    post("/collection/{hackId}/sram") {
        val hackId =
                call.parameters["hackId"]
                        ?: return@post call.respond(
                                HttpStatusCode.BadRequest,
                                mapOf("error" to "Missing hackId")
                        )

        val entry = InstalledLibrary.entries(context).firstOrNull { it.id == hackId }
        val targetId = entry?.romId ?: hackId
        val multipart = call.receiveMultipart()
        var saved = false
        multipart.forEachPart { part ->
            if (part is PartData.FileItem) {
                val sramFile = Storage.getInstance(context).sram(targetId)
                part.streamProvider().use { input ->
                    sramFile.outputStream().use { output -> input.copyTo(output) }
                }
                saved = true
            }
            part.dispose()
        }

        if (saved) call.respond(mapOf("success" to true, "message" to "SRAM restored"))
        else call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No file uploaded"))
    }

    /** GET /api/collection/{hackId}/state — Download the save state file. */
    get("/collection/{hackId}/state") {
        val hackId =
                call.parameters["hackId"]
                        ?: return@get call.respond(
                                HttpStatusCode.BadRequest,
                                mapOf("error" to "Missing hackId")
                        )

        val entry = InstalledLibrary.entries(context).firstOrNull { it.id == hackId }
        val stateFile = Storage.getInstance(context).state(entry?.romId ?: hackId)
        if (!stateFile.exists()) {
            return@get call.respond(HttpStatusCode.NotFound, mapOf("error" to "No save state"))
        }

        call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(
                                ContentDisposition.Parameters.FileName,
                                "state_$hackId.state"
                        )
                        .toString()
        )
        call.respondFile(stateFile)
    }

    /** POST /api/collection/{hackId}/state — Upload/restore a save state file. */
    post("/collection/{hackId}/state") {
        val hackId =
                call.parameters["hackId"]
                        ?: return@post call.respond(
                                HttpStatusCode.BadRequest,
                                mapOf("error" to "Missing hackId")
                        )

        val entry = InstalledLibrary.entries(context).firstOrNull { it.id == hackId }
        val targetId = entry?.romId ?: hackId
        val multipart = call.receiveMultipart()
        var saved = false
        multipart.forEachPart { part ->
            if (part is PartData.FileItem) {
                val stateFile = Storage.getInstance(context).state(targetId)
                part.streamProvider().use { input ->
                    stateFile.outputStream().use { output -> input.copyTo(output) }
                }
                saved = true
            }
            part.dispose()
        }

        if (saved) call.respond(mapOf("success" to true, "message" to "Save state restored"))
        else call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No file uploaded"))
    }

    /** POST /api/collection/{hackId}/cover — Upload a custom cover image (validated, 10 MB max). */
    post("/collection/{hackId}/cover") {
        val hackId =
                call.parameters["hackId"]
                        ?: return@post call.respond(
                                HttpStatusCode.BadRequest,
                                mapOf("error" to "Missing hackId")
                        )

        val multipart = call.receiveMultipart()
        var saved = false
        var fileName = ""
        var coverError: String? = null
        multipart.forEachPart { part ->
            if (part is PartData.FileItem) {
                fileName = part.originalFileName ?: "cover.jpg"
                val coverDir = File(context.filesDir, "user_hack_covers")
                coverDir.mkdirs()
                val coverFile = File(coverDir, "$hackId.cover")
                val tempFile = File(coverDir, "$hackId.cover.tmp")
                try {
                    part.streamProvider().use { input ->
                        tempFile.outputStream().use { output ->
                            val buffer = ByteArray(32 * 1024)
                            var copied = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                copied += count
                                require(copied <= 10L * 1024L * 1024L) {
                                    "Cover image is too large"
                                }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    val opts =
                            android.graphics.BitmapFactory.Options().apply {
                                inJustDecodeBounds = true
                            }
                    android.graphics.BitmapFactory.decodeFile(tempFile.absolutePath, opts)
                    require(opts.outWidth > 0 && opts.outHeight > 0) { "Invalid cover image" }
                    if (!tempFile.renameTo(coverFile)) {
                        tempFile.inputStream().use { src ->
                            coverFile.outputStream().use(src::copyTo)
                        }
                        tempFile.delete()
                    }
                    try {
                        val userHacks =
                                br.com.redclaw.zelda64player.data.local.AppRepositories
                                        .userHacksRepository(context)
                        userHacks.updateCover(
                                hackId,
                                android.net.Uri.fromFile(coverFile).toString()
                        )
                    } catch (_: Exception) {}
                    File(context.filesDir, "cover_cache/$hackId").delete()
                    File(context.filesDir, "cover_cache/$hackId.jpg").delete()
                    saved = true
                } catch (e: Exception) {
                    tempFile.delete()
                    coverError = e.message ?: "Invalid cover"
                }
            }
            part.dispose()
        }

        if (coverError != null) {
            call.respond(HttpStatusCode.BadRequest, mapOf("error" to coverError))
        } else if (saved) call.respond(mapOf("success" to true, "fileName" to fileName))
        else call.respond(HttpStatusCode.BadRequest, mapOf("error" to "No file uploaded"))
    }
}

@Serializable
internal data class CollectionGame(
        val hackId: String,
        val romId: String = hackId,
        val name: String,
        val coverUrl: String = "/covers/$hackId",
        val hasRom: Boolean,
        val hasSram: Boolean,
        val hasState: Boolean,
        val isVanilla: Boolean = false,
        val canPlay: Boolean = hasRom,
        val sramSize: Long,
        val stateSize: Long,
        val lastModified: Long
)

@Serializable
internal data class CollectionResponse(val games: List<CollectionGame>, val total: Int)

@Serializable internal data class EmulatorSaveResponse(val data: String?, val modifiedAt: Long)

@Serializable internal data class EmulatorSaveRequest(val data: String)

private const val MAX_SRAM_BYTES = 2 * 1024 * 1024
