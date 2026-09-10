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
 * Maps OoT tracker item ids to their location inside `icon_item_static` (DMA 8).
 *
 * Each icon is 32x32 RGBA32 = 4096 bytes (0x1000). Offsets from zeldaret/oot
 * `assets/xml/textures/icon_item_static.xml` (Segment 8, Format rgba32). Medallions live in
 * `icon_item_dungeon_static` — not mapped here, will use fallback.
 */
object OotIconMap {

    private const val ICON = DmaTableOffsets.OOT_ICON_FILE_INDEX
    private const val QUEST = DmaTableOffsets.OOT_QUEST_ICON_FILE_INDEX

    val entries: List<IconMapping> =
            listOf(
                    IconMapping("deku_stick", ICON, 0x0000, format = N64TextureFormat.RGBA32),
                    IconMapping("deku_nut", ICON, 0x1000, format = N64TextureFormat.RGBA32),
                    IconMapping("bomb_bag", ICON, 0x4D000, format = N64TextureFormat.RGBA32),
                    IconMapping("bow", ICON, 0x3000, format = N64TextureFormat.RGBA32),
                    IconMapping("fire_arrows", ICON, 0x4000, format = N64TextureFormat.RGBA32),
                    IconMapping("din_fire", ICON, 0x5000, format = N64TextureFormat.RGBA32),
                    IconMapping("slingshot", ICON, 0x6000, format = N64TextureFormat.RGBA32),
                    IconMapping("ocarina", ICON, 0x7000, format = N64TextureFormat.RGBA32),
                    IconMapping("ocarina_2", ICON, 0x8000, format = N64TextureFormat.RGBA32),
                    IconMapping("bombchu", ICON, 0x9000, format = N64TextureFormat.RGBA32),
                    IconMapping(
                            "hookshot_longshot",
                            ICON,
                            0xA000,
                            format = N64TextureFormat.RGBA32
                    ),
                    IconMapping("ice_arrows", ICON, 0xC000, format = N64TextureFormat.RGBA32),
                    IconMapping("farore_wind", ICON, 0xD000, format = N64TextureFormat.RGBA32),
                    IconMapping("boomerang", ICON, 0xE000, format = N64TextureFormat.RGBA32),
                    IconMapping("lens_of_truth", ICON, 0xF000, format = N64TextureFormat.RGBA32),
                    IconMapping("megaton_hammer", ICON, 0x11000, format = N64TextureFormat.RGBA32),
                    IconMapping("light_arrows", ICON, 0x12000, format = N64TextureFormat.RGBA32),
                    IconMapping("nayru_love", ICON, 0x13000, format = N64TextureFormat.RGBA32),
                    IconMapping("kokiri_sword", ICON, 0x3B000, format = N64TextureFormat.RGBA32),
                    IconMapping("master_sword", ICON, 0x3C000, format = N64TextureFormat.RGBA32),
                    IconMapping("biggoron_sword", ICON, 0x3D000, format = N64TextureFormat.RGBA32),
                    IconMapping("deku_shield", ICON, 0x3E000, format = N64TextureFormat.RGBA32),
                    IconMapping("hylian_shield", ICON, 0x3F000, format = N64TextureFormat.RGBA32),
                    IconMapping("mirror_shield", ICON, 0x40000, format = N64TextureFormat.RGBA32),
                    IconMapping("iron_boots", ICON, 0x45000, format = N64TextureFormat.RGBA32),
                    IconMapping("hover_boots", ICON, 0x46000, format = N64TextureFormat.RGBA32),
                    IconMapping("strength", ICON, 0x50000, format = N64TextureFormat.RGBA32),
                    IconMapping("strength_2", ICON, 0x51000, format = N64TextureFormat.RGBA32),
                    IconMapping("strength_3", ICON, 0x52000, format = N64TextureFormat.RGBA32),
                    IconMapping(
                            "magic",
                            QUEST,
                            0xA200,
                            width = 24,
                            height = 24,
                            format = N64TextureFormat.RGBA32
                    ),
                    IconMapping(
                            "magic_2",
                            QUEST,
                            0xAB00,
                            width = 24,
                            height = 24,
                            format = N64TextureFormat.RGBA32
                    ),
                    IconMapping("scale", ICON, 0x53000, format = N64TextureFormat.RGBA32),
                    IconMapping("scale_2", ICON, 0x54000, format = N64TextureFormat.RGBA32),
                    IconMapping("kokiri_tunic", ICON, 0x41000, format = N64TextureFormat.RGBA32),
                    IconMapping("goron_tunic", ICON, 0x42000, format = N64TextureFormat.RGBA32),
                    IconMapping("zora_tunic", ICON, 0x43000, format = N64TextureFormat.RGBA32),
                    IconMapping("rupees", ICON, 0x56000, format = N64TextureFormat.RGBA32),
                    IconMapping(
                            "forest_medallion",
                            QUEST,
                            0x0000,
                            width = 24,
                            height = 24,
                            format = N64TextureFormat.RGBA32
                    ),
                    IconMapping(
                            "fire_medallion",
                            QUEST,
                            0x0900,
                            width = 24,
                            height = 24,
                            format = N64TextureFormat.RGBA32
                    ),
                    IconMapping(
                            "water_medallion",
                            QUEST,
                            0x1200,
                            width = 24,
                            height = 24,
                            format = N64TextureFormat.RGBA32
                    ),
                    IconMapping(
                            "spirit_medallion",
                            QUEST,
                            0x1B00,
                            width = 24,
                            height = 24,
                            format = N64TextureFormat.RGBA32
                    ),
                    IconMapping(
                            "shadow_medallion",
                            QUEST,
                            0x2400,
                            width = 24,
                            height = 24,
                            format = N64TextureFormat.RGBA32
                    ),
                    IconMapping(
                            "light_medallion",
                            QUEST,
                            0x2D00,
                            width = 24,
                            height = 24,
                            format = N64TextureFormat.RGBA32
                    ),
                    IconMapping(
                            "kokiri_emerald",
                            QUEST,
                            0x3600,
                            width = 24,
                            height = 24,
                            format = N64TextureFormat.RGBA32
                    ),
                    IconMapping(
                            "goron_ruby",
                            QUEST,
                            0x3F00,
                            width = 24,
                            height = 24,
                            format = N64TextureFormat.RGBA32
                    ),
                    IconMapping(
                            "zora_sapphire",
                            QUEST,
                            0x4800,
                            width = 24,
                            height = 24,
                            format = N64TextureFormat.RGBA32
                    ),
                    IconMapping("keaton_mask", ICON, 0x24000, format = N64TextureFormat.RGBA32),
                    IconMapping("skull_mask", ICON, 0x25000, format = N64TextureFormat.RGBA32),
                    IconMapping("spooky_mask", ICON, 0x26000, format = N64TextureFormat.RGBA32),
                    IconMapping("bunny_hood", ICON, 0x27000, format = N64TextureFormat.RGBA32),
                    IconMapping("goron_mask", ICON, 0x28000, format = N64TextureFormat.RGBA32),
                    IconMapping("zora_mask", ICON, 0x29000, format = N64TextureFormat.RGBA32),
                    IconMapping("gerudo_mask", ICON, 0x2A000, format = N64TextureFormat.RGBA32),
                    IconMapping("mask_of_truth", ICON, 0x2B000, format = N64TextureFormat.RGBA32),
            )

    val byId: Map<String, IconMapping> = entries.associateBy { it.itemId }
}
