package br.com.redclaw.zelda64player.data.model

import org.json.JSONObject

/**
 * Checksums for a ROM or patch file.
 *
 * [crc32] is mandatory (lowercase hex; the `0x` prefix used by some catalogs is
 * stripped on parse). [md5] and [sha1] are optional extra validations. All
 * digests are normalized to lowercase hex so comparisons are case-insensitive.
 */
data class Checksums(
    val crc32: String,
    val md5: String? = null,
    val sha1: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("crc32", crc32)
        put("md5", md5)
        put("sha1", sha1)
    }

    companion object {
        /** Normalize a hex digest: strip an optional `0x`/`0X` prefix and lowercase. */
        fun normalize(raw: String): String =
            raw.trim().removePrefix("0x").removePrefix("0X").lowercase()

        fun fromJson(o: JSONObject): Checksums = Checksums(
            crc32 = normalize(o.getString("crc32")),
            md5 = if (o.isNull("md5")) null else normalize(o.getString("md5")),
            sha1 = if (o.isNull("sha1")) null else normalize(o.getString("sha1"))
        )
    }
}
