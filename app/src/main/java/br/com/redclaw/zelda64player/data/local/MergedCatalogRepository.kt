package br.com.redclaw.zelda64player.data.local

import br.com.redclaw.zelda64player.data.model.HackEntry
import org.json.JSONArray
import java.io.File

/**
 * Persists the last successfully-fetched merged catalog so the Library can show
 * hack names offline and the Store can render instantly before a network
 * refresh. Stored as a JSON array of [HackEntry] at [file].
 *
 * Takes an explicit file so it is unit-testable on the JVM with a temp file.
 */
class MergedCatalogRepository(private val file: File) {
    init {
        file.parentFile?.mkdirs()
    }

    fun save(hacks: List<HackEntry>) {
        val arr = JSONArray()
        hacks.forEach { arr.put(it.toJson()) }
        file.writeText(arr.toString(2))
    }

    fun load(): List<HackEntry> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { i ->
                runCatching { HackEntry.fromJson(arr.getJSONObject(i)) }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    fun asMap(): Map<String, HackEntry> = load().associateBy { it.id }

    /** All persisted hacks belonging to a given store id (e.g. "hylianmodding"). */
    fun getByStore(storeId: String): List<HackEntry> =
        load().filter { it.storeId == storeId }
}
