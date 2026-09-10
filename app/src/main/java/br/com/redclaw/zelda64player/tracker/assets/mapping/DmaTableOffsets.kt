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

/**
 * Known DMA table offsets per ROM version.
 *
 * Offsets are for the normalized big-endian `.z64` layout. Source: zeldaret/oot & zeldaret/mm
 * `assets/xml/` + community docs. TODO: validate PAL offsets and OoT 1.1 via Calamari/Puffy before
 * freezing.
 */
object DmaTableOffsets {

    /**
     * Resolve DMA table offset from [gameCode] + [versionByte]. Returns null if the ROM version is
     * not recognized for extraction.
     */
    fun forRom(gameCode: String, versionByte: Int): Long? =
            when (gameCode) {
                "CZLE" ->
                        when (versionByte) {
                            0 -> 0x00007430L // OoT NTSC 1.0 USA
                            1 -> 0x00007430L // OoT NTSC 1.1 (same offset, to confirm)
                            else -> null
                        }
                "NZSE" ->
                        when (versionByte) {
                            0 -> 0x0001A500L // MM NTSC 1.0 USA
                            else -> null
                        }
                // PAL variants — offsets differ, extraction falls back to embedded icons
                "CZLP",
                "CZLE_PAL" -> 0x00007950L
                "NZSP" -> null // TODO: confirm MM PAL offset
                else -> null
            }

    /** DMA file index of `icon_item_static` (OoT) / `icon_item_24_static` (MM). */
    const val OOT_ICON_FILE_INDEX = 2
    const val MM_ICON_FILE_INDEX = 2
}
