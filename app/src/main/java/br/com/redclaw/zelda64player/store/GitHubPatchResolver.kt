package br.com.redclaw.zelda64player.store

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.regex.Pattern

/**
 * Resolves a concrete patch download URL from a GitHub releases page at
 * download time (not during catalog load, to keep store refresh fast and
 * offline-friendly).
 *
 * Given a `github.com/owner/repo` (or `.../releases`, `.../releases/tag/X`)
 * URL it queries the GitHub Releases API, scans the assets of all releases for
 * a patch-like file (`*.bps`, `*.ips`, `*.xdelta`, `*.zip`, case-insensitive),
 * preferring assets whose download path contains `dist/`, and returns the
 * chosen `browser_download_url`. On any failure or API rate-limit it returns
 * null so the caller can fall back to opening the page in a browser.
 *
 * Resolutions are cached in memory for the process lifetime.
 */
class GitHubPatchResolver(private val client: OkHttpClient = OkHttpClient.Builder().build()) {

    private val cache = mutableMapOf<String, String?>()
    private val patchNamePattern = Pattern.compile(".*\\.(bps|ips|xdelta|zip)$", Pattern.CASE_INSENSITIVE)
    private val ownerRepoPattern = Pattern.compile("github\\.com/([^/]+)/([^/?#]+)", Pattern.CASE_INSENSITIVE)

    /** Resolve [repoUrl] to a direct patch asset URL, or null if none found. */
    suspend fun resolve(repoUrl: String): String? = withContext(Dispatchers.IO) {
        cache[repoUrl]?.let { return@withContext it }

        val match = ownerRepoPattern.matcher(repoUrl)
        if (!match.find()) return@withContext cacheAndReturn(repoUrl, null)
        val owner = match.group(1)!!
        val repo = match.group(2)!!.removeSuffix(".git")

        val apiUrl = "https://api.github.com/repos/$owner/$repo/releases"
        val request = Request.Builder()
            .url(apiUrl)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Zelda64Player")
            .build()

        val body = try {
            val response = client.newCall(request).execute()
            try {
                if (!response.isSuccessful) return@withContext cacheAndReturn(repoUrl, null)
                response.body?.string().orEmpty()
            } finally {
                response.close()
            }
        } catch (_: Exception) {
            return@withContext cacheAndReturn(repoUrl, null)
        }

        val resolved = runCatching { pickAsset(body) }.getOrNull()
        cacheAndReturn(repoUrl, resolved)
    }

    private fun pickAsset(releasesJson: String): String? {
        val releases = runCatching { JSONArray(releasesJson) }.getOrNull() ?: return null
        val candidates = mutableListOf<String>()
        for (i in 0 until releases.length()) {
            val release = runCatching { releases.getJSONObject(i) }.getOrNull() ?: continue
            val assets = runCatching { release.getJSONArray("assets") }.getOrNull() ?: continue
            for (j in 0 until assets.length()) {
                val asset = runCatching { assets.getJSONObject(j) }.getOrNull() ?: continue
                val name = asset.optString("name", "")
                val url = asset.optString("browser_download_url", "")
                if (name.isBlank() || url.isBlank()) continue
                if (patchNamePattern.matcher(name).matches()) candidates.add(url)
            }
        }
        if (candidates.isEmpty()) return null
        // Prefer an asset served from a dist/ path.
        return candidates.firstOrNull { it.contains("/dist/", ignoreCase = true) } ?: candidates.first()
    }

    private fun cacheAndReturn(key: String, value: String?): String? {
        cache[key] = value
        return value
    }
}
