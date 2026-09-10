/*
 * Zelda 64 Player - native Android N64 emulator frontend for Zelda ROM hacks.
 * Copyright (C) 2026 RedClaw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package br.com.redclaw.zelda64player.tracker.assets

import android.content.Context
import br.com.redclaw.zelda64player.patcher.n64.ChecksumCalculator
import br.com.redclaw.zelda64player.patcher.n64.RomHeader
import br.com.redclaw.zelda64player.tracker.assets.cache.TrackerAssetCache
import br.com.redclaw.zelda64player.tracker.assets.compression.YarDecompressor
import br.com.redclaw.zelda64player.tracker.assets.compression.Yaz0Decompressor
import br.com.redclaw.zelda64player.tracker.assets.dma.DmaTableParser
import br.com.redclaw.zelda64player.tracker.assets.graphics.TextureDecoder
import br.com.redclaw.zelda64player.tracker.assets.mapping.DmaTableOffsets
import br.com.redclaw.zelda64player.tracker.assets.mapping.IconArchiveFormat
import br.com.redclaw.zelda64player.tracker.assets.mapping.IconMapping
import br.com.redclaw.zelda64player.tracker.assets.mapping.MmIconMap
import br.com.redclaw.zelda64player.tracker.assets.mapping.OotIconMap
import br.com.redclaw.zelda64player.tracker.model.TrackerGame
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ExtractReport(
        val cached: Boolean = false,
        val extracted: Int = 0,
        val failed: Int = 0,
        val errors: List<String> = emptyList(),
)

/**
 * Extracts tracker icons from a normalized `.z64` base ROM and caches them as PNGs.
 *
 * Runs on [Dispatchers.IO], streaming via [DmaTableParser] — never loads the full ROM.
 */
class RomAssetExtractor(
        private val context: Context,
        private val cache: TrackerAssetCache = TrackerAssetCache(context),
) {

        companion object {
                fun mappingCount(game: TrackerGame): Int = mappingsFor(game).size

                private fun mappingsFor(game: TrackerGame): List<IconMapping> =
                        when (game) {
                                TrackerGame.OOT -> OotIconMap.entries
                                TrackerGame.MM -> MmIconMap.entries
                        }
        }

        suspend fun extractAll(baseRomFile: File, game: TrackerGame): Result<ExtractReport> =
                withContext(Dispatchers.IO) {
                        runCatching {
                                require(baseRomFile.exists()) {
                                        "Base ROM not found: ${baseRomFile.absolutePath}"
                                }

                                val crc32 = ChecksumCalculator.crc32(baseRomFile)
                                val mappings = mappingsFor(game)

                                if (cache.hasValidCache(crc32, mappings.size)) {
                                        return@runCatching ExtractReport(
                                                cached = true,
                                                extracted = mappings.size
                                        )
                                }
                                cache.clear(crc32)

                                val header = RomHeader.fromNormalizedZ64(baseRomFile)
                                android.util.Log.d(
                                        "TrackerAssets",
                                        "ROM header: gameCode=${header.gameCode} version=${header.versionByte} title=${header.title}"
                                )
                                val dmaOffset =
                                        DmaTableOffsets.forRom(header.gameCode, header.versionByte)
                                                ?: return@runCatching ExtractReport(
                                                        extracted = 0,
                                                        failed = mappings.size,
                                                        errors =
                                                                listOf(
                                                                        "ROM ${header.gameCode} v${header.versionByte} not supported for extraction"
                                                                ),
                                                )
                                android.util.Log.d(
                                        "TrackerAssets",
                                        "DMA offset: 0x${dmaOffset.toString(16)} for ${header.gameCode}"
                                )

                                val parser = DmaTableParser(baseRomFile, dmaOffset)
                                val entries = parser.parseEntries()

                                // Cache decompressed archives by DMA index to avoid re-reading
                                val archiveCache =
                                        mutableMapOf<Pair<Int, IconArchiveFormat>, ByteArray>()
                                fun getArchive(mapping: IconMapping): ByteArray =
                                        archiveCache.getOrPut(
                                                mapping.dmaFileIndex to mapping.archiveFormat
                                        ) {
                                                val entry =
                                                        entries.getOrNull(mapping.dmaFileIndex)
                                                                ?: error(
                                                                        "DMA entry ${mapping.dmaFileIndex} not found (table size ${entries.size})"
                                                                )
                                                require(entry.exists) {
                                                        "DMA entry ${mapping.dmaFileIndex} does not exist"
                                                }
                                                val raw = parser.readEntryBytes(entry)
                                                val dmaBytes =
                                                        if (entry.isCompressed) {
                                                                Yaz0Decompressor.decompress(raw)
                                                        } else {
                                                                raw
                                                        }
                                                when (mapping.archiveFormat) {
                                                        IconArchiveFormat.RAW -> dmaBytes
                                                        IconArchiveFormat.YAR ->
                                                                YarDecompressor.decompress(dmaBytes)
                                                }
                                        }

                                var extracted = 0
                                var failed = 0
                                val errors = mutableListOf<String>()

                                for (mapping in mappings) {
                                        try {
                                                val archiveBytes = getArchive(mapping)
                                                val pixels =
                                                        when (mapping.format) {
                                                                br.com.redclaw.zelda64player.tracker
                                                                        .assets.graphics
                                                                        .N64TextureFormat.RGBA32 ->
                                                                        TextureDecoder.decodeRGBA32(
                                                                                archiveBytes,
                                                                                mapping.offset,
                                                                                mapping.width,
                                                                                mapping.height
                                                                        )
                                                                br.com.redclaw.zelda64player.tracker
                                                                        .assets.graphics
                                                                        .N64TextureFormat.CI8 -> {
                                                                        val tlutOff =
                                                                                mapping.tlutOffset
                                                                                        ?: error(
                                                                                                "CI8 mapping ${mapping.itemId} missing tlutOffset"
                                                                                        )
                                                                        TextureDecoder.decodeCI8(
                                                                                archiveBytes,
                                                                                mapping.offset,
                                                                                mapping.width,
                                                                                mapping.height,
                                                                                archiveBytes,
                                                                                tlutOff
                                                                        )
                                                                }
                                                                else ->
                                                                        error(
                                                                                "Unsupported format ${mapping.format} for ${mapping.itemId}"
                                                                        )
                                                        }
                                                val outFile = cache.fileFor(mapping.itemId, crc32)
                                                TextureDecoder.saveAsPng(
                                                        pixels,
                                                        mapping.width,
                                                        mapping.height,
                                                        outFile
                                                )
                                                extracted++
                                        } catch (e: Exception) {
                                                failed++
                                                errors.add("${mapping.itemId}: ${e.message}")
                                        }
                                }

                                if (extracted > 0) {
                                        cache.writeMeta(crc32, game.name, mappings.size)
                                }

                                ExtractReport(
                                        extracted = extracted,
                                        failed = failed,
                                        errors = errors
                                )
                        }
                }
}
