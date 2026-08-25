package br.com.redclaw.zelda64player.views

import br.com.redclaw.zelda64player.data.local.PatchRepository
import br.com.redclaw.zelda64player.ocarina.OcarinaGame

/** Visual category of a library tile, rendered as an icon badge. */
enum class BadgeType {
    /** User-imported vanilla base ROM (managed in Settings). */
    VANILLA,
    /** Store hack or locally-placed patch. */
    HACK
}

/** A single hack shown in the library grid. */
data class HackLibraryEntry(
    val id: String,
    val title: String,
    val coverUrl: String? = null,
    /** Icon badge drawn over the tile, distinguishing tile categories. Null hides the badge. */
    val badge: BadgeType? = null,
    /**
     * Game family driving the badge's chip background and icon tint. Null means
     * the family is unknown, in which case the adapter falls back to a neutral
     * chip (color_primary background / white icon). OoT -> yellow bg / black
     * icon, MM -> purple bg / white icon.
     */
    val family: OcarinaGame? = null,
    /** True when this tile is a user-imported vanilla base ROM (managed in Settings). */
    val isVanilla: Boolean = false
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
        patchRepository.listHackIds().map {
            HackLibraryEntry(it, prettify(it), badge = BadgeType.HACK)
        }

    companion object {
        /** Turn a slug like `ocarina_of_time_dx` into `Ocarina Of Time Dx`. */
        fun prettify(hackId: String): String =
            hackId.split('_', '-', '.')
                .filter { it.isNotBlank() }
                .joinToString(" ") { it.replaceFirstChar(Char::titlecase) }
    }
}
