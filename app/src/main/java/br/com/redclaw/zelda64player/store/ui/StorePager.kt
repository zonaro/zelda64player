package br.com.redclaw.zelda64player.store.ui

import br.com.redclaw.zelda64player.data.model.HackEntry
import java.text.Normalizer

/**
 * Pure, Android-free helper that drives client-side search and pagination for
 * the Hack Store. Kept free of any `android.*` import so it can run on the
 * plain JVM in unit tests.
 *
 * All filtering is case-insensitive and accent-insensitive (diacritics are
 * stripped via [java.text.Normalizer] before comparison), matching the user
 * query against a hack's name, author, description and tags.
 */
object StorePager {

    /** Number of hacks shown per page. Single source of truth. */
    const val PAGE_SIZE: Int = 12

    /**
     * Returns the subset of [hacks] matching [query]. An empty/blank query
     * returns the full list unchanged. Matching is performed against the
     * normalized (lower-cased, accent-stripped) name, author, description and
     * each tag; a hack matches if ANY field contains the query.
     */
    fun filter(hacks: List<HackEntry>, query: String): List<HackEntry> {
        val q = normalize(query).trim()
        if (q.isEmpty()) return hacks
        return hacks.filter { hack ->
            normalize(hack.name).contains(q) ||
                normalize(hack.author).contains(q) ||
                normalize(hack.description).contains(q) ||
                hack.tags.any { normalize(it).contains(q) }
        }
    }

    /**
     * Slices [items] into a single page. [pageIndex] is clamped to the valid
     * range `[0, totalPages - 1]` so out-of-bounds requests never throw and
     * always return a deterministic (possibly empty) page.
     */
    fun page(items: List<HackEntry>, pageIndex: Int): PageResult {
        val total = totalPages(items.size)
        val clamped = if (total == 0) 0 else pageIndex.coerceIn(0, total - 1)
        val from = (clamped * PAGE_SIZE).coerceAtMost(items.size)
        val to = (from + PAGE_SIZE).coerceAtMost(items.size)
        return PageResult(
            items = if (from <= to) items.subList(from, to) else emptyList(),
            pageIndex = clamped,
            totalPages = total
        )
    }

    /** Total number of pages for [itemCount] items (0 when empty). */
    fun totalPages(itemCount: Int): Int =
        if (itemCount <= 0) 0 else (itemCount + PAGE_SIZE - 1) / PAGE_SIZE

    /**
     * Normalizes text for comparison: Unicode NFD decomposition followed by
     * removal of combining diacritical marks, then lower-casing. This makes
     * "Pokémon" match "pokemon" and "SÃO" match "sao".
     */
    private fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        val withoutAccents = decomposed.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        return withoutAccents.lowercase()
    }
}

/**
 * Result of a single pagination slice.
 *
 * @param items The hacks belonging to the requested (clamped) page.
 * @param pageIndex The effective 0-based page index after clamping.
 * @param totalPages Total number of pages for the full item list.
 */
data class PageResult(
    val items: List<HackEntry>,
    val pageIndex: Int,
    val totalPages: Int
)
