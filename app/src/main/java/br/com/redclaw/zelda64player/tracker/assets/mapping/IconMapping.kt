/*
 * Zelda 64 Player - native Android N64 emulator frontend for Zelda ROM hacks.
 * Copyright (C) 2026 RedClaw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package br.com.redclaw.zelda64player.tracker.assets.mapping

import br.com.redclaw.zelda64player.tracker.assets.graphics.N64TextureFormat

/**
 * Maps a tracker [itemId] to its location inside the decompressed icon file.
 *
 * @param itemId tracker item id (e.g. "kokiri_sword")
 * @param dmaFileIndex DMA index of the icon archive (e.g. icon_item_static)
 * @param offset byte offset inside the decompressed file
 * @param width icon width in pixels
 * @param height icon height in pixels
 * @param format N64 texture format
 * @param tlutOffset byte offset of the TLUT inside the same file (CI8 only)
 */
data class IconMapping(
    val itemId: String,
    val dmaFileIndex: Int,
    val offset: Int,
    val width: Int = 32,
    val height: Int = 32,
    val format: N64TextureFormat = N64TextureFormat.RGBA16,
    val tlutOffset: Int? = null,
)
