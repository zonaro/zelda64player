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

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class BackupFileCollectorTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var saveDir: File
    private lateinit var galleryDir: File

    @Before
    fun setUp() {
        saveDir = temp.newFolder("save")
        galleryDir = temp.newFolder("gallery")
    }

    @After
    fun tearDown() {
        // TemporaryFolder cleans up automatically.
    }

    private fun touch(file: File, ageMillis: Long = 0) {
        file.writeText("data")
        if (ageMillis > 0) {
            file.setLastModified(System.currentTimeMillis() - ageMillis)
        }
    }

    @Test
    fun collectsAllCategoriesWhenSinceIsZero() {
        touch(File(saveDir, "sram_hack1"))
        touch(File(saveDir, "state_hack1"))
        touch(File(galleryDir, "screenshot_hack1_1000.png"))
        touch(File(galleryDir, "recording_hack1_2000.mp4"))

        val items = collectBackupItems(
            saveDir, galleryDir,
            listOf("hack1"),
            BackupCategory.values().toSet(),
            sinceMillis = 0
        )

        assertEquals(4, items.size)
        assertTrue(items.any { it.remotePath == "saves/hack1/sram_hack1" })
        assertTrue(items.any { it.remotePath == "saves/hack1/state_hack1" })
        assertTrue(items.any { it.remotePath == "images/screenshot_hack1_1000.png" })
        assertTrue(items.any { it.remotePath == "videos/recording_hack1_2000.mp4" })
    }

    @Test
    fun onlyIncludesEnabledCategories() {
        touch(File(saveDir, "sram_hack1"))
        touch(File(galleryDir, "screenshot_hack1_1000.png"))

        val items = collectBackupItems(
            saveDir, galleryDir,
            listOf("hack1"),
            setOf(BackupCategory.IMAGES),
            sinceMillis = 0
        )

        assertEquals(1, items.size)
        assertEquals(BackupCategory.IMAGES, items[0].category)
        assertEquals("images/screenshot_hack1_1000.png", items[0].remotePath)
    }

    @Test
    fun incrementalBackupExcludesUnmodifiedFiles() {
        val old = File(saveDir, "sram_hack1")
        touch(old, ageMillis = 10_000)
        val recent = File(saveDir, "state_hack1")
        touch(recent)

        val since = System.currentTimeMillis() - 5_000
        val items = collectBackupItems(
            saveDir, galleryDir,
            listOf("hack1"),
            setOf(BackupCategory.SAVES),
            sinceMillis = since
        )

        assertEquals(1, items.size)
        assertEquals("state_hack1", items[0].localFile.name)
    }

    @Test
    fun missingSaveFilesAreSkipped() {
        // Only sram exists; state is absent.
        touch(File(saveDir, "sram_hack1"))

        val items = collectBackupItems(
            saveDir, galleryDir,
            listOf("hack1"),
            setOf(BackupCategory.SAVES),
            sinceMillis = 0
        )

        assertEquals(1, items.size)
        assertEquals("sram_hack1", items[0].localFile.name)
    }

    @Test
    fun ignoresNonMatchingGalleryFiles() {
        touch(File(galleryDir, "screenshot_hack1_1.png"))
        touch(File(galleryDir, "recording_hack1_2.mp4"))
        // Should be ignored: wrong prefix / extension.
        touch(File(galleryDir, "notes.txt"))
        touch(File(galleryDir, "screenshot_hack1.tmp"))

        val items = collectBackupItems(
            saveDir, galleryDir,
            listOf("hack1"),
            setOf(BackupCategory.IMAGES, BackupCategory.VIDEOS),
            sinceMillis = 0
        )

        assertEquals(2, items.size)
        assertFalse(items.any { it.localFile.name == "notes.txt" })
        assertFalse(items.any { it.localFile.name == "screenshot_hack1.tmp" })
    }

    @Test
    fun onlyCollectsSavesForRequestedHackIds() {
        touch(File(saveDir, "sram_hack1"))
        touch(File(saveDir, "sram_hack2"))

        val items = collectBackupItems(
            saveDir, galleryDir,
            listOf("hack1"),
            setOf(BackupCategory.SAVES),
            sinceMillis = 0
        )

        assertEquals(1, items.size)
        assertEquals("sram_hack1", items[0].localFile.name)
    }
}
