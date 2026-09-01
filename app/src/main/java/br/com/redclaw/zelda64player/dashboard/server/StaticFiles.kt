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
import android.util.Log
import br.com.redclaw.zelda64player.views.InstalledLibrary
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Static file serving routes for the dashboard SPA and EmulatorJS bundle — Switch UI edition.
 *
 * Assets are served from two sources:
 * 1. Android assets (bundled in APK): dashboard SPA, EmulatorJS, i18n files
 * 2. Device storage: cover images (user covers + cached remote covers proxied from the phone)
 *
 * Cover strategy (the key fix for the dashboard):
 * - User-selected covers live at `filesDir/user_hack_covers/{hackId}.cover` (canonical) or
 *   legacy `filesDir/user_hack_covers/{hackId}_*`.
 * - Remote catalog covers (PICKS/HylianModding/Libretro CDN) are proxied through the phone:
 *   the browser never fetches remote URLs directly. On first request the server downloads the
 *   remote image (via HttpURLConnection), caches it under `filesDir/cover_cache/{hackId}`,
 *   and serves it with long-lived cache headers. Subsequent requests hit the cache.
 * - Vanilla base-ROM covers (Libretro thumbnails CDN) are handled the same way.
 * - Fallback is the bundled `dashboard/covers/default.svg`.
 *
 * The dashboard SPA is served at "/" and any SPA client-side route falls back to index.html.
 */
