package br.com.redclaw.zelda64player.randomizer.patch

import br.com.redclaw.zelda64player.patcher.n64.RomHeader
import br.com.redclaw.zelda64player.randomizer.patch.RandomizerPatchException.ApplyFailed
import br.com.redclaw.zelda64player.randomizer.patch.RandomizerPatchException.BaseRomMissing
import br.com.redclaw.zelda64player.randomizer.patch.RandomizerPatchException.PatchMissing
import br.com.redclaw.zelda64player.randomizer.patch.RandomizerPatchException.UnsupportedBaseRom
import br.com.redclaw.zelda64player.randomizer.patch.ZpfApplier.applyWorld
import br.com.redclaw.zelda64player.randomizer.patch.ZpfValidator.validate
import br.com.redclaw.zelda64player.randomizer.patch.ZpfzDecoder.decode
import br.com.redclaw.zelda64player.randomizer.patch.ZpfParser.parse
import java.io.File

/**
 * Single public entry point for applying an OoTRandomizer ZPF/ZPFZ seed patch
 * onto a user-supplied base ROM.
 *
 * Pipeline (all fallible steps raise [RandomizerPatchException]; no raw
 * exceptions cross this boundary):
 *  1. Copy the base ROM to `outputDir/outputName` (the working copy).
 *  2. Decode the concatenated zlib streams of the `.zpfz` into per-world blobs.
 *  3. Parse, validate and apply each world in order (DMA relocations, then XOR
 *     edits) onto the working copy.
 *  4. Recompute and rewrite the N64 CIC 6105 boot CRCs in place.
 *  5. Verify the patched header is still an accepted OoT 1.0 image
 *     (`CZLE`/`CZLJ`, version byte `0`).
 *
 * Licensing: clean-room implementation from the documented ZPF/ZPFZ format.
 */
object RandomizerPatcherFacade {

    /** Accepted game codes (OoT 1.0 NTSC-U / NTSC-J). */
    private val ACCEPTED_GAME_CODES = setOf("CZLE", "CZLJ")

    /**
     * Apply [patchFile] onto [baseRom], writing the patched ROM to
     * `outputDir/outputName`.
     *
     * @return the resulting patched ROM [File].
     * @throws RandomizerPatchException on any failure (missing inputs, corrupt
     *   patch, structural error, apply I/O error, or an unsupported base ROM).
     */
    fun applySeedPatch(baseRom: File, patchFile: File, outputDir: File, outputName: String): File {
        if (!baseRom.exists()) throw BaseRomMissing(baseRom.absolutePath)
        if (!patchFile.exists()) throw PatchMissing(patchFile.absolutePath)
        if (!outputDir.exists()) outputDir.mkdirs()

        val outputFile = File(outputDir, outputName)
        try {
            baseRom.copyTo(outputFile, overwrite = true)
        } catch (e: Exception) {
            throw ApplyFailed("failed to copy base ROM to output: ${e.message}")
        }

        val blobs = decode(patchFile)
        val romSize = outputFile.length()
        blobs.forEachIndexed { index, blob ->
            val parsed = parse(blob)
            validate(parsed, romSize)
            applyWorld(outputFile, baseRom, parsed)
        }

        N64BootCrcCalculator.fixBootCrc(outputFile)

        val header = RomHeader.fromNormalizedZ64(outputFile)
        if (header.gameCode !in ACCEPTED_GAME_CODES) {
            throw UnsupportedBaseRom(
                "expected one of ${ACCEPTED_GAME_CODES.joinToString()}, found '${header.gameCode}'"
            )
        }
        if (header.versionByte != 0) {
            throw UnsupportedBaseRom(
                "expected base ROM version byte 0, found ${header.versionByte}"
            )
        }

        return outputFile
    }
}
