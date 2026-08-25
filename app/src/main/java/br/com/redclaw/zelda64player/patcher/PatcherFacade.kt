package br.com.redclaw.zelda64player.patcher

import br.com.redclaw.zelda64player.patcher.bps.BpsApplier
import br.com.redclaw.zelda64player.patcher.bps.BpsValidator
import br.com.redclaw.zelda64player.patcher.ips.IpsApplier
import br.com.redclaw.zelda64player.patcher.n64.ChecksumCalculator
import br.com.redclaw.zelda64player.patcher.xdelta.XdeltaApplier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/**
 * Single public entry point for patch application. All fallible operations
 * return [kotlin.Result] carrying a typed [PatcherException] on failure — no
 * raw exceptions cross this boundary.
 *
 * The facade auto-detects the patch container format (BPS or IPS) from its
 * magic and dispatches to the appropriate applier, so callers (e.g. the launch
 * flow) need not know which format a given hack uses.
 *
 * Licensing: clean-room implementation from the public BPS and IPS
 * specifications (both public domain / freely documented). No GPL patcher
 * source (rom_patcher_js, UniPatcher, Lips, beat) was read or copied.
 */
object PatcherFacade {

    /** Detected patch container format. */
    enum class PatchFormat { BPS, IPS, XDELTA, UNKNOWN }

    /**
     * Apply [patch] onto [baseRom], writing the patched ROM to [output].
     * Runs on [Dispatchers.IO]. The format is detected automatically.
     */
    suspend fun applyPatch(baseRom: File, patch: File, output: File): Result<Unit> =
        withContext(Dispatchers.IO) { applyPatchBlocking(baseRom, patch, output) }

    /**
     * Non-suspending variant of [applyPatch] for plain-JVM unit tests and
     * synchronous callers. Dispatches by detected format; BPS behavior is
     * byte-identical to the pre-IPS implementation.
     */
    fun applyPatchBlocking(baseRom: File, patch: File, output: File): Result<Unit> =
        when (detectPatchFormat(patch)) {
            PatchFormat.BPS -> BpsApplier.apply(baseRom, patch, output).fold(
                onSuccess = { applyResult -> BpsValidator.validate(applyResult) },
                onFailure = { e -> Result.failure(e) }
            )
            PatchFormat.IPS -> IpsApplier.apply(baseRom, patch, output)
            PatchFormat.XDELTA -> XdeltaApplier.apply(baseRom, patch, output)
            PatchFormat.UNKNOWN -> Result.failure(
                PatcherException.PatchFormatError(
                    "Unrecognized patch format (expected 'BPS1' or 'PATCH')"
                )
            )
        }

    /**
     * Sniff the patch magic to determine its container format without applying
     * it. Used by the launch flow to branch between BPS (which needs a matching
     * base ROM) and IPS (self-contained).
     */
    fun detectPatchFormat(file: File): PatchFormat {
        if (!file.exists() || file.length() < 4) return PatchFormat.UNKNOWN
        RandomAccessFile(file, "r").use { raf ->
            val buf = ByteArray(5)
            val n = raf.read(buf)
            if (n >= 5 && buf.contentEquals("PATCH".toByteArray(Charsets.US_ASCII))) {
                return PatchFormat.IPS
            }
            if (n >= 4 && buf.copyOf(4).contentEquals("BPS1".toByteArray(Charsets.US_ASCII))) {
                return PatchFormat.BPS
            }
            // VCDIFF / xdelta3 magic: 0xD6 0xC3 0xC4 0x00 (RFC 3284 / xdelta3).
            if (n >= 4 &&
                buf[0] == 0xD6.toByte() && buf[1] == 0xC3.toByte() &&
                buf[2] == 0xC4.toByte() && buf[3] == 0x00.toByte()
            ) {
                return PatchFormat.XDELTA
            }
            return PatchFormat.UNKNOWN
        }
    }

    /**
     * Read the source (base ROM) CRC32 that [patch] expects, without applying
     * it. Used to probe which imported base ROM a BPS patch targets. IPS patches
     * carry no source CRC32, so this returns a typed error for them.
     * @return the expected source CRC32 as an 8-digit lowercase hex string.
     */
    fun expectedSourceCrc32(patch: File): Result<String> = runCatching {
        when (detectPatchFormat(patch)) {
            PatchFormat.BPS -> {
                val patchTotal = patch.length()
                if (patchTotal < 12) throw PatcherException.PatchFormatError("Patch file is too small to be valid")
                RandomAccessFile(patch, "r").use { raf ->
                    val magic = ByteArray(4)
                    raf.readFully(magic)
                    if (!magic.contentEquals("BPS1".toByteArray(Charsets.US_ASCII))) {
                        throw PatcherException.PatchFormatError("Missing 'BPS1' magic")
                    }
                    raf.seek(patchTotal - 12)
                    val b = ByteArray(4)
                    raf.readFully(b)
                    val crc = (b[0].toLong() and 0xFF) or
                        ((b[1].toLong() and 0xFF) shl 8) or
                        ((b[2].toLong() and 0xFF) shl 16) or
                        ((b[3].toLong() and 0xFF) shl 24)
                    ChecksumCalculator.toHex(crc)
                }
            }
            PatchFormat.IPS ->
                throw PatcherException.PatchFormatError("IPS patches do not carry a source CRC32")
            PatchFormat.XDELTA ->
                throw PatcherException.PatchFormatError("xdelta3 (VCDIFF) patches do not carry a source CRC32")
            PatchFormat.UNKNOWN ->
                throw PatcherException.PatchFormatError("Unrecognized patch format")
        }
    }
}