internal fun Route.staticFiles() {

    val context = application.attributes[DashboardManager.CONTEXT_KEY]

    get("/") { serveAsset(context, call, "dashboard/index.html", ContentType.Text.Html) }

    get("/static/{path...}") {
        val path = call.parameters.getAll("path")?.joinToString("/") ?: return@get
        serveAsset(context, call, "dashboard/$path", guessContentType(path))
    }

    get("/emulatorjs/{path...}") {
        val path = call.parameters.getAll("path")?.joinToString("/") ?: return@get
        serveAsset(context, call, "dashboard/emulatorjs/$path", guessContentType(path))
    }

    get("/i18n/{path...}") {
        val path = call.parameters.getAll("path")?.joinToString("/") ?: return@get
        serveAsset(context, call, "dashboard/i18n/$path", ContentType.Application.Json)
    }

    // Serve cover images — proxied + cached remote covers, plus local user covers.
    get("/covers/{hackId}") {
        val hackId = call.parameters["hackId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val coverFile = resolveCoverFile(context, hackId)
        if (coverFile != null && coverFile.exists()) {
            val ct = guessContentType(coverFile.name).let {
                if (it == ContentType.Application.OctetStream) ContentType.Image.JPEG else it
            }
            val finalCt = detectContentType(coverFile) ?: ct
            call.response.header(HttpHeaders.CacheControl, "public, max-age=86400")
            call.response.header(HttpHeaders.ContentType, finalCt.toString())
            call.respondFile(coverFile)
        } else {
            serveAsset(context, call, "dashboard/covers/default.svg", ContentType.Image.SVG)
        }
    }

    // Also serve covers under /api/covers for API consistency
    get("/api/covers/{hackId}") {
        val hackId = call.parameters["hackId"] ?: return@get call.respond(HttpStatusCode.BadRequest)
        val coverFile = resolveCoverFile(context, hackId)
        if (coverFile != null && coverFile.exists()) {
            val ct = guessContentType(coverFile.name).let {
                if (it == ContentType.Application.OctetStream) ContentType.Image.JPEG else it
            }
            val finalCt = detectContentType(coverFile) ?: ct
            call.response.header(HttpHeaders.CacheControl, "public, max-age=86400")
            call.response.header(HttpHeaders.ContentType, finalCt.toString())
            call.respondFile(coverFile)
        } else {
            serveAsset(context, call, "dashboard/covers/default.svg", ContentType.Image.SVG)
        }
    }

    get("/{path...}") {
        val path = call.parameters.getAll("path")?.joinToString("/") ?: ""
        if (path.startsWith("api") || path.startsWith("ws") || path.startsWith("covers")) {
            call.respond(HttpStatusCode.NotFound)
            return@get
        }
        serveAsset(context, call, "dashboard/index.html", ContentType.Text.Html)
    }
}

/**
 * Resolve the best cover file for [hackId]:
 * 1. Canonical user cover: filesDir/user_hack_covers/{hackId}.cover
 * 2. Legacy user cover: filesDir/user_hack_covers/{hackId}_*
 * 3. Cached remote cover: filesDir/cover_cache/{hackId}
 * 4. Remote catalog cover (download + cache on demand)
 */
private suspend fun resolveCoverFile(context: Context, hackId: String): File? = withContext(Dispatchers.IO) {
    // 1. Canonical user cover
    val canonical = File(context.filesDir, "user_hack_covers/$hackId.cover")
    if (canonical.exists() && canonical.length() > 0) return@withContext canonical

    // 2. Legacy user cover
    val coverDir = File(context.filesDir, "user_hack_covers")
    val legacy = coverDir.listFiles()?.firstOrNull { it.name.startsWith("${hackId}_") && it.isFile && it.length() > 0 }
    if (legacy != null) return@withContext legacy

    // 3. Cached remote cover
    val cacheDir = File(context.filesDir, "cover_cache").apply { mkdirs() }
    val cached = File(cacheDir, hackId)
    val cachedJpg = File(cacheDir, "$hackId.jpg")
    val cachedPng = File(cacheDir, "$hackId.png")
    val cachedWebp = File(cacheDir, "$hackId.webp")
    listOf(cached, cachedJpg, cachedPng, cachedWebp).firstOrNull { it.exists() && it.length() > 0 }?.let { return@withContext it }

    // 4. Try to fetch remote cover and cache it
    val remoteUrl = findRemoteCoverUrl(context, hackId) ?: return@withContext null
    // Handle file:// URIs (user hacks with local cover stored as file URI)
    if (remoteUrl.startsWith("file://")) {
        try {
            val path = remoteUrl.removePrefix("file://")
            val f = File(path)
            if (f.exists() && f.length() > 0) return@withContext f
        } catch (_: Exception) {}
        return@withContext null
    }
    if (!remoteUrl.startsWith("http://") && !remoteUrl.startsWith("https://")) return@withContext null

    try {
        val url = URL(remoteUrl)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 10000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Zelda64Player/1.0")
            setRequestProperty("Accept", "image/*")
        }
        conn.connect()
        if (conn.responseCode !in 200..299) {
            Log.d(TAG, "Remote cover fetch failed for $hackId: HTTP ${conn.responseCode} $remoteUrl")
            return@withContext null
        }
        val contentType = conn.contentType ?: ""
        val ext = when {
            contentType.contains("png") -> ".png"
            contentType.contains("webp") -> ".webp"
            contentType.contains("svg") -> ".svg"
            contentType.contains("jpeg") || contentType.contains("jpg") -> ".jpg"
            remoteUrl.endsWith(".png", ignoreCase = true) -> ".png"
            remoteUrl.endsWith(".webp", ignoreCase = true) -> ".webp"
            remoteUrl.endsWith(".svg", ignoreCase = true) -> ".svg"
            else -> ""
        }
        val target = if (ext.isNotEmpty()) File(cacheDir, "$hackId$ext") else File(cacheDir, hackId)
        conn.inputStream.use { input ->
            target.outputStream().use { output ->
                val buffer = ByteArray(32 * 1024)
                var total = 0L
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    total += n
                    if (total > 10L * 1024 * 1024) throw IllegalStateException("Cover too large")
                    output.write(buffer, 0, n)
                }
            }
        }
        // Validate it's an image (at least check size)
        if (target.length() == 0L) {
            target.delete()
            return@withContext null
        }
        // For non-SVG, validate it's decodable
        if (!contentType.contains("svg") && !remoteUrl.endsWith(".svg", ignoreCase = true)) {
            val opts = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(target.absolutePath, opts)
            if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                // Might be webp/svg that BitmapFactory can't decode — keep it anyway if size > 0
                // Only delete if it's clearly not an image (very small)
                if (target.length() < 100) {
                    target.delete()
                    return@withContext null
                }
            }
        }
        return@withContext target
    } catch (e: Exception) {
        Log.d(TAG, "Failed to fetch remote cover for $hackId: $remoteUrl", e)
        return@withContext null
    }
}

