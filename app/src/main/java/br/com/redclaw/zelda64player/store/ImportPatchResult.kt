package br.com.redclaw.zelda64player.store

import br.com.redclaw.zelda64player.ocarina.OcarinaGame

/**
 * Outcome of [br.com.redclaw.zelda64player.store.ImportedPatchInstaller.install].
 *
 * Declared as a sealed interface with top-level data-class implementations
 * (rather than nested classes) for maximum compiler compatibility.
 *
 * - [ImportPatchSuccess]: the patch was applied and the hack is installed
 *   (available in the Library). [family] is the detected game family (OoT/MM)
 *   or null.
 * - [ImportPatchNoCompatibleRom]: a BPS patch whose required base ROM CRC32
 *   matches no imported base ROM. [targetDescription] names the expected game
 *   (from [KnownBaseRomTable]) when known, else null. [foundCrc32s] lists the
 *   CRC32s of the currently imported base ROMs (for diagnostics).
 * - [ImportPatchInvalid]: the patch could not be applied (corrupt/unsupported
 *   BPS/IPS).
 * - [ImportPatchUnsupported]: the file is neither BPS nor IPS.
 */
sealed interface ImportPatchResult

data class ImportPatchSuccess(
    val hackId: String,
    val title: String,
    val family: OcarinaGame?
) : ImportPatchResult

data class ImportPatchNoCompatibleRom(
    val expectedCrc32: String,
    val targetDescription: String?,
    val foundCrc32s: List<String>
) : ImportPatchResult

data class ImportPatchInvalid(val message: String) : ImportPatchResult

data class ImportPatchUnsupported(val message: String) : ImportPatchResult
