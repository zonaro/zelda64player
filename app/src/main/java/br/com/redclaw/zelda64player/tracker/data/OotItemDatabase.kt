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
import br.com.redclaw.zelda64player.tracker.model.TrackerGame
import br.com.redclaw.zelda64player.tracker.model.TrackerItem
import br.com.redclaw.zelda64player.tracker.model.TrackerLocation
import br.com.redclaw.zelda64player.tracker.model.TrackerSong
import br.com.redclaw.zelda64player.tracker.model.TrackerUpgrade

/**
 * Static, hand-curated vanilla Ocarina of Time tracker content. Names are factual game terms
 * surfaced through string resources (i18n). The set is representative and can be expanded without
 * code changes.
 */
/** Common content contract implemented by [OotItemDatabase] and [MmItemDatabase]. */
interface TrackerContent {
        val items: List<TrackerItem>
        val locations: List<TrackerLocation>
        val songs: List<TrackerSong>
        val upgrades: List<TrackerUpgrade>
}

object OotItemDatabase : TrackerContent {

        override val items: List<TrackerItem> =
                listOf(
                        TrackerItem(
                                "kokiri_sword",
                                R.string.oot_item_kokiri_sword,
                                iconRes = R.drawable.kokiri_sword,
                                maxCount = 1
                        ),
                        TrackerItem(
                                "master_sword",
                                R.string.oot_item_master_sword,
                                iconRes = R.drawable.master_sword
                        ),
                        TrackerItem(
                                "biggoron_sword",
                                R.string.oot_item_biggoron_sword,
                                iconRes = R.drawable.biggoron
                        ),
                        TrackerItem("deku_shield", R.string.oot_item_deku_shield),
                        TrackerItem(
                                "hylian_shield",
                                R.string.oot_item_hylian_shield,
                                iconRes = R.drawable.hylian_shield
                        ),
                        TrackerItem(
                                "mirror_shield",
                                R.string.oot_item_mirror_shield,
                                iconRes = R.drawable.mirror_shield
                        ),
                        TrackerItem(
                                "iron_boots",
                                R.string.oot_item_iron_boots,
                                iconRes = R.drawable.iron_boots
                        ),
                        TrackerItem(
                                "hover_boots",
                                R.string.oot_item_hover_boots,
                                iconRes = R.drawable.hover_boots
                        ),
                        TrackerItem(
                                "hookshot_longshot",
                                R.string.oot_item_hookshot_longshot,
                                iconRes = R.drawable.hookshot,
                                maxCount = 2,
                                cycleLabels =
                                        listOf(
                                                R.string.oot_item_hookshot,
                                                R.string.oot_item_longshot
                                        ),
                                cycleIcons = listOf(R.drawable.hookshot, R.drawable.longshot)
                        ),
                        TrackerItem(
                                "boomerang",
                                R.string.oot_item_boomerang,
                                iconRes = R.drawable.boomerang
                        ),
                        TrackerItem(
                                "bow",
                                R.string.oot_item_bow,
                                iconRes = R.drawable.bow,
                                maxCount = 3
                        ),
                        TrackerItem(
                                "bomb_bag",
                                R.string.oot_item_bomb_bag,
                                iconRes = R.drawable.bombs,
                                maxCount = 3
                        ),
                        TrackerItem(
                                "slingshot",
                                R.string.oot_item_slingshot,
                                iconRes = R.drawable.slingshot,
                                maxCount = 3
                        ),
                        TrackerItem(
                                "lens_of_truth",
                                R.string.oot_item_lens_of_truth,
                                iconRes = R.drawable.lens
                        ),
                        TrackerItem(
                                "megaton_hammer",
                                R.string.oot_item_megaton_hammer,
                                iconRes = R.drawable.hammer
                        ),
                        TrackerItem(
                                "ocarina",
                                R.string.oot_item_ocarina_cycle,
                                iconRes = R.drawable.ocarina_of_time,
                                maxCount = 2,
                                cycleLabels =
                                        listOf(
                                                R.string.oot_item_fairy_ocarina,
                                                R.string.oot_item_ocarina_of_time
                                        ),
                                cycleIcons =
                                        listOf(
                                                R.drawable.ocarina_of_time,
                                                R.drawable.ocarina_of_time
                                        )
                        ),
                        TrackerItem(
                                "fire_arrows",
                                R.string.oot_item_fire_arrows,
                                iconRes = R.drawable.fire_arrow
                        ),
                        TrackerItem(
                                "ice_arrows",
                                R.string.oot_item_ice_arrows,
                                iconRes = R.drawable.ice_arrow
                        ),
                        TrackerItem(
                                "light_arrows",
                                R.string.oot_item_light_arrows,
                                iconRes = R.drawable.light_arrow
                        ),
                        TrackerItem(
                                "din_fire",
                                R.string.oot_item_din_fire,
                                iconRes = R.drawable.dins_fire
                        ),
                        TrackerItem(
                                "farore_wind",
                                R.string.oot_item_farore_wind,
                                iconRes = R.drawable.farores_wind
                        ),
                        TrackerItem(
                                "nayru_love",
                                R.string.oot_item_nayru_love,
                                iconRes = R.drawable.nairus_love
                        ),
                        TrackerItem("goron_bracelet", R.string.oot_item_goron_bracelet),
                        TrackerItem("silver_gauntlets", R.string.oot_item_silver_gauntlets),
                        TrackerItem("golden_gauntlets", R.string.oot_item_golden_gauntlets),
                        TrackerItem(
                                "kokiri_tunic",
                                R.string.oot_item_kokiri_tunic,
                                iconRes = R.drawable.green_tunic
                        ),
                        TrackerItem(
                                "goron_tunic",
                                R.string.oot_item_goron_tunic,
                                iconRes = R.drawable.goron_tunic
                        ),
                        TrackerItem(
                                "zora_tunic",
                                R.string.oot_item_zora_tunic,
                                iconRes = R.drawable.zora_tunic
                        ),
                        TrackerItem("rupees", R.string.oot_item_rupees, maxCount = 999)
                )

