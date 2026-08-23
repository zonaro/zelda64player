package br.com.redclaw.zelda64player.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Validates the live catalog shipped in this repository
 * (catalog/catalog.json) with the exact parser the app uses in production.
 *
 * The tolerant parser silently drops entries missing required fields (e.g.
 * patch checksums not yet published), so this test asserts the invariant that
 * matters for users: every entry that survives parsing is fully downloadable
 * and validatable. Skips silently when the file is not present.
 */
class LiveCatalogTest {

    private fun liveCatalogFile(): File {
        // Unit tests run with working dir at <repo>/app; catalog lives one level up.
        val fromApp = File("../catalog/catalog.json")
        if (fromApp.exists()) return fromApp
        return File("catalog/catalog.json")
    }

    @Test
    fun liveCatalogParsesWithProductionParser() {
        val file = liveCatalogFile()
        assumeTrue("catalog/catalog.json not found; skipping", file.isFile)

        val catalog = HackCatalog.parse(file.readText())

        assertTrue("live catalog should contain at least one complete hack", catalog.hacks.isNotEmpty())
        assertEquals("hack ids must be unique", catalog.hacks.size, catalog.hacks.map { it.id }.distinct().size)
    }

    @Test
    fun everyParsedEntryIsFullyDownloadable() {
        val file = liveCatalogFile()
        assumeTrue("catalog/catalog.json not found; skipping", file.isFile)

        val catalog = HackCatalog.parse(file.readText())

        catalog.hacks.forEach { entry ->
            assertTrue("patch url must be https: ${entry.id}", entry.patch.url.startsWith("https://"))
            assertTrue("patch filename required: ${entry.id}", entry.patch.filename.isNotBlank())
            assertTrue("patch size must be positive: ${entry.id}", entry.patch.size > 0L)
            assertTrue(
                "patch crc32 required for download validation: ${entry.id}",
                entry.patch.checksums.crc32.isNotBlank()
            )
            assertTrue("base rom crc32 required: ${entry.id}", entry.baseRom.checksums.crc32.isNotBlank())
            assertTrue("gameCode must be 4 chars: ${entry.id}", entry.baseRom.gameCode.length == 4)
        }
    }
}
