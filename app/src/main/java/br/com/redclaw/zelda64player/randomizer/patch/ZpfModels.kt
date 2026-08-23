package br.com.redclaw.zelda64player.randomizer.patch

/**
 * Immutable model classes produced by [ZpfParser] from a single `.zpf` blob.
 *
 * All multi-byte integer fields are big-endian as stored in the patch. Address
 * and size values are interpreted as unsigned 32-bit quantities; they are kept
 * as signed [Int] here (bit patterns preserved) and widened to [Long] with
 * `and 0xFFFFFFFFL` at the point of use (file offsets / DMA arithmetic).
 */
object ZpfModels {

    /**
     * Header of a `.zpf` blob.
     *
     * @property dmaTableStart ROM offset where new DMA table entries are written
     *   (the patched ROM's DMA table base). This is a ROM offset, NOT an offset
     *   into the patch blob — the DMA-update section itself begins immediately
     *   after this 21-byte header (verified against OoTRandomizer `N64Patch.py`).
     * @property keyRangeMin Inclusive lower bound of the XOR key source window
     *   in the base ROM.
     * @property keyRangeMax Inclusive upper bound of the XOR key source window.
     * @property keyAddress Initial XOR key cursor position within
     *   `[keyRangeMin, keyRangeMax]`; the first key byte is read from
     *   `keyAddress + 1` (with wraparound and zero-skip).
     */
    data class ZpfHeader(
        val dmaTableStart: Int,
        val keyRangeMin: Int,
        val keyRangeMax: Int,
        val keyAddress: Int
    )

    /**
     * A single DMA table relocation entry.
     *
     * @property index DMA table slot index (entry is written at
     *   `dmaTableStart + index * 0x10`).
     * @property fromFileAddress Base-ROM offset of the source file to copy from,
     *   or `0xFFFFFFFF` to zero-fill the destination instead.
     * @property newStart Destination ROM offset where the file's data is placed.
     * @property size Number of bytes the file occupies (end = newStart + size).
     */
    data class DmaUpdate(
        val index: Int,
        val fromFileAddress: Int,
        val newStart: Int,
        val size: Int
    )

    /**
     * A contiguous run of XOR-encoded byte edits.
     *
     * @property address Absolute ROM offset where this block's data begins. For
     *   a continue block this is the running address (previous block end),
     *   resolved during parsing.
     * @property data Raw patch bytes as stored in the patch. A `0x00` byte means
     *   "write 0x00"; any other byte is XORed with the next non-zero key byte.
     * @property isContinue `true` if this block is a continuation (prefixed by a
     *   `0xFF` marker and a key-skip count) rather than a fresh start block.
     * @property keySkip Number of XOR key bytes to advance before applying this
     *   continuation block's data.
     */
    data class EditBlock(
        val address: Int,
        val data: ByteArray,
        val isContinue: Boolean,
        val keySkip: Int
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as EditBlock
            return address == other.address &&
                isContinue == other.isContinue &&
                keySkip == other.keySkip &&
                data.contentEquals(other.data)
        }

        override fun hashCode(): Int {
            var result = address
            result = 31 * result + isContinue.hashCode()
            result = 31 * result + keySkip
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    /**
     * Fully parsed `.zpf` blob: header, DMA relocations and XOR edit blocks,
     * in the order they appear in the patch.
     */
    data class ParsedZpf(
        val header: ZpfHeader,
        val dmaUpdates: List<DmaUpdate>,
        val editBlocks: List<EditBlock>
    )
}
