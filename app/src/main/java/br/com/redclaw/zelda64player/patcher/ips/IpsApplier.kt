package br.com.redclaw.zelda64player.patcher.ips

import br.com.redclaw.zelda64player.patcher.PatcherException.PatchFormatError
import java.io.File
import java.io.RandomAccessFile

/**
 * Streaming IPS (International Patching System) applier.
 *
 * IPS is a record-based format with absolute offsets and no checksums, so the
 * only validation possible is structural (magic, record framing). The base ROM
 * is irrelevant to IPS: each record carries the literal target bytes at an
 * absolute offset, so the patched ROM is reconstructed entirely from the patch
 * (gaps between records are zero-filled, matching the reference IPS behavior).
 *
 * Implementation is clean-room from the public IPS specification only. No GPL
 * patcher source (rom_patcher_js, UniPatcher, Lips) was read or copied.
 *
 * Format (all multi-byte integers big-endian):
 *   string "PATCH"                         (5 bytes magic)
 *   repeated until "EOF" or end of file:
 *     uint32 offset
 *     uint16 length
 *     if length == 0:                       (RLE record)
 *       uint16 run_length
 *       uint8  value
 *     else:
 *       uint8  data[length]
 *   string "EOF"                            (3 bytes terminator; may be absent,
 *                                            and trailing bytes after it are
 *                                            ignored — both handled tolerantly)
 */
object IpsApplier {

    private const val BUFFER_SIZE = 64 * 1024
    private const val MAGIC = "PATCH"
    private const val EOF = "EOF"

    /**
     * Apply [patch] to produce [output]. [source] is ignored (IPS is
     * self-contained); it is accepted only to keep the public facade signature
     * uniform with BPS.
     *
     * @return [Result.success] on a structurally valid patch, or
     * [Result.failure] with a typed [PatchFormatError] on a bad magic or a
     * truncated record.
     */
    fun apply(source: File, patch: File, output: File): Result<Unit> {
        val result = runCatching { doApply(patch, output) }
        if (result.isSuccess) return result
        val e = result.exceptionOrNull()!!
        return if (e is java.io.IOException) {
            Result.failure(PatchFormatError("Patch stream ended unexpectedly: ${e.message}"))
        } else {
            result
        }
    }

    private fun doApply(patch: File, output: File) {
        if (patch.length() < MAGIC.length) {
            throw PatchFormatError("Patch file is too small to be a valid IPS patch")
        }
        RandomAccessFile(patch, "r").use { patchRaf ->
            val magic = ByteArray(MAGIC.length)
            patchRaf.readFully(magic)
            if (!magic.contentEquals(MAGIC.toByteArray(Charsets.US_ASCII))) {
                throw PatchFormatError("Missing 'PATCH' magic (got '${magic.toString(Charsets.US_ASCII)}')")
            }
            RandomAccessFile(output, "rw").use { outRaf ->
                outRaf.setLength(0)
                var maxOffset = 0L
                while (true) {
                    val header = ByteArray(3)
                    val n = patchRaf.read(header)
                    if (n < 3) {
                        // Clean end of file (no EOF marker) is tolerated; a
                        // partial header byte is a truncation error.
                        if (n == -1) break
                        throw PatchFormatError("Truncated record header")
                    }
                    if (header.contentEquals(EOF.toByteArray(Charsets.US_ASCII))) {
                        break // terminator reached; ignore any trailing bytes
                    }
                    val b3 = patchRaf.read()
                    if (b3 < 0) throw PatchFormatError("Truncated offset")
                    val offset = ((header[0].toLong() and 0xFF) shl 24) or
                        ((header[1].toLong() and 0xFF) shl 16) or
                        ((header[2].toLong() and 0xFF) shl 8) or
                        (b3.toLong() and 0xFF)
                    val lenHi = patchRaf.read()
                    val lenLo = patchRaf.read()
                    if (lenHi < 0 || lenLo < 0) throw PatchFormatError("Truncated record length")
                    val length = (lenHi shl 8) or lenLo
                    if (length == 0) {
                        val rlHi = patchRaf.read()
                        val rlLo = patchRaf.read()
                        if (rlHi < 0 || rlLo < 0) throw PatchFormatError("Truncated RLE run length")
                        val runLength = (rlHi shl 8) or rlLo
                        val value = patchRaf.read()
                        if (value < 0) throw PatchFormatError("Truncated RLE value")
                        writeRun(outRaf, offset, runLength.toLong(), value)
                        maxOffset = maxOf(maxOffset, offset + runLength)
                    } else {
                        var remaining = length.toLong()
                        var pos = offset
                        val buf = ByteArray(BUFFER_SIZE)
                        while (remaining > 0) {
                            val chunk = minOf(remaining, buf.size.toLong()).toInt()
                            val got = patchRaf.read(buf, 0, chunk)
                            if (got < 0) throw PatchFormatError("Truncated record data")
                            outRaf.seek(pos)
                            outRaf.write(buf, 0, got)
                            pos += got
                            remaining -= got
                        }
                        maxOffset = maxOf(maxOffset, offset + length)
                    }
                }
                outRaf.setLength(maxOffset)
            }
        }
    }

    private fun writeRun(raf: RandomAccessFile, offset: Long, runLength: Long, value: Int) {
        raf.seek(offset)
        var remaining = runLength
        val buf = ByteArray(BUFFER_SIZE) { value.toByte() }
        while (remaining > 0) {
            val n = minOf(remaining, buf.size.toLong()).toInt()
            raf.write(buf, 0, n)
            remaining -= n
        }
    }
}
