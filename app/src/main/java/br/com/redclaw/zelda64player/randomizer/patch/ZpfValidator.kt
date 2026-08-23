package br.com.redclaw.zelda64player.randomizer.patch

import br.com.redclaw.zelda64player.randomizer.patch.ZpfModels.DmaUpdate
import br.com.redclaw.zelda64player.randomizer.patch.ZpfModels.EditBlock
import br.com.redclaw.zelda64player.randomizer.patch.ZpfModels.ParsedZpf

/**
 * Structural sanity checks performed on a [ParsedZpf] before it is applied to a
 * base ROM. These catch malformed patches early (before any ROM bytes are
 * touched) and guard against out-of-bounds writes.
 *
 * Note on `dmaTableStart`: per the verified `N64Patch.py` semantics this field
 * is a **ROM offset** (the patched ROM's DMA table base), not an offset into
 * the patch blob. Consequently the bounds checks below are performed against the
 * base ROM size, not the blob size.
 *
 * Licensing: clean-room implementation from the documented format.
 */
object ZpfValidator {

    private const val DMA_ENTRY_SIZE = 0x10
    private const val WORD_SIZE = 4

    /**
     * Validate [parsed] against the base ROM of size [romSizeBytes].
     * @throws RandomizerPatchException.InvalidStructure if any check fails.
     */
    fun validate(parsed: ParsedZpf, romSizeBytes: Long) {
        val h = parsed.header
        val romSize = romSizeBytes and 0xFFFFFFFFL

        if (h.keyRangeMin > h.keyRangeMax) {
            throw RandomizerPatchException.InvalidStructure(
                "key range min (${h.keyRangeMin}) > max (${h.keyRangeMax})"
            )
        }
        if (h.keyRangeMin < 0 || h.keyRangeMax >= romSize) {
            throw RandomizerPatchException.InvalidStructure(
                "key window [${h.keyRangeMin}, ${h.keyRangeMax}] outside ROM bounds (size $romSize)"
            )
        }
        if (h.keyAddress < h.keyRangeMin || h.keyAddress > h.keyRangeMax) {
            throw RandomizerPatchException.InvalidStructure(
                "key address ${h.keyAddress} outside key window [${h.keyRangeMin}, ${h.keyRangeMax}]"
            )
        }
        if (h.dmaTableStart < 0 || h.dmaTableStart >= romSize) {
            throw RandomizerPatchException.InvalidStructure(
                "dmaTableStart ${h.dmaTableStart} outside ROM bounds (size $romSize)"
            )
        }

        var maxDmaIndex = -1
        for (dma in parsed.dmaUpdates) {
            validateDma(dma, romSize)
            if (dma.index > maxDmaIndex) maxDmaIndex = dma.index
        }
        if (parsed.dmaUpdates.isNotEmpty()) {
            val lastEntryEnd = h.dmaTableStart.toLong() and 0xFFFFFFFFL +
                (maxDmaIndex.toLong() * DMA_ENTRY_SIZE) + DMA_ENTRY_SIZE
            if (lastEntryEnd > romSize) {
                throw RandomizerPatchException.InvalidStructure(
                    "DMA table write would exceed ROM bounds (end $lastEntryEnd > size $romSize)"
                )
            }
        }

        if (parsed.editBlocks.isNotEmpty() && parsed.editBlocks.first().isContinue) {
            throw RandomizerPatchException.InvalidStructure(
                "first XOR edit block must be a start block, not a continuation"
            )
        }
        for (block in parsed.editBlocks) {
            validateBlock(block, romSize)
        }
    }

    private fun validateDma(dma: DmaUpdate, romSize: Long) {
        if (dma.index < 0) {
            throw RandomizerPatchException.InvalidStructure("negative DMA index ${dma.index}")
        }
        val start = dma.newStart.toLong() and 0xFFFFFFFFL
        val size = dma.size.toLong() and 0xFFFFFFFFL
        if (size < 0) {
            throw RandomizerPatchException.InvalidStructure("negative DMA size ${dma.size}")
        }
        if (start + size > romSize) {
            throw RandomizerPatchException.InvalidStructure(
                "DMA file [${dma.newStart}, ${dma.newStart + dma.size}) exceeds ROM bounds"
            )
        }
        if (dma.fromFileAddress != 0xFFFFFFFF.toInt()) {
            val src = dma.fromFileAddress.toLong() and 0xFFFFFFFFL
            if (src < 0 || src >= romSize) {
                throw RandomizerPatchException.InvalidStructure(
                    "DMA source address ${dma.fromFileAddress} outside ROM bounds"
                )
            }
        }
    }

    private fun validateBlock(block: EditBlock, romSize: Long) {
        val addr = block.address.toLong() and 0xFFFFFFFFL
        val len = block.data.size.toLong()
        if (addr < 0 || addr + len > romSize) {
            throw RandomizerPatchException.InvalidStructure(
                "XOR edit block [${block.address}, ${block.address + block.data.size}) exceeds ROM bounds"
            )
        }
        if (block.keySkip < 0) {
            throw RandomizerPatchException.InvalidStructure("negative keySkip ${block.keySkip}")
        }
    }
}
