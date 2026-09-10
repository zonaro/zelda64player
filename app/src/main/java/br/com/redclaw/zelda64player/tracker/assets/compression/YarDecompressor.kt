/*
 * Zelda 64 Player - native Android N64 emulator frontend for Zelda ROM hacks.
 * Copyright (C) 2026 RedClaw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package br.com.redclaw.zelda64player.tracker.assets.compression

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Decompresses Majora's Mask YAR containers into one contiguous virtual archive. */
object YarDecompressor {

        private const val MAX_ARCHIVE_SIZE = 64_000_000

        /**
         * A YAR starts with a big-endian offset table. The first word is the payload start; the
         * remaining words are cumulative block ends relative to that start. Every payload block is
         * an independent Yaz0 stream whose output is concatenated in table order.
         */
        fun decompress(src: ByteArray): ByteArray {
                require(src.size >= 8) { "YAR: source too small (${src.size})" }
                val buffer = ByteBuffer.wrap(src).order(ByteOrder.BIG_ENDIAN)
                val payloadStart = buffer.getInt(0)
                require(payloadStart in 8..src.size && payloadStart % 4 == 0) {
                        "YAR: invalid payload offset $payloadStart"
                }

                val boundaryCount = payloadStart / 4
                require(boundaryCount >= 2) { "YAR: missing block boundary table" }
                val output = ByteArrayOutputStream()
                var relativeStart = 0

                for (boundaryIndex in 1 until boundaryCount) {
                        val relativeEnd = buffer.getInt(boundaryIndex * 4)
                        require(relativeEnd > relativeStart) {
                                "YAR: non-increasing block boundary $relativeEnd at index $boundaryIndex"
                        }
                        val blockStart = payloadStart.toLong() + relativeStart
                        val blockEnd = payloadStart.toLong() + relativeEnd
                        require(blockEnd <= src.size.toLong()) {
                                "YAR: block $boundaryIndex exceeds source (${blockEnd} > ${src.size})"
                        }

                        val decompressed =
                                Yaz0Decompressor.decompress(
                                        src.copyOfRange(blockStart.toInt(), blockEnd.toInt())
                                )
                        require(output.size().toLong() + decompressed.size <= MAX_ARCHIVE_SIZE) {
                                "YAR: decompressed archive exceeds $MAX_ARCHIVE_SIZE bytes"
                        }
                        output.write(decompressed)
                        relativeStart = relativeEnd
                }

                return output.toByteArray()
        }
}
