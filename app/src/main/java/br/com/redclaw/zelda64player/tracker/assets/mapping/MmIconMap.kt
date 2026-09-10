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
 * Maps MM tracker item ids to their location inside `icon_item_static` (DMA 8).
 *
 * MM reuses the same icon archive layout as OoT: 32x32 RGBA32, stride 0x1000. Offsets are
 * provisional (same order as OoT where applicable); refine via zeldaret/mm when the decomp exposes
 * the texture table.
 */
object MmIconMap {

        private const val ICON = DmaTableOffsets.MM_ICON_FILE_INDEX

        val entries: List<IconMapping> =
                listOf(
                        IconMapping("sword", ICON, 0x0000, format = N64TextureFormat.RGBA32),
                        IconMapping("shield", ICON, 0x1000, format = N64TextureFormat.RGBA32),
                        IconMapping("hookshot", ICON, 0x2000, format = N64TextureFormat.RGBA32),
                        IconMapping(
                                "great_fairy_sword",
                                ICON,
                                0x3000,
                                format = N64TextureFormat.RGBA32
                        ),
                        IconMapping("bow", ICON, 0x4000, format = N64TextureFormat.RGBA32),
                        IconMapping("bomb_bag", ICON, 0x5000, format = N64TextureFormat.RGBA32),
                        IconMapping("magic_bean", ICON, 0x6000, format = N64TextureFormat.RGBA32),
                        IconMapping("deku_stick", ICON, 0x7000, format = N64TextureFormat.RGBA32),
                        IconMapping("deku_nut", ICON, 0x8000, format = N64TextureFormat.RGBA32),
                        IconMapping("bombchu", ICON, 0x9000, format = N64TextureFormat.RGBA32),
                        IconMapping(
                                "lens_of_truth",
                                ICON,
                                0xA000,
                                format = N64TextureFormat.RGBA32
                        ),
                        IconMapping("pictobox", ICON, 0xB000, format = N64TextureFormat.RGBA32),
                        IconMapping(
                                "bombers_notebook",
                                ICON,
                                0xC000,
                                format = N64TextureFormat.RGBA32
                        ),
                        IconMapping(
                                "ocarina_of_time",
                                ICON,
                                0xD000,
                                format = N64TextureFormat.RGBA32
                        ),
                        IconMapping("fire_arrows", ICON, 0xE000, format = N64TextureFormat.RGBA32),
                        IconMapping("ice_arrows", ICON, 0xF000, format = N64TextureFormat.RGBA32),
                        IconMapping(
                                "light_arrows",
                                ICON,
                                0x10000,
                                format = N64TextureFormat.RGBA32
                        ),
                        IconMapping("magic_power", ICON, 0x11000, format = N64TextureFormat.RGBA32),
                        IconMapping("rupees", ICON, 0x12000, format = N64TextureFormat.RGBA32),
                        IconMapping(
                                "odolwa_remains",
                                ICON,
                                0x13000,
                                format = N64TextureFormat.RGBA32
                        ),
                        IconMapping(
                                "goht_remains",
                                ICON,
                                0x14000,
                                format = N64TextureFormat.RGBA32
                        ),
                        IconMapping(
                                "gyorg_remains",
                                ICON,
                                0x15000,
                                format = N64TextureFormat.RGBA32
                        ),
                        IconMapping(
                                "twinmold_remains",
                                ICON,
                                0x16000,
                                format = N64TextureFormat.RGBA32
                        ),
                        IconMapping("deku_mask", ICON, 0x17000, format = N64TextureFormat.RGBA32),
                        IconMapping("goron_mask", ICON, 0x18000, format = N64TextureFormat.RGBA32),
                        IconMapping("zora_mask", ICON, 0x19000, format = N64TextureFormat.RGBA32),
                        IconMapping(
                                "fierce_deity_mask",
                                ICON,
                                0x1A000,
                                format = N64TextureFormat.RGBA32
                        ),
                        IconMapping(
                                "great_fairy_mask",
                                ICON,
                                0x1B000,
                                format = N64TextureFormat.RGBA32
                        ),
                        IconMapping("romani_mask", ICON, 0x1C000, format = N64TextureFormat.RGBA32),
                        IconMapping(
                                "circus_leader_mask",
                                ICON,
                                0x1D000,
                                format = N64TextureFormat.RGBA32
                        ),
                        IconMapping("kafei_mask", ICON, 0x1E000, format = N64TextureFormat.RGBA32),
                        IconMapping(
                                "couples_mask",
                                ICON,
                                0x1F000,
                                format = N64TextureFormat.RGBA32
                        ),
                        IconMapping("postman_hat", ICON, 0x20000, format = N64TextureFormat.RGBA32),
                        IconMapping(
                                "all_night_mask",
                                ICON,
                                0x21000,
                                format = N64TextureFormat.RGBA32
                        ),
                        IconMapping("blast_mask", ICON, 0x22000, format = N64TextureFormat.RGBA32),
                        IconMapping("stone_mask", ICON, 0x23000, format = N64TextureFormat.RGBA32),
                        IconMapping("bremen_mask", ICON, 0x24000, format = N64TextureFormat.RGBA32),
                        IconMapping("bunny_hood", ICON, 0x25000, format = N64TextureFormat.RGBA32),
                        IconMapping(
                                "don_gero_mask",
                                ICON,
                                0x26000,
                                format = N64TextureFormat.RGBA32
                        ),
                        IconMapping("kamaro_mask", ICON, 0x27000, format = N64TextureFormat.RGBA32),
                        IconMapping("gibdo_mask", ICON, 0x28000, format = N64TextureFormat.RGBA32),
                        IconMapping("garo_mask", ICON, 0x29000, format = N64TextureFormat.RGBA32),
                        IconMapping(
                                "captains_hat",
                                ICON,
                                0x2A000,
                                format = N64TextureFormat.RGBA32
                        ),
                        IconMapping("giants_mask", ICON, 0x2B000, format = N64TextureFormat.RGBA32),
                        IconMapping(
                                "mask_of_truth",
                                ICON,
                                0x2C000,
                                format = N64TextureFormat.RGBA32
                        ),
                        IconMapping(
                                "mask_of_scents",
                                ICON,
                                0x2D000,
                                format = N64TextureFormat.RGBA32
                        ),
                        IconMapping("keaton_mask", ICON, 0x2E000, format = N64TextureFormat.RGBA32),
                )

        val byId: Map<String, IconMapping> = entries.associateBy { it.itemId }
}
