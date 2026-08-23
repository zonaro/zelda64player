package br.com.redclaw.zelda64player.views

import br.com.redclaw.zelda64player.data.local.InstalledHacksRepository
import br.com.redclaw.zelda64player.data.model.HackEntry
import java.io.File

/**
 * Library source backed by installed hacks. A hack is considered installed if
 * its durable patched ROM exists ([storagePath]/`rom_<id>`) OR it is recorded in
 * [InstalledHacksRepository] (e.g. an orphan record whose ROM was removed).
 *
 * The display title comes from the catalog entry name when available, otherwise
 * the id is prettified (reusing [LocalPatchesSource.prettify]).
 *
 * Takes an explicit [storagePath] (no Android [android.content.Context]) so it
 * is unit-testable on the JVM with a temporary folder.
 */
class CatalogBackedLibrarySource(
    private val storagePath: File,
    private val installedRepository: InstalledHacksRepository,
    private val catalog: Map<String, HackEntry>
) : HackLibrarySource {
    override fun available(): List<HackLibraryEntry> {
        val romIds = storagePath.listFiles { f ->
            f.isFile && f.name.startsWith("rom_") && f.length() > 0
        }?.map { it.name.removePrefix("rom_") } ?: emptyList()
        val ids = (romIds + installedRepository.load().keys).distinct()
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
