/*
 * Zelda 64 Player - native Android N64 emulator frontend for Zelda ROM hacks.
 * Copyright (C) 2026 RedClaw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package br.com.redclaw.zelda64player.tracker.assets.graphics

/** N64 texture formats used for item icons. */
enum class N64TextureFormat {
    /** 16-bit RGBA 5551: 5R + 5G + 5B + 1A, 2 bytes/pixel. */
    RGBA16,

    /** 8-bit color-indexed with 256-entry RGBA16 TLUT (palette). 1 byte/pixel + 512-byte TLUT. */
    CI8,

    /** 8-bit intensity+alpha: 4I + 4A. */
    IA8,

    /** 8-bit intensity (grayscale). */
    I8,
}
