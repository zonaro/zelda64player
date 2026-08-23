package br.com.redclaw.zelda64player.data.local

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

    fun markInstalled(hackId: String, version: String, fileName: String) {
        val all = load().toMutableMap()
        all[hackId] = InstalledHack(hackId, version, fileName)
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
        val fileName: String
    ) {
        fun toJson() = JSONObject().apply {
            put("hackId", hackId)
            put("version", version)
            put("fileName", fileName)
        }

        companion object {
            fun fromJson(o: JSONObject) = InstalledHack(
                hackId = o.getString("hackId"),
                version = o.getString("version"),
                fileName = o.getString("fileName")
            )
        }
    }
}
