package br.com.redclaw.zelda64player.randomizer.patch

import br.com.redclaw.zelda64player.randomizer.patch.ZpfModels.DmaUpdate
import br.com.redclaw.zelda64player.randomizer.patch.ZpfModels.EditBlock
import br.com.redclaw.zelda64player.randomizer.patch.ZpfModels.ParsedZpf
import br.com.redclaw.zelda64player.randomizer.patch.ZpfModels.ZpfHeader

/**
 * Parses a single `.zpf` blob (already inflated) into a [ParsedZpf] model.
 *
 * Layout (all multi-byte integers big-endian), verified against OoTRandomizer
 * `N64Patch.py`:
 * ```
 * [4] magic 'ZPFv'
 * [1] version byte ('1')
 * [4] dmaTableStart        (ROM offset of the DMA table base)
 * [4] keyRangeMin          (XOR key window lower bound, ROM offset)
 * [4] keyRangeMax          (XOR key window upper bound, ROM offset)
 * [4] keyAddress           (initial XOR key cursor, ROM offset)
 * --- DMA updates (immediately after the header, NOT at dmaTableStart) ---
 *   repeated: [2] dmaIndex  (0xFFFF terminates)
 *             [4] fromFileAddress
 *             [4] newStart
 *             [3] size
 * --- XOR edit blocks (to EOF) ---
 *   start block:  [4] address, [2] size, [size] patchBytes
 *   continue block: [1] 0xFF, [1] keySkip, [2] size, [size] patchBytes
 * ```
 *
 * Licensing: clean-room implementation from the documented format.
 */
object ZpfParser {

    private const val MAGIC = 0x5A504676 // 'Z' 'P' 'F' 'v' as a big-endian u32
    private const val SUPPORTED_VERSION = '1'.code // 0x31
    private const val DMA_TERMINATOR: Int = 0xFFFF
    private const val CONTINUE_MARKER: Int = 0xFF

    /**
     * Parse [blob] into a [ParsedZpf].
     * @throws RandomizerPatchException.BadMagic if the magic is wrong.
     * @throws RandomizerPatchException.UnsupportedVersion for unknown versions.
     * @throws RandomizerPatchException.TruncatedPatch if the stream ends early.
     */
    fun parse(blob: ByteArray): ParsedZpf {
        val r = ByteReader(blob)
        val magic = r.readInt32()
        if (magic != MAGIC) {
            val asAscii = blob.copyOfRange(0, minOf(4, blob.size))
                .toString(Charsets.US_ASCII).replace(Regex("[^\\x20-\\x7E]"), ".")
            throw RandomizerPatchException.BadMagic(asAscii)
        }
        val version = r.readByte()
        if (version != SUPPORTED_VERSION) {
            throw RandomizerPatchException.UnsupportedVersion(version)
        }

        val header = ZpfHeader(
            dmaTableStart = r.readInt32(),
            keyRangeMin = r.readInt32(),
            keyRangeMax = r.readInt32(),
            keyAddress = r.readInt32()
        )

        val dmaUpdates = mutableListOf<DmaUpdate>()
        while (true) {
            val index = r.readInt16()
            if (index == DMA_TERMINATOR) break
            val fromFileAddress = r.readInt32()
            val newStart = r.readInt32()
            val size = r.readInt24()
            dmaUpdates.add(DmaUpdate(index, fromFileAddress, newStart, size))
        }

        val editBlocks = mutableListOf<EditBlock>()
        // Running absolute address for continuation blocks: each block's data
        // begins where the previous block ended.
        var currentAddress = 0
        while (r.hasMore()) {
            val marker = r.readByte()
            if (marker != CONTINUE_MARKER) {
                // Start block: the marker byte is the high byte of the address.
                r.rewind(1)
                val address = r.readInt32()
                val size = r.readInt16()
                val data = r.readBytes(size)
                editBlocks.add(EditBlock(address, data, isContinue = false, keySkip = 0))
                currentAddress = address + size
            } else {
                // Continue block: skip keys, then read size + data. Its address
                // is the running end of the previous block.
                val keySkip = r.readByte()
                val size = r.readInt16()
                val data = r.readBytes(size)
                editBlocks.add(EditBlock(currentAddress, data, isContinue = true, keySkip = keySkip))
                currentAddress += size
            }
        }

        return ParsedZpf(header, dmaUpdates, editBlocks)
    }

    /** Minimal big-endian cursor over an in-memory `.zpf` blob. */
    private class ByteReader(private val data: ByteArray, private var pos: Int = 0) {
        fun hasMore(): Boolean = pos < data.size

        fun rewind(n: Int) {
            pos -= n
            if (pos < 0) throw RandomizerPatchException.TruncatedPatch("reader underflow")
        }

        fun readByte(): Int {
            if (pos >= data.size) {
                throw RandomizerPatchException.TruncatedPatch("expected 1 byte at end of patch")
            }
            return data[pos++].toInt() and 0xFF
        }

        fun readInt16(): Int {
            val hi = readByte()
            val lo = readByte()
            return (hi shl 8) or lo
        }

        fun readInt24(): Int {
            val b0 = readByte()
            val b1 = readByte()
            val b2 = readByte()
            return (b0 shl 16) or (b1 shl 8) or b2
        }

        fun readInt32(): Int {
            val b0 = readByte()
            val b1 = readByte()
            val b2 = readByte()
            val b3 = readByte()
            return (b0 shl 24) or (b1 shl 16) or (b2 shl 8) or b3
        }

        fun readBytes(n: Int): ByteArray {
            if (n < 0) throw RandomizerPatchException.TruncatedPatch("negative length $n")
            if (pos + n > data.size) {
                throw RandomizerPatchException.TruncatedPatch("expected $n bytes, only ${data.size - pos} remaining")
            }
            val out = data.copyOfRange(pos, pos + n)
            pos += n
            return out
        }
    }
}
