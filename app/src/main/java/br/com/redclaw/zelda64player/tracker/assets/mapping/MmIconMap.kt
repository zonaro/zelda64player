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
 * Maps MM tracker item ids to the US 1.0 `icon_item_static_yar` archives.
 *
 * DMA indexes and texture offsets follow zeldaret/mm `tools/filelists/n64-us/archives.csv` and
 * `assets/xml/archives/icon_item*_static.xml`. MM stores these textures in a YAR container, unlike
 * OoT's directly addressable icon files.
 */
object MmIconMap {

        private const val ICON = DmaTableOffsets.MM_ICON_FILE_INDEX
        private const val QUEST = DmaTableOffsets.MM_QUEST_ICON_FILE_INDEX

        private fun icon(itemId: String, offset: Int): IconMapping =
                IconMapping(
                        itemId,
                        ICON,
                        offset,
                        format = N64TextureFormat.RGBA32,
                        archiveFormat = IconArchiveFormat.YAR
                )

        private fun questIcon(itemId: String, offset: Int): IconMapping =
                IconMapping(
                        itemId,
                        QUEST,
                        offset,
                        width = 24,
                        height = 24,
                        format = N64TextureFormat.RGBA32,
                        archiveFormat = IconArchiveFormat.YAR
                )

        val entries: List<IconMapping> =
                listOf(
                        icon("sword", 0x4D000),
                        icon("sword_2", 0x4E000),
                        icon("sword_3", 0x4F000),
                        icon("shield", 0x51000),
                        icon("shield_2", 0x52000),
                        icon("hookshot", 0x0F000),
                        icon("great_fairy_sword", 0x10000),
                        icon("bow", 0x01000),
                        icon("bow_2", 0x54000),
                        icon("bow_3", 0x55000),
                        icon("bomb_bag", 0x56000),
                        icon("magic_bean", 0x0A000),
                        icon("deku_stick", 0x08000),
                        icon("deku_nut", 0x09000),
                        icon("bombchu", 0x07000),
                        icon("lens_of_truth", 0x0E000),
                        icon("pictobox", 0x0D000),
                        icon("bombers_notebook", 0x61000),
                        icon("ocarina_of_time", 0x00000),
                        icon("fire_arrows", 0x02000),
                        icon("ice_arrows", 0x03000),
                        icon("light_arrows", 0x04000),
                        questIcon("magic_power", 0x6300),
                        questIcon("magic_power_2", 0x6C00),
                        icon("rupees", 0x5A000),
                        icon("rupees_2", 0x5B000),
                        icon("odolwa_remains", 0x5D000),
                        icon("goht_remains", 0x5E000),
                        icon("gyorg_remains", 0x5F000),
                        icon("twinmold_remains", 0x60000),
                        icon("deku_mask", 0x32000),
                        icon("goron_mask", 0x33000),
                        icon("zora_mask", 0x34000),
                        icon("fierce_deity_mask", 0x35000),
                        icon("great_fairy_mask", 0x40000),
                        icon("romani_mask", 0x3C000),
                        icon("circus_leader_mask", 0x3D000),
                        icon("kafei_mask", 0x37000),
                        icon("couples_mask", 0x3F000),
                        icon("postman_hat", 0x3E000),
                        icon("all_night_mask", 0x38000),
                        icon("blast_mask", 0x47000),
                        icon("stone_mask", 0x45000),
                        icon("bremen_mask", 0x46000),
                        icon("bunny_hood", 0x39000),
                        icon("don_gero_mask", 0x42000),
                        icon("kamaro_mask", 0x43000),
                        icon("gibdo_mask", 0x41000),
                        icon("garo_mask", 0x3B000),
                        icon("captains_hat", 0x44000),
                        icon("giants_mask", 0x49000),
                        icon("mask_of_truth", 0x36000),
                        icon("mask_of_scents", 0x48000),
                        icon("keaton_mask", 0x3A000),
                )

        val byId: Map<String, IconMapping> = entries.associateBy { it.itemId }
}
