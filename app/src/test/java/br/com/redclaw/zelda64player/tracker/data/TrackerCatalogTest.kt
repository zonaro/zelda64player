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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackerCatalogTest {

    @Test
    fun ootProgressionsAreSingleCyclicItems() {
        val items = OotItemDatabase.items.associateBy { it.id }

        assertCycle(items.getValue("strength"), 3)
        assertCycle(items.getValue("bow"), 3)
        assertCycle(items.getValue("rupees"), 2)
        assertCycle(items.getValue("deku_stick"), 2)
        assertCycle(items.getValue("deku_nut"), 2)
        assertCycle(items.getValue("magic"), 2)
        assertCycle(items.getValue("scale"), 2)
        assertFalse(items.containsKey("goron_bracelet"))
        assertFalse(items.containsKey("silver_gauntlets"))
        assertFalse(items.containsKey("golden_gauntlets"))
    }

    @Test
    fun ootUsesRequestedMmSpritesAndIncludesItsCompletionItems() {
        val items = OotItemDatabase.items.associateBy { it.id }

        assertEquals(R.drawable.mm_bomb_bag, items.getValue("bomb_bag").iconRes)
        assertEquals(R.drawable.mm_lens_of_truth, items.getValue("lens_of_truth").iconRes)
        assertEquals(R.drawable.mm_fire_arrow, items.getValue("fire_arrows").iconRes)
        assertEquals(R.drawable.mm_ice_arrow, items.getValue("ice_arrows").iconRes)
        assertEquals(R.drawable.mm_light_arrow, items.getValue("light_arrows").iconRes)
        assertEquals(R.drawable.kokiri_shield, items.getValue("deku_shield").iconRes)

        val medallions = items.keys.filter { it.endsWith("_medallion") }
        val masks =
                items.keys.filter {
                    it.endsWith("_mask") || it.startsWith("mask_of_") || it == "bunny_hood"
                }
        assertEquals(6, medallions.size)
        assertEquals(8, masks.size)
        assertTrue(items.containsKey("bombchu"))
    }

    @Test
    fun mmHasItsOwnLegalInventoryAndAllMasks() {
        val items = MmItemDatabase.items.associateBy { it.id }

        assertCycle(items.getValue("sword"), 3)
        assertCycle(items.getValue("shield"), 2)
        assertCycle(items.getValue("bow"), 3)
        assertCycle(items.getValue("rupees"), 2)
        assertCycle(items.getValue("deku_stick"), 1)
        assertCycle(items.getValue("deku_nut"), 1)
        assertCycle(items.getValue("magic_power"), 2)
        assertFalse(items.containsKey("iron_boots"))
        assertFalse(items.containsKey("hover_boots"))
        assertTrue(items.containsKey("bombchu"))

        val remains = items.keys.filter { it.endsWith("_remains") }
        val masks =
                items.keys.filter {
                    it.endsWith("_mask") ||
                            it.startsWith("mask_of_") ||
                            it == "bunny_hood" ||
                            it == "postman_hat" ||
                            it == "captains_hat"
                }
        assertEquals(4, remains.size)
        assertEquals(24, masks.size)
    }

    private fun assertCycle(item: TrackerItem, level: Int) {
        assertEquals(level, item.maxCount)
        assertEquals(level, item.cycleLabels.size)
        assertEquals(level, item.cycleIcons.size)
    }
}
