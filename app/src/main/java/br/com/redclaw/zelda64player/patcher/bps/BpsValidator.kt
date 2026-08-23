package br.com.redclaw.zelda64player.patcher.bps

import br.com.redclaw.zelda64player.patcher.PatcherException.PatchChecksumMismatch
import br.com.redclaw.zelda64player.patcher.PatcherException.PatchFormatError
import br.com.redclaw.zelda64player.patcher.PatcherException.SourceChecksumMismatch
import br.com.redclaw.zelda64player.patcher.PatcherException.TargetChecksumMismatch
import br.com.redclaw.zelda64player.patcher.n64.ChecksumCalculator

/**
 * Validates the triple CRC32 check mandated by the BPS specification:
 * the source (base ROM), the target (patched ROM) and the patch file itself.
 *
 * Licensing: clean-room implementation from the public BPS specification only.
 */
object BpsValidator {

    /**
     * Verify the measured CRC32s against the values declared in the patch footer.
     * @return [Result.success] if all three match, otherwise a typed failure:
     *  - [SourceChecksumMismatch] when the base ROM is wrong/corrupt
     *  - [TargetChecksumMismatch] when the produced ROM does not match
     *  - [PatchChecksumMismatch] when the patch file itself is corrupt
     */
    fun validate(result: BpsApplier.ApplyResult): Result<Unit> = runCatching {
        val expectedSource = ChecksumCalculator.toHex(result.expectedSourceCrc)
        val foundSource = ChecksumCalculator.toHex(result.actualSourceCrc)
        if (result.actualSourceCrc != result.expectedSourceCrc) {
            throw SourceChecksumMismatch(expectedSource, foundSource)
        }

        val expectedTarget = ChecksumCalculator.toHex(result.expectedTargetCrc)
        val foundTarget = ChecksumCalculator.toHex(result.actualTargetCrc)
        if (result.actualTargetCrc != result.expectedTargetCrc) {
            throw TargetChecksumMismatch(expectedTarget, foundTarget)
        }

        val expectedPatch = ChecksumCalculator.toHex(result.expectedPatchCrc)
        val foundPatch = ChecksumCalculator.toHex(result.actualPatchCrc)
        if (result.actualPatchCrc != result.expectedPatchCrc) {
            throw PatchChecksumMismatch(expectedPatch, foundPatch)
        }
        Unit
    }

    /**
     * Convenience overload that validates only the source CRC32 of a base ROM
     * against the patch's declared source CRC32 (used to probe which imported
     * base ROM a patch expects, without applying it).
     */
    fun validateSource(baseRomCrc32Hex: String, expectedSourceCrc32Hex: String): Result<Unit> =
        if (baseRomCrc32Hex.equals(expectedSourceCrc32Hex, ignoreCase = true)) {
            Result.success(Unit)
        } else {
            Result.failure(SourceChecksumMismatch(expectedSourceCrc32Hex, baseRomCrc32Hex))
        }

    /** Thrown if a patch cannot be opened/parsed at all (e.g. not a BPS1 file). */
    fun formatError(message: String): PatchFormatError = PatchFormatError(message)
}
