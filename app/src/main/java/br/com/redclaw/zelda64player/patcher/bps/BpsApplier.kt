package br.com.redclaw.zelda64player.patcher.bps

import br.com.redclaw.zelda64player.patcher.PatcherException.PatchFormatError
import br.com.redclaw.zelda64player.patcher.n64.ChecksumCalculator
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.zip.CRC32

/**
 * Streaming BPS applier.
 *
 * The source ROM and the produced target ROM are accessed through
 * [RandomAccessFile] with a small bounded transfer buffer, so a 32–64 MB ROM is
 * never held entirely in the heap. The patch is read sequentially through a
 * [PatchReader] that simultaneously accumulates the patch CRC32 (over every
 * byte except the trailing 4-byte checksum field).
 *
 * Licensing: clean-room implementation from the public BPS specification only.
 */
object BpsApplier {

    private const val BUFFER_SIZE = 64 * 1024
    private const val FOOTER_SIZE = 12L

    /**
     * Outcome of an apply pass: the CRC32s declared in the patch footer
     * (expected) alongside the CRC32s actually measured while applying (actual).
     */
    data class ApplyResult(
        val expectedSourceCrc: Long,
        val expectedTargetCrc: Long,
        val expectedPatchCrc: Long,
        val actualSourceCrc: Long,
        val actualTargetCrc: Long,
        val actualPatchCrc: Long
    )

    /**
     * Apply [patch] onto [source], writing the result to [output].
     * @return [Result.success] with the measured/expected CRC32s, or
     * [Result.failure] with a typed [PatchFormatError] on a malformed patch.
     */
    fun apply(source: File, patch: File, output: File): Result<ApplyResult> {
        val result = runCatching { doApply(source, patch, output) }
        if (result.isSuccess) return result
        val e = result.exceptionOrNull()!!
        return if (e is java.io.IOException) {
            Result.failure(PatchFormatError("Patch stream ended unexpectedly: ${e.message}"))
        } else {
            result
        }
    }

    private fun doApply(source: File, patch: File, output: File): ApplyResult {
        val patchTotal = patch.length()
        if (patchTotal < FOOTER_SIZE + 4) throw PatchFormatError("Patch file is too small to be valid")

        val patchReader = PatchReader(BufferedInputStream(FileInputStream(patch)), patchTotal)
        val header = BpsParser.readHeader(patchReader)

        RandomAccessFile(source, "r").use { sourceRaf ->
            RandomAccessFile(output, "rw").use { outputRaf ->
                outputRaf.setLength(0)

                val targetCrc = CRC32()
                var outputOffset = 0L
                var sourceRelative = 0L
                var targetRelative = 0L
                val buffer = ByteArray(BUFFER_SIZE)

                while (patchReader.bytesRead < patchTotal - FOOTER_SIZE) {
                    val data = VarInt.decode(patchReader)
                    val action = (data and 3L).toInt()
                    val length = (data ushr 2) + 1L

                    when (action) {
                        0 -> applySourceRead(sourceRaf, outputRaf, targetCrc, buffer, length) {
                            outputOffset
                        }.also { outputOffset = it }

                        1 -> outputOffset = applyTargetRead(
                            patchReader, outputRaf, targetCrc, buffer, length, outputOffset
                        )

                        2 -> {
                            val offset = BpsParser.decodeSignedOffset(VarInt.decode(patchReader))
                            sourceRelative += offset
                            if (sourceRelative < 0 || sourceRelative + length > header.sourceSize) {
                                throw PatchFormatError(
                                    "SourceCopy offset out of bounds (sourceRelative=$sourceRelative, length=$length)"
                                )
                            }
                            outputOffset = applyCopy(
                                sourceRaf, outputRaf, targetCrc, buffer, length,
                                sourceRelative, outputOffset
                            ) { src -> sourceRelative = src }
                        }

                        3 -> {
                            val offset = BpsParser.decodeSignedOffset(VarInt.decode(patchReader))
                            targetRelative += offset
                            // Spec: the STARTING targetRelativeOffset must point at
                            // already-written data (< outputOffset). The read cursor MAY
                            // then run past the write cursor's original position — reads
                            // and writes advance together, which is exactly how BPS encodes
                            // RLE runs (TargetCopy growing its own output).
                            if (outputOffset == 0L || targetRelative < 0 || targetRelative >= outputOffset) {
                                throw PatchFormatError(
                                    "TargetCopy offset out of bounds (targetRelative=$targetRelative, length=$length)"
                                )
                            }
                            outputOffset = applyCopy(
                                outputRaf, outputRaf, targetCrc, buffer, length,
                                targetRelative, outputOffset
                            ) { tgt -> targetRelative = tgt }
                        }

                        else -> throw PatchFormatError("Unknown BPS action: $action")
                    }
                }

                val sourceCrc32 = readUint32Le(patchReader)
                val targetCrc32 = readUint32Le(patchReader)
                // The patch CRC covers every byte EXCEPT the trailing 4-byte patch CRC
                // field itself, so the patch CRC field must be read outside the CRC stream.
                val patchCrc32 = readUint32LeAt(patch, patchTotal - 4)
                val actualSourceCrc = ChecksumCalculator.crc32Raw(source)
                val actualTargetCrc = targetCrc.value
                val actualPatchCrc = patchReader.crc.value

                return ApplyResult(
                    expectedSourceCrc = sourceCrc32,
                    expectedTargetCrc = targetCrc32,
                    expectedPatchCrc = patchCrc32,
                    actualSourceCrc = actualSourceCrc,
                    actualTargetCrc = actualTargetCrc,
                    actualPatchCrc = actualPatchCrc
                )
            }
        }
    }

