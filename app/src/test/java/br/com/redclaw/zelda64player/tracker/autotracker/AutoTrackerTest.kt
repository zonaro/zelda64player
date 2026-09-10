package br.com.redclaw.zelda64player.tracker.autotracker

import br.com.redclaw.zelda64player.tracker.autotracker.mapper.AutoTrackerMapper
import br.com.redclaw.zelda64player.tracker.autotracker.model.AutoTrackerSnapshot
import br.com.redclaw.zelda64player.tracker.autotracker.parser.SaveContextParser
import br.com.redclaw.zelda64player.tracker.data.OotItemDatabase
import br.com.redclaw.zelda64player.tracker.model.TrackerGame
import br.com.redclaw.zelda64player.tracker.model.TrackerState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class AutoTrackerTest {
    private fun fixture(game: TrackerGame, lane: Int = 0): ByteBuffer {
        val b = ByteBuffer.allocate(0x4000)
        val oot = game == TrackerGame.OOT
        val signature = if (oot) "ZELDAZ" else "ZELDA3"
        signature.forEachIndexed { i, c -> b.put(((if (oot) 0x1C else 0x24) + i) xor lane, c.code.toByte()) }
        b.put((if (oot) 0x2F else 0x35) xor lane, 0x30)
        repeat(if (oot) 24 else 48) { b.put(((if (oot) 0x74 else 0x70) + it) xor lane, 0xFF.toByte()) }
        return b
    }

    @Test fun parsesOotAcrossAllByteLanesWithoutChangingBufferState() {
        for (lane in listOf(0, 1, 3)) {
            val b = fixture(TrackerGame.OOT, lane)
            b.put(0x77 xor lane, 3)
            b.put(0x9D xor lane, 1)
            b.put(0xA2 xor lane, 0x10) // adult wallet
            b.put(0xA6 xor lane, 0x10) // lullaby, bit12
            b.position(42)
            b.order(ByteOrder.LITTLE_ENDIAN)
            val snapshot = SaveContextParser.parse(b, TrackerGame.OOT, 0)!!
            assertEquals(3, snapshot.inventory[3])
            assertEquals(1, snapshot.equipment)
            assertEquals(1, snapshot.upgrade(12, 3))
            assertTrue(snapshot.hasQuestFlag(12))
            assertEquals(42, b.position())
            assertEquals(ByteOrder.LITTLE_ENDIAN, b.order())
        }
    }

    @Test fun parsesMmSeparateInventoryMasksEquipmentUpgradesAndMagic() {
        val b = fixture(TrackerGame.MM)
        b.put(0x70, 1)
        b.put(0x88, 0x32)
        b.putShort(0x6C, 0x23)
        b.putInt(0xB8, 3 or (2 shl 3))
        b.putInt(0xBC, (1 shl 6) or 1)
        b.put(0x40, 1); b.put(0x41, 1)
        val snapshot = SaveContextParser.parse(b, TrackerGame.MM, 0)!!
        val state = TrackerState(TrackerGame.MM)
        assertTrue(AutoTrackerMapper.apply(snapshot, state))
        assertEquals(48, snapshot.inventory.size)
        assertEquals(1, state.obtainedItems["deku_mask"])
        assertEquals(3, state.obtainedItems["sword"])
        assertEquals(2, state.obtainedItems["shield"])
        assertEquals(3, state.obtainedItems["bow"])
        assertEquals(2, state.obtainedItems["magic_power"])
        assertEquals(1, state.obtainedItems["odolwa_remains"])
        assertTrue("sonata_of_awakening" in state.foundSongs)
    }

    @Test fun rejectsInvalidBoundsSignatureAndTitleScreen() {
        assertNull(SaveContextParser.parse(ByteBuffer.allocate(32), TrackerGame.OOT, 0))
        assertNull(SaveContextParser.parse(fixture(TrackerGame.OOT), TrackerGame.OOT, -4))
        assertNull(SaveContextParser.parse(fixture(TrackerGame.OOT), TrackerGame.OOT, Int.MAX_VALUE))
        assertNull(SaveContextParser.parse(ByteBuffer.allocate(0x4000), TrackerGame.OOT, 0))
        for (game in TrackerGame.values()) {
            val b = fixture(game)
            b.putInt(if (game == TrackerGame.OOT) 0x135C else 0x3CA8, 1)
            assertNull(SaveContextParser.parse(b, game, 0))
        }
    }

    @Test fun acceptsEmptyInventoryAndIgnoresUnknownIds() {
        val snapshot = SaveContextParser.parse(fixture(TrackerGame.OOT), TrackerGame.OOT, 0)!!
        val state = TrackerState(TrackerGame.OOT)
        assertFalse(AutoTrackerMapper.apply(snapshot.copy(inventory = listOf(0xFE, 0xFF)), state))
        assertTrue(state.obtainedItems.isEmpty())
    }

    @Test fun preservesManualProgressHintsChecksAndTimerAndSeparatesGames() {
        val state = TrackerState(TrackerGame.OOT)
        state.obtainedItems["bow"] = 3
        state.checkedLocations.add("manual")
        state.timerElapsedMs = 1234
        val snapshot = AutoTrackerSnapshot(TrackerGame.OOT, listOf(3, 11), 1, 1, 1 shl 12, 0)
        assertTrue(AutoTrackerMapper.apply(snapshot, state))
        assertEquals(3, state.obtainedItems["bow"])
        assertEquals(2, state.obtainedItems["hookshot_longshot"])
        assertEquals(1, state.obtainedItems["kokiri_sword"])
        assertTrue("zeldas_lullaby" in state.foundSongs)
        assertFalse(AutoTrackerMapper.apply(snapshot, state))
        assertFalse(AutoTrackerMapper.apply(snapshot.copy(game = TrackerGame.MM), state))
        assertEquals(setOf("manual"), state.checkedLocations)
        assertEquals(1234L, state.timerElapsedMs)
    }

    @Test fun mappedIdsAndLevelsExistInCatalogs() {
        for (game in TrackerGame.values()) {
            val state = TrackerState(game)
            AutoTrackerMapper.apply(AutoTrackerSnapshot(game, (0..255).toList(), 0x7777, 0xFFFFFF, -1, 2, true), state)
            val catalog = OotItemDatabase.forGame(game)
            state.obtainedItems.forEach { (id, level) ->
                val item = catalog.items.single { it.id == id }
                assertTrue("$game $id=$level", level in 1..item.maxCount)
            }
            assertTrue(catalog.songs.map { it.id }.containsAll(state.foundSongs))
        }
    }

    @Test fun throttlesDiffsAndReemitsAfterDisable() {
        val base = SaveContextParser.base(TrackerGame.OOT)
        val ram = ByteBuffer.allocate(base + 0x4000)
        val f = fixture(TrackerGame.OOT)
        repeat(f.capacity()) { ram.put(base + it, f.get(it)) }
        var now = 0L
        var enabled = true
        val snapshots = mutableListOf<AutoTrackerSnapshot>()
        val poller = AutoTrackerPoller(ram, TrackerGame.OOT, { enabled }, { snapshots.add(it) }, { now })
        poller.onFrame()
        ram.put(base + 0x77, 3)
        now = 99_000_000; poller.onFrame()
        assertEquals(1, snapshots.size)
        now = 100_000_000; poller.onFrame()
        assertEquals(2, snapshots.size)
        now = 200_000_000; poller.onFrame()
        assertEquals(2, snapshots.size)
        enabled = false; now = 300_000_000; poller.onFrame()
        enabled = true; now = 400_000_000; poller.onFrame()
        assertEquals(3, snapshots.size)
        poller.invalidate() // OFF/ON before another emulated frame
        now = 500_000_000; poller.onFrame()
        assertEquals(4, snapshots.size)
    }
}
