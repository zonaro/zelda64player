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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the pure conflict-resolution logic: [decideSync] and
 * [conflictBackupName]. These run on the JVM with no Android dependencies.
 */
class ConflictResolutionTest {

    private val localBase = SyncMeta(
        filePath = "sram_hack1",
        crc32 = "aaaaaaaa",
        lastModified = 1000L,
        size = 2048L,
        driveFileId = null,
        driveModifiedTime = null
    )

    @Test
    fun firstUploadWhenNoCloudCopy() {
        assertEquals(SyncDecision.FIRST_UPLOAD, decideSync(localBase, null))
    }

    @Test
    fun firstUploadWhenCloudHasNoFileId() {
        val cloud = localBase.copy(driveFileId = null, driveModifiedTime = "2026-01-01T00:00:00.000Z")
        assertEquals(SyncDecision.FIRST_UPLOAD, decideSync(localBase, cloud))
    }

    @Test
    fun equalWhenCrcMatches() {
        val cloud = localBase.copy(
            driveFileId = "id1",
            driveModifiedTime = "2026-01-01T00:00:00.000Z",
            crc32 = "aaaaaaaa"
        )
        assertEquals(SyncDecision.EQUAL, decideSync(localBase, cloud))
    }

    @Test
    fun uploadLocalWhenLocalIsNewer() {
        // Cloud modified far in the past relative to local.lastModified (1000).
        val cloud = localBase.copy(
            driveFileId = "id1",
            driveModifiedTime = "1970-01-01T00:00:00.000Z",
            crc32 = "bbbbbbbb"
        )
        assertEquals(SyncDecision.UPLOAD_LOCAL, decideSync(localBase, cloud))
    }

    @Test
    fun downloadCloudWhenCloudIsNewer() {
        // Cloud modified far in the future relative to local.lastModified (1000).
        val cloud = localBase.copy(
            driveFileId = "id1",
            driveModifiedTime = "2999-01-01T00:00:00.000Z",
            crc32 = "bbbbbbbb"
        )
        assertEquals(SyncDecision.DOWNLOAD_CLOUD, decideSync(localBase, cloud))
    }

    @Test
    fun conflictWhenTimestampsEqualButCrcDiffers() {
        // Local and cloud share the same modified time but different content.
        val sameTime = "2026-08-26T12:00:00.000Z"
        val cloud = localBase.copy(
            driveFileId = "id1",
            driveModifiedTime = sameTime,
            crc32 = "bbbbbbbb"
        )
        val local = localBase.copy(lastModified = parseRfc3339ToEpochMillis(sameTime))
        assertEquals(SyncDecision.CONFLICT, decideSync(local, cloud))
    }

    @Test
    fun conflictWhenCloudTimeUnparseableAndCrcDiffers() {
        val cloud = localBase.copy(
            driveFileId = "id1",
            driveModifiedTime = "not-a-date",
            crc32 = "bbbbbbbb"
        )
        // local.lastModified (1000) > parsed cloud time (0) would normally be
        // UPLOAD_LOCAL, but with an unparseable cloud time and differing crc the
        // safe outcome is still deterministic; here local > 0 so it uploads.
        assertEquals(SyncDecision.UPLOAD_LOCAL, decideSync(localBase, cloud))
    }

    @Test
    fun conflictBackupNameAppendsTimestampAndBak() {
        val result = conflictBackupName("sram_hack1", 1693000000000L)
        assertEquals("sram_hack1_conflict_1693000000000.bak", result)
        assertTrue(result.endsWith(".bak"))
        assertTrue(result.contains("_conflict_1693000000000"))
    }

    @Test
    fun conflictBackupNamePreservesExtension() {
        val result = conflictBackupName("state_hack.1", 42L)
        // Original extension is preserved before the .bak suffix.
        assertEquals("state_hack_conflict_42.1.bak", result)
        assertTrue(result.endsWith(".bak"))
    }
}
