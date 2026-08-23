package br.com.redclaw.zelda64player.randomizer.patch

import br.com.redclaw.zelda64player.randomizer.patch.ZpfModels.EditBlock
import br.com.redclaw.zelda64player.randomizer.patch.ZpfModels.ParsedZpf
import br.com.redclaw.zelda64player.randomizer.patch.ZpfModels.ZpfHeader
import java.io.File
import java.io.RandomAccessFile

/**
 * Applies a parsed [ParsedZpf] world onto a base-ROM copy using random-access
 * I/O. The base ROM is **never** loaded fully into the heap; the XOR key window
 * is the only region read eagerly (it is a small subset of the ROM, e.g. ~3.6 MB
 * for OoT), and all other reads/writes are bounded and seek-based.
 *
 * Application order per world (matching OoTRandomizer `N64Patch.py`):
 *  1. DMA table relocations (each writes a 16-byte `(start, end, start, 0)`
 *     entry and copies/zero-fills the file's data).
 *  2. XOR edit blocks, advancing the key cursor per non-zero written byte and
 *     skipping zero key bytes.
 *
 * The XOR key is always read from the **original** base ROM (the source file),
 * never from the output copy being mutated.
 *
 * Licensing: clean-room implementation from the documented format.
 */
object ZpfApplier {

    private const val DMA_ENTRY_SIZE = 0x10
    private const val BUFFER_SIZE = 64 * 1024
    private const val MAX_DMA_SCAN = 4096 // upper bound on original DMA table entries to scan
    private const val NULL_SOURCE: Int = -1 // sentinel for "zero-fill" DMA source

