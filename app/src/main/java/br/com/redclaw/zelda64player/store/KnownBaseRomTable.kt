package br.com.redclaw.zelda64player.store

import br.com.redclaw.zelda64player.ocarina.OcarinaGame

/**
 * Canonical mapping from a known base-ROM CRC32 (No-Intro, big-endian `.z64` —
 * matches our normalized CRC32) to the game it belongs to and a human-readable
 * version label.
 *
 * Used by [ImportedPatchInstaller] to tell the user WHICH game a BPS patch
 * targets when no matching base ROM is imported, so the "no compatible ROM"
 * message can name the exact title (e.g. "Ocarina of Time (NTSC-U 1.0
 * (CZLE))") instead of only showing the raw CRC32.
 *
 * The CRC32 values are the verified No-Intro references documented in the
 * project plan. Lookups are case-insensitive.
 */
object KnownBaseRomTable {

    /** Which game a CRC32 belongs to plus its canonical English version label. */
    data class GameTargetInfo(val game: OcarinaGame?, val versionLabel: String)

    private val TABLE: Map<String, GameTargetInfo> = mapOf(
        // Ocarina of Time (CZL*)
        "cd16c529" to GameTargetInfo(OcarinaGame.OOT, "NTSC-U 1.0 (CZLE)"),
        "ec95702d" to GameTargetInfo(OcarinaGame.OOT, "NTSC-U 1.0 (CZLE)"),
        "3fd2151e" to GameTargetInfo(OcarinaGame.OOT, "NTSC-U 1.1 (CZL1)"),
        "32120c23" to GameTargetInfo(OcarinaGame.OOT, "NTSC-U 1.2 (CZL2)"),
        "946fd0f7" to GameTargetInfo(OcarinaGame.OOT, "PAL 1.0 (CZLP)"),
        "a108f6e3" to GameTargetInfo(OcarinaGame.OOT, "PAL 1.1 (CZLP)"),
        "d423e8b0" to GameTargetInfo(OcarinaGame.OOT, "NTSC-J 1.0 (CZLJ)"),
        "26e73887" to GameTargetInfo(OcarinaGame.OOT, "NTSC-J 1.1 (CZLJ)"),
        "2b2721ba" to GameTargetInfo(OcarinaGame.OOT, "NTSC-J 1.2 (CZLJ)"),
        "c744c4db" to GameTargetInfo(OcarinaGame.OOT, "Master Quest (GameCube)"),

        // Majora's Mask (NZL*/NSM*/NZS*)
        "b428d8a7" to GameTargetInfo(OcarinaGame.MM, "NTSC-U 1.0 (NZSE)"),
        "9ead1608" to GameTargetInfo(OcarinaGame.MM, "PAL 1.0 (NZSP)"),
        "e2e6823d" to GameTargetInfo(OcarinaGame.MM, "PAL 1.1 (NZSP)"),
        "0d33e1db" to GameTargetInfo(OcarinaGame.MM, "NTSC-J 1.0 (NZSJ)"),
        "356c2e19" to GameTargetInfo(OcarinaGame.MM, "NTSC-J 1.1 (NZSJ)")
    )

    /**
     * Look up the [GameTargetInfo] for [crc32] (case-insensitive). Returns null
     * when the CRC32 is not one of the known base ROMs (e.g. a randomizer seed
     * or an unknown dump).
     */
    fun infoFor(crc32: String): GameTargetInfo? = TABLE[crc32.lowercase()]
}
