/* Copyright (C) 2026 RedClaw. SPDX-License-Identifier: GPL-3.0-or-later */
package br.com.redclaw.zelda64player.tracker.autotracker.mapper

import br.com.redclaw.zelda64player.tracker.autotracker.model.AutoTrackerSnapshot
import br.com.redclaw.zelda64player.tracker.model.TrackerGame
import br.com.redclaw.zelda64player.tracker.model.TrackerState

/** Factual item/quest IDs from zeldaret/{oot,mm}/include/{item.h,z64item.h}.
 * Only adds evidence; never downgrades levels or changes locations, hints, or timers.
 */
object AutoTrackerMapper {
    private val ootItems = mapOf(0 to "deku_stick", 1 to "deku_nut", 3 to "bow",
        4 to "fire_arrows", 5 to "din_fire", 6 to "slingshot", 9 to "bombchu",
        12 to "ice_arrows", 13 to "farore_wind", 14 to "boomerang", 15 to "lens_of_truth",
        17 to "megaton_hammer", 18 to "light_arrows", 19 to "nayru_love",
        0x24 to "keaton_mask", 0x25 to "skull_mask", 0x26 to "spooky_mask", 0x27 to "bunny_hood",
        0x28 to "goron_mask", 0x29 to "zora_mask", 0x2A to "gerudo_mask", 0x2B to "mask_of_truth")
    private val mmItems = mapOf(0 to "ocarina_of_time", 1 to "bow", 2 to "fire_arrows",
        3 to "ice_arrows", 4 to "light_arrows", 7 to "bombchu", 8 to "deku_stick",
        9 to "deku_nut", 10 to "magic_bean", 13 to "pictobox", 14 to "lens_of_truth",
        15 to "hookshot", 16 to "great_fairy_sword")
    private val masks = listOf("deku_mask", "goron_mask", "zora_mask", "fierce_deity_mask",
        "mask_of_truth", "kafei_mask", "all_night_mask", "bunny_hood", "keaton_mask", "garo_mask",
        "romani_mask", "circus_leader_mask", "postman_hat", "couples_mask", "great_fairy_mask",
        "gibdo_mask", "don_gero_mask", "kamaro_mask", "captains_hat", "stone_mask", "bremen_mask",
        "blast_mask", "mask_of_scents", "giants_mask")
    private val ootSongs = mapOf(6 to "minuet_of_forest", 7 to "bolero_of_fire", 8 to "serenade_of_water",
        9 to "requiem_of_spirit", 10 to "nocturne_of_shadow", 11 to "prelude_of_light",
        12 to "zeldas_lullaby", 13 to "eponas_song", 14 to "sarias_song", 15 to "sun_song",
        16 to "song_of_time", 17 to "song_of_storms")
    private val mmSongs = mapOf(6 to "sonata_of_awakening", 7 to "goron_lullaby", 8 to "new_wave_bossa_nova",
        9 to "elegy_of_emptiness", 10 to "oath_to_order", 12 to "song_of_time", 13 to "song_of_healing",
        14 to "eponas_song", 15 to "song_of_soaring", 16 to "song_of_storms")

    /** Merges observed progress and returns true only when the matching game state changed. */
    fun apply(snapshot: AutoTrackerSnapshot, state: TrackerState): Boolean {
        if (state.game != snapshot.game) return false
        var changed = false
        fun mark(id: String, level: Int = 1) {
            if (level > (state.obtainedItems[id] ?: 0)) {
                state.obtainedItems[id] = level
                changed = true
            }
        }
        val oot = snapshot.game == TrackerGame.OOT
        snapshot.inventory.forEach { item -> (if (oot) ootItems else mmItems)[item]?.let { mark(it) } }
        mark("bow", snapshot.upgrade(0).coerceAtMost(3))
        mark("bomb_bag", snapshot.upgrade(3).coerceAtMost(3))
        mark("rupees", snapshot.upgrade(12, 3).coerceAtMost(2))
        mark(if (oot) "magic" else "magic_power", snapshot.magicLevel)
        if (oot) {
            if (7 in snapshot.inventory) mark("ocarina")
            if (8 in snapshot.inventory) mark("ocarina", 2)
            if (10 in snapshot.inventory) mark("hookshot_longshot")
            if (11 in snapshot.inventory) mark("hookshot_longshot", 2)
            mark("slingshot", snapshot.upgrade(14).coerceAtMost(3))
            mark("strength", snapshot.upgrade(6).coerceAtMost(3))
            mark("scale", snapshot.upgrade(9).coerceAtMost(2))
            // Tracker has two capacity variants (20/30 sticks, 30/40 nuts).
            mark("deku_stick", (snapshot.upgrade(17) - 1).coerceIn(0, 2))
            mark("deku_nut", (snapshot.upgrade(20) - 1).coerceIn(0, 2))
            mapOf(0 to "kokiri_sword", 1 to "master_sword",
                4 to "deku_shield", 5 to "hylian_shield", 6 to "mirror_shield",
                8 to "kokiri_tunic", 9 to "goron_tunic", 10 to "zora_tunic",
                13 to "iron_boots", 14 to "hover_boots").forEach { (bit, id) ->
                if (snapshot.equipment and (1 shl bit) != 0) mark(id)
            }
            if (snapshot.biggoronOwned) mark("biggoron_sword")
            listOf("forest_medallion", "fire_medallion", "water_medallion", "spirit_medallion",
                "shadow_medallion", "light_medallion").forEachIndexed { bit, id -> if (snapshot.hasQuestFlag(bit)) mark(id) }
            listOf("kokiri_emerald", "goron_ruby", "zora_sapphire").forEachIndexed { i, id -> if (snapshot.hasQuestFlag(18 + i)) mark(id) }
        } else {
            mark("sword", (snapshot.equipment and 15).coerceAtMost(3))
            mark("shield", ((snapshot.equipment ushr 4) and 15).coerceAtMost(2))
            masks.forEachIndexed { i, id -> if (i + 0x32 in snapshot.inventory) mark(id) }
            listOf("odolwa_remains", "goht_remains", "gyorg_remains", "twinmold_remains").forEachIndexed { bit, id -> if (snapshot.hasQuestFlag(bit)) mark(id) }
            if (snapshot.hasQuestFlag(18)) mark("bombers_notebook")
        }
        (if (oot) ootSongs else mmSongs).forEach { (bit, id) ->
            if (snapshot.hasQuestFlag(bit) && state.foundSongs.add(id)) changed = true
        }
        return changed
    }
}
