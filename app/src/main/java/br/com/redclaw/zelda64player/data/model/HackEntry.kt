package br.com.redclaw.zelda64player.data.model

import br.com.redclaw.zelda64player.ocarina.OcarinaSong
import org.json.JSONArray
import org.json.JSONObject

/** Reference to the base ROM a hack requires (user-supplied, never shipped). */
data class BaseRomRef(
    val name: String,
    val gameCode: String,
    val versionByte: Int,
    val checksums: Checksums
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("gameCode", gameCode)
        put("versionByte", versionByte)
        put("checksums", checksums.toJson())
    }

    companion object {
        fun fromJson(o: JSONObject): BaseRomRef = BaseRomRef(
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
    fun toJson(): JSONObject = JSONObject().apply {
        put("url", url)
        put("filename", filename)
        put("size", size)
        put("checksums", checksums.toJson())
    }

    companion object {
        fun fromJson(o: JSONObject): PatchRef = PatchRef(
            url = o.getString("url"),
            filename = o.getString("filename"),
            size = o.getLong("size"),
            checksums = Checksums.fromJson(o.getJSONObject("checksums"))
        )
    }
}

/**
 * A single hack as published in a catalog. Immutable; carries enough metadata
 * for the Store UI, the download/validation pipeline, and base-ROM matching.
 */
data class HackEntry(
    val id: String,
    val name: String,
    val description: String,
    val author: String,
    val version: String,
    val baseRom: BaseRomRef,
    val patch: PatchRef,
    val coverImageUrl: String? = null,
    val tags: List<String> = emptyList(),
    val compatibleCores: List<String> = emptyList(),
    /** Optional Ocarina songs contributed by a downloaded hack (catalog extension). */
    val ocarinaSongs: List<OcarinaSong> = emptyList()
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("description", description)
        put("author", author)
        put("version", version)
        put("baseRom", baseRom.toJson())
        put("patch", patch.toJson())
        put("coverImageUrl", coverImageUrl)
        put("tags", JSONArray(tags))
        put("compatibleCores", JSONArray(compatibleCores))
        put("ocarinaSongs", JSONArray(ocarinaSongs.map { it.toJson() }))
    }

    companion object {
        fun fromJson(o: JSONObject): HackEntry = HackEntry(
            id = o.getString("id"),
            name = o.getString("name"),
            description = o.getString("description"),
            author = o.getString("author"),
            version = o.getString("version"),
            baseRom = BaseRomRef.fromJson(o.getJSONObject("baseRom")),
            patch = PatchRef.fromJson(o.getJSONObject("patch")),
            coverImageUrl = if (o.isNull("coverImageUrl")) null else o.getString("coverImageUrl"),
            tags = if (o.has("tags")) jsonToStringList(o.getJSONArray("tags")) else emptyList(),
            compatibleCores = if (o.has("compatibleCores")) {
                jsonToStringList(o.getJSONArray("compatibleCores"))
            } else {
                emptyList()
            },
            ocarinaSongs = if (o.has("ocarinaSongs")) {
                val arr = o.getJSONArray("ocarinaSongs")
                (0 until arr.length()).mapNotNull { i ->
                    runCatching { OcarinaSong.fromCatalogJson(arr.getJSONObject(i)) }.getOrNull()
                }
            } else {
                emptyList()
            }
        )

        private fun jsonToStringList(arr: JSONArray): List<String> =
            (0 until arr.length()).map { arr.getString(it) }
    }
}
