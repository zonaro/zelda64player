package br.com.redclaw.zelda64player.store

import br.com.redclaw.zelda64player.data.model.HackEntry

/**
 * Merge hack lists from multiple catalogs. Hacks are keyed by [HackEntry.id];
 * when the same id appears in more than one catalog, the entry from the LATER
 * catalog in [sources] order wins. Insertion order of first-seen ids is
 * preserved so the displayed list stays stable across refreshes.
 */
object CatalogMerger {
    fun merge(sources: List<List<HackEntry>>): List<HackEntry> {
        val byId = LinkedHashMap<String, HackEntry>()
        for (source in sources) {
            for (hack in source) {
                byId[hack.id] = hack
            }
        }
        return byId.values.toList()
    }
}
