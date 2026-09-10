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

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Yaz0 (LZSS variant) decompressor used by N64 Zelda ROMs.
 *
 * Header (16 bytes): magic "Yaz0" + uncompressedSize (BE u32) + 8 reserved bytes.
 * Body: code byte (8 flags) interleaved with literals and back-references.
 *
 * Pure Kotlin, no Android dependency — unit-testable on JVM.
 * All bounds are validated (Regra 16) to avoid OOB on corrupted data.
 */
object Yaz0Decompressor {

    fun decompress(src: ByteArray): ByteArray {
        require(src.size >= 16) { "Yaz0: src too small (${src.size})" }
        val magic = String(src, 0, 4, Charsets.US_ASCII)
        require(magic == "Yaz0") { "Invalid Yaz0 magic: $magic" }

        val uncompressedSize = ByteBuffer.wrap(src, 4, 4).order(ByteOrder.BIG_ENDIAN).int
        require(uncompressedSize in 1..32_000_000) { "Yaz0: invalid uncompressed size $uncompressedSize" }

        val dst = ByteArray(uncompressedSize)
        var srcPos = 16
        var dstPos = 0
        var validBitCount = 0
        var currCodeByte = 0

        while (dstPos < uncompressedSize) {
            if (validBitCount == 0) {
                require(srcPos < src.size) { "Yaz0: truncated code byte at srcPos=$srcPos" }
                currCodeByte = src[srcPos++].toInt() and 0xFF
                validBitCount = 8
            }
            if ((currCodeByte and 0x80) != 0) {
                require(srcPos < src.size) { "Yaz0: truncated literal at srcPos=$srcPos" }
                require(dstPos < dst.size) { "Yaz0: dst overflow on literal" }
                dst[dstPos++] = src[srcPos++]
            } else {
                require(srcPos + 1 < src.size) { "Yaz0: truncated copy header at srcPos=$srcPos" }
                val byte1 = src[srcPos++].toInt() and 0xFF
                val byte2 = src[srcPos++].toInt() and 0xFF
                val dist = ((byte1 and 0x0F) shl 8) or byte2
                var copyLen = byte1 ushr 4
                if (copyLen == 0) {
                    require(srcPos < src.size) { "Yaz0: truncated extended length at srcPos=$srcPos" }
                    copyLen = (src[srcPos++].toInt() and 0xFF) + 0x12
                } else {
                    copyLen += 2
                }
                val copyFrom = dstPos - dist - 1
                require(copyFrom >= 0) { "Yaz0: invalid copy distance dist=$dist dstPos=$dstPos" }
                require(dstPos + copyLen <= dst.size) { "Yaz0: copy would overflow dst (len=$copyLen dstPos=$dstPos size=${dst.size})" }
                require(copyFrom + copyLen <= dstPos || copyFrom >= 0) { "Yaz0: invalid copy range" }
                for (i in 0 until copyLen) {
                    dst[dstPos++] = dst[copyFrom + i]
                }
            }
            currCodeByte = currCodeByte shl 1
            validBitCount--
        }
        return dst
    }
}
