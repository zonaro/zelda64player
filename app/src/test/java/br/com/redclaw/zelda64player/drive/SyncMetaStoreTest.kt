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

package br.com.redclaw.zelda64player.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

/**
 * Tests for [SyncMetaStore] using an in-memory [KeyValueStore] so the persistence
 * logic runs on the JVM without Android.
 */
class SyncMetaStoreTest {

    /** Minimal thread-safe in-memory KeyValueStore for tests. */
    private class InMemoryStore : KeyValueStore {
        private val map = ConcurrentHashMap<String, String>()
        override fun getString(key: String): String? = map[key]
        override fun putString(key: String, value: String) {
            map[key] = value
        }
    }

    private fun newStore() = SyncMetaStore(InMemoryStore())

    @Test
    fun dirtySetStartsEmpty() {
        val store = newStore()
        assertTrue(store.getDirty().isEmpty())
    }

    @Test
    fun markDirtyAddsAndClearDirtyRemoves() {
        val store = newStore()
        store.markDirty("sram_hack1")
        store.markDirty("state_hack1")
        assertEquals(setOf("sram_hack1", "state_hack1"), store.getDirty())

        store.clearDirty("sram_hack1")
        assertEquals(setOf("state_hack1"), store.getDirty())
    }

    @Test
    fun markingSameFileDirtyIsIdempotent() {
        val store = newStore()
        store.markDirty("sram_hack1")
        store.markDirty("sram_hack1")
        assertEquals(setOf("sram_hack1"), store.getDirty())
    }

    @Test
    fun metaRoundTripsThroughJson() {
        val store = newStore()
        val meta = SyncMeta(
            filePath = "sram_hack1",
            crc32 = "deadbeef",
            lastModified = 123456L,
            size = 2048L,
            driveFileId = "file123",
            driveModifiedTime = "2026-08-26T12:00:00.000Z"
        )
        store.putMeta(meta)
        val loaded = store.getMeta("sram_hack1")
        assertEquals(meta, loaded)
    }

    @Test
    fun metaWithoutDriveIdRoundTrips() {
        val store = newStore()
        val meta = SyncMeta(
            filePath = "state_hack1",
            crc32 = "cafebabe",
            lastModified = 999L,
            size = 1024L,
            driveFileId = null,
            driveModifiedTime = null
        )
        store.putMeta(meta)
        val loaded = store.getMeta("state_hack1")
        assertEquals(meta, loaded)
        assertNull(loaded?.driveFileId)
    }

    @Test
    fun removeMetaDropsEntry() {
        val store = newStore()
        store.putMeta(
            SyncMeta("sram_hack1", "aa", 1L, 2L, "id", "2026-01-01T00:00:00.000Z")
        )
        store.removeMeta("sram_hack1")
        assertNull(store.getMeta("sram_hack1"))
    }

    @Test
    fun clearAllEmptiesBothStores() {
        val store = newStore()
        store.markDirty("sram_hack1")
        store.putMeta(SyncMeta("sram_hack1", "aa", 1L, 2L, "id", null))
        store.clearAll()
        assertTrue(store.getDirty().isEmpty())
        assertNull(store.getMeta("sram_hack1"))
    }

    @Test
    fun unknownFileReturnsNullMeta() {
        val store = newStore()
        assertNull(store.getMeta("does_not_exist"))
    }
}
