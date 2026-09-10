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
 * Maps OoT tracker item ids to their location inside `icon_item_static`.
 *
 * Each icon is 32x32 RGBA16 = 2048 bytes (0x800). Offsets are sequential. Source: zeldaret/oot
 * `assets/xml/icon_item_static.xml` + OoTMM. TODO: confirm exact offsets and dmaFileIndex via
 * Calamari/Puffy.
 */
object OotIconMap {

    private const val ICON = DmaTableOffsets.OOT_ICON_FILE_INDEX
    private const val STRIDE = 0x800 // 32*32*2

    val entries: List<IconMapping> =
            listOf(
                    IconMapping("deku_stick", ICON, 0x0000),
                    IconMapping("deku_nut", ICON, 0x0800),
                    IconMapping("bomb_bag", ICON, 0x1000),
                    IconMapping("bow", ICON, 0x1800),
                    IconMapping("fire_arrows", ICON, 0x2000),
                    IconMapping("din_fire", ICON, 0x2800),
                    IconMapping("slingshot", ICON, 0x3000),
                    IconMapping(
                            "ocarina",
                            ICON,
                            0x3800
                    ), // fairy ocarina / ocarina of time share slot
                    IconMapping("bombchu", ICON, 0x4800),
                    IconMapping("hookshot_longshot", ICON, 0x5000),
                    IconMapping("ice_arrows", ICON, 0x5800),
                    IconMapping("farore_wind", ICON, 0x6000),
                    IconMapping("boomerang", ICON, 0x6800),
                    IconMapping("lens_of_truth", ICON, 0x7000),
                    IconMapping("megaton_hammer", ICON, 0x7800),
                    IconMapping("light_arrows", ICON, 0x8000),
                    IconMapping("nayru_love", ICON, 0x8800),
                    // Tunics / shields / boots — may live in a different file (e.g.
                    // icon_item_dungeon)
                    // For now map to the same archive; adjust dmaFileIndex when validated.
                    IconMapping("kokiri_sword", ICON, 0x9000),
                    IconMapping("master_sword", ICON, 0x9800),
                    IconMapping("biggoron_sword", ICON, 0xA000),
                    IconMapping("deku_shield", ICON, 0xA800),
                    IconMapping("hylian_shield", ICON, 0xB000),
                    IconMapping("mirror_shield", ICON, 0xB800),
                    IconMapping("iron_boots", ICON, 0xC000),
                    IconMapping("hover_boots", ICON, 0xC800),
                    IconMapping("strength", ICON, 0xD000),
                    IconMapping("magic", ICON, 0xD800),
                    IconMapping("scale", ICON, 0xE000),
                    IconMapping("kokiri_tunic", ICON, 0xE800),
                    IconMapping("goron_tunic", ICON, 0xF000),
                    IconMapping("zora_tunic", ICON, 0xF800),
                    IconMapping("rupees", ICON, 0x10000),
                    IconMapping("forest_medallion", ICON, 0x10800),
                    IconMapping("fire_medallion", ICON, 0x11000),
                    IconMapping("water_medallion", ICON, 0x11800),
                    IconMapping("spirit_medallion", ICON, 0x12000),
                    IconMapping("shadow_medallion", ICON, 0x12800),
                    IconMapping("light_medallion", ICON, 0x13000),
                    IconMapping("keaton_mask", ICON, 0x13800),
                    IconMapping("skull_mask", ICON, 0x14000),
                    IconMapping("spooky_mask", ICON, 0x14800),
                    IconMapping("bunny_hood", ICON, 0x15000),
                    IconMapping("goron_mask", ICON, 0x15800),
                    IconMapping("zora_mask", ICON, 0x16000),
                    IconMapping("gerudo_mask", ICON, 0x16800),
                    IconMapping("mask_of_truth", ICON, 0x17000),
            )

    val byId: Map<String, IconMapping> = entries.associateBy { it.itemId }
}
