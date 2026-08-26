package br.com.redclaw.zelda64player.store

import br.com.redclaw.zelda64player.data.model.HackCatalog
import br.com.redclaw.zelda64player.data.model.HackEntry
import org.json.JSONObject

/**
 * Parses the legacy "Zelda 64 Picks" catalog format (the `catalog.json` schema
 * with `catalogVersion`, `lastUpdated` and a `hacks` array). Every produced
 * entry is stamped with `storeId = "picks"` and `sourceCatalogId = "picks"`.
 *
 * Tolerant: malformed individual entries are skipped by [HackCatalog.parse].
 */
class PicksCatalogParser : CatalogParser {
    override fun parse(json: String): List<HackEntry> {
        val catalog = HackCatalog.parse(json)
        return catalog.hacks.map { it.copy(storeId = "picks", sourceCatalogId = "picks") }
    }

    /** Reads the optional top-level `storeName` (used to name the PICKS store). */
    fun storeName(json: String): String? =
        runCatching { JSONObject(json).optString("storeName", null) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
}
