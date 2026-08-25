package br.com.redclaw.zelda64player.store.ui

import br.com.redclaw.zelda64player.R
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

/**
 * Sidebar filter categories for the Hack Store. Every option is derived purely
 * from data already present on each [HackEntry] (no new catalog fields needed):
 *
 * - [All] shows everything.
 * - [Installed] / [Updates] depend on the computed install [StoreStatus].
 * - [Oot] / [Mm] depend on the base ROM game code prefix.
 *
 * [labelRes] points at the localized category name shown in the sidebar and as
 * the main-content section header.
 */
sealed class StoreCategory(val labelRes: Int) {
    /** Every catalog hack. */
    object All : StoreCategory(R.string.store_cat_all)

    /** Hacks whose install status is Installed or UpdateAvailable. */
    object Installed : StoreCategory(R.string.store_cat_installed)

    /** Hacks whose install status is UpdateAvailable. */
    object Updates : StoreCategory(R.string.store_cat_updates)

    /** Hacks whose base ROM game code starts with "CZL" (Ocarina of Time). */
    object Oot : StoreCategory(R.string.store_cat_oot)

    /** Hacks whose base ROM game code starts with "NZL" or "NSM" (Majora's Mask). */
    object Mm : StoreCategory(R.string.store_cat_mm)

    companion object {
        /** Stable, display-ordered list of every category. */
        val ALL: List<StoreCategory> = listOf(All, Installed, Updates, Oot, Mm)
    }
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
