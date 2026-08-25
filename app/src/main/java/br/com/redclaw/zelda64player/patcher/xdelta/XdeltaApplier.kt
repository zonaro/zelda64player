package br.com.redclaw.zelda64player.patcher.xdelta

import br.com.redclaw.zelda64player.patcher.PatcherException
import java.io.File

/**
 * Applies xdelta3 (VCDIFF) patches onto a base ROM using the bundled native
 * xdelta3 decoder (libxdelta_jni.so).
 *
 * xdelta3 validates the source ROM internally during decode and fails on a
 * mismatch; we surface that as [PatcherException.SourceChecksumMismatch] when
 * its diagnostics mention a source mismatch, and any other failure as
 * [PatcherException.PatchFormatError].
 *
 * Unlike BPS, the VCDIFF container does not carry the source CRC32, so base-ROM
 * resolution for xdelta3 is driven by the catalog-declared CRC (see
 * [br.com.redclaw.zelda64player.store.DownloadManager.resolveBaseFile]).
 *
 * Licensing: the native decoder is jmacd/xdelta, Apache License 2.0 (compatible
 * with this app's GPL-3.0). This Kotlin wrapper is original code.
 */
object XdeltaApplier {

    /** Serializes native calls: xdelta3's main() uses static state. */
    private val lock = Any()

    init {
        // Tests may point at a host-built copy via this system property; the
        // packaged app relies on the bundled libxdelta_jni.so instead.
        val customPath = System.getProperty("zelda64.xdelta.jni.path")
        if (customPath != null) {
            System.load(customPath)
        } else {
            System.loadLibrary("xdelta_jni")
        }
    }

    @JvmStatic
    external fun applyNative(src: String, patch: String, out: String): Int

    @JvmStatic
    external fun getLastError(): String

    /**
     * Apply [patch] onto [baseRom], writing the result to [output].
     * @return [Result.success] on success, or a typed [PatcherException] on
     * failure (source mismatch or malformed patch).
     */
    fun apply(baseRom: File, patch: File, output: File): Result<Unit> = runCatching {
        synchronized(lock) {
            output.parentFile?.mkdirs()
            // Drop any stale output so a failed run never leaves a partial file
            // that a later step could mistake for a successful ROM.
            if (output.exists()) output.delete()
            val rc = applyNative(baseRom.absolutePath, patch.absolutePath, output.absolutePath)
            if (rc != 0) {
                val diagnostic = getLastError().takeIf { it.isNotBlank() }
                    ?: "xdelta3 exited with code $rc"
                throw classify(diagnostic)
            }
            Unit
        }
    }

    /**
     * Map xdelta3's diagnostic text to the most useful typed exception. A source
     * mismatch is reported distinctly so the UI can tell the user the imported
     * base ROM is wrong; everything else is treated as a malformed patch.
     */
    private fun classify(diagnostic: String): PatcherException {
        val lower = diagnostic.lowercase()
        val sourceMismatch = lower.contains("source") &&
            (lower.contains("mismatch") || lower.contains("checksum") || lower.contains("not match"))
        return if (sourceMismatch) {
            PatcherException.SourceChecksumMismatch(
                "(xdelta3 source validation)",
                diagnostic.trim().lineSequence().firstOrNull { it.isNotBlank() } ?: diagnostic.trim()
            )
        } else {
            PatcherException.PatchFormatError("xdelta3 decode failed: ${diagnostic.trim()}")
        }
    }
}
