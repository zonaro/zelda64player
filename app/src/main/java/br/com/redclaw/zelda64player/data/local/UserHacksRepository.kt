package br.com.redclaw.zelda64player.data.local

import br.com.redclaw.zelda64player.data.model.HackEntry
import org.json.JSONArray
import java.io.File

/**
 * Persists the user-imported hacks (patches the user dropped in via the Store's
 * "Import Patch" action) as a JSON array of [HackEntry] at [file].
 *
 * Mirrors the [MergedCatalogRepository] style (explicit [File], JSON array of
 * [HackEntry]) so imported hacks surface in the Library with their proper title
 * (not a prettified id) and can be removed just like catalog hacks.
 *
 * Takes an explicit file so it is unit-testable on the JVM with a temp file.
 */
class UserHacksRepository(private val file: File) {
    init {
        file.parentFile?.mkdirs()
    }

    /** Append (or replace, by id) [entry] and persist the updated list. */
    fun add(entry: HackEntry) {
        val all = getAll().toMutableList()
        val index = all.indexOfFirst { it.id == entry.id }
        if (index >= 0) all[index] = entry else all.add(entry)
        save(all)
    }

    fun getAll(): List<HackEntry> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { i ->
                runCatching { HackEntry.fromJson(arr.getJSONObject(i)) }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    /** All entries keyed by id (last write wins on duplicate ids). */
    fun asMap(): Map<String, HackEntry> = getAll().associateBy { it.id }

    fun getById(id: String): HackEntry? = getAll().firstOrNull { it.id == id }

    /** Remove [id] from the persisted set. Safe to call when absent. */
    fun remove(id: String) {
        val all = getAll().toMutableList()
        if (all.removeIf { it.id == id }) save(all)
    }

    private fun save(all: List<HackEntry>) {
        val arr = JSONArray()
        all.forEach { arr.put(it.toJson()) }
        file.writeText(arr.toString(2))
    }
}
