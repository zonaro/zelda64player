package br.com.redclaw.zelda64player.views

import android.content.Context
import br.com.redclaw.zelda64player.data.local.AppRepositories
import br.com.redclaw.zelda64player.data.local.InstalledHacksRepository
import br.com.redclaw.zelda64player.data.local.MergedCatalogRepository
import br.com.redclaw.zelda64player.repositories.GameRomResolver
import br.com.redclaw.zelda64player.repositories.Storage
import br.com.redclaw.zelda64player.shortcuts.GamePlayHistoryStore
import br.com.redclaw.zelda64player.views.BaseRomLibrarySource
import java.io.File

/**
 * Builds the list of installed [HackLibraryEntry] shown in the Library grid.
 * Shared between [LibraryActivity] and the shortcut sync so the same source of
 * truth is used everywhere (DRY) instead of duplicating the inline logic that
 * previously lived in `LibraryActivity.buildLibrarySource`.
 *
 * The grid shows user-imported vanilla base ROMs ([BaseRomLibrarySource]) and
 * store hacks (catalog-backed), merged through a [CompositeLibrarySource].
 *
     *

 * User-imported vanilla base ROMs ([BaseRomLibrarySource]) are prepended so the
 * raw [entries] order is: vanilla games, then store hacks. Higher-level orderings
 * are layered on top of this source:
 * - [recentEntries] powers the home row (most-recently-played first, capped at 5,
 *   with a fresh-install fallback to the default order).
 * - [sortedEntries] powers the "Todos os Jogos" grid (alphabetical by default,
 *   with last-played and download-date alternatives).
 */
object InstalledLibrary {
    /** File name of the play-history store, shared with [GamePlayHistoryStore] callers. */
    private const val HISTORY_FILE = "game_play_history.json"

    fun entries(context: Context): List<HackLibraryEntry> {
        val storagePath = File(Storage.getInstance(context).storagePath)
        val installedRepository =
            InstalledHacksRepository(File(context.filesDir, "installed_hacks.json"))
        val mergedCatalog = MergedCatalogRepository(File(context.filesDir, "merged_catalog.json"))

        val vanillaSource = BaseRomLibrarySource(
            AppRepositories.baseRomRepository(context).getAll()
        )
        // Merge user-imported hacks into the catalog map so imported hacks show
        // with their proper title (not a prettified id) and resolve their cover.
        val userHacks = AppRepositories.userHacksRepository(context).asMap()
        val catalogMap = mergedCatalog.asMap() + userHacks
        val storeSource = object : HackLibrarySource {
            private val delegate = CatalogBackedLibrarySource(
                storagePath,
                installedRepository,
                catalogMap
            )
            override fun available(): List<HackLibraryEntry> = delegate.available()
        }
        return CompositeLibrarySource(
            listOf(vanillaSource, storeSource)
        ).available()
    }

    /**
     * Home-row entries: the [limit] most-recently-played installed games, newest
     * first. Only games that have actually been played are included.
     *
     * FALLBACK: on a fresh install (or after the history is cleared) nothing has
     * ever been played, so [LibraryOrdering.recentPlayed] returns empty. In that
     * case we fall back to the default [entries] order capped at [limit],
     * guaranteeing the home row is never empty. This fallback is documented here
     * and in [LibraryActivity].
     *
     * @param context host context (used to read the library and play history)
     * @param limit maximum number of entries to return (default 5)
     */
    fun recentEntries(context: Context, limit: Int = 5): List<HackLibraryEntry> {
        val all = entries(context)
        val history = GamePlayHistoryStore(File(context.filesDir, HISTORY_FILE)).all()
        val played = LibraryOrdering.recentPlayed(all, history, limit)
        if (played.isEmpty()) return all.take(limit)
        return played
    }

    /**
     * "Todos os Jogos" grid entries sorted by [mode]. The full installed list is
     * returned (uncapped) so the grid still shows every entry; the active search
     * filter is applied later by the grid UI. ROM file resolution for
     * [GridSortMode.DOWNLOAD_DATE] reuses [GameRomResolver.resolveRomFile] (the
     * single ROM-resolution point) so vanilla and non-vanilla ids are handled
     * identically (DRY); a missing ROM file sorts last.
     */
    fun sortedEntries(context: Context, mode: GridSortMode): List<HackLibraryEntry> {
        val all = entries(context)
        val history = GamePlayHistoryStore(File(context.filesDir, HISTORY_FILE)).all()
        return LibraryOrdering.sortByMode(
            entries = all,
            mode = mode,
            history = history
        ) { id ->
            GameRomResolver.resolveRomFile(context, id)?.lastModified() ?: 0L
        }
    }
}
