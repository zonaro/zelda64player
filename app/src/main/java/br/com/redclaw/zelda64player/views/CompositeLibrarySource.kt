package br.com.redclaw.zelda64player.views

/**
 * Merges multiple [HackLibrarySource]s into a single flat list for the Library
 * grid. Used by [InstalledLibrary] to show store hacks and randomizer seeds
 * together while keeping each source's data access isolated.
 */
class CompositeLibrarySource(
    private val sources: List<HackLibrarySource>
) : HackLibrarySource {
    override fun available(): List<HackLibraryEntry> =
        sources.flatMap { it.available() }
}
