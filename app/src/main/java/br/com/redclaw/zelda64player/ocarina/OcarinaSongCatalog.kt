package br.com.redclaw.zelda64player.ocarina

import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.patcher.n64.RomHeader

/**
 * Which Ocarina game the running ROM belongs to.
 *
 * Only Ocarina of Time and Majora's Mask have the in-game Ocarina, so only these
 * two values (plus null for "unsupported") are possible.
 */
enum class OcarinaGame { OOT, MM }

/**
 * Built-in Ocarina song lists per game plus the merge logic that appends
 * catalog-provided custom songs for a specific hack.
 *
 * All built-in sequences were verified against primary sources
 * (zeldadungeon.net song pages, pockettactics, cheatingdome) for the N64
 * (big-endian) control mapping.
 */
object OcarinaSongCatalog {

    /**
     * Detect the Ocarina game from a parsed [RomHeader].
     * "CZL*" prefix -> OoT family; "NZL*"/"NSM*" prefix -> MM family.
     * Returns null for any other (unsupported) base ROM.
     */
    fun detectGame(header: RomHeader): OcarinaGame? = when {
        header.gameCode.startsWith("CZL") -> OcarinaGame.OOT
        header.gameCode.startsWith("NZL") || header.gameCode.startsWith("NSM") -> OcarinaGame.MM
        else -> null
    }

    /** Built-in songs for [game], localized via string resources. */
    fun builtInSongs(game: OcarinaGame): List<OcarinaSong> = when (game) {
        OcarinaGame.OOT -> ootSongs
        OcarinaGame.MM -> mmSongs
    }

    /**
     * Final song list for [game]: built-ins followed by [customSongs] (which the
     * caller has already filtered to the current hackId). Custom songs therefore
     * appear after the built-ins for that specific hack only.
     */
    fun getSongs(game: OcarinaGame, customSongs: List<OcarinaSong>): List<OcarinaSong> =
        builtInSongs(game) + customSongs

    // ---- Ocarina of Time (12) ----
    private val ootSongs: List<OcarinaSong> = listOf(
        OcarinaSong(
            "oot_zeldas_lullaby", R.string.ocarina_zeldas_lullaby,
            notes = listOf(
                OcarinaNote.C_LEFT, OcarinaNote.C_UP, OcarinaNote.C_RIGHT,
                OcarinaNote.C_LEFT, OcarinaNote.C_UP, OcarinaNote.C_RIGHT
            )
        ),
        OcarinaSong(
            "oot_eponas_song", R.string.ocarina_eponas_song,
            notes = listOf(
                OcarinaNote.C_UP, OcarinaNote.C_LEFT, OcarinaNote.C_RIGHT,
                OcarinaNote.C_UP, OcarinaNote.C_LEFT, OcarinaNote.C_RIGHT
            )
        ),
        OcarinaSong(
            "oot_sarias_song", R.string.ocarina_sarias_song,
            notes = listOf(
                OcarinaNote.C_DOWN, OcarinaNote.C_RIGHT, OcarinaNote.C_LEFT,
                OcarinaNote.C_DOWN, OcarinaNote.C_RIGHT, OcarinaNote.C_LEFT
            )
        ),
        OcarinaSong(
            "oot_suns_song", R.string.ocarina_suns_song,
            notes = listOf(
                OcarinaNote.C_RIGHT, OcarinaNote.C_UP, OcarinaNote.C_DOWN,
                OcarinaNote.C_RIGHT, OcarinaNote.C_UP, OcarinaNote.C_DOWN
            )
        ),
        OcarinaSong(
            "oot_song_of_time", R.string.ocarina_song_of_time,
            notes = listOf(
                OcarinaNote.C_RIGHT, OcarinaNote.A, OcarinaNote.C_DOWN,
                OcarinaNote.C_RIGHT, OcarinaNote.A, OcarinaNote.C_DOWN
            )
        ),
        OcarinaSong(
            "oot_song_of_storms", R.string.ocarina_song_of_storms,
            notes = listOf(
                OcarinaNote.A, OcarinaNote.C_DOWN, OcarinaNote.C_UP,
                OcarinaNote.A, OcarinaNote.C_DOWN, OcarinaNote.C_UP
            )
        ),
        OcarinaSong(
            "oot_minuet_of_forest", R.string.ocarina_minuet_of_forest,
            notes = listOf(
                OcarinaNote.A, OcarinaNote.C_UP, OcarinaNote.C_LEFT,
                OcarinaNote.C_RIGHT, OcarinaNote.C_LEFT, OcarinaNote.C_RIGHT
            )
        ),
        OcarinaSong(
            "oot_bolero_of_fire", R.string.ocarina_bolero_of_fire,
            notes = listOf(
                OcarinaNote.C_DOWN, OcarinaNote.A, OcarinaNote.C_DOWN,
                OcarinaNote.A, OcarinaNote.C_RIGHT, OcarinaNote.C_DOWN,
                OcarinaNote.C_RIGHT, OcarinaNote.C_DOWN
            )
        ),
        OcarinaSong(
            "oot_serenade_of_water", R.string.ocarina_serenade_of_water,
            notes = listOf(
                OcarinaNote.A, OcarinaNote.C_DOWN, OcarinaNote.C_RIGHT,
                OcarinaNote.C_RIGHT, OcarinaNote.C_LEFT
            )
        ),
        OcarinaSong(
            "oot_requiem_of_spirit", R.string.ocarina_requiem_of_spirit,
            notes = listOf(
                OcarinaNote.A, OcarinaNote.C_DOWN, OcarinaNote.A,
                OcarinaNote.C_RIGHT, OcarinaNote.C_DOWN, OcarinaNote.A
            )
        ),
        OcarinaSong(
            "oot_nocturne_of_shadow", R.string.ocarina_nocturne_of_shadow,
            notes = listOf(
                OcarinaNote.C_LEFT, OcarinaNote.C_RIGHT, OcarinaNote.C_RIGHT,
                OcarinaNote.A, OcarinaNote.C_LEFT, OcarinaNote.C_RIGHT, OcarinaNote.C_DOWN
            )
        ),
        OcarinaSong(
            "oot_prelude_of_light", R.string.ocarina_prelude_of_light,
            notes = listOf(
                OcarinaNote.C_UP, OcarinaNote.C_RIGHT, OcarinaNote.C_UP,
                OcarinaNote.C_RIGHT, OcarinaNote.C_LEFT, OcarinaNote.C_UP
            )
        )
    )

