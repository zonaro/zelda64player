package br.com.redclaw.zelda64player.views

import br.com.redclaw.zelda64player.data.local.InstalledHacksRepository
import br.com.redclaw.zelda64player.data.local.PatchRepository
import br.com.redclaw.zelda64player.data.model.HackEntry

/**
 * Library source backed by installed patches. A hack is considered installed if
 * its patch file is present OR it is recorded in [InstalledHacksRepository].
 *
 * The display title comes from the catalog entry name when available, otherwise
 * the id is prettified (reusing [LocalPatchesSource.prettify]).
 */
class CatalogBackedLibrarySource(
    private val patchRepository: PatchRepository,
    private val installedRepository: InstalledHacksRepository,
    private val catalog: Map<String, HackEntry>
) : HackLibrarySource {
    override fun available(): List<HackLibraryEntry> {
        val ids = (patchRepository.listHackIds() + installedRepository.load().keys)
            .distinct()
        return ids.map { id ->
            val entry = catalog[id]
            HackLibraryEntry(
                id = id,
                title = entry?.name ?: LocalPatchesSource.prettify(id),
                coverUrl = entry?.coverImageUrl
            )
        }
    }
}
