package br.com.redclaw.zelda64player.patcher

import br.com.redclaw.zelda64player.patcher.bps.BpsApplier
import br.com.redclaw.zelda64player.patcher.bps.BpsValidator
import br.com.redclaw.zelda64player.patcher.n64.ChecksumCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/**
 * Single public entry point for BPS patching. All fallible operations return
 * [kotlin.Result] carrying a typed [PatcherException] on failure — no raw
 * exceptions cross this boundary.
 *
 * Licensing: clean-room implementation from the public BPS specification
 * (byuu, public domain). No GPL patcher source was read or copied.
 */
object PatcherFacade {

    /**
     * Apply [patch] onto [baseRom], writing the patched ROM to [output].
     * Runs on [Dispatchers.IO].
     */
    suspend fun applyPatch(baseRom: File, patch: File, output: File): Result<Unit> =
        withContext(Dispatchers.IO) { applyPatchBlocking(baseRom, patch, output) }

    /**
     * Non-suspending variant of [applyPatch] for plain-JVM unit tests and
     * synchronous callers.
     */
    fun applyPatchBlocking(baseRom: File, patch: File, output: File): Result<Unit> =
        BpsApplier.apply(baseRom, patch, output).fold(
            onSuccess = { applyResult -> BpsValidator.validate(applyResult) },
            onFailure = { e -> Result.failure(e) }
        )

    /**
     * Read the source (base ROM) CRC32 that [patch] expects, without applying
     * it. Used to probe which imported base ROM a patch targets.
     * @return the expected source CRC32 as an 8-digit lowercase hex string.
     */
    fun expectedSourceCrc32(patch: File): Result<String> = runCatching {
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
}
