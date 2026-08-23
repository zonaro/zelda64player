package br.com.redclaw.zelda64player.views

import android.content.Context
import br.com.redclaw.zelda64player.data.local.AppRepositories
import br.com.redclaw.zelda64player.data.local.InstalledHacksRepository
import br.com.redclaw.zelda64player.data.local.MergedCatalogRepository
import br.com.redclaw.zelda64player.repositories.Storage
import br.com.redclaw.zelda64player.views.RandomizerLibrarySource
import java.io.File

/**
 * Builds the list of installed [HackLibraryEntry] shown in the Library grid.
 * Shared between [LibraryActivity] and the shortcut sync so the same source of
 * truth is used everywhere (DRY) instead of duplicating the inline logic that
 * previously lived in `LibraryActivity.buildLibrarySource`.
 *
 * Store hacks (catalog-backed) and generated randomizer seeds are merged through
 * a [CompositeLibrarySource]. The store source is filtered so it never surfaces
 * `ootr_`-prefixed seed ROMs (those live in the same `rom_*` storage dir but are
 * owned by [RandomizerLibrarySource]); otherwise a seed would appear twice.
 */
object InstalledLibrary {
    /** Prefix used for randomizer seed ids; must match [RandomizedSeedEntry] ids. */
    private const val RANDOMIZER_ID_PREFIX = "ootr_"

    fun entries(context: Context): List<HackLibraryEntry> {
        val storagePath = File(Storage.getInstance(context).storagePath)
        val installedRepository =
            InstalledHacksRepository(File(context.filesDir, "installed_hacks.json"))
        val mergedCatalog = MergedCatalogRepository(File(context.filesDir, "merged_catalog.json"))

        val storeSource = object : HackLibrarySource {
            private val delegate = CatalogBackedLibrarySource(
                storagePath,
                installedRepository,
                mergedCatalog.asMap()
            )
            override fun available(): List<HackLibraryEntry> =
                delegate.available().filterNot { it.id.startsWith(RANDOMIZER_ID_PREFIX) }
        }
        val randomizerSource = RandomizerLibrarySource(
            AppRepositories.randomizedSeedRepository(context)
        )
        return CompositeLibrarySource(listOf(storeSource, randomizerSource)).available()
    }
}
