/*
 * Zelda 64 Player - native Android N64 emulator frontend for Zelda ROM hacks.
 * Copyright (C) 2026 RedClaw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package br.com.redclaw.zelda64player.gallery

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import br.com.redclaw.zelda64player.BuildConfig
import br.com.redclaw.zelda64player.repositories.Storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Reads and mutates the gallery directory.
 *
 * Screenshots are named `screenshot_<hackId>_<timestamp>_<overlay|clean>.png`
 * and recordings `recording_<hackId>_<timestamp>.mp4`. [hackId] may itself
 * contain underscores (e.g. `vanilla_abc123`), so the parser never splits the
 * whole name on `_`; instead it strips the known prefix/suffix and treats the
 * trailing one or two segments as the timestamp (+ overlay flag), joining the
 * remainder back into the hack id. This keeps parsing robust to arbitrary ids.
 *
 * All file IO runs on [Dispatchers.IO] so callers can `suspend` safely.
 */
class GalleryRepository(context: Context) {

    private val storage = Storage.getInstance(context)
    private val appContext = context.applicationContext

    /** Scan [Storage.galleryDir] and parse every matching capture file. */
    suspend fun list(): List<GalleryItem> = withContext(Dispatchers.IO) {
        val dir = storage.galleryDir()
        val files = dir.listFiles { f -> f.isFile && isGalleryFile(f.name) } ?: return@withContext emptyList()
        files.mapNotNull { parse(it) }.sortedByDescending { it.timestamp }
    }

    /** Delete a single item. Returns true when the file no longer exists. */
    fun delete(item: GalleryItem): Boolean = runCatching { item.path.delete() }.getOrDefault(false)

    /** Content URI for sharing [item] via [FileProvider] (read-only grant). */
    fun shareUri(item: GalleryItem): Uri =
        FileProvider.getUriForFile(
            appContext,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            item.path
        )

    private fun isGalleryFile(name: String): Boolean =
        (name.startsWith("screenshot_") && name.endsWith(".png")) ||
            (name.startsWith("recording_") && name.endsWith(".mp4"))

    private fun parse(file: File): GalleryItem? = runCatching {
        val name = file.name
        when {
            name.startsWith("screenshot_") && name.endsWith(".png") -> {
                val body = name.removePrefix("screenshot_").removeSuffix(".png")
                val lastUnder = body.lastIndexOf('_')
                val overlayToken = body.substring(lastUnder + 1) // "overlay" | "clean"
                val rest = body.substring(0, lastUnder)
                val tsUnder = rest.lastIndexOf('_')
                val timestamp = rest.substring(tsUnder + 1).toLongOrNull() ?: 0L
                val hackId = rest.substring(0, tsUnder).takeIf { it.isNotEmpty() }
                GalleryItem(
                    type = MediaType.IMAGE,
                    path = file,
                    hackId = hackId,
                    timestamp = timestamp,
                    withOverlay = overlayToken == "overlay"
                )
            }
            name.startsWith("recording_") && name.endsWith(".mp4") -> {
                val body = name.removePrefix("recording_").removeSuffix(".mp4")
                val tsUnder = body.lastIndexOf('_')
                val timestamp = body.substring(tsUnder + 1).toLongOrNull() ?: 0L
                val hackId = body.substring(0, tsUnder).takeIf { it.isNotEmpty() }
                GalleryItem(
                    type = MediaType.VIDEO,
                    path = file,
                    hackId = hackId,
                    timestamp = timestamp,
                    withOverlay = false
                )
            }
            else -> null
        }
    }.getOrNull()
}
