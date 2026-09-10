/*
 * Zelda 64 Player - native Android N64 emulator frontend for Zelda ROM hacks.
 * Copyright (C) 2026 RedClaw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package br.com.redclaw.zelda64player.tracker.assets.dma

/**
 * One entry of the N64 DMA (virtual filesystem) table.
 *
 * Each entry is 16 bytes: VROM start/end + ROM start/end (all big-endian u32). See
 * `plano-extracao-assets-tracker.md` §3.2 and `patcher/n64/` for context.
 */
data class DmaEntry(
        val index: Int,
        val vromStart: Int,
        val vromEnd: Int,
        val romStart: Int,
        val romEnd: Int,
) {
    /** True when the file is Yaz0-compressed on ROM (romEnd != 0). */
    val isCompressed: Boolean
        get() = romEnd != 0

    /** True when the entry actually exists (romStart != -1 / 0xFFFFFFFF). */
    val exists: Boolean
        get() = romStart != -1 && vromStart != 0

    /** Size on ROM (compressed if [isCompressed], otherwise same as [uncompressedSize]). */
    val compressedSize: Int
        get() = if (isCompressed) romEnd - romStart else vromEnd - vromStart

    /** Decompressed size (VROM range). */
    val uncompressedSize: Int
        get() = vromEnd - vromStart
}
