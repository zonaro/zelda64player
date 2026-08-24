package br.com.redclaw.zelda64player.views

import br.com.redclaw.zelda64player.ocarina.OcarinaGame
import br.com.redclaw.zelda64player.randomizer.repository.RandomizedSeedRepository

/**
 * Library source exposing generated OoTRandomizer seeds as tiles in the Library
 * grid, alongside the store hacks. Implements the same [HackLibrarySource]
 * interface as [CatalogBackedLibrarySource] so [InstalledLibrary] can merge both
 * through a [CompositeLibrarySource] without touching the UI.
 *
 * Each seed tile is flagged [HackLibraryEntry.isRandomizer] (so the context menu
 * offers delete-seed instead of uninstall) and carries a [HackLibraryEntry.badge]
 * [BadgeType.RANDOMIZER] icon to visually distinguish it from store hacks.
 */
class RandomizerLibrarySource(
    private val repository: RandomizedSeedRepository
) : HackLibrarySource {
    override fun available(): List<HackLibraryEntry> =
        repository.list().map { entry ->
            HackLibraryEntry(
                id = entry.id,
                title = entry.name,
                coverUrl = null,
                badge = BadgeType.RANDOMIZER,
                isRandomizer = true,
                // Seeds are always OoT: AGENTS.md Rule 17 only accepts OoT 1.0
                // (CZLE/CZLJ) as the randomizer base ROM, so every seed is OoT.
                family = OcarinaGame.OOT
            )
        }
}
