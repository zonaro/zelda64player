package br.com.redclaw.zelda64player.store.ui

import br.com.redclaw.zelda64player.data.model.HackEntry
import br.com.redclaw.zelda64player.store.DownloadPhase

/** Install status of a catalog hack, derived for display in the store grid. */
sealed class StoreStatus {
    /** Not downloaded yet. */
    object NotInstalled : StoreStatus()

    /** Installed at the given version (matches the catalog). */
    data class Installed(val version: String) : StoreStatus()

    /** A newer version exists in the catalog than the installed one. */
    data class UpdateAvailable(val installedVersion: String, val catalogVersion: String) : StoreStatus()
}

/** A catalog hack paired with its computed display status. */
data class StoreItem(
    val hack: HackEntry,
    val status: StoreStatus,
    /** Non-null while the hack is queued/downloading/patching (excludes SUCCESS). */
    val downloadPhase: DownloadPhase? = null
)

/**
 * Rendering state for the Store grid after search + pagination have been
 * applied. The Activity observes this to decide what to show: the catalog
 * empty message, the "no search results" message, or the current page plus
 * the pagination controls.
 *
 * @param items Hacks for the current page (empty when [filteredEmpty]).
 * @param pageIndex Effective 0-based page index.
 * @param totalPages Total pages for the filtered list.
 * @param query The active search query (trimmed).
 * @param catalogEmpty True when the full catalog has zero hacks.
 * @param filteredEmpty True when a non-empty catalog yields zero results for
 *   the active query (distinct from [catalogEmpty]).
 */
data class StorePageState(
    val items: List<HackEntry>,
    val pageIndex: Int,
    val totalPages: Int,
    val query: String,
    val catalogEmpty: Boolean,
    val filteredEmpty: Boolean
)
