package br.com.redclaw.zelda64player.data.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * A hack catalog as published at a catalog URL.
 *
 * Parsing is tolerant: a malformed individual hack entry (missing required
 * field, bad JSON, unparseable nested object) is skipped rather than failing
 * the whole catalog, so a single bad entry cannot take down the store.
 */
data class HackCatalog(
    val catalogVersion: Int,
    val lastUpdated: String,
    val hacks: List<HackEntry>
) {
    companion object {
        /** Parse from a raw JSON string. */
        fun parse(json: String): HackCatalog = parse(JSONObject(json))

        /** Parse from an already-parsed root object. */
        fun parse(root: JSONObject): HackCatalog {
            val catalogVersion = runCatching { root.getInt("catalogVersion") }.getOrDefault(0)
            val lastUpdated = runCatching { root.getString("lastUpdated") }.getOrDefault("")
            val hacks = mutableListOf<HackEntry>()
            val arr = runCatching { root.getJSONArray("hacks") }.getOrNull() ?: JSONArray()
            for (i in 0 until arr.length()) {
                val obj = runCatching { arr.getJSONObject(i) }.getOrNull() ?: continue
                // Skip entries missing required top-level fields before attempting full parse.
                if (!hasRequiredFields(obj)) continue
                runCatching { HackEntry.fromJson(obj) }
                    .onSuccess { hacks.add(it) }
                    .onFailure { /* skip malformed entry */ }
            }
            return HackCatalog(catalogVersion, lastUpdated, hacks)
        }

        /**
         * Required top-level fields per the catalog schema. A hack must expose
         * either a direct [PatchRef] or a [DownloadTarget]. The latter covers
         * catalog records whose publisher supplies a GitHub release page or an
         * external archive rather than a directly downloadable patch.
         */
        private fun hasRequiredFields(o: JSONObject): Boolean {
            val required = listOf(
                "id", "name", "description", "author", "version", "baseRom"
            )
            return required.all { o.has(it) } &&
                ((o.has("patch") && !o.isNull("patch")) ||
                    (o.has("downloadTarget") && !o.isNull("downloadTarget")))
        }
    }
}