    // ---- Majora's Mask (11) ----
    private val mmSongs: List<OcarinaSong> = listOf(
        // Song of Time and Song of Storms are shared with OoT (same sequences).
        OcarinaSong(
            "mm_song_of_time", R.string.ocarina_song_of_time,
            notes = listOf(
                OcarinaNote.C_RIGHT, OcarinaNote.A, OcarinaNote.C_DOWN,
                OcarinaNote.C_RIGHT, OcarinaNote.A, OcarinaNote.C_DOWN
            )
        ),
        OcarinaSong(
            "mm_song_of_storms", R.string.ocarina_song_of_storms,
            notes = listOf(
                OcarinaNote.A, OcarinaNote.C_DOWN, OcarinaNote.C_UP,
                OcarinaNote.A, OcarinaNote.C_DOWN, OcarinaNote.C_UP
            )
        ),
        OcarinaSong(
            "mm_song_of_healing", R.string.ocarina_song_of_healing,
            notes = listOf(
                OcarinaNote.C_LEFT, OcarinaNote.C_RIGHT, OcarinaNote.C_DOWN,
                OcarinaNote.C_LEFT, OcarinaNote.C_RIGHT, OcarinaNote.C_DOWN
            )
        ),
        OcarinaSong(
            "mm_song_of_soaring", R.string.ocarina_song_of_soaring,
            notes = listOf(
                OcarinaNote.C_DOWN, OcarinaNote.C_LEFT, OcarinaNote.C_UP,
                OcarinaNote.C_DOWN, OcarinaNote.C_LEFT, OcarinaNote.C_UP
            )
        ),
        OcarinaSong(
            "mm_sonata_of_awakening", R.string.ocarina_sonata_of_awakening,
            notes = listOf(
                OcarinaNote.C_UP, OcarinaNote.C_LEFT, OcarinaNote.C_UP,
                OcarinaNote.C_LEFT, OcarinaNote.A, OcarinaNote.C_RIGHT, OcarinaNote.A
            )
        ),
        OcarinaSong(
            "mm_goron_lullaby", R.string.ocarina_goron_lullaby,
            notes = listOf(
                OcarinaNote.A, OcarinaNote.C_RIGHT, OcarinaNote.C_LEFT,
                OcarinaNote.A, OcarinaNote.C_RIGHT, OcarinaNote.C_LEFT,
                OcarinaNote.C_RIGHT, OcarinaNote.A
            )
        ),
        OcarinaSong(
            "mm_new_wave_bossa_nova", R.string.ocarina_new_wave_bossa_nova,
            notes = listOf(
                OcarinaNote.C_LEFT, OcarinaNote.C_UP, OcarinaNote.C_LEFT,
                OcarinaNote.C_RIGHT, OcarinaNote.C_DOWN, OcarinaNote.C_LEFT, OcarinaNote.C_RIGHT
            )
        ),
        OcarinaSong(
            "mm_elegy_of_emptiness", R.string.ocarina_elegy_of_emptiness,
            notes = listOf(
                OcarinaNote.C_RIGHT, OcarinaNote.C_LEFT, OcarinaNote.C_RIGHT,
                OcarinaNote.C_DOWN, OcarinaNote.C_RIGHT, OcarinaNote.C_UP, OcarinaNote.C_LEFT
            )
        ),
        OcarinaSong(
            "mm_oath_to_order", R.string.ocarina_oath_to_order,
            notes = listOf(
                OcarinaNote.C_RIGHT, OcarinaNote.C_DOWN, OcarinaNote.A,
                OcarinaNote.C_DOWN, OcarinaNote.C_RIGHT, OcarinaNote.C_UP
            )
        ),
        OcarinaSong(
            "mm_inverted_song_of_time", R.string.ocarina_inverted_song_of_time,
            notes = listOf(
                OcarinaNote.C_DOWN, OcarinaNote.A, OcarinaNote.C_RIGHT,
                OcarinaNote.C_DOWN, OcarinaNote.A, OcarinaNote.C_RIGHT
            )
        ),
        OcarinaSong(
            "mm_song_of_double_time", R.string.ocarina_song_of_double_time,
            notes = listOf(
                OcarinaNote.C_RIGHT, OcarinaNote.C_RIGHT, OcarinaNote.A,
                OcarinaNote.A, OcarinaNote.C_DOWN, OcarinaNote.C_DOWN
            )
        )
    )
}
