package br.com.redclaw.zelda64player.store

import br.com.redclaw.zelda64player.ocarina.OcarinaGame

/**
 * Maps well-known Nintendo 64 Zelda base-ROM CRC32 values (No-Intro, computed
 * over the big-endian `.z64` image — identical to the CRC32 our normalizer
 * produces) to the game they belong to. Used when an imported BPS patch's
 * required source ROM is not present, so the UI can tell the user exactly which
 * game (Ocarina of Time or Majora's Mask) the patch targets.
 *
 * Values verified against the No-Intro BigEndian DAT (2026-07-28) via fact-check.
 * Note: an alternate OoT USA v1.0 dump (`ec95702d`) circulates in the community
 * and many patches were built against it, so it is mapped to the same game as the
 * canonical `cd16c529`.
 */
data class GameTargetInfo(val game: OcarinaGame?, val versionLabel: String)

object KnownBaseRomTable {
    private val map: Map<String, GameTargetInfo> = mapOf(
        "cd16c529" to GameTargetInfo(OcarinaGame.OOT, "NTSC-U 1.0 (CZLE)"),
        "ec95702d" to GameTargetInfo(OcarinaGame.OOT, "NTSC-U 1.0 (CZLE)"),
        "3fd2151e" to GameTargetInfo(OcarinaGame.OOT, "NTSC-U 1.1 (CZLE)"),
        "32120c23" to GameTargetInfo(OcarinaGame.OOT, "NTSC-U 1.2 (CZLE)"),
        "946fd0f7" to GameTargetInfo(OcarinaGame.OOT, "PAL 1.0 (NZLP)"),
        "a108f6e3" to GameTargetInfo(OcarinaGame.OOT, "PAL 1.1 (NZLP)"),
        "d423e8b0" to GameTargetInfo(OcarinaGame.OOT, "NTSC-J 1.0 (CZLJ)"),
        "26e73887" to GameTargetInfo(OcarinaGame.OOT, "NTSC-J 1.1 (CZLJ)"),
        "2b2721ba" to GameTargetInfo(OcarinaGame.OOT, "NTSC-J 1.2 (CZLJ)"),
        "c744c4db" to GameTargetInfo(OcarinaGame.OOT, "Master Quest GC (CZLE)"),
        "b428d8a7" to GameTargetInfo(OcarinaGame.MM, "NTSC-U 1.0 (NZSE)"),
        "9ead1608" to GameTargetInfo(OcarinaGame.MM, "PAL 1.0 (NZSP)"),
        "e2e6823d" to GameTargetInfo(OcarinaGame.MM, "PAL 1.1 (NZSP)"),
        "0d33e1db" to GameTargetInfo(OcarinaGame.MM, "NTSC-J 1.0 (NZSJ)"),
        "356c2e19" to GameTargetInfo(OcarinaGame.MM, "NTSC-J 1.1 (NZSJ)")
    )

    /** Look up the game a [crc32] (case-insensitive) belongs to, or null. */
    fun infoFor(crc32: String): GameTargetInfo? = map[crc32.lowercase()]
}
