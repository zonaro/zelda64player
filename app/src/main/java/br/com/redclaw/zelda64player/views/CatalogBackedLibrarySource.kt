package br.com.redclaw.zelda64player.views

import br.com.redclaw.zelda64player.data.local.InstalledHacksRepository
import br.com.redclaw.zelda64player.data.model.HackEntry
import br.com.redclaw.zelda64player.ocarina.OcarinaGame
import br.com.redclaw.zelda64player.ocarina.OcarinaSongCatalog
import br.com.redclaw.zelda64player.patcher.n64.RomHeader
import java.io.File

/**
 * Library source backed by installed hacks. A hack is considered installed if
 * its durable patched ROM exists ([storagePath]/`rom_<id>`) OR it is recorded in
 * [InstalledHacksRepository] (e.g. an orphan record whose ROM was removed).
 *
 * The display title comes from the catalog entry name when available, otherwise
 * the id is prettified (reusing [LocalPatchesSource.prettify]).
 *
 * The badge's game family is detected from the installed patched ROM header
 * (installed hack ROMs are normalized z64, patcher output, so [RomHeader] reads
 * them directly). Only a few bytes are read per file (Rule 9 friendly). A
 * missing ROM (orphan record) or an unparseable/garbage header yields a null
 * family, which the adapter renders as a neutral chip — never crashes the grid.
 *
 * Takes an explicit [storagePath] (no Android [android.content.Context]) so it
 * is unit-testable on the JVM with a temporary folder.
 */
class CatalogBackedLibrarySource(
    private val storagePath: File,
    private val installedRepository: InstalledHacksRepository,
    private val catalog: Map<String, HackEntry>,
    private val userImportedIds: Set<String> = emptySet()
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
                coverUrl = entry?.coverImageUrl,
                badge = BadgeType.HACK,
                family = familyFor(id),
                isUserImported = id in userImportedIds,
                storeId = entry?.storeId
            )
        }
    }

    /**
     * Detect the game family for [id] from its installed patched ROM header.
     * Returns null when there is no ROM file on disk (orphan record) or the
     * header cannot be parsed (defensive — a malformed/garbage file must not
     * crash the library build).
     */
    private fun familyFor(id: String): OcarinaGame? {
        val romFile = File(storagePath, "rom_$id")
        if (!romFile.isFile) return null
        return runCatching {
            OcarinaSongCatalog.detectGame(RomHeader.fromNormalizedZ64(romFile))
        }.getOrNull()
    }
}
