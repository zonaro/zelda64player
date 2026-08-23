package br.com.redclaw.zelda64player.views

import br.com.redclaw.zelda64player.data.local.PatchRepository

/** A single hack shown in the library grid. */
data class HackLibraryEntry(
    val id: String,
    val title: String,
    val coverUrl: String? = null
)

/**
 * Seam for the library data source. Phase 1 is backed by locally-placed patch
 * files; Phase 2 will add a catalog-driven implementation behind the same
 * interface without touching the UI.
 */
interface HackLibrarySource {
    fun available(): List<HackLibraryEntry>
}

/**
 * Interim [HackLibrarySource] reading locally-placed `<hackId>.bps` patches.
 * The displayed title is derived from the hack id (filename without extension).
 */
class LocalPatchesSource(private val patchRepository: PatchRepository) : HackLibrarySource {
    override fun available(): List<HackLibraryEntry> =
        patchRepository.listHackIds().map { HackLibraryEntry(it, prettify(it)) }

    companion object {
        /** Turn a slug like `ocarina_of_time_dx` into `Ocarina Of Time Dx`. */
        fun prettify(hackId: String): String =
            hackId.split('_', '-', '.')
                .filter { it.isNotBlank() }
                .joinToString(" ") { it.replaceFirstChar(Char::titlecase) }
    }
}