        override val locations: List<TrackerLocation> =
                listOf(
                        // Kokiri Forest
                        TrackerLocation(
                                "kf_shop",
                                R.string.region_kokiri_forest,
                                R.string.loc_kf_shop
                        ),
                        TrackerLocation(
                                "kf_saria",
                                R.string.region_kokiri_forest,
                                R.string.loc_kf_saria
                        ),
                        TrackerLocation(
                                "kf_stump",
                                R.string.region_kokiri_forest,
                                R.string.loc_kf_stump
                        ),
                        TrackerLocation(
                                "kf_sword_chest",
                                R.string.region_kokiri_forest,
                                R.string.loc_kf_sword_chest
                        ),
                        TrackerLocation(
                                "kf_storms_grotto",
                                R.string.region_kokiri_forest,
                                R.string.loc_kf_storms_grotto
                        ),
                        TrackerLocation(
                                "kf_links_house",
                                R.string.region_kokiri_forest,
                                R.string.loc_kf_links_house
                        ),
                        TrackerLocation(
                                "kf_gs_house",
                                R.string.region_kokiri_forest,
                                R.string.loc_kf_gs_house
                        ),
                        TrackerLocation(
                                "kf_gs_bean",
                                R.string.region_kokiri_forest,
                                R.string.loc_kf_gs_bean
                        ),
                        // Lost Woods
                        TrackerLocation(
                                "lw_skull_kid",
                                R.string.region_lost_woods,
                                R.string.loc_lw_skull_kid
                        ),
                        TrackerLocation(
                                "lw_ocarina_game",
                                R.string.region_lost_woods,
                                R.string.loc_lw_ocarina_game
                        ),
                        TrackerLocation(
                                "lw_target",
                                R.string.region_lost_woods,
                                R.string.loc_lw_target
                        ),
                        TrackerLocation(
                                "lw_deku_theater",
                                R.string.region_lost_woods,
                                R.string.loc_lw_deku_theater
                        ),
                        TrackerLocation(
                                "lw_grog",
                                R.string.region_lost_woods,
                                R.string.loc_lw_grog
                        ),
                        TrackerLocation(
                                "lw_scrub_bridge",
                                R.string.region_lost_woods,
                                R.string.loc_lw_scrub_bridge
                        ),
                        TrackerLocation(
                                "lw_grotto_scrubs",
                                R.string.region_lost_woods,
                                R.string.loc_lw_grotto_scrubs
                        ),
                        // Sacred Forest Meadow
                        TrackerLocation(
                                "sfm_saria",
                                R.string.region_sacred_meadow,
                                R.string.loc_sfm_saria
                        ),
                        TrackerLocation(
                                "sfm_sheik",
                                R.string.region_sacred_meadow,
                                R.string.loc_sfm_sheik
                        ),
                        TrackerLocation(
                                "sfm_maze",
                                R.string.region_sacred_meadow,
                                R.string.loc_sfm_maze
                        ),
                        TrackerLocation(
                                "sfm_wolfos_grotto",
                                R.string.region_sacred_meadow,
                                R.string.loc_sfm_wolfos_grotto
                        ),
                        // Hyrule Field
                        TrackerLocation(
                                "hf_ocarina",
                                R.string.region_hyrule_field,
                                R.string.loc_hf_ocarina
                        ),
                        TrackerLocation(
                                "hf_tektite_grotto",
                                R.string.region_hyrule_field,
                                R.string.loc_hf_tektite_grotto
                        ),
                        TrackerLocation(
                                "hf_cow_grotto",
                                R.string.region_hyrule_field,
                                R.string.loc_hf_cow_grotto
                        ),
                        TrackerLocation(
                                "hf_grotto_open",
                                R.string.region_hyrule_field,
                                R.string.loc_hf_grotto_open
                        ),
                        TrackerLocation(
                                "hf_lon_lon",
                                R.string.region_hyrule_field,
                                R.string.loc_hf_lon_lon
                        ),
                        TrackerLocation(
                                "hf_cow",
                                R.string.region_hyrule_field,
                                R.string.loc_hf_cow
                        ),
                        TrackerLocation(
                                "hf_ocarina_game",
                                R.string.region_hyrule_field,
                                R.string.loc_hf_ocarina_game
                        ),
                        TrackerLocation(
                                "hf_running_man",
                                R.string.region_hyrule_field,
                                R.string.loc_hf_running_man
                        ),
                        // Lake Hylia
                        TrackerLocation(
                                "lh_underwater",
                                R.string.region_lake_hylia,
                                R.string.loc_lh_underwater
                        ),
                        TrackerLocation(
                                "lh_fishing_child",
                                R.string.region_lake_hylia,
                                R.string.loc_lh_fishing_child
                        ),
                        TrackerLocation(
                                "lh_fishing_adult",
                                R.string.region_lake_hylia,
                                R.string.loc_lh_fishing_adult
                        ),
                        TrackerLocation("lh_sun", R.string.region_lake_hylia, R.string.loc_lh_sun),
                        TrackerLocation("lh_lab", R.string.region_lake_hylia, R.string.loc_lh_lab),
                        TrackerLocation(
                                "lh_scarecrow",
                                R.string.region_lake_hylia,
                                R.string.loc_lh_scarecrow
                        ),
                        TrackerLocation(
                                "lh_grotto",
                                R.string.region_lake_hylia,
                                R.string.loc_lh_grotto
                        ),
                        // Lon Lon Ranch
                        TrackerLocation(
                                "llr_talons",
                                R.string.region_lon_lon,
                                R.string.loc_llr_talons
                        ),
                        TrackerLocation(
                                "llr_stables",
                                R.string.region_lon_lon,
                                R.string.loc_llr_stables
                        ),
                        TrackerLocation(
                                "llr_tower",
                                R.string.region_lon_lon,
                                R.string.loc_llr_tower
                        ),
                        // Kakariko
                        TrackerLocation(
                                "kak_roof",
                                R.string.region_kakariko,
                                R.string.loc_kak_roof
                        ),
                        TrackerLocation(
                                "kak_anju_child",
                                R.string.region_kakariko,
                                R.string.loc_kak_anju_child
                        ),
                        TrackerLocation(
                                "kak_anju_adult",
                                R.string.region_kakariko,
                                R.string.loc_kak_anju_adult
                        ),
                        TrackerLocation(
                                "kak_impas_back",
                                R.string.region_kakariko,
                                R.string.loc_kak_impas_back
                        ),
                        TrackerLocation(
                                "kak_windmill",
                                R.string.region_kakariko,
                                R.string.loc_kak_windmill
                        ),
                        TrackerLocation(
                                "kak_shooting",
                                R.string.region_kakariko,
                                R.string.loc_kak_shooting
                        ),
                        TrackerLocation(
                                "kak_potion_shop",
                                R.string.region_kakariko,
                                R.string.loc_kak_potion_shop
                        ),
                        TrackerLocation(
                                "kak_bazaar",
                                R.string.region_kakariko,
                                R.string.loc_kak_bazaar
                        ),
                        TrackerLocation(
                                "kak_skulltula_house",
                                R.string.region_kakariko,
                                R.string.loc_kak_skulltula_house
                        ),
                        TrackerLocation("kak_gs", R.string.region_kakariko, R.string.loc_kak_gs),
                        // Graveyard
                        TrackerLocation(
                                "grav_dampe_race",
                                R.string.region_graveyard,
                                R.string.loc_grav_dampe_race
                        ),
                        TrackerLocation(
                                "grav_royal_tomb",
                                R.string.region_graveyard,
                                R.string.loc_grav_royal_tomb
                        ),
                        TrackerLocation(
                                "grav_shield_grave",
                                R.string.region_graveyard,
                                R.string.loc_grav_shield_grave
                        ),
                        TrackerLocation(
                                "grav_heart_grave",
                                R.string.region_graveyard,
                                R.string.loc_grav_heart_grave
                        ),
                        // Goron City
                        TrackerLocation(
                                "gc_maze",
                                R.string.region_goron_city,
                                R.string.loc_gc_maze
                        ),
                        TrackerLocation(
                                "gc_maze_left",
                                R.string.region_goron_city,
                                R.string.loc_gc_maze_left
                        ),
                        TrackerLocation(
                                "gc_maze_center",
                                R.string.region_goron_city,
                                R.string.loc_gc_maze_center
                        ),
                        TrackerLocation(
                                "gc_rolling_goron",
                                R.string.region_goron_city,
                                R.string.loc_gc_rolling_goron
                        ),
                        TrackerLocation(
                                "gc_medigoron",
                                R.string.region_goron_city,
                                R.string.loc_gc_medigoron
                        ),
                        TrackerLocation(
                                "gc_darunia",
                                R.string.region_goron_city,
                                R.string.loc_gc_darunia
                        ),
                        TrackerLocation(
                                "gc_shop",
                                R.string.region_goron_city,
                                R.string.loc_gc_shop
                        ),
                        // Death Mountain
                        TrackerLocation(
                                "dmt_chest",
                                R.string.region_death_mountain,
                                R.string.loc_dmt_chest
                        ),
                        TrackerLocation(
                                "dmt_great_fairy",
                                R.string.region_death_mountain,
                                R.string.loc_dmt_great_fairy
                        ),
                        TrackerLocation(
                                "dmt_biggoron",
                                R.string.region_death_mountain,
                                R.string.loc_dmt_biggoron
                        ),
                        TrackerLocation(
                                "dmt_cow_grotto",
                                R.string.region_death_mountain,
                                R.string.loc_dmt_cow_grotto
                        ),
                        TrackerLocation(
                                "dmc_volcano",
                                R.string.region_death_mountain,
                                R.string.loc_dmc_volcano
                        ),
                        TrackerLocation(
                                "dmc_great_fairy",
                                R.string.region_death_mountain,
                                R.string.loc_dmc_great_fairy
                        ),
                        TrackerLocation(
                                "dmc_deku_scrub",
                                R.string.region_death_mountain,
                                R.string.loc_dmc_deku_scrub
                        ),
                        // Market
                        TrackerLocation(
                                "market_dog_lady",
                                R.string.region_market,
                                R.string.loc_market_dog_lady
                        ),
                        TrackerLocation(
                                "market_shooting",
                                R.string.region_market,
                                R.string.loc_market_shooting
                        ),
                        TrackerLocation(
                                "market_mask_shop",
                                R.string.region_market,
                                R.string.loc_market_mask_shop
                        ),
                        TrackerLocation(
                                "market_bombchu_bowling",
                                R.string.region_market,
                                R.string.loc_market_bombchu_bowling
                        ),
                        TrackerLocation(
                                "market_treasure_game",
                                R.string.region_market,
                                R.string.loc_market_treasure_game
                        ),
                        TrackerLocation(
                                "market_bombchu_shop",
                                R.string.region_market,
                                R.string.loc_market_bombchu_shop
                        ),
                        TrackerLocation(
                                "market_potion_shop",
                                R.string.region_market,
                                R.string.loc_market_potion_shop
                        ),
                        TrackerLocation(
                                "market_bazaar",
                                R.string.region_market,
                                R.string.loc_market_bazaar
                        ),
                        TrackerLocation(
                                "market_temple_time",
                                R.string.region_market,
                                R.string.loc_market_temple_time
                        ),
                        // Hyrule Castle
                        TrackerLocation(
                                "hc_garden",
                                R.string.region_hyrule_castle,
                                R.string.loc_hc_garden
                        ),
                        TrackerLocation(
                                "hc_great_fairy",
                                R.string.region_hyrule_castle,
                                R.string.loc_hc_great_fairy
                        ),
                        TrackerLocation(
                                "ogc_great_fairy",
                                R.string.region_ganon_castle,
                                R.string.loc_ogc_great_fairy
                        ),
                        // Zora
                        TrackerLocation(
                                "zr_frogs",
                                R.string.region_zora_river,
                                R.string.loc_zr_frogs
                        ),
                        TrackerLocation(
                                "zr_magic_bean",
                                R.string.region_zora_river,
                                R.string.loc_zr_magic_bean
                        ),
                        TrackerLocation(
                                "zr_open_grotto",
                                R.string.region_zora_river,
                                R.string.loc_zr_open_grotto
                        ),
                        TrackerLocation(
                                "zd_chest",
                                R.string.region_zora_domain,
                                R.string.loc_zd_chest
                        ),
                        TrackerLocation(
                                "zd_diving",
                                R.string.region_zora_domain,
                                R.string.loc_zd_diving
                        ),
                        TrackerLocation(
                                "zd_king_zora",
                                R.string.region_zora_domain,
                                R.string.loc_zd_king_zora
                        ),
                        TrackerLocation(
                                "zf_great_fairy",
                                R.string.region_zora_fountain,
                                R.string.loc_zf_great_fairy
                        ),
                        TrackerLocation(
                                "zf_iceberg",
                                R.string.region_zora_fountain,
                                R.string.loc_zf_iceberg
                        ),
                        TrackerLocation(
                                "zf_hidden_cave",
                                R.string.region_zora_fountain,
                                R.string.loc_zf_hidden_cave
                        ),
                        // Gerudo
                        TrackerLocation(
                                "gv_crate",
                                R.string.region_gerudo_valley,
                                R.string.loc_gv_crate
                        ),
                        TrackerLocation(
                                "gv_waterfall",
                                R.string.region_gerudo_valley,
                                R.string.loc_gv_waterfall
                        ),
                        TrackerLocation(
                                "gv_cow",
                                R.string.region_gerudo_valley,
                                R.string.loc_gv_cow
                        ),
                        TrackerLocation(
                                "gv_small_bridge",
                                R.string.region_gerudo_valley,
                                R.string.loc_gv_small_bridge
                        ),
                        TrackerLocation(
                                "gv_chest",
                                R.string.region_gerudo_valley,
                                R.string.loc_gv_chest
                        ),
                        TrackerLocation(
                                "gf_chest",
                                R.string.region_gerudo_fortress,
                                R.string.loc_gf_chest
                        ),
                        TrackerLocation(
                                "gf_hba",
                                R.string.region_gerudo_fortress,
                                R.string.loc_gf_hba
                        ),
                        TrackerLocation(
                                "gf_carpenter",
                                R.string.region_gerudo_fortress,
                                R.string.loc_gf_carpenter
                        ),
                        TrackerLocation(
                                "gf_archery",
                                R.string.region_gerudo_fortress,
                                R.string.loc_gf_archery
                        ),
                        TrackerLocation(
                                "wasteland_structure",
                                R.string.region_wasteland,
                                R.string.loc_wasteland_structure
                        ),
                        TrackerLocation(
                                "colossus_great_fairy",
                                R.string.region_colossus,
                                R.string.loc_colossus_great_fairy
                        ),
                        TrackerLocation(
                                "colossus_arch",
                                R.string.region_colossus,
                                R.string.loc_colossus_arch
                        ),
                        TrackerLocation(
                                "colossus_spirit",
                                R.string.region_colossus,
                                R.string.loc_colossus_spirit
                        ),
                        // Dungeons (overworld-visible checks)
                        TrackerLocation(
                                "dc_entrance",
                                R.string.region_dodongos_cavern,
                                R.string.loc_dc_entrance
                        ),
                        TrackerLocation(
                                "dc_compass",
                                R.string.region_dodongos_cavern,
                                R.string.loc_dc_compass
                        ),
                        TrackerLocation(
                                "dc_boss",
                                R.string.region_dodongos_cavern,
                                R.string.loc_dc_boss
                        ),
                        TrackerLocation(
                                "dc_map",
                                R.string.region_dodongos_cavern,
                                R.string.loc_dc_map
                        ),
                        TrackerLocation(
                                "dc_slingshot",
                                R.string.region_dodongos_cavern,
                                R.string.loc_dc_slingshot
                        ),
                        TrackerLocation(
                                "dc_bomb_bag",
                                R.string.region_dodongos_cavern,
                                R.string.loc_dc_bomb_bag
                        ),
                        TrackerLocation(
                                "dc_gs_side",
                                R.string.region_dodongos_cavern,
                                R.string.loc_dc_gs_side
                        ),
                        TrackerLocation(
                                "jj_compass",
                                R.string.region_jabu_jabu,
                                R.string.loc_jj_compass
                        ),
                        TrackerLocation("jj_map", R.string.region_jabu_jabu, R.string.loc_jj_map),
                        TrackerLocation("jj_boss", R.string.region_jabu_jabu, R.string.loc_jj_boss),
                        TrackerLocation(
                                "jj_boomerang",
                                R.string.region_jabu_jabu,
                                R.string.loc_jj_boomerang
                        ),
                        TrackerLocation(
                                "jj_gs_water",
                                R.string.region_jabu_jabu,
                                R.string.loc_jj_gs_water
                        ),
                        TrackerLocation(
                                "deku_map",
                                R.string.region_deku_tree,
                                R.string.loc_deku_map
                        ),
                        TrackerLocation(
                                "deku_slingshot",
                                R.string.region_deku_tree,
                                R.string.loc_deku_slingshot
                        ),
                        TrackerLocation(
                                "deku_compass",
                                R.string.region_deku_tree,
                                R.string.loc_deku_compass
                        ),
                        TrackerLocation(
                                "deku_basement",
                                R.string.region_deku_tree,
                                R.string.loc_deku_basement
                        ),
                        TrackerLocation(
                                "deku_gs_compass",
                                R.string.region_deku_tree,
                                R.string.loc_deku_gs_compass
                        ),
                        TrackerLocation(
                                "deku_gs_basement",
                                R.string.region_deku_tree,
                                R.string.loc_deku_gs_basement
                        ),
                        TrackerLocation(
                                "deku_boss",
                                R.string.region_deku_tree,
                                R.string.loc_deku_boss
                        ),
                        TrackerLocation(
                                "forest_first",
                                R.string.region_forest_temple,
                                R.string.loc_forest_first
                        ),
                        TrackerLocation(
                                "forest_bow",
                                R.string.region_forest_temple,
                                R.string.loc_forest_bow
                        ),
                        TrackerLocation(
                                "forest_outdoors",
                                R.string.region_forest_temple,
                                R.string.loc_forest_outdoors
                        ),
                        TrackerLocation(
                                "forest_boss",
                                R.string.region_forest_temple,
                                R.string.loc_forest_boss
                        ),
                        TrackerLocation(
                                "fire_boss",
                                R.string.region_fire_temple,
                                R.string.loc_fire_boss
                        ),
                        TrackerLocation(
                                "fire_maze",
                                R.string.region_fire_temple,
                                R.string.loc_fire_maze
                        ),
                        TrackerLocation(
                                "water_morpha",
                                R.string.region_water_temple,
                                R.string.loc_water_morpha
                        ),
                        TrackerLocation(
                                "water_dark_link",
                                R.string.region_water_temple,
                                R.string.loc_water_dark_link
                        ),
                        TrackerLocation(
                                "spirit_silver_gauntlets",
                                R.string.region_spirit_temple,
                                R.string.loc_spirit_silver_gauntlets
                        ),
                        TrackerLocation(
                                "spirit_mirror_shield",
                                R.string.region_spirit_temple,
                                R.string.loc_spirit_mirror_shield
                        ),
                        TrackerLocation(
                                "spirit_boss",
                                R.string.region_spirit_temple,
                                R.string.loc_spirit_boss
                        ),
                        TrackerLocation(
                                "shadow_bongo",
                                R.string.region_shadow_temple,
                                R.string.loc_shadow_bongo
                        ),
                        TrackerLocation(
                                "shadow_invisible_maze",
                                R.string.region_shadow_temple,
                                R.string.loc_shadow_invisible_maze
                        ),
                        TrackerLocation(
                                "well_map",
                                R.string.region_bottom_well,
                                R.string.loc_well_map
                        ),
                        TrackerLocation(
                                "well_compass",
                                R.string.region_bottom_well,
                                R.string.loc_well_compass
                        ),
                        TrackerLocation(
                                "well_dead_hand",
                                R.string.region_bottom_well,
                                R.string.loc_well_dead_hand
                        ),
                        TrackerLocation(
                                "ice_compass",
                                R.string.region_ice_cavern,
                                R.string.loc_ice_compass
                        ),
                        TrackerLocation(
                                "ice_serenade",
                                R.string.region_ice_cavern,
                                R.string.loc_ice_serenade
                        ),
                        TrackerLocation(
                                "ganon_trials",
                                R.string.region_ganon_castle,
                                R.string.loc_ganon_trials
                        ),
                        TrackerLocation(
                                "ganon_boss",
                                R.string.region_ganon_castle,
                                R.string.loc_ganon_boss
                        )
                )

