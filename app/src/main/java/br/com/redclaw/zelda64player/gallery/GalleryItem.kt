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

import java.io.File

/**
 * Media kind stored in the gallery.
 *
 * [IMAGE] screenshots are PNGs; [VIDEO] recordings are MP4s produced by the
 * screen-capture / recording feature.
 */
enum class MediaType {
    IMAGE,
    VIDEO
}

/**
 * A single captured item (screenshot or screen recording) in the gallery.
 *
 * Parsed from a file name written by
 * [br.com.redclaw.zelda64player.repositories.Storage] (see
 * [GalleryRepository] for the naming contract). New screenshots are always
 * direct emulator-framebuffer captures and therefore report `false` for
 * [withOverlay]. The field remains only to label legacy overlay captures.
 *
 * @param type image or video.
 * @param path absolute file in [br.com.redclaw.zelda64player.repositories.Storage.galleryDir].
 * @param hackId the game the capture belongs to (may contain underscores).
 * @param timestamp epoch millis when the capture was taken.
 * @param withOverlay true only for a legacy "with controls" screenshot.
 */
data class GalleryItem(
    val type: MediaType,
    val path: File,
    val hackId: String?,
    val timestamp: Long,
    val withOverlay: Boolean
)
