package br.com.redclaw.zelda64player.randomizer.patch

/**
 * Typed exception hierarchy for the randomizer (ZPF/ZPFZ) patch decoder,
 * validator, applier and boot-CRC fixer.
 *
 * Every failure raised while handling an OoTRandomizer patch is represented by
 * one of these subtypes so callers can present precise, user-recoverable
 * messages. No raw [Exception] from the patch pipeline crosses the public
 * [RandomizerPatcherFacade] boundary unwrapped.
 *
 * Licensing note: this implementation is clean-room, written solely from the
 * public ZPF/ZPFZ specification (cross-checked against the OoTRandomizer
 * project's `N64Patch.py`). No GPL source was read or copied.
 */
sealed class RandomizerPatchException(message: String) : Exception(message) {

    /** The patch file does not begin with the `ZPFv` magic. */
    class BadMagic(actual: String) :
        RandomizerPatchException("Not a Zelda Patch Format file (expected 'ZPFv' magic, found '$actual')")

    /** The patch version byte is not the only supported value (`1`). */
    class UnsupportedVersion(version: Int) :
        RandomizerPatchException("Unsupported ZPF patch version: $version (only version '1' is supported)")

    /** A concatenated zlib stream could not be inflated (corrupt/truncated). */
    class CorruptPatchStream(detail: String) :
        RandomizerPatchException("Corrupt zlib stream in patch file: $detail")

    /** The patch file contained zero concatenated zlib streams. */
    object NoPatchStreams :
        RandomizerPatchException("Patch file contained no ZPF streams")

    /** A zlib stream inflated to zero bytes (an empty .zpf blob). */
    object EmptyPatchStream :
        RandomizerPatchException("A ZPF stream inflated to zero bytes")

    /** The .zpf byte stream ended before a field could be read. */
    class TruncatedPatch(detail: String) :
        RandomizerPatchException("Patch data truncated: $detail")

    /** The parsed structure failed a sanity check (e.g. ranges misordered). */
    class InvalidStructure(detail: String) :
        RandomizerPatchException("Invalid ZPF structure: $detail")

    /** The supplied base ROM file does not exist. */
    class BaseRomMissing(path: String) :
        RandomizerPatchException("Base ROM not found: $path")

    /** The supplied patch file does not exist. */
    class PatchMissing(path: String) :
        RandomizerPatchException("Patch file not found: $path")

    /** The base ROM is not an accepted OoT 1.0 image (CZLE/CZLJ, version 0). */
    class UnsupportedBaseRom(detail: String) :
        RandomizerPatchException("Unsupported base ROM: $detail")

    /** A low-level I/O or apply step failed. */
    class ApplyFailed(detail: String) :
        RandomizerPatchException("Failed to apply randomizer patch: $detail")
}
