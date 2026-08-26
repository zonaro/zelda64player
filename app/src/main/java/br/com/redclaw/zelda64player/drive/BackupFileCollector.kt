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

package br.com.redclaw.zelda64player.drive

import java.io.File

/**
 * Categories of data that can be backed up to Google Drive.
 */
enum class BackupCategory {
    /** SRAM + save-states, keyed by hack id. */
    SAVES,
    /** Screenshot PNGs from the gallery directory. */
    IMAGES,
    /** Screen-recording MP4s from the gallery directory. */
    VIDEOS
}

/**
 * A single file scheduled for upload, together with the remote path (relative to
 * the app backup folder on Drive) it should be stored under.
 *
 * @param category the backup category the file belongs to.
 * @param localFile the on-device file to upload.
 * @param remotePath forward-slash path under the app folder, e.g.
 *   `saves/<hackId>/sram_<hackId>`.
 */
data class BackupItem(
    val category: BackupCategory,
    val localFile: File,
    val remotePath: String
)

/**
 * Pure (Android-free) selection of the files that should be uploaded to Drive.
 *
 * Extracted from the Activity / Worker so the incremental-selection logic is
 * unit-testable on the JVM with temporary folders. The caller supplies the
 * concrete directories and the list of installed hack ids; this function
 * performs no network or Android calls.
 *
 * @param saveDir directory containing per-hack `sram_<hackId>` / `state_<hackId>`
 *   files (typically [br.com.redclaw.zelda64player.repositories.Storage.storagePath]).
 * @param galleryDir directory containing `screenshot_*` / `recording_*` captures
 *   (typically [br.com.redclaw.zelda64player.repositories.Storage.galleryDir]).
 * @param hackIds installed hack ids whose save files should be considered.
 * @param categories enabled categories; only files in these are collected.
 * @param sinceMillis only files modified strictly after this epoch time are
 *   included (0 includes every matching file). Drives incremental backups.
 */
fun collectBackupItems(
    saveDir: File,
    galleryDir: File,
    hackIds: List<String>,
    categories: Set<BackupCategory>,
    sinceMillis: Long
): List<BackupItem> {
    val items = mutableListOf<BackupItem>()

    if (BackupCategory.SAVES in categories) {
        for (hackId in hackIds) {
            for (name in listOf("sram_$hackId", "state_$hackId")) {
                val file = File(saveDir, name)
                if (file.isFile && file.lastModified() > sinceMillis) {
                    items.add(BackupItem(BackupCategory.SAVES, file, "saves/$hackId/$name"))
                }
            }
        }
    }

    if (BackupCategory.IMAGES in categories) {
        galleryDir.listFiles { f ->
            f.isFile && f.name.startsWith("screenshot_") && f.name.endsWith(".png")
        }?.forEach { file ->
            if (file.lastModified() > sinceMillis) {
                items.add(BackupItem(BackupCategory.IMAGES, file, "images/${file.name}"))
            }
        }
    }

    if (BackupCategory.VIDEOS in categories) {
        galleryDir.listFiles { f ->
            f.isFile && f.name.startsWith("recording_") && f.name.endsWith(".mp4")
        }?.forEach { file ->
            if (file.lastModified() > sinceMillis) {
                items.add(BackupItem(BackupCategory.VIDEOS, file, "videos/${file.name}"))
            }
        }
    }

    return items
}
