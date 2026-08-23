package br.com.redclaw.zelda64player.randomizer

import br.com.redclaw.zelda64player.patcher.n64.RomHeader

/**
 * Acceptance rule for the OoTRandomizer base ROM (Hard Rule #17): ONLY
 * Ocarina of Time 1.0 is supported, specifically the NTSC-U build (game code
 * `CZLE`) or the NTSC-J build (game code `CZLJ`). The header version byte must
 * be `0` (v1.0). Every other ROM — OoT 1.1/1.2/PAL/MQ/GC/VC/iQue, Majora's Mask
 * (`NSME`), or any non-v1.0 build — is rejected.
 *
 * Pure and JVM-testable: callers parse the header (already normalized to z64)
 * and pass it in; no Android dependencies.
 */
object BaseRomValidator {
    /** Accepted OoT 1.0 game codes (NTSC-U / NTSC-J). */
    val ACCEPTED_GAME_CODES = setOf("CZLE", "CZLJ")

    /** The only accepted header version byte (v1.0). */
    const val ACCEPTED_VERSION_BYTE = 0

    /** True iff [header] is an accepted OoT 1.0 NTSC-U/J base ROM. */
    fun isAccepted(header: RomHeader): Boolean =
        header.gameCode in ACCEPTED_GAME_CODES && header.versionByte == ACCEPTED_VERSION_BYTE
}
