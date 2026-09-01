package br.com.redclaw.zelda64player.data.model

import br.com.redclaw.zelda64player.ocarina.OcarinaSong
import br.com.redclaw.zelda64player.store.CanonicalIdResolver
import br.com.redclaw.zelda64player.store.DownloadTarget
import org.json.JSONArray
import org.json.JSONObject

/**
 * A single changelog entry of a catalog hack (tolerant: either field may be absent). Serialized as
 * a `{date, content}` object in the catalog JSON.
 */
data class ChangelogEntry(val date: String? = null, val content: String? = null) {
    fun toJson(): JSONObject =
            JSONObject().apply {
                put("date", date ?: JSONObject.NULL)
                put("content", content ?: JSONObject.NULL)
            }

    companion object {
        fun fromJson(o: JSONObject): ChangelogEntry =
                ChangelogEntry(
                        date = if (o.isNull("date")) null else o.optString("date", null),
                        content = if (o.isNull("content")) null else o.optString("content", null)
                )
    }
}

/** Upstream update metadata retained when a catalog is generated from a source feed. */
data class SourceMetadata(val timestamp: Long? = null, val isUpdate: Boolean? = null) {
    fun toJson(): JSONObject =
            JSONObject().apply {
                timestamp?.let { put("timestamp", it) }
                isUpdate?.let { put("isUpdate", it) }
            }

    companion object {
        fun fromJson(o: JSONObject): SourceMetadata =
                SourceMetadata(
                        timestamp =
                                if (o.has("timestamp") && !o.isNull("timestamp"))
                                        o.optLong("timestamp")
                                else null,
                        isUpdate =
                                if (o.has("isUpdate") && !o.isNull("isUpdate"))
                                        o.optBoolean("isUpdate")
                                else null
                )
    }
}

/** Provenance for records imported into the Main Store catalog. */
data class CatalogImportSource(
        val provider: String,
        val catalogId: String? = null,
        val modUrl: String? = null
) {
    fun toJson(): JSONObject =
            JSONObject().apply {
                put("provider", provider)
                catalogId?.let { put("catalogId", it) }
                modUrl?.let { put("modUrl", it) }
            }

    companion object {
        fun fromJson(o: JSONObject): CatalogImportSource =
                CatalogImportSource(
                        provider = o.getString("provider"),
                        catalogId =
                                if (o.has("catalogId") && !o.isNull("catalogId"))
                                        o.getString("catalogId")
                                else null,
                        modUrl =
                                if (o.has("modUrl") && !o.isNull("modUrl")) o.getString("modUrl")
                                else null
                )
    }
}

/** A developer / project link (GitHub, itch.io, etc.) for a hack. */
data class DeveloperLink(val label: String, val url: String) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("label", label)
        put("url", url)
    }

    companion object {
        fun fromJson(o: JSONObject): DeveloperLink = DeveloperLink(
            label = o.optString("label", "Site"),
            url = o.getString("url")
        )
    }
}

/** Reference to the base ROM a hack requires (user-supplied, never shipped). */
data class BaseRomRef(
        val name: String,
        val gameCode: String,
        val versionByte: Int,
        val checksums: Checksums
) {
    fun toJson(): JSONObject =
            JSONObject().apply {
                put("name", name)
                put("gameCode", gameCode)
                put("versionByte", versionByte)
                put("checksums", checksums.toJson())
            }

    companion object {
        fun fromJson(o: JSONObject): BaseRomRef =
                BaseRomRef(
                        name = o.getString("name"),
                        gameCode = o.getString("gameCode"),
                        versionByte = o.getInt("versionByte"),
                        checksums = Checksums.fromJson(o.getJSONObject("checksums"))
                )
    }
}