    private fun applySourceRead(
        sourceRaf: RandomAccessFile,
        outputRaf: RandomAccessFile,
        targetCrc: CRC32,
        buffer: ByteArray,
        length: Long,
        getOffset: () -> Long
    ): Long {
        var remaining = length
        var offset = getOffset()
        while (remaining > 0) {
            val n = minOf(remaining, buffer.size.toLong()).toInt()
            sourceRaf.seek(offset)
            readFully(sourceRaf, buffer, n)
            outputRaf.seek(offset)
            outputRaf.write(buffer, 0, n)
            targetCrc.update(buffer, 0, n)
            offset += n
            remaining -= n
        }
        return offset
    }

    private fun applyTargetRead(
        patchReader: PatchReader,
        outputRaf: RandomAccessFile,
        targetCrc: CRC32,
        buffer: ByteArray,
        length: Long,
        outputOffset: Long
    ): Long {
        var remaining = length
        var offset = outputOffset
        while (remaining > 0) {
            val n = minOf(remaining, buffer.size.toLong()).toInt()
            val got = patchReader.read(buffer, 0, n)
            if (got <= 0) throw PatchFormatError("Unexpected end of stream in TargetRead payload")
            outputRaf.seek(offset)
            outputRaf.write(buffer, 0, got)
            targetCrc.update(buffer, 0, got)
            offset += got
            remaining -= got
        }
        return offset
    }

    /**
     * Copy [length] bytes from [readRaf] at [readPos] to [outputRaf] at
     * [writePos], advancing both. Used for both SourceCopy and TargetCopy.
     * [onReadAdvance] reports the new read position so the caller can persist
     * its relative cursor.
     *
     * Overlap handling: TargetCopy may read from the SAME file it writes to,
     * with the read window behind the write cursor (readPos < writePos). The
     * spec's semantics are strictly sequential — each byte written becomes
     * readable by the next iterations (this is how BPS encodes RLE runs).
     * A naive buffered chunk copy would read stale pre-write bytes, so:
     *  - when the overlap period fits in the buffer, the period-sized source
     *    window is read ONCE and then repeated (every subsequent period-sized
     *    block equals the previous one);
     *  - otherwise chunks are capped at the gap so a chunk never reads bytes
     *    that the same command has not yet written.
     */
    private fun applyCopy(
        readRaf: RandomAccessFile,
        outputRaf: RandomAccessFile,
        targetCrc: CRC32,
        buffer: ByteArray,
        length: Long,
        readPos: Long,
        writePos: Long,
        onReadAdvance: (Long) -> Unit
    ): Long {
        val sameFile = readRaf === outputRaf
        val overlapPeriod = if (sameFile && writePos > readPos) writePos - readPos else 0L

        // Fast path: small self-overlap (classic RLE run) — read the unique
        // period-sized window once, then replay it for the whole length.
        if (overlapPeriod in 1..buffer.size.toLong()) {
            val period = overlapPeriod.toInt()
            readRaf.seek(readPos)
            readFully(readRaf, buffer, period)
            outputRaf.seek(writePos)
            var written = 0L
            while (written < length) {
                val n = minOf(length - written, period.toLong()).toInt()
                outputRaf.write(buffer, 0, n)
                targetCrc.update(buffer, 0, n)
                written += n
            }
            onReadAdvance(readPos + length)
            return writePos + length
        }

        var readOffset = readPos
        var writeOffset = writePos
        var remaining = length
        while (remaining > 0) {
            // Never read past what this command has already written when the
            // read window trails the write cursor within the same file.
            val gap = if (sameFile && writeOffset > readOffset) writeOffset - readOffset else Long.MAX_VALUE
            val n = minOf(remaining, buffer.size.toLong(), gap).toInt()
            readRaf.seek(readOffset)
            readFully(readRaf, buffer, n)
            outputRaf.seek(writeOffset)
            outputRaf.write(buffer, 0, n)
            targetCrc.update(buffer, 0, n)
            readOffset += n
            writeOffset += n
            remaining -= n
        }
        onReadAdvance(readOffset)
        return writeOffset
    }

    private fun readUint32Le(stream: InputStream): Long {
        var value = 0L
        repeat(4) { i -> value = value or ((stream.read() and 0xFF).toLong() shl (8 * i)) }
        return value
    }

    private fun readUint32LeAt(file: File, offset: Long): Long {
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(offset)
            var value = 0L
            repeat(4) { i -> value = value or ((raf.read() and 0xFF).toLong() shl (8 * i)) }
            return value
        }
    }

    private fun readFully(raf: RandomAccessFile, buf: ByteArray, len: Int) {
        var offset = 0
        while (offset < len) {
            val n = raf.read(buf, offset, len - offset)
            if (n < 0) throw PatchFormatError("Unexpected end of source/target stream")
            offset += n
        }
    }

    /**
     * InputStream wrapper that tracks how many bytes have been read and updates
     * an embedded CRC32 for every byte except those in the final 4-byte patch
     * checksum field (i.e. bytes at index >= [patchTotal] - 4 are excluded).
     */
    class PatchReader(
        private val delegate: InputStream,
        private val patchTotal: Long
    ) : InputStream() {
        val crc = CRC32()
        var bytesRead = 0L
            private set

        override fun read(): Int {
            val b = ByteArray(1)
            val n = read(b, 0, 1)
            return if (n <= 0) -1 else b[0].toInt() and 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val n = delegate.read(b, off, len)
            if (n <= 0) return n
            for (i in 0 until n) {
                if (bytesRead < patchTotal - 4) crc.update(b[off + i].toInt() and 0xFF)
                bytesRead++
            }
            return n
        }

        override fun close() = delegate.close()
    }
}