/** Find the remote cover URL for [hackId] via InstalledLibrary (vanilla + catalog). */
private fun findRemoteCoverUrl(context: Context, hackId: String): String? {
    return try {
        val entry = InstalledLibrary.entries(context).firstOrNull { it.id == hackId }
        entry?.coverUrl?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }
}

/** Detect content type from file header when extension is missing. */
private fun detectContentType(file: File): ContentType? {
    return try {
        val header = ByteArray(12)
        file.inputStream().use { it.read(header) }
        when {
            header[0] == 0x89.toByte() && header[1] == 0x50.toByte() -> ContentType.Image.PNG
            header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() -> ContentType.Image.JPEG
            header[0] == 0x47.toByte() && header[1] == 0x49.toByte() -> ContentType.Image.GIF
            header[0] == 0x52.toByte() && header[1] == 0x49.toByte() -> ContentType.parse("image/webp")
            String(header).contains("<svg", ignoreCase = true) -> ContentType.Image.SVG
            else -> null
        }
    } catch (_: Exception) { null }
}

private suspend fun serveAsset(
    context: Context,
    call: io.ktor.server.application.ApplicationCall,
    assetPath: String,
    contentType: ContentType
) {
    try {
        val assetManager = context.assets
        val files = assetManager.list(assetPath)
        if (files != null && files.isNotEmpty()) {
            call.respond(mapOf("directory" to assetPath, "contents" to files.toList()))
            return
        }
        val inputStream = assetManager.open(assetPath)
        val bytes = inputStream.readBytes()
        inputStream.close()
        call.response.header(HttpHeaders.CacheControl, "public, max-age=3600")
        call.respondBytes(bytes, contentType)
    } catch (e: Exception) {
        Log.d(TAG, "Asset not found: $assetPath")
        call.respond(HttpStatusCode.NotFound, mapOf("error" to "Asset not found: $assetPath"))
    }
}

private fun guessContentType(path: String): ContentType {
    return when {
        path.endsWith(".js", ignoreCase = true) -> ContentType.Application.JavaScript
        path.endsWith(".css", ignoreCase = true) -> ContentType.Text.CSS
        path.endsWith(".html", ignoreCase = true) -> ContentType.Text.Html
        path.endsWith(".json", ignoreCase = true) -> ContentType.Application.Json
        path.endsWith(".svg", ignoreCase = true) -> ContentType.Image.SVG
        path.endsWith(".png", ignoreCase = true) -> ContentType.Image.PNG
        path.endsWith(".jpg", ignoreCase = true) || path.endsWith(".jpeg", ignoreCase = true) -> ContentType.Image.JPEG
        path.endsWith(".webp", ignoreCase = true) -> ContentType.parse("image/webp")
        path.endsWith(".woff", ignoreCase = true) -> ContentType("application", "font-woff")
        path.endsWith(".woff2", ignoreCase = true) -> ContentType("application", "font-woff2")
        path.endsWith(".ttf", ignoreCase = true) -> ContentType("application", "font-ttf")
        path.endsWith(".wasm", ignoreCase = true) -> ContentType("application", "wasm")
        path.endsWith(".mp3", ignoreCase = true) -> ContentType.Audio.MPEG
        path.endsWith(".wav", ignoreCase = true) -> ContentType("audio", "wav")
        else -> ContentType.Application.OctetStream
    }
}

private const val TAG = "StaticFiles"
