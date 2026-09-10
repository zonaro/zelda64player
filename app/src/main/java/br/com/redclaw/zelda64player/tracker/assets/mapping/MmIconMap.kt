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
 * Maps MM tracker item ids to their location inside `icon_item_24_static`.
 *
 * MM icons are 32x32 RGBA16 (same stride as OoT) for most items; some HUD variants are 24x24.
 * Offsets are sequential in the decompressed archive. TODO: confirm exact offsets and dmaFileIndex
 * via zeldaret/mm.
 */
object MmIconMap {

    private const val ICON = DmaTableOffsets.MM_ICON_FILE_INDEX
    private const val STRIDE = 0x800

    val entries: List<IconMapping> =
            listOf(
                    IconMapping("sword", ICON, 0x0000),
                    IconMapping("shield", ICON, 0x0800),
                    IconMapping("hookshot", ICON, 0x1000),
                    IconMapping("great_fairy_sword", ICON, 0x1800),
                    IconMapping("bow", ICON, 0x2000),
                    IconMapping("bomb_bag", ICON, 0x2800),
                    IconMapping("magic_bean", ICON, 0x3000),
                    IconMapping("deku_stick", ICON, 0x3800),
                    IconMapping("deku_nut", ICON, 0x4000),
                    IconMapping("bombchu", ICON, 0x4800),
                    IconMapping("lens_of_truth", ICON, 0x5000),
                    IconMapping("pictobox", ICON, 0x5800),
                    IconMapping("bombers_notebook", ICON, 0x6000),
                    IconMapping("ocarina_of_time", ICON, 0x6800),
                    IconMapping("fire_arrows", ICON, 0x7000),
                    IconMapping("ice_arrows", ICON, 0x7800),
                    IconMapping("light_arrows", ICON, 0x8000),
                    IconMapping("magic_power", ICON, 0x8800),
                    IconMapping("rupees", ICON, 0x9000),
                    IconMapping("odolwa_remains", ICON, 0x9800),
                    IconMapping("goht_remains", ICON, 0xA000),
                    IconMapping("gyorg_remains", ICON, 0xA800),
                    IconMapping("twinmold_remains", ICON, 0xB000),
                    IconMapping("deku_mask", ICON, 0xB800),
                    IconMapping("goron_mask", ICON, 0xC000),
                    IconMapping("zora_mask", ICON, 0xC800),
                    IconMapping("fierce_deity_mask", ICON, 0xD000),
                    IconMapping("great_fairy_mask", ICON, 0xD800),
                    IconMapping("romani_mask", ICON, 0xE000),
                    IconMapping("circus_leader_mask", ICON, 0xE800),
                    IconMapping("kafei_mask", ICON, 0xF000),
                    IconMapping("couples_mask", ICON, 0xF800),
                    IconMapping("postman_hat", ICON, 0x10000),
                    IconMapping("all_night_mask", ICON, 0x10800),
                    IconMapping("blast_mask", ICON, 0x11000),
                    IconMapping("stone_mask", ICON, 0x11800),
                    IconMapping("bremen_mask", ICON, 0x12000),
                    IconMapping("bunny_hood", ICON, 0x12800),
                    IconMapping("don_gero_mask", ICON, 0x13000),
                    IconMapping("kamaro_mask", ICON, 0x13800),
                    IconMapping("gibdo_mask", ICON, 0x14000),
                    IconMapping("garo_mask", ICON, 0x14800),
                    IconMapping("captains_hat", ICON, 0x15000),
                    IconMapping("giants_mask", ICON, 0x15800),
                    IconMapping("mask_of_truth", ICON, 0x16000),
                    IconMapping("mask_of_scents", ICON, 0x16800),
                    IconMapping("keaton_mask", ICON, 0x17000),
            )

    val byId: Map<String, IconMapping> = entries.associateBy { it.itemId }
}
