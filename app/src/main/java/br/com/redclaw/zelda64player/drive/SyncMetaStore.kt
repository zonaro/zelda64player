/*
 * Zelda 64 Player - native Android N64 emulator frontend for Zelda ROM hacks.
 * Copyright (C) 2026 RedClaw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package br.com.redclaw.zelda64player.drive

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Minimal key/value persistence abstraction so the sync metadata store can be
 * unit-tested on the JVM with an in-memory backend instead of Android
 * [android.content.SharedPreferences].
 */
interface KeyValueStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
}

/** [KeyValueStore] backed by an Android [android.content.SharedPreferences]. */
class SharedPreferencesKeyValueStore(
    context: Context,
    name: String
) : KeyValueStore {
    private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}

/**
 * Persists the local side of the cloud-sync state:
 * - the **dirty set**: file names that changed locally and still need a sync;
 * - the **meta map**: last-synced [SyncMeta] per file (including the Drive file
 *   id and modified time) so the worker can detect conflicts without re-querying
 *   Drive on every run.
 *
 * All data is stored as JSON under a single SharedPreferences file. The store is
 * idempotent: marking the same file dirty twice is a no-op, and clearing it after
 * a successful sync prevents redundant uploads.
 */
class SyncMetaStore(private val kv: KeyValueStore) {

    constructor(context: Context) : this(SharedPreferencesKeyValueStore(context, PREFS_NAME))

    /** Mark [fileName] as needing a sync. */
    fun markDirty(fileName: String) {
        val set = getDirty().toMutableSet().apply { add(fileName) }
        kv.putString(KEY_DIRTY, JSONArray(set.toList()).toString())
    }

    /** Clear [fileName] from the dirty set (called after a successful sync). */
    fun clearDirty(fileName: String) {
        val set = getDirty().toMutableSet().apply { remove(fileName) }
        kv.putString(KEY_DIRTY, JSONArray(set.toList()).toString())
    }

    /** All file names currently waiting for a sync. */
    fun getDirty(): Set<String> {
        val raw = kv.getString(KEY_DIRTY) ?: return emptySet()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length())
                .mapNotNull { arr.optString(it).takeIf { s -> s.isNotEmpty() } }
                .toSet()
        }.getOrDefault(emptySet())
    }

    /** Persist (or replace) the last-synced metadata for a file. */
    fun putMeta(meta: SyncMeta) {
        val all = loadMetaMap().toMutableMap()
        all[meta.filePath] = meta
        saveMetaMap(all)
    }

    /** Read the last-synced metadata for [fileName], or null. */
    fun getMeta(fileName: String): SyncMeta? = loadMetaMap()[fileName]

    /** Drop the metadata for [fileName] (e.g. when the local file is deleted). */
    fun removeMeta(fileName: String) {
        val all = loadMetaMap().toMutableMap()
        all.remove(fileName)
        saveMetaMap(all)
    }

    /** Reset all sync state (dirty set + metadata). */
    fun clearAll() {
        kv.putString(KEY_DIRTY, JSONArray().toString())
        kv.putString(KEY_META, JSONObject().toString())
    }

    private fun loadMetaMap(): Map<String, SyncMeta> {
        val raw = kv.getString(KEY_META) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            buildMap {
                obj.keys().forEach { key ->
                    obj.optJSONObject(key)?.let { put(key, SyncMeta.fromJson(it)) }
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun saveMetaMap(map: Map<String, SyncMeta>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, v.toJson()) }
        kv.putString(KEY_META, obj.toString())
    }

    companion object {
        private const val PREFS_NAME = "cloud_sync_meta"
        private const val KEY_DIRTY = "dirty_files"
        private const val KEY_META = "sync_meta"
    }
}
