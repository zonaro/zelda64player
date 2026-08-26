package br.com.redclaw.zelda64player.store

import br.com.redclaw.zelda64player.data.model.HackCatalog
import br.com.redclaw.zelda64player.data.model.HackEntry
import org.json.JSONObject

/**
 * Parses a single catalog document into a list of [HackEntry]. Implementations
 * are responsible for stamping each entry with the correct [HackEntry.storeId]
 * and [HackEntry.sourceCatalogId] so the merged catalog can be filtered by
 * store without re-fetching.
 */
interface CatalogParser {
    fun parse(json: String): List<HackEntry>
}
