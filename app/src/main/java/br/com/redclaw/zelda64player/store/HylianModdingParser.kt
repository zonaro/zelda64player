package br.com.redclaw.zelda64player.store

import br.com.redclaw.zelda64player.data.model.BaseRomRef
import br.com.redclaw.zelda64player.data.model.Checksums
import br.com.redclaw.zelda64player.data.model.ChangelogEntry
import br.com.redclaw.zelda64player.data.model.HackEntry
import br.com.redclaw.zelda64player.data.model.PatchRef
import org.json.JSONArray
import org.json.JSONObject

/**
 * Tolerant parser for the Hylian Modding `mod.json` format. The format is fully
 * optional except `id`/`name`; every other field is read defensively so a
 * single malformed mod cannot crash the store fetch.
 *
 * Relative URLs (thumbnail, screenshots, download link) are resolved against
 * [HM_BASE_URL]; both leading-slash (`/mods/x/y.png`) and no-leading-slash
 * (`mods/x/y.png`) relative forms are handled, while absolute `http(s)` URLs are
 * left untouched.
 *
 * Hack ids are namespaced with the `hm_` prefix to avoid colliding with PICKS
 * ids (which are bare slugs).
 */
class HylianModdingParser {

    companion object {
        const val HM_BASE_URL = "https://hylianmodding.com"
    }

    /** Parse a competition/main index JSON into the list of mod slugs. */
    fun parseIndex(json: String): List<String> = runCatching {
        val root = JSONObject(json)
        val arr = root.optJSONArray("mods") ?: JSONArray()
        (0 until arr.length()).mapNotNull { i ->
            arr.optString(i, null)?.takeIf { it.isNotBlank() }
        }
    }.getOrDefault(emptyList())

    /**
     * Parse a single mod document into a [HackEntry]. Returns null if the
     * required `id`/`name` are missing or parsing fails.
     *
     * @param json raw mod.json text
     * @param baseUrl base URL used to resolve relative asset paths
     * @param sourceCatalogId the source slug (e.g. "mods" or a competition slug)
     */
    fun parseMod(json: String, baseUrl: String, sourceCatalogId: String): HackEntry? = runCatching {
        val o = JSONObject(json)
        val id = o.optString("id", null) ?: return null
        val name = o.optString("name", null) ?: return null

        val authors = jsonToStringList(o.optJSONArray("authors"))
        val author = authors.joinToString(", ")

        val supportedGamesList = jsonToStringList(o.optJSONArray("supported_games"))
        val supportedGames = supportedGamesList.joinToString(", ").takeIf { it.isNotBlank() }

        val thumbnail = o.optString("thumbnail_image", null)
            ?.takeIf { it.isNotBlank() }
            ?.let { resolveUrl(baseUrl, it) }

        val screenshots = jsonToStringList(o.optJSONArray("screenshots"))
            .mapNotNull { if (it.isBlank()) null else resolveUrl(baseUrl, it) }

        val videos = collectVideos(o)

        val downloadLink = o.optString("download_link", null)?.takeIf { it.isNotBlank() }
        val downloadTarget = downloadLink?.let { buildDownloadTarget(baseUrl, it) }

        val changelog = if (o.has("changelog")) {
            val arr = o.optJSONArray("changelog")
            (0 until (arr?.length() ?: 0)).mapNotNull { i ->
                runCatching { ChangelogEntry.fromJson(arr!!.getJSONObject(i)) }.getOrNull()
            }
        } else emptyList()

        HackEntry(
            id = "hm_$id",
            name = name,
            description = o.optString("description", ""),
            author = author,
            version = o.optString("version", "1.0"),
            baseRom = deriveBaseRom(supportedGamesList),
            patch = (downloadTarget as? DownloadTarget.DirectPatch)?.patch,
            coverImageUrl = thumbnail,
            tags = listOfNotNull(o.optString("category", null)?.takeIf { it.isNotBlank() }),
            compatibleCores = emptyList(),
            storeId = "hylianmodding",
            sourceCatalogId = sourceCatalogId,
            screenshots = screenshots,
            videos = videos,
            completionStatus = o.optString("completion_status", null)?.takeIf { it.isNotBlank() },
            supportedGames = supportedGames,
            lastUpdated = o.optString("last_updated", null)?.takeIf { it.isNotBlank() },
            changelog = changelog,
            downloadTarget = downloadTarget
        )
    }.getOrNull()

    /** Map supported games to a base ROM reference (empty checksums; BPS source CRC drives matching). */
    private fun deriveBaseRom(supported: List<String>): BaseRomRef {
        val game = supported.firstOrNull()
        return when {
            game.equals("OoT", ignoreCase = true) -> BaseRomRef(
                name = "Ocarina of Time", gameCode = "CZLE", versionByte = -1,
                checksums = Checksums("", null, null)
            )
            game.equals("MM", ignoreCase = true) -> BaseRomRef(
                name = "Majora's Mask", gameCode = "NSME", versionByte = -1,
                checksums = Checksums("", null, null)
            )
            else -> BaseRomRef(
                name = "Unknown", gameCode = "", versionByte = -1,
                checksums = Checksums("", null, null)
            )
        }
    }

    /** Build a [DownloadTarget] from a raw download link. */
    private fun buildDownloadTarget(baseUrl: String, link: String): DownloadTarget {
        val lower = link.lowercase()
        return when {
            lower.contains("github.com") -> {
                // Normalize to the releases page so the resolver can list assets.
                val normalized = if (lower.contains("/releases")) link else "$link/releases"
                DownloadTarget.GitHubRelease(normalized)
            }
            lower.endsWith(".bps") || lower.endsWith(".ips") ||
                lower.endsWith(".xdelta") || lower.endsWith(".zip") -> {
                val abs = resolveUrl(baseUrl, link)
                val filename = abs.substringAfterLast('/').takeIf { it.isNotBlank() } ?: abs
                DownloadTarget.DirectPatch(
                    PatchRef(url = abs, filename = filename, size = 0, checksums = Checksums("", null, null))
                )
            }
            else -> DownloadTarget.ExternalLink(resolveUrl(baseUrl, link))
        }
    }

    /** Resolve [path] against [baseUrl]; absolute http(s) URLs are returned unchanged. */
    private fun resolveUrl(baseUrl: String, path: String): String {
        val trimmed = path.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return trimmed
        }
        val cleanBase = baseUrl.trimEnd('/')
        return if (trimmed.startsWith("/")) "$cleanBase$trimmed" else "$cleanBase/$trimmed"
    }

    /** Tolerant video collection: capture video/youtube/trailer keys if present. */
    private fun collectVideos(o: JSONObject): List<String> {
        val result = mutableListOf<String>()
        for (key in listOf("video", "videos", "youtube", "trailer")) {
            when (val v = o.opt(key)) {
                is String -> if (v.isNotBlank()) result.add(v)
                is JSONArray -> result.addAll(jsonToStringList(v).filter { it.isNotBlank() })
            }
        }
        return result
    }

    private fun jsonToStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i -> arr.optString(i, null)?.takeIf { it.isNotBlank() } }
    }
}
