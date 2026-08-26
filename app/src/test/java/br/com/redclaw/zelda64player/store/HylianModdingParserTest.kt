package br.com.redclaw.zelda64player.store

import br.com.redclaw.zelda64player.data.model.ChangelogEntry
import br.com.redclaw.zelda64player.data.model.HackEntry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HylianModdingParserTest {

    private val parser = HylianModdingParser()
    private val baseUrl = HylianModdingParser.HM_BASE_URL

    @Test
    fun parseIndexReturnsSlugs() {
        val json = JSONObject().apply {
            put("mods", org.json.JSONArray().apply {
                put("foo"); put("bar"); put("")
            })
        }.toString()
        assertEquals(listOf("foo", "bar"), parser.parseIndex(json))
    }

    @Test
    fun parseModDirectPatchResolvesRelativeUrl() {
        val json = modJson(
            id = "star_fox",
            downloadLink = "/mods/star_fox/downloads/StarFox.bps",
            supported = listOf("OoT"),
            thumbnail = "/mods/star_fox/screenshots/thumbnail.jpg",
            screenshots = listOf("/mods/star_fox/screenshots/a.jpg", "", "/mods/star_fox/screenshots/b.jpg")
        )
        val entry = parser.parseMod(json, baseUrl, "mods")!!
        assertEquals("hm_star_fox", entry.id)
        assertEquals("hylianmodding", entry.storeId)
        assertEquals("mods", entry.sourceCatalogId)
        assertTrue(entry.downloadTarget is DownloadTarget.DirectPatch)
        val patch = (entry.downloadTarget as DownloadTarget.DirectPatch).patch
        assertEquals("https://hylianmodding.com/mods/star_fox/downloads/StarFox.bps", patch.url)
        assertEquals("StarFox.bps", patch.filename)
        assertEquals("CZLE", entry.baseRom.gameCode)
        assertEquals("https://hylianmodding.com/mods/star_fox/screenshots/thumbnail.jpg", entry.coverImageUrl)
        assertEquals(2, entry.screenshots.size)
        assertEquals("OoT", entry.supportedGames)
    }

    @Test
    fun parseModNoLeadingSlashRelativeUrl() {
        val json = modJson(
            id = "x",
            downloadLink = "mods/x/downloads/x.bps",
            supported = listOf("MM")
        )
        val entry = parser.parseMod(json, baseUrl, "mods")!!
        val patch = (entry.downloadTarget as DownloadTarget.DirectPatch).patch
        assertEquals("https://hylianmodding.com/mods/x/downloads/x.bps", patch.url)
        assertEquals("NSME", entry.baseRom.gameCode)
    }

    @Test
    fun parseModGitHubLinkBecomesGitHubRelease() {
        val json = modJson(
            id = "indigo",
            downloadLink = "https://github.com/krm01/oot-indigo-mod/releases",
            supported = listOf("OoT")
        )
        val entry = parser.parseMod(json, baseUrl, "mods")!!
        assertTrue(entry.downloadTarget is DownloadTarget.GitHubRelease)
        assertEquals(
            "https://github.com/krm01/oot-indigo-mod/releases",
            (entry.downloadTarget as DownloadTarget.GitHubRelease).repoUrl
        )
    }

    @Test
    fun parseModUnsupportedArchiveBecomesExternalLink() {
        val json = modJson(
            id = "big",
            downloadLink = "https://hylianmodding.com/mods/big/downloads/big.7z",
            supported = listOf("OoT")
        )
        val entry = parser.parseMod(json, baseUrl, "mods")!!
        assertTrue(entry.downloadTarget is DownloadTarget.ExternalLink)
    }

    @Test
    fun parseModAbsoluteUrlLeftUntouched() {
        val json = modJson(
            id = "x",
            downloadLink = "https://example.com/patch.bps",
            supported = listOf("OoT")
        )
        val entry = parser.parseMod(json, baseUrl, "mods")!!
        val patch = (entry.downloadTarget as DownloadTarget.DirectPatch).patch
        assertEquals("https://example.com/patch.bps", patch.url)
    }

    @Test
    fun parseModTolerantMissingFields() {
        val json = JSONObject().apply {
            put("id", "minimal")
            put("name", "Minimal Mod")
        }.toString()
        val entry = parser.parseMod(json, baseUrl, "mods")!!
        assertEquals("hm_minimal", entry.id)
        assertEquals("", entry.description)
        assertEquals("1.0", entry.version)
        assertNull(entry.downloadTarget)
        assertEquals("Unknown", entry.baseRom.name)
    }

    @Test
    fun parseModChangelogAndVideos() {
        val json = modJson(
            id = "x",
            downloadLink = "https://example.com/x.bps",
            supported = listOf("OoT"),
            changelog = listOf(ChangelogEntry("2024-01-01", "First release"))
        )
        val entry = parser.parseMod(json, baseUrl, "mods")!!
        assertEquals(1, entry.changelog.size)
        assertEquals("First release", entry.changelog[0].content)
        assertTrue(entry.videos.isEmpty())
    }

    private fun modJson(
        id: String,
        downloadLink: String,
        supported: List<String> = emptyList(),
        thumbnail: String? = null,
        screenshots: List<String> = emptyList(),
        changelog: List<ChangelogEntry> = emptyList()
    ): String = JSONObject().apply {
        put("id", id)
        put("name", "Mod $id")
        put("authors", org.json.JSONArray().apply { put("Author One"); put("Author Two") })
        put("description", "A description")
        put("download_link", downloadLink)
        put("supported_games", org.json.JSONArray(supported))
        thumbnail?.let { put("thumbnail_image", it) }
        put("screenshots", org.json.JSONArray(screenshots))
        if (changelog.isNotEmpty()) {
            put("changelog", org.json.JSONArray().apply {
                changelog.forEach {
                    put(JSONObject().apply {
                        put("date", it.date)
                        put("content", it.content)
                    })
                }
            })
        }
    }.toString()
}
