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

package br.com.redclaw.zelda64player.tracker.data

import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.tracker.model.TrackerItem
import br.com.redclaw.zelda64player.tracker.model.TrackerLocation
import br.com.redclaw.zelda64player.tracker.model.TrackerSong

/**
 * Static, hand-curated vanilla Majora's Mask tracker content. Names are factual game terms surfaced
 * through string resources (i18n).
 */
object MmItemDatabase : TrackerContent {

        override val items: List<TrackerItem> =
                listOf(
                        TrackerItem(
                                "sword",
                                R.string.mm_item_sword,
                                iconRes = R.drawable.ic_tracker_fallback,
                                maxCount = 3,
                                cycleLabels =
                                        listOf(
                                                R.string.mm_item_kokiri_sword,
                                                R.string.mm_item_razor_sword,
                                                R.string.mm_item_gilded_sword
                                        ),
                                cycleIcons =
                                        listOf(
                                                R.drawable.ic_tracker_fallback,
                                                R.drawable.ic_tracker_fallback,
                                                R.drawable.ic_tracker_fallback
                                        )
                        ),
                        TrackerItem(
                                "shield",
                                R.string.mm_item_shield,
                                iconRes = R.drawable.ic_tracker_fallback,
                                maxCount = 2,
                                cycleLabels =
                                        listOf(
                                                R.string.mm_item_hero_shield,
                                                R.string.mm_item_mirror_shield
                                        ),
                                cycleIcons = listOf(R.drawable.ic_tracker_fallback, R.drawable.ic_tracker_fallback)
                        ),
                        TrackerItem(
                                "hookshot",
                                R.string.mm_item_hookshot,
                                iconRes = R.drawable.ic_tracker_fallback
                        ),
                        TrackerItem(
                                "great_fairy_sword",
                                R.string.mm_item_great_fairy_sword,
                                iconRes = R.drawable.ic_tracker_fallback
                        ),
                        TrackerItem(
                                "bow",
                                R.string.mm_item_bow,
                                iconRes = R.drawable.ic_tracker_fallback,
                                maxCount = 3,
                                cycleLabels =
                                        listOf(
                                                R.string.mm_item_bow_level_1,
                                                R.string.mm_item_bow_level_2,
                                                R.string.mm_item_bow_level_3
                                        ),
                                cycleIcons =
                                        listOf(
                                                R.drawable.ic_tracker_fallback,
                                                R.drawable.ic_tracker_fallback,
                                                R.drawable.ic_tracker_fallback
                                        )
                        ),
                        TrackerItem(
                                "bomb_bag",
                                R.string.mm_item_bomb_bag,
                                iconRes = R.drawable.ic_tracker_fallback,
                                maxCount = 3
                        ),
                        TrackerItem(
                                "magic_bean",
                                R.string.mm_item_magic_bean,
                                iconRes = R.drawable.ic_tracker_fallback
                        ),
                        TrackerItem(
                                "deku_stick",
                                R.string.mm_item_deku_stick,
                                iconRes = R.drawable.ic_tracker_fallback,
                                maxCount = 1,
                                cycleLabels = listOf(R.string.mm_item_deku_stick_level_1),
                                cycleIcons = listOf(R.drawable.ic_tracker_fallback)
                        ),
                        TrackerItem(
                                "deku_nut",
                                R.string.mm_item_deku_nut,
                                iconRes = R.drawable.ic_tracker_fallback,
                                maxCount = 1,
                                cycleLabels = listOf(R.string.mm_item_deku_nut_level_1),
                                cycleIcons = listOf(R.drawable.ic_tracker_fallback)
                        ),
                        TrackerItem("bombchu", R.string.mm_item_bombchu, iconRes = R.drawable.ic_tracker_fallback),
                        TrackerItem(
                                "lens_of_truth",
                                R.string.mm_item_lens_of_truth,
                                iconRes = R.drawable.ic_tracker_fallback
                        ),
                        TrackerItem(
                                "pictobox",
                                R.string.mm_item_pictobox,
                                iconRes = R.drawable.ic_tracker_fallback
                        ),
                        TrackerItem(
                                "bombers_notebook",
                                R.string.mm_item_bombers_notebook,
                                iconRes = R.drawable.ic_tracker_fallback
                        ),
                        TrackerItem(
                                "ocarina_of_time",
                                R.string.mm_item_ocarina_of_time,
                                iconRes = R.drawable.ic_tracker_fallback
                        ),
                        TrackerItem(
                                "fire_arrows",
                                R.string.mm_item_fire_arrows,
                                iconRes = R.drawable.ic_tracker_fallback
                        ),
                        TrackerItem(
                                "ice_arrows",
                                R.string.mm_item_ice_arrows,
                                iconRes = R.drawable.ic_tracker_fallback
                        ),
                        TrackerItem(
                                "light_arrows",
                                R.string.mm_item_light_arrows,
                                iconRes = R.drawable.ic_tracker_fallback
                        ),
                        TrackerItem(
                                "magic_power",
                                R.string.mm_item_magic_power,
                                iconRes = R.drawable.ic_tracker_fallback,
                                maxCount = 2,
                                cycleLabels =
                                        listOf(
                                                R.string.mm_item_magic_level_1,
                                                R.string.mm_item_magic_level_2
                                        ),
                                cycleIcons = listOf(R.drawable.ic_tracker_fallback, R.drawable.ic_tracker_fallback)
                        ),
                        TrackerItem(
                                "rupees",
                                R.string.mm_item_rupees,
                                iconRes = R.drawable.ic_tracker_fallback,
                                maxCount = 2,
                                cycleLabels =
                                        listOf(
                                                R.string.mm_item_wallet_adult,
                                                R.string.mm_item_wallet_giant
                                        ),
                                cycleIcons = listOf(R.drawable.ic_tracker_fallback, R.drawable.ic_tracker_fallback)
                        ),
                        TrackerItem("odolwa_remains", R.string.mm_item_odolwa_remains, R.drawable.ic_tracker_fallback),
                        TrackerItem("goht_remains", R.string.mm_item_goht_remains, R.drawable.ic_tracker_fallback),
                        TrackerItem("gyorg_remains", R.string.mm_item_gyorg_remains, R.drawable.ic_tracker_fallback),
                        TrackerItem("twinmold_remains", R.string.mm_item_twinmold_remains, R.drawable.ic_tracker_fallback),
                        TrackerItem("deku_mask", R.string.mm_item_deku_mask, R.drawable.ic_tracker_fallback),
                        TrackerItem("goron_mask", R.string.mm_item_goron_mask, R.drawable.ic_tracker_fallback),
                        TrackerItem("zora_mask", R.string.mm_item_zora_mask, R.drawable.ic_tracker_fallback),
                        TrackerItem("fierce_deity_mask", R.string.mm_item_fierce_deity_mask, R.drawable.ic_tracker_fallback),
                        TrackerItem("great_fairy_mask", R.string.mm_item_great_fairy_mask, R.drawable.ic_tracker_fallback),
                        TrackerItem("romani_mask", R.string.mm_item_romani_mask, R.drawable.ic_tracker_fallback),
                        TrackerItem("circus_leader_mask", R.string.mm_item_circus_leader_mask, R.drawable.ic_tracker_fallback),
                        TrackerItem("kafei_mask", R.string.mm_item_kafei_mask, R.drawable.ic_tracker_fallback),
                        TrackerItem("couples_mask", R.string.mm_item_couples_mask, R.drawable.ic_tracker_fallback),
                        TrackerItem("postman_hat", R.string.mm_item_postman_hat, R.drawable.ic_tracker_fallback),
                        TrackerItem("all_night_mask", R.string.mm_item_all_night_mask, R.drawable.ic_tracker_fallback),
                        TrackerItem("blast_mask", R.string.mm_item_blast_mask, R.drawable.ic_tracker_fallback),
                        TrackerItem("stone_mask", R.string.mm_item_stone_mask, R.drawable.ic_tracker_fallback),
                        TrackerItem("bremen_mask", R.string.mm_item_bremen_mask, R.drawable.ic_tracker_fallback),
                        TrackerItem("bunny_hood", R.string.mm_item_bunny_hood, R.drawable.ic_tracker_fallback),
                        TrackerItem("don_gero_mask", R.string.mm_item_don_gero_mask, R.drawable.ic_tracker_fallback),
                        TrackerItem("kamaro_mask", R.string.mm_item_kamaro_mask, R.drawable.ic_tracker_fallback),
                        TrackerItem("gibdo_mask", R.string.mm_item_gibdo_mask, R.drawable.ic_tracker_fallback),
                        TrackerItem("garo_mask", R.string.mm_item_garo_mask, R.drawable.ic_tracker_fallback),
                        TrackerItem("captains_hat", R.string.mm_item_captains_hat, R.drawable.ic_tracker_fallback),
                        TrackerItem("giants_mask", R.string.mm_item_giants_mask, R.drawable.ic_tracker_fallback),
                        TrackerItem("mask_of_truth", R.string.mm_item_mask_of_truth, R.drawable.ic_tracker_fallback),
                        TrackerItem("mask_of_scents", R.string.mm_item_mask_of_scents, R.drawable.ic_tracker_fallback),
                        TrackerItem("keaton_mask", R.string.mm_item_keaton_mask, R.drawable.ic_tracker_fallback)
                )