        override val songs: List<TrackerSong> =
                listOf(
                        TrackerSong("zeldas_lullaby", R.string.oot_song_zeldas_lullaby),
                        TrackerSong("eponas_song", R.string.oot_song_eponas_song),
                        TrackerSong("sarias_song", R.string.oot_song_sarias_song),
                        TrackerSong("song_of_time", R.string.oot_song_song_of_time),
                        TrackerSong("song_of_storms", R.string.oot_song_song_of_storms),
                        TrackerSong("sun_song", R.string.oot_song_sun_song),
                        TrackerSong("minuet_of_forest", R.string.oot_song_minuet_of_forest),
                        TrackerSong("bolero_of_fire", R.string.oot_song_bolero_of_fire),
                        TrackerSong("serenade_of_water", R.string.oot_song_serenade_of_water),
                        TrackerSong("nocturne_of_shadow", R.string.oot_song_nocturne_of_shadow),
                        TrackerSong("requiem_of_spirit", R.string.oot_song_requiem_of_spirit),
                        TrackerSong("prelude_of_light", R.string.oot_song_prelude_of_light)
                )

        override val upgrades: List<TrackerUpgrade> =
                listOf(
                        TrackerUpgrade("strength", R.string.oot_upg_strength, 3),
                        TrackerUpgrade("bombs", R.string.oot_upg_bombs, 3),
                        TrackerUpgrade("bow", R.string.oot_upg_bow, 3),
                        TrackerUpgrade("wallet", R.string.oot_upg_wallet, 2),
                        TrackerUpgrade("scale", R.string.oot_upg_scale, 2),
                        TrackerUpgrade("magic", R.string.oot_upg_magic, 2)
                )

        fun forGame(game: TrackerGame): TrackerContent =
                when (game) {
                        TrackerGame.OOT -> this
                        TrackerGame.MM -> MmItemDatabase
                }
}