/** Reference to the patch file (BPS, possibly inside a .zip) for a hack. */
data class PatchRef(
        val url: String,
        val filename: String,
        val size: Long,
        val checksums: Checksums
) {
    fun toJson(): JSONObject =
            JSONObject().apply {
                put("url", url)
                put("filename", filename)
                put("size", size)
                put("checksums", checksums.toJson())
            }

    companion object {
        fun fromJson(o: JSONObject): PatchRef =
                PatchRef(
                        url = o.getString("url"),
                        filename = o.getString("filename"),
                        size = o.getLong("size"),
                        checksums = Checksums.fromJson(o.getJSONObject("checksums"))
                )
    }
}

/**
 * A single hack as published in a catalog. Immutable; carries enough metadata for the Store UI, the
 * download/validation pipeline, and base-ROM matching.
 *
 * Multi-store fields ([storeId], [sourceCatalogId], [screenshots], [videos], [completionStatus],
 * [supportedGames], [lastUpdated], [changelog], [downloadTarget]) are optional with
 * backward-compatible defaults so legacy PICKS catalogs and existing unit tests keep working
 * unchanged.
 */
data class HackEntry(
        val id: String,
        val name: String,
        val description: String,
        val author: String,
        val version: String,
        val baseRom: BaseRomRef,
        val patch: PatchRef? = null,
        val coverImageUrl: String? = null,
        val tags: List<String> = emptyList(),
        val compatibleCores: List<String> = emptyList(),
        /** Optional Ocarina songs contributed by a downloaded hack (catalog extension). */
        val ocarinaSongs: List<OcarinaSong> = emptyList(),
        /**
         * Optional RetroAchievements metadata (catalog extension): known RA game id for this hack,
         * letting the app pre-resolve achievements without waiting for an install-time hash lookup.
         */
        val retroAchievements: RetroAchievementsRef? = null,
        /** Store this hack belongs to ("picks" | "hylianmodding"). */
        val storeId: String = "picks",
        /** Specific source catalog within the store (e.g. "picks", "mods", "hm_2025-crossover"). */
        val sourceCatalogId: String = "picks",
        /** Absolute screenshot URLs (remote only; never bundled). */
        val screenshots: List<String> = emptyList(),
        /** Absolute video URLs (tolerant; usually empty — HM has no video field). */
        val videos: List<String> = emptyList(),
        /** Developer / project links (GitHub, itch.io, etc.) extracted from the source. */
        val developerLinks: List<DeveloperLink> = emptyList(),
        /** Completion status string from the catalog, if declared. */
        val completionStatus: String? = null,
        /** Raw supported game(s) string ("OoT" / "MM"), if declared. */
        val supportedGames: String? = null,
        /** Last-updated timestamp string from the catalog, if declared. */
        val lastUpdated: String? = null,
        /** Changelog entries, if declared. */
        val changelog: List<ChangelogEntry> = emptyList(),
        /** Upstream compatibility note, if the source publishes one. */
        val compatibility: String? = null,
        /** Upstream timestamp/update marker retained by catalog importers. */
        val sourceMetadata: SourceMetadata? = null,
        /** Origin metadata for records imported into this catalog. */
        val importSource: CatalogImportSource? = null,
        /**
         * How the patch should be obtained at download time. Null for legacy PICKS entries that
         * rely on [patch] directly.
         */
        val downloadTarget: DownloadTarget? = null
) {
    /**
     * Stable, store-agnostic identity for cross-catalog dedupe. Computed (not stored) by delegating
     * to [CanonicalIdResolver]; two catalog entries that describe the same hack resolve to the same
     * canonical id regardless of the store that published them.
     */
    val canonicalId: String
        get() = CanonicalIdResolver.resolve(id, storeId)
    fun toJson(): JSONObject =
            JSONObject().apply {
                put("id", id)
                put("name", name)
                put("description", description)
                put("author", author)
                put("version", version)
                put("baseRom", baseRom.toJson())
                put("patch", patch?.toJson() ?: JSONObject.NULL)
                put("coverImageUrl", coverImageUrl)
                put("tags", JSONArray(tags))
                put("compatibleCores", JSONArray(compatibleCores))
                put("ocarinaSongs", JSONArray(ocarinaSongs.map { it.toJson() }))
                if (retroAchievements != null) {
                    put("retroAchievements", retroAchievements.toJson())
                }
                put("storeId", storeId)
                put("sourceCatalogId", sourceCatalogId)
                put("screenshots", JSONArray(screenshots))
                put("videos", JSONArray(videos))
                put("developerLinks", JSONArray(developerLinks.map { it.toJson() }))
                put("completionStatus", completionStatus ?: JSONObject.NULL)
                put("supportedGames", supportedGames ?: JSONObject.NULL)
                put("lastUpdated", lastUpdated ?: JSONObject.NULL)
                put("changelog", JSONArray(changelog.map { it.toJson() }))
                put("compatibility", compatibility ?: JSONObject.NULL)
                sourceMetadata?.let { put("sourceMetadata", it.toJson()) }
                importSource?.let { put("importSource", it.toJson()) }
                downloadTarget?.let { dt ->
                    put(
                            "downloadTarget",
                            JSONObject().apply {
                                when (dt) {
                                    is DownloadTarget.DirectPatch -> {
                                        put("type", "direct")
                                        put("patch", dt.patch.toJson())
                                    }
                                    is DownloadTarget.GitHubRelease -> {
                                        put("type", "github")
                                        put("repoUrl", dt.repoUrl)
                                    }
                                    is DownloadTarget.ExternalLink -> {
                                        put("type", "external")
                                        put("url", dt.url)
                                    }
                                }
                            }
                    )
                }
            }

    companion object {
        fun fromJson(o: JSONObject): HackEntry =
                HackEntry(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        description = o.getString("description"),
                        author = o.getString("author"),
                        version = o.getString("version"),
                        baseRom = BaseRomRef.fromJson(o.getJSONObject("baseRom")),
                        patch =
                                if (o.has("patch") && !o.isNull("patch")) {
                                    PatchRef.fromJson(o.getJSONObject("patch"))
                                } else {
                                    null
                                },
                        coverImageUrl =
                                if (o.isNull("coverImageUrl")) null
                                else o.optString("coverImageUrl", null),
                        tags =
                                if (o.has("tags")) jsonToStringList(o.getJSONArray("tags"))
                                else emptyList(),
                        compatibleCores =
                                if (o.has("compatibleCores")) {
                                    jsonToStringList(o.getJSONArray("compatibleCores"))
                                } else {
                                    emptyList()
                                },
                        ocarinaSongs =
                                if (o.has("ocarinaSongs")) {
                                    val arr = o.getJSONArray("ocarinaSongs")
                                    (0 until arr.length()).mapNotNull { i ->
                                        runCatching {
                                                    OcarinaSong.fromCatalogJson(
                                                            arr.getJSONObject(i)
                                                    )
                                                }
                                                .getOrNull()
                                    }
                                } else {
                                    emptyList()
                                },
                        retroAchievements =
                                if (o.has("retroAchievements") && !o.isNull("retroAchievements")) {
                                    runCatching {
                                                RetroAchievementsRef.fromJson(
                                                        o.getJSONObject("retroAchievements")
                                                )
                                            }
                                            .getOrNull()
                                } else {
                                    null
                                },
                        storeId = o.optString("storeId", "picks"),
                        sourceCatalogId = o.optString("sourceCatalogId", "picks"),
                        screenshots =
                                if (o.has("screenshots"))
                                        jsonToStringList(o.getJSONArray("screenshots"))
                                else emptyList(),
                        videos =
                                if (o.has("videos")) jsonToStringList(o.getJSONArray("videos"))
                                else emptyList(),
                        developerLinks =
                                if (o.has("developerLinks")) {
                                    val arr = o.getJSONArray("developerLinks")
                                    (0 until arr.length()).mapNotNull { i ->
                                        runCatching { DeveloperLink.fromJson(arr.getJSONObject(i)) }.getOrNull()
                                    }
                                } else emptyList(),
                        completionStatus =
                                if (o.isNull("completionStatus")) null
                                else o.optString("completionStatus", null),
                        supportedGames =
                                if (o.isNull("supportedGames")) null
                                else o.optString("supportedGames", null),
                        lastUpdated =
                                if (o.isNull("lastUpdated")) null
                                else o.optString("lastUpdated", null),
                        changelog =
                                if (o.has("changelog")) {
                                    val arr = o.getJSONArray("changelog")
                                    (0 until arr.length()).mapNotNull { i ->
                                        runCatching {
                                                    ChangelogEntry.fromJson(arr.getJSONObject(i))
                                                }
                                                .getOrNull()
                                    }
                                } else {
                                    emptyList()
                                },
                        compatibility =
                                if (o.has("compatibility") && !o.isNull("compatibility")) {
                                    o.getString("compatibility")
                                } else null,
                        sourceMetadata =
                                if (o.has("sourceMetadata") && !o.isNull("sourceMetadata")) {
                                    runCatching {
                                                SourceMetadata.fromJson(
                                                        o.getJSONObject("sourceMetadata")
                                                )
                                            }
                                            .getOrNull()
                                } else null,
                        importSource =
                                if (o.has("importSource") && !o.isNull("importSource")) {
                                    runCatching {
                                                CatalogImportSource.fromJson(
                                                        o.getJSONObject("importSource")
                                                )
                                            }
                                            .getOrNull()
                                } else null,
                        downloadTarget =
                                if (o.has("downloadTarget") && !o.isNull("downloadTarget")) {
                                    parseDownloadTarget(o.getJSONObject("downloadTarget"))
                                } else {
                                    null
                                }
                )

        private fun parseDownloadTarget(o: JSONObject): DownloadTarget? =
                runCatching {
                            when (o.optString("type")) {
                                "direct" ->
                                        DownloadTarget.DirectPatch(
                                                PatchRef.fromJson(o.getJSONObject("patch"))
                                        )
                                "github" -> DownloadTarget.GitHubRelease(o.getString("repoUrl"))
                                "external" -> DownloadTarget.ExternalLink(o.getString("url"))
                                else -> null
                            }
                        }
                        .getOrNull()

        private fun jsonToStringList(arr: JSONArray): List<String> =
                (0 until arr.length()).map { arr.getString(it) }

        /**
         * Whether [a] and [b] describe the same hack across stores.
         *
         * Primary: matching [canonicalId] (covers slug normalization + alias map). Fallback: both
         * carry non-null patch [Checksums] whose CRC32, MD5, and SHA-1 all match. A canonical match
         * with differing checksums still counts as the same hack (a different patch version).
         */
        fun isSameHack(a: HackEntry, b: HackEntry): Boolean {
            if (a.canonicalId == b.canonicalId) return true
            val ca = a.patch?.checksums
            val cb = b.patch?.checksums
            return ca != null &&
                    cb != null &&
                    ca.crc32 == cb.crc32 &&
                    ca.md5 == cb.md5 &&
                    ca.sha1 == cb.sha1
        }
    }
}

/**
 * Optional RetroAchievements metadata attached to a catalog hack.
 *
 * @param gameId Known RA game id; lets the app seed the install-time identity without waiting for a
 * hash lookup. 0 means "tracked, id unknown".
 * @param title RA game title when known (informational).
 */
data class RetroAchievementsRef(val gameId: Long = 0, val title: String? = null) {
    fun toJson(): JSONObject =
            JSONObject().apply {
                put("gameId", gameId)
                put("title", title ?: JSONObject.NULL)
            }

    companion object {
        fun fromJson(o: JSONObject): RetroAchievementsRef =
                RetroAchievementsRef(
                        gameId = o.optLong("gameId", 0),
                        title = if (o.isNull("title")) null else o.optString("title")
                )
    }
}
