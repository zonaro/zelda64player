package br.com.redclaw.zelda64player.store

import br.com.redclaw.zelda64player.data.model.HackEntry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogEntrySerializationTest {

    @Test
    fun picksParserStampsStoreId() {
        val json = JSONObject().apply {
            put("storeName", "Main Store")
            put("catalogVersion", 1)
            put("hacks", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("id", "my_hack")
                    put("name", "My Hack")
                    put("description", "desc")
                    put("author", "auth")
                    put("version", "1.0")
                    put("baseRom", JSONObject().apply {
                        put("name", "ROM"); put("gameCode", "CZLE"); put("versionByte", 0)
                        put("checksums", JSONObject().apply { put("crc32", "abc") })
                    })
                    put("patch", JSONObject().apply {
                        put("url", "https://x/y.bps"); put("filename", "y.bps")
                        put("size", 1); put("checksums", JSONObject().apply { put("crc32", "def") })
                    })
                })
            })
        }.toString()
        val parser = PicksCatalogParser()
        val hacks = parser.parse(json)
        assertEquals(1, hacks.size)
        assertEquals("picks", hacks[0].storeId)
        assertEquals("picks", hacks[0].sourceCatalogId)
        assertEquals("Main Store", parser.storeName(json))
    }

    @Test
    fun hackEntryDownloadTargetRoundTrips() {
        val original = HackEntry(
            id = "x",
            name = "X",
            description = "d",
            author = "a",
            version = "1.0",
            baseRom = br.com.redclaw.zelda64player.data.model.BaseRomRef(
                "OoT", "CZLE", -1,
                br.com.redclaw.zelda64player.data.model.Checksums("", null, null)
            ),
            storeId = "picks",
            sourceCatalogId = "picks",
            screenshots = listOf("https://x/a.jpg"),
            supportedGames = "OoT",
            completionStatus = "Complete",
            changelog = listOf(
                br.com.redclaw.zelda64player.data.model.ChangelogEntry("2024", "done")
            ),
            downloadTarget = DownloadTarget.GitHubRelease("https://github.com/o/r/releases")
        )
        val json = original.toJson().toString()
        val restored = HackEntry.fromJson(JSONObject(json))
        assertEquals("picks", restored.storeId)
        assertEquals("picks", restored.sourceCatalogId)
        assertEquals(listOf("https://x/a.jpg"), restored.screenshots)
        assertEquals("OoT", restored.supportedGames)
        assertEquals("Complete", restored.completionStatus)
        assertEquals(1, restored.changelog.size)
        assertTrue(restored.downloadTarget is DownloadTarget.GitHubRelease)
        assertEquals(
            "https://github.com/o/r/releases",
            (restored.downloadTarget as DownloadTarget.GitHubRelease).repoUrl
        )
    }

    @Test
    fun hackEntryDirectPatchTargetRoundTrips() {
        val patch = br.com.redclaw.zelda64player.data.model.PatchRef(
            "https://x/y.bps", "y.bps", 10,
            br.com.redclaw.zelda64player.data.model.Checksums("abc", null, null)
        )
        val original = HackEntry(
            id = "y", name = "Y", description = "d", author = "a", version = "1.0",
            baseRom = br.com.redclaw.zelda64player.data.model.BaseRomRef(
                "MM", "NSME", -1,
                br.com.redclaw.zelda64player.data.model.Checksums("", null, null)
            ),
            downloadTarget = DownloadTarget.DirectPatch(patch)
        )
        val restored = HackEntry.fromJson(JSONObject(original.toJson().toString()))
        assertTrue(restored.downloadTarget is DownloadTarget.DirectPatch)
        assertEquals(
            "https://x/y.bps",
            (restored.downloadTarget as DownloadTarget.DirectPatch).patch.url
        )
    }

    @Test
    fun hackEntryLegacyNoTargetStillParses() {
        // A legacy Picks entry without downloadTarget must round-trip with patch intact.
        val original = HackEntry(
            id = "legacy", name = "L", description = "d", author = "a", version = "1.0",
            baseRom = br.com.redclaw.zelda64player.data.model.BaseRomRef(
                "ROM", "CZLE", 0,
                br.com.redclaw.zelda64player.data.model.Checksums("abc", null, null)
            ),
            patch = br.com.redclaw.zelda64player.data.model.PatchRef(
                "https://x/y.bps", "y.bps", 10,
                br.com.redclaw.zelda64player.data.model.Checksums("abc", null, null)
            )
        )
        val restored = HackEntry.fromJson(JSONObject(original.toJson().toString()))
        assertNull(restored.downloadTarget)
        assertNotNull(restored.patch)
        assertEquals("https://x/y.bps", restored.patch!!.url)
    }
}
