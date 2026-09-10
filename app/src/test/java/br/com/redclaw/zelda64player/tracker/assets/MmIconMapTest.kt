package br.com.redclaw.zelda64player.tracker.assets

import br.com.redclaw.zelda64player.tracker.assets.mapping.DmaTableOffsets
import br.com.redclaw.zelda64player.tracker.assets.mapping.IconArchiveFormat
import br.com.redclaw.zelda64player.tracker.assets.mapping.MmIconMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MmIconMapTest {

        @Test
        fun `uses MM YAR dmadata entries`() {
                assertEquals(19, DmaTableOffsets.MM_ICON_FILE_INDEX)
                assertEquals(20, DmaTableOffsets.MM_QUEST_ICON_FILE_INDEX)
                assertTrue(MmIconMap.entries.all { it.archiveFormat == IconArchiveFormat.YAR })
        }

        @Test
        fun `maps representative MM textures to decomp offsets`() {
                val icons = MmIconMap.byId

                assertEquals(0x00000, icons.getValue("ocarina_of_time").offset)
                assertEquals(0x0F000, icons.getValue("hookshot").offset)
                assertEquals(0x32000, icons.getValue("deku_mask").offset)
                assertEquals(0x4D000, icons.getValue("sword").offset)
                assertEquals(0x52000, icons.getValue("shield_2").offset)
                assertEquals(0x5D000, icons.getValue("odolwa_remains").offset)
                assertEquals(0x61000, icons.getValue("bombers_notebook").offset)
        }

        @Test
        fun `includes extracted variants for MM cyclic items`() {
                val keys = MmIconMap.byId.keys

                assertTrue(keys.containsAll(listOf("sword_2", "sword_3")))
                assertTrue(keys.contains("shield_2"))
                assertTrue(keys.containsAll(listOf("bow_2", "bow_3")))
                assertTrue(keys.contains("magic_power_2"))
                assertTrue(keys.contains("rupees_2"))
                assertEquals(MmIconMap.entries.size, keys.size)
        }
}
