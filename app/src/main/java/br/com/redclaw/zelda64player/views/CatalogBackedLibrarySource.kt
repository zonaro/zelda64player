package br.com.redclaw.zelda64player.views

import br.com.redclaw.zelda64player.data.local.InstalledHacksRepository
import br.com.redclaw.zelda64player.data.model.HackEntry
import br.com.redclaw.zelda64player.ocarina.OcarinaGame
import br.com.redclaw.zelda64player.ocarina.OcarinaSongCatalog
import br.com.redclaw.zelda64player.patcher.n64.RomHeader
import br.com.redclaw.zelda64player.store.CanonicalIdResolver
import br.com.redclaw.zelda64player.ui.switchui.BadgeBinder
import java.io.File

/**
 * Library source backed by installed hacks. A hack is considered installed if its durable patched
 * ROM exists ([storagePath]/`rom_<id>`) OR it is recorded in [InstalledHacksRepository] (e.g. an
 * orphan record whose ROM was removed).
 *
 * The display title comes from the catalog entry name when available, otherwise the id is
 * prettified (reusing [LocalPatchesSource.prettify]).
 *
 * The badge's game family is resolved from the catalog entry's [HackEntry.supportedGames] /
 * [HackEntry.baseRom] ([BadgeBinder.familyForHack]) when a catalog entry is available — this is the
 * same source the Store uses, so Library and Store badges stay consistent. Only when no catalog
 * entry exists (user-imported patch) or the catalog omits both fields does it fall back to reading
 * the installed patched ROM header ([RomHeader] / [OcarinaSongCatalog.detectGame]). A missing ROM
 * (orphan record) or an unparseable/garbage header yields a null family, which the adapter renders
 * as a neutral chip — never crashes the grid.
 *
 * Rationale: some hacks rewrite the N64 header's gameCode (e.g. Ultimate Trial patches CZLE -> NZL
 * for its custom title), so the patched ROM header is NOT authoritative for the family badge.
 *
 * Takes an explicit [storagePath] (no Android [android.content.Context]) so it is unit-testable on
 * the JVM with a temporary folder.
 */
class CatalogBackedLibrarySource(
        private val storagePath: File,
        private val installedRepository: InstalledHacksRepository,
        private val catalog: Map<String, HackEntry>,
        private val userImportedIds: Set<String> = emptySet()
) : HackLibrarySource {
        override fun available(): List<HackLibraryEntry> {
                val romIds =
                        storagePath
                                .listFiles { f ->
                                        f.isFile && f.name.startsWith("rom_") && f.length() > 0
                                }
                                ?.map { it.name.removePrefix("rom_") }
                                ?: emptyList()
                val ids = (romIds + installedRepository.load().keys).distinct()

                // Group installed ids by their canonical id so the same hack published
                // under different store ids (e.g. PICKS `the-missing-link` vs HM
                // `hm_themissinglink`) collapses to a single Library tile.
                val byCanonical = ids.groupBy { CanonicalIdResolver.resolve(it, "") }

                // Catalog entries grouped by canonical id for representative selection.
                val catalogByCanonical = catalog.values.groupBy { it.canonicalId }

                return byCanonical.map { (canonicalId, groupIds) ->
                        val representative =
                                catalogByCanonical[canonicalId]?.let { pickRepresentative(it) }
                        // The original hack id that maps to a real rom_<id> file on disk.
                        // This is used for all file-system operations (ROM, saves, shortcuts,
                        // uninstall) and may differ from the canonical display id.
                        val fsId =
                                groupIds.firstOrNull { File(storagePath, "rom_$it").isFile }
                                        ?: groupIds.first()
                        if (representative != null) {
                                HackLibraryEntry(
                                        id = canonicalId,
                                        title = representative.name,
                                        coverUrl = representative.coverImageUrl,
                                        badge = BadgeType.HACK,
                                        family = BadgeBinder.familyForHack(representative)
                                                        ?: familyFor(fsId),
                                        isUserImported = representative.id in userImportedIds,
                                        storeId = representative.storeId,
                                        romId = fsId
                                )
                        } else {
                                HackLibraryEntry(
                                        id = canonicalId,
                                        title = LocalPatchesSource.prettify(fsId),
                                        coverUrl = null,
                                        badge = BadgeType.HACK,
                                        family = familyFor(fsId),
                                        isUserImported = fsId in userImportedIds,
                                        storeId = null,
                                        romId = fsId
                                )
                        }
                }
        }

        /**
         * Pick the "best" representative for a canonical-id group: prefer a PICKS store entry
         * (canonical by definition), otherwise the first source.
         */
        private fun pickRepresentative(entries: List<HackEntry>): HackEntry =
                entries.firstOrNull { it.storeId == "picks" } ?: entries.first()

        /**
         * Detect the game family for [id] from its installed patched ROM header. Returns null when
         * there is no ROM file on disk (orphan record) or the header cannot be parsed (defensive —
         * a malformed/garbage file must not crash the library build).
         */
        private fun familyFor(id: String): OcarinaGame? {
                val romFile = File(storagePath, "rom_$id")
                if (!romFile.isFile) return null
                return runCatching {
                                OcarinaSongCatalog.detectGame(RomHeader.fromNormalizedZ64(romFile))
                        }
                        .getOrNull()
        }
}
