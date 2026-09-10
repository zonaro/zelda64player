/*
 * Zelda 64 Player - native Android N64 emulator frontend for Zelda ROM hacks.
 * Copyright (C) 2026 RedClaw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package br.com.redclaw.zelda64player.tracker.assets.dma

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Parses the N64 DMA table from a normalized big-endian `.z64` ROM file.
 *
 * Streaming via [FileChannel] — never loads the full ROM into heap (Regra 9).
 * Each entry is 16 bytes (VROM start/end, ROM start/end, all BE u32).
 * The table is terminated by a 16-byte zero entry.
 */
class DmaTableParser(
    private val romFile: File,
    private val tableOffset: Long,
) {
    /**
     * Parse up to [maxEntries] DMA entries starting at [tableOffset].
     * Stops early on the zero terminator.
     */
    fun parseEntries(maxEntries: Int = 2000): List<DmaEntry> {
        val entries = mutableListOf<DmaEntry>()
        RandomAccessFile(romFile, "r").use { raf ->
            val channel: FileChannel = raf.channel
            val buf = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
            for (i in 0 until maxEntries) {
                buf.clear()
                val read = channel.read(buf, tableOffset + i * 16L)
                if (read < 16) break
                buf.flip()
                val vStart = buf.int
                val vEnd = buf.int
                val rStart = buf.int
                val rEnd = buf.int
                if (vStart == 0 && vEnd == 0 && rStart == 0 && rEnd == 0) break
                entries.add(DmaEntry(i, vStart, vEnd, rStart, rEnd))
            }
        }
        return entries
    }

    /**
     * Read raw bytes for a single [entry] from ROM.
     * Returns the compressed bytes if [DmaEntry.isCompressed], otherwise the raw bytes.
     * Caller is responsible for Yaz0 decompression when needed.
     */
    fun readEntryBytes(entry: DmaEntry): ByteArray {
        val offset = entry.romStart.toLong() and 0xFFFFFFFFL
        val size = entry.compressedSize
        require(size in 1..64_000_000) { "DMA entry ${entry.index}: invalid size $size" }
        val bytes = ByteArray(size)
        RandomAccessFile(romFile, "r").use { raf ->
            raf.seek(offset)
            raf.readFully(bytes)
        }
        return bytes
    }
}
