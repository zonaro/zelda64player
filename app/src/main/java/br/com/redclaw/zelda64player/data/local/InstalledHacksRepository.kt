package br.com.redclaw.zelda64player.data.local

import br.com.redclaw.zelda64player.data.model.Checksums
import br.com.redclaw.zelda64player.store.CanonicalIdResolver
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Tracks which hack patches are installed locally and at what version, so the
 * Library and Store can show install state and detect available updates
 * (catalog version != installed version).
 *
 * Persisted as JSON at [file] (a `filesDir/installed_hacks.json`). Takes an
 * explicit file so it is unit-testable on the JVM with a temp file.
 */
class InstalledHacksRepository(private val file: File) {
    init {
        file.parentFile?.mkdirs()
    }

    fun markInstalled(
        hackId: String,
        version: String,
        fileName: String,
        canonicalId: String = CanonicalIdResolver.resolve(hackId, ""),
        patchChecksums: Checksums? = null
    ) {
        val all = load().toMutableMap()
        all[hackId] = InstalledHack(hackId, version, fileName, canonicalId, patchChecksums)
        save(all)
    }

    fun getInstalled(hackId: String): InstalledHack? = load()[hackId]

    fun isInstalled(hackId: String): Boolean = load().containsKey(hackId)

    /**
     * Remove [hackId] from the installed set (e.g. after the user uninstalls a
     * game). Safe to call when the hack was never recorded.
     */
    fun unmarkInstalled(hackId: String) {
        val all = load().toMutableMap()
        all.remove(hackId)
        save(all)
    }

    fun installedVersion(hackId: String): String? = getInstalled(hackId)?.version

    fun load(): Map<String, InstalledHack> {
        if (!file.exists()) return emptyMap()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { i ->
                runCatching { InstalledHack.fromJson(arr.getJSONObject(i)) }.getOrNull()
            }.associateBy { it.hackId }
        }.getOrDefault(emptyMap())
    }

    private fun save(all: Map<String, InstalledHack>) {
        val arr = JSONArray()
        all.values.forEach { arr.put(it.toJson()) }
        file.writeText(arr.toString(2))
    }

    data class InstalledHack(
        val hackId: String,
        val version: String,
        val fileName: String,
        val canonicalId: String,
        val patchChecksums: Checksums? = null
    ) {
        fun toJson() = JSONObject().apply {
            put("hackId", hackId)
            put("version", version)
            put("fileName", fileName)
            put("canonicalId", canonicalId)
            patchChecksums?.let { put("patchChecksums", it.toJson()) }
                ?: put("patchChecksums", JSONObject.NULL)
        }

        companion object {
            fun fromJson(o: JSONObject): InstalledHack {
                val hackId = o.getString("hackId")
                return InstalledHack(
                    hackId = hackId,
                    version = o.getString("version"),
                    fileName = o.getString("fileName"),
                    canonicalId = if (o.has("canonicalId")) {
                        o.getString("canonicalId")
                    } else {
                        // Legacy record (pre-Phase 6): backfill via slug normalization.
                        CanonicalIdResolver.resolve(hackId, "")
                    },
                    patchChecksums = if (o.has("patchChecksums") && !o.isNull("patchChecksums")) {
                        Checksums.fromJson(o.getJSONObject("patchChecksums"))
                    } else {
                        null
                    }
                )
            }
        }
    }
}
