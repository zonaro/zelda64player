package br.com.redclaw.zelda64player.store

import android.content.res.AssetManager
import br.com.redclaw.zelda64player.data.model.Checksums
import br.com.redclaw.zelda64player.patcher.n64.ChecksumCalculator
import org.json.JSONObject

/**
 * Resolves a stable, store-agnostic "canonical" hack id from a raw catalog id,
 * so the same hack published under different ids across stores (e.g. PICKS
 * `the-missing-link` vs Hylian Modding `hm_themissinglink`) is treated as one.
 *
 * Resolution = explicit alias map lookup (HM -> PICKS) followed by slug
 * normalization. The alias map is loaded from `assets/aliases.json` at app
 * startup via [load]; unit tests inject it via [loadFromJson] / [setAliases].
 *
 * The canonical id is the key used for Library grouping, Store install-state
 * recognition, and [br.com.redclaw.zelda64player.data.model.HackEntry.isSameHack].
 */
object CanonicalIdResolver {

    private var aliasMap: Map<String, String> = emptyMap()

    /**
     * Normalize a raw id to its canonical slug form: lowercase, strip the `hm_`
     * namespace prefix, drop every non-alphanumeric character, and trim.
     */
    fun normalizeSlug(raw: String): String =
        raw.lowercase()
            .removePrefix("hm_")
            .replace(Regex("[^a-z0-9]+"), "")
            .trim()

    /**
     * Resolve [rawId] to its canonical id. [storeId] is accepted for API
     * symmetry (future per-store overrides) but the current algorithm is
     * store-agnostic: an explicit alias wins, otherwise the slug is normalized.
     */
    fun resolve(rawId: String, storeId: String): String {
        val aliased = aliasMap[rawId]
        return if (aliased != null) normalizeSlug(aliased) else normalizeSlug(rawId)
    }

    /**
     * Load the alias map from the app's `assets/aliases.json`. A missing asset
     * or malformed JSON degrades gracefully to an empty map (slug normalization
     * still works).
     */
    fun load(assetManager: AssetManager) {
        runCatching {
            assetManager.open("aliases.json").bufferedReader().use { loadFromJson(it.readText()) }
        }
    }

    /** Parse the alias map from a JSON string (used by tests and [load]). */
    fun loadFromJson(json: String) {
        runCatching {
            val obj = JSONObject(json)
            val aliases = obj.optJSONObject("aliases") ?: JSONObject()
            val map = mutableMapOf<String, String>()
            aliases.keys().forEach { key -> map[key] = aliases.getString(key) }
            aliasMap = map
        }
    }

    /** Directly set the alias map (test helper). */
    fun setAliases(map: Map<String, String>) {
        aliasMap = map
    }

    /** Test helper: reset to an empty alias map. */
    fun reset() {
        aliasMap = emptyMap()
    }

    /**
     * Compute CRC32/MD5/SHA-1 [Checksums] for an in-memory byte array (the
     * downloaded/imported BPS patch). Shared by [DownloadManager] and
     * [ImportedPatchInstaller] so the digest logic lives in exactly one place.
     */
    fun computePatchChecksums(bytes: ByteArray): Checksums {
        val crc = ChecksumCalculator.crc32(bytes)
        val md5 = ChecksumCalculator.md5(bytes)
        val sha1 = sha1Hex(bytes)
        return Checksums(crc, md5, sha1)
    }

    private fun sha1Hex(bytes: ByteArray): String {
        val md = java.security.MessageDigest.getInstance("SHA-1")
        return md.digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