    /**
     * Apply [world] to [outputRom], reading key/copy source bytes from
     * [sourceRom] (the unmodified base ROM).
     *
     * @throws RandomizerPatchException.ApplyFailed on I/O or bounds errors.
     */
    fun applyWorld(outputRom: File, sourceRom: File, world: ParsedZpf) {
        try {
            RandomAccessFile(sourceRom, "r").use { source ->
                RandomAccessFile(outputRom, "rw").use { output ->
                    applyDmaUpdates(source, output, world)
                    applyXorBlocks(source, output, world)
                }
            }
        } catch (e: RandomizerPatchException) {
            throw e
        } catch (e: Exception) {
            throw RandomizerPatchException.ApplyFailed(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun applyDmaUpdates(source: RandomAccessFile, output: RandomAccessFile, world: ParsedZpf) {
        val h = world.header
        val dmaBase = h.dmaTableStart.toLong() and 0xFFFFFFFFL
        for (dma in world.dmaUpdates) {
            val entryOffset = dmaBase + (dma.index.toLong() and 0xFFFFFFFFL) * DMA_ENTRY_SIZE
            val start = dma.newStart.toLong() and 0xFFFFFFFFL
            val size = dma.size.toLong() and 0xFFFFFFFFL
            val end = start + size

            writeWordBE(output, entryOffset, start)
            writeWordBE(output, entryOffset + 4, end)
            writeWordBE(output, entryOffset + 8, start)
            writeWordBE(output, entryOffset + 12, 0)

            if (dma.fromFileAddress != 0xFFFFFFFF.toInt()) {
                val srcOff = dma.fromFileAddress.toLong() and 0xFFFFFFFFL
                // N64Patch.py copies min(size, originalFileSize) from the source
                // file, then zero-fills the remainder. originalFileSize is taken
                // from the ORIGINAL DMA table (at dmaTableStart in the base ROM).
                // If the source file cannot be located there, fall back to
                // copying as many bytes as are available from the source address.
                val originalSize = lookupOriginalFileSize(source, h, dma.fromFileAddress)
                val available = (source.length() - srcOff).coerceAtLeast(0)
                val copySize = if (originalSize >= 0) {
                    minOf(size, originalSize.toLong())
                } else {
                    minOf(size, available)
                }
                copyRegion(source, output, srcOff, start, copySize)
                val remaining = size - copySize
                if (remaining > 0) zeroFill(output, start + copySize, remaining)
            } else {
                zeroFill(output, start, size)
            }
        }
    }

    private fun applyXorBlocks(source: RandomAccessFile, output: RandomAccessFile, world: ParsedZpf) {
        val h = world.header
        val window = readKeyWindow(source, h)
        var cursor = h.keyAddress

        for (block in world.editBlocks) {
            if (block.isContinue) {
                repeat(block.keySkip) {
                    cursor = keyNext(cursor, window, h)
                }
            }
            var addr = block.address.toLong() and 0xFFFFFFFFL
            for (b in block.data) {
                val patchByte = b.toInt() and 0xFF
                if (patchByte == 0) {
                    output.seek(addr)
                    output.write(0)
                } else {
                    cursor = keyNext(cursor, window, h)
                    val key = window[cursor - h.keyRangeMin].toInt() and 0xFF
                    val out = (key xor patchByte) and 0xFF
                    output.seek(addr)
                    output.write(out)
                }
                addr += 1
            }
        }
    }

    /**
     * Advance the XOR key cursor by one non-zero key byte.
     *
     * Mirrors `N64Patch.py`'s `key_next`: increment the cursor, wrap from
     * `keyRangeMax + 1` back to `keyRangeMin`, read the byte, and skip (without
     * consuming) any `0x00` bytes.
     */
    private fun keyNext(cursor: Int, window: ByteArray, h: ZpfHeader): Int {
        var c = cursor
        var key: Int
        do {
            c += 1
            if (c > h.keyRangeMax) c = h.keyRangeMin
            key = window[c - h.keyRangeMin].toInt() and 0xFF
        } while (key == 0)
        return c
    }

    /** Read the `[keyRangeMin, keyRangeMax]` window from the source ROM once. */
    private fun readKeyWindow(source: RandomAccessFile, h: ZpfHeader): ByteArray {
        val min = h.keyRangeMin.toLong() and 0xFFFFFFFFL
        val max = h.keyRangeMax.toLong() and 0xFFFFFFFFL
        val len = (max - min + 1).toInt()
        if (len <= 0) throw RandomizerPatchException.ApplyFailed("invalid key window size $len")
        if (min + len > source.length()) {
            throw RandomizerPatchException.ApplyFailed("key window extends past end of base ROM")
        }
        val window = ByteArray(len)
        source.seek(min)
        source.readFully(window)
        return window
    }

    private fun copyRegion(
        source: RandomAccessFile,
        output: RandomAccessFile,
        srcOff: Long,
        dstOff: Long,
        length: Long
    ) {
        if (length <= 0) return
        val buffer = ByteArray(BUFFER_SIZE)
        var remaining = length
        var s = srcOff
        var d = dstOff
        while (remaining > 0) {
            val n = minOf(remaining, buffer.size.toLong()).toInt()
            source.seek(s)
            source.readFully(buffer, 0, n)
            output.seek(d)
            output.write(buffer, 0, n)
            s += n
            d += n
            remaining -= n
        }
    }

    /**
     * Find the size of the original file whose DMA start equals [fromFileAddress]
     * by scanning the original DMA table at [header.dmaTableStart] in [source].
     * Each entry is 16 bytes: `(start, end, start, 0)`. Returns the file size
     * (`end - start`), or `-1` if no matching entry (or a terminator) is found.
     */
    private fun lookupOriginalFileSize(
        source: RandomAccessFile,
        header: ZpfHeader,
        fromFileAddress: Int
    ): Int {
        val base = header.dmaTableStart.toLong() and 0xFFFFFFFFL
        val target = fromFileAddress.toLong() and 0xFFFFFFFFL
        for (idx in 0 until MAX_DMA_SCAN) {
            val off = base + idx * DMA_ENTRY_SIZE
            if (off + DMA_ENTRY_SIZE > source.length()) break
            val entryStart = readWordBE(source, off)
            if (entryStart == 0xFFFFFFFFL) break // DMA table terminator
            if (entryStart == target) {
                val entryEnd = readWordBE(source, off + 4)
                return (entryEnd - entryStart).coerceAtLeast(0).toInt()
            }
        }
        return -1
    }

    private fun zeroFill(output: RandomAccessFile, offset: Long, length: Long) {
        if (length <= 0) return
        val buffer = ByteArray(BUFFER_SIZE)
        var remaining = length
        var d = offset
        while (remaining > 0) {
            val n = minOf(remaining, buffer.size.toLong()).toInt()
            output.seek(d)
            output.write(buffer, 0, n)
            d += n
            remaining -= n
        }
    }

    private fun writeWordBE(raf: RandomAccessFile, offset: Long, value: Long) {
        val v = value and 0xFFFFFFFFL
        raf.seek(offset)
        raf.write(((v ushr 24) and 0xFF).toInt())
        raf.write(((v ushr 16) and 0xFF).toInt())
        raf.write(((v ushr 8) and 0xFF).toInt())
        raf.write((v and 0xFF).toInt())
    }

    /** Read a big-endian 32-bit word from [raf] at [offset]. */
    private fun readWordBE(raf: RandomAccessFile, offset: Long): Long {
        raf.seek(offset)
        val b0 = raf.read().let { if (it < 0) 0 else it }
        val b1 = raf.read().let { if (it < 0) 0 else it }
        val b2 = raf.read().let { if (it < 0) 0 else it }
        val b3 = raf.read().let { if (it < 0) 0 else it }
        return ((b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3).toLong() and 0xFFFFFFFFL
    }
}
