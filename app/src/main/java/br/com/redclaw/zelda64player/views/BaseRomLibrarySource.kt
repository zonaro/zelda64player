package br.com.redclaw.zelda64player.views

import br.com.redclaw.zelda64player.data.model.BaseRom
import br.com.redclaw.zelda64player.ocarina.OcarinaGame
import br.com.redclaw.zelda64player.ocarina.OcarinaSongCatalog
import br.com.redclaw.zelda64player.patcher.n64.RomHeader
import java.io.File

/**
 * Library source exposing the user's imported vanilla base ROMs (OoT / MM) as
 * playable tiles in the Library grid, alongside store hacks and randomizer
 * seeds. Implements the same [HackLibrarySource] interface as
 * [CatalogBackedLibrarySource] and [RandomizerLibrarySource] so [InstalledLibrary]
 * can merge all three through a [CompositeLibrarySource] without touching the UI.
 *
 * Each vanilla tile is flagged [HackLibraryEntry.isVanilla] (so the context menu
 * omits the management section — base ROMs are managed in Settings) and carries a
 * [HackLibraryEntry.badge] [BadgeType.VANILLA] icon to visually distinguish it
 * from store hacks and randomizer seeds.
 *
 * The cover image is fetched at runtime from the Libretro thumbnails CDN, chosen
 * by game family via [OcarinaSongCatalog.detectGame] (reusing the existing
 * OoT/MM prefix logic — DRY, no duplicated prefix checks). Unknown families get a
 * null cover and the grid adapter falls back to the placeholder drawable.
 *
 * Entries whose ROM file no longer exists on disk are skipped defensively: the
 * registry may lag behind a manual deletion, and we must never surface a tile
 * that cannot be launched.
 *
 * Takes an explicit list of [BaseRom] (no Android [android.content.Context]) so
 * it is unit-testable on the JVM with plain data objects.
 */
class BaseRomLibrarySource(
    private val roms: List<BaseRom>
) : HackLibrarySource {

    override fun available(): List<HackLibraryEntry> =
        roms.mapNotNull { rom ->
            // Defensive: skip entries whose file was deleted outside the app.
            if (!File(rom.path).exists()) return@mapNotNull null

            val game = OcarinaSongCatalog.detectGame(
                RomHeader(rom.gameCode, rom.versionByte, rom.displayName)
            )
            HackLibraryEntry(
                id = "$PREFIX${rom.id}",
                title = rom.displayName,
                coverUrl = coverFor(game),
                badge = BadgeType.VANILLA,
                isVanilla = true,
                family = game
            )
        }

    companion object {
        /** Prefix applied to every vanilla tile id (e.g. "vanilla_<crc32>"). */
        const val PREFIX = "vanilla_"

        /**
         * Box-art cover for OoT (USA) on the Libretro thumbnails CDN. Verified
         * reachable (HTTP 200) — used for the whole OoT family (CZL*), including
         * randomizer seeds, since they share the same base game art.
         */
        private const val OOT_COVER_URL =
            "https://thumbnails.libretro.com/Nintendo%20-%20Nintendo%2064/Named_Boxarts/" +
                "Legend%20of%20Zelda%2C%20The%20-%20Ocarina%20of%20Time%20(USA).png"

        /**
         * Box-art cover for Majora's Mask (USA) on the Libretro thumbnails CDN.
         * Verified reachable (HTTP 200) — used for the whole MM family (NZL* / NSM*).
         */
        private const val MM_COVER_URL =
            "https://thumbnails.libretro.com/Nintendo%20-%20Nintendo%2064/Named_Boxarts/" +
                "Legend%20of%20Zelda%2C%20The%20-%20Majora%27s%20Mask%20(USA).png"

        /** Map a detected Ocarina game to its box-art URL, or null when unknown. */
        private fun coverFor(game: OcarinaGame?): String? = when (game) {
            OcarinaGame.OOT -> OOT_COVER_URL
            OcarinaGame.MM -> MM_COVER_URL
            null -> null
        }
    }
}
