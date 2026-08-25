package br.com.redclaw.zelda64player.data.local

import br.com.redclaw.zelda64player.data.model.HackEntry
import org.json.JSONArray
import java.io.File

/**
 * Persists user-imported patch hacks (BPS/IPS imported from the Store screen)
 * as [HackEntry] objects in `filesDir/user_hacks.json`. Kept separate from the
 * remote merged catalog so a catalog refresh never drops a user's own hacks.
 *
 * Takes an explicit file so it is unit-testable on the JVM with a temp file.
 */
class UserHacksRepository(private val file: File) {
    init {
        file.parentFile?.mkdirs()
    }

    /** Add or replace a user hack (keyed by [HackEntry.id]). */
    fun add(entry: HackEntry) {
        val all = load().toMutableList()
        all.removeIf { it.id == entry.id }
        all.add(entry)
        save(all)
    }

    fun getAll(): List<HackEntry> = load()

    fun asMap(): Map<String, HackEntry> = load().associateBy { it.id }

    fun getById(id: String): HackEntry? = load().firstOrNull { it.id == id }

    /** Persist a local cover URI for a manually imported hack. Returns false when unknown. */
    fun updateCover(id: String, coverUri: String): Boolean {
        val all = load().toMutableList()
        val index = all.indexOfFirst { it.id == id }
        if (index < 0) return false
        all[index] = all[index].copy(coverImageUrl = coverUri)
        save(all)
        return true
    }

    /** Remove a user hack by id (e.g. on Library uninstall). */
    fun remove(id: String) {
        val all = load().toMutableList()
        all.removeIf { it.id == id }
        save(all)
    }

    private fun load(): List<HackEntry> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { i ->
                runCatching { HackEntry.fromJson(arr.getJSONObject(i)) }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    private fun save(all: List<HackEntry>) {
        val arr = JSONArray()
        all.forEach { arr.put(it.toJson()) }
        file.writeText(arr.toString(2))
    }
}
