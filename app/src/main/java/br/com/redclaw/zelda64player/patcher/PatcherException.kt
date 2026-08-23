package br.com.redclaw.zelda64player.patcher

/**
 * Sealed hierarchy of expected, user-recoverable failures raised by the patcher
 * module. These are the only exception types that cross the public API boundary
 * (wrapped in [kotlin.Result]); unexpected [Exception]s are never leaked to
 * callers as raw exceptions.
 *
 * Licensing note: this implementation is clean-room, written solely from the
 * public BPS specification (byuu, public domain — see
 * https://github.com/blakesmith/rombp/blob/master/docs/bps_spec.md). No GPL
 * source (rom_patcher_js, UniPatcher, beat) was read or copied.
 */
sealed class PatcherException(message: String) : Exception(message) {

    /** The patch file is not a valid BPS1 stream (bad magic, truncated, malformed). */
    class PatchFormatError(message: String) : PatcherException(message)

    /** The supplied base ROM's CRC32 does not match the patch's expected source CRC32. */
    class SourceChecksumMismatch(
        val expectedCrc32: String,
        val foundCrc32: String
    ) : PatcherException(
        "Source (base ROM) checksum mismatch: expected $expectedCrc32 but found $foundCrc32"
    )

    /** The produced target ROM's CRC32 does not match the patch's expected target CRC32. */
    class TargetChecksumMismatch(
        val expectedCrc32: String,
        val foundCrc32: String
    ) : PatcherException(
        "Target (patched ROM) checksum mismatch: expected $expectedCrc32 but found $foundCrc32"
    )

    /** The patch file itself is corrupted (its embedded patch CRC32 does not verify). */
    class PatchChecksumMismatch(
        val expectedCrc32: String,
        val foundCrc32: String
    ) : PatcherException(
        "Patch checksum mismatch: expected $expectedCrc32 but found $foundCrc32"
    )

    /** The N64 ROM byte order could not be detected (unknown magic word). */
    class RomFormatError(message: String) : PatcherException(message)
}