        override val locations: List<TrackerLocation> =
                listOf(
                        TrackerLocation(
                                "clock_town_east",
                                R.string.region_clock_town,
                                R.string.loc_ct_east
                        ),
                        TrackerLocation(
                                "clock_town_north",
                                R.string.region_clock_town,
                                R.string.loc_ct_north
                        ),
                        TrackerLocation(
                                "clock_town_west",
                                R.string.region_clock_town,
                                R.string.loc_ct_west
                        ),
                        TrackerLocation(
                                "woodfall_temple",
                                R.string.region_woodfall,
                                R.string.loc_wf_temple
                        ),
                        TrackerLocation(
                                "woodfall_swamp",
                                R.string.region_woodfall,
                                R.string.loc_wf_swamp
                        ),
                        TrackerLocation(
                                "snowhead_temple",
                                R.string.region_snowhead,
                                R.string.loc_sh_temple
                        ),
                        TrackerLocation(
                                "snowhead_goron",
                                R.string.region_snowhead,
                                R.string.loc_sh_goron
                        ),
                        TrackerLocation(
                                "great_bay_temple",
                                R.string.region_great_bay,
                                R.string.loc_gb_temple
                        ),
                        TrackerLocation(
                                "great_bay_fisher",
                                R.string.region_great_bay,
                                R.string.loc_gb_fisher
                        ),
                        TrackerLocation(
                                "ikana_castle",
                                R.string.region_ikana,
                                R.string.loc_ik_castle
                        ),
                        TrackerLocation("ikana_well", R.string.region_ikana, R.string.loc_ik_well),
                        TrackerLocation(
                                "stone_tower",
                                R.string.region_stone_tower,
                                R.string.loc_st_tower
                        ),
                        TrackerLocation(
                                "stone_tower_inverted",
                                R.string.region_stone_tower,
                                R.string.loc_st_inverted
                        ),
                        TrackerLocation(
                                "bond_fairy",
                                R.string.region_other,
                                R.string.loc_other_fairy
                        ),
                        TrackerLocation("bond_bank", R.string.region_other, R.string.loc_other_bank)
                )

        override val songs: List<TrackerSong> =
                listOf(
                        TrackerSong("song_of_time", R.string.mm_song_song_of_time),
                        TrackerSong("song_of_healing", R.string.mm_song_song_of_healing),
                        TrackerSong("eponas_song", R.string.mm_song_eponas_song),
                        TrackerSong("song_of_soaring", R.string.mm_song_song_of_soaring),
                        TrackerSong("song_of_storms", R.string.mm_song_song_of_storms),
                        TrackerSong("sonata_of_awakening", R.string.mm_song_sonata_of_awakening),
                        TrackerSong("goron_lullaby", R.string.mm_song_goron_lullaby),
                        TrackerSong("new_wave_bossa_nova", R.string.mm_song_new_wave_bossa_nova),
                        TrackerSong("elegy_of_emptiness", R.string.mm_song_elegy_of_emptiness),
                        TrackerSong("oath_to_order", R.string.mm_song_oath_to_order),
                        TrackerSong("song_of_double_time", R.string.mm_song_song_of_double_time)
                )

}
