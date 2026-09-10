/*
 * Zelda 64 Player - native Android N64 emulator frontend for Zelda ROM hacks.
 * Copyright (C) 2026 RedClaw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package br.com.redclaw.zelda64player.tracker.assets.cache

import android.content.Context
import java.io.File
import org.json.JSONObject

/**
 * File cache for tracker icons extracted from the user's base ROM.
 *
 * Layout: `filesDir/tracker_assets/<crc32>/<itemId>.png` + `.meta.json`. Keyed by ROM CRC32 so
 * vanilla and hacks sharing the same base reuse the cache.
 */
class TrackerAssetCache(private val context: Context) {

    companion object {
        const val CACHE_VERSION = 6 // MM now resolves and decompresses its YAR icon archives
    }

    private fun dirFor(crc32: String): File =
            File(context.filesDir, "tracker_assets/$crc32").apply { mkdirs() }

    fun fileFor(itemId: String, crc32: String): File = File(dirFor(crc32), "$itemId.png")

    fun has(itemId: String, crc32: String): Boolean = fileFor(itemId, crc32).exists()

    fun hasValidCache(crc32: String, expectedCount: Int): Boolean {
        val dir = dirFor(crc32)
        val meta = File(dir, ".meta.json")
        if (!meta.exists()) return false
        return try {
            val obj = JSONObject(meta.readText())
            val count = obj.optInt("count", -1)
            val version = obj.optInt("version", 0)
            // Bump version when extraction logic changes to force re-extraction
            if (version != CACHE_VERSION) return false
            val pngCount = dir.listFiles { f -> f.extension == "png" }?.size ?: 0
            count == expectedCount && pngCount == expectedCount
        } catch (_: Exception) {
            false
        }
    }

    fun writeMeta(crc32: String, game: String, count: Int) {
        val dir = dirFor(crc32)
        val meta = File(dir, ".meta.json")
        val obj =
                JSONObject().apply {
                    put("game", game)
                    put("count", count)
                    put("version", CACHE_VERSION)
                    put("extractedAt", System.currentTimeMillis())
                }
        meta.writeText(obj.toString(2))
    }

    fun clear(crc32: String) {
        dirFor(crc32).deleteRecursively()
    }
}
