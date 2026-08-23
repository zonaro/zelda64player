package br.com.redclaw.zelda64player.randomizer.repository

import br.com.redclaw.zelda64player.repositories.uninstallHackFiles
import org.json.JSONArray
import java.io.File

/**
 * Persists generated OoTRandomizer seeds and their patched ROM files.
 *
 * - The index (list of [RandomizedSeedEntry]) is a JSON array at [indexFile]
 *   (e.g. `filesDir/randomizer/seeds.json`).
 * - Each seed's patched ROM is stored at `romsDir/rom_<id>` — the exact path
 *   [br.com.redclaw.zelda64player.repositories.Storage.rom] returns for the seed
 *   id, so the existing [br.com.redclaw.zelda64player.views.GameActivity] launch
 *   path (which loads `Storage.rom(hackId)`) works unchanged for randomizer
 *   seeds. In production [romsDir] is the app's durable external-files dir.
 *
 * Removing a seed also clears its SRAM/state files via [uninstallHackFiles]
 * (mirroring how store-hack uninstall works), so no orphaned saves remain.
 *
 * Takes explicit directories (no Android [android.content.Context]) so it is
 * unit-testable on the JVM with temporary folders.
 */
class RandomizedSeedRepository(
    private val romsDir: File,
    private val indexFile: File
) {
    init {
        romsDir.mkdirs()
        indexFile.parentFile?.mkdirs()
    }

    /** All persisted seeds, newest not guaranteed (callers may sort). */
    fun list(): List<RandomizedSeedEntry> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(indexFile.readText())
            (0 until arr.length()).mapNotNull { i ->
                runCatching { RandomizedSeedEntry.fromJson(arr.getJSONObject(i)) }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    /** Look up a single seed by id, or null if not present. */
    fun get(id: String): RandomizedSeedEntry? = list().firstOrNull { it.id == id }

    /** Absolute path of the patched ROM for [id] (matches `Storage.rom(id)`). */
    fun getRomFile(id: String): File = File(romsDir, "rom_$id")

    /**
     * Persist [entry] and move [romFile] into the repository as `romsDir/rom_<id>`
     * (the original temp file is deleted after a successful copy). If a ROM
     * already exists for the id it is replaced.
     */
    fun add(entry: RandomizedSeedEntry, romFile: File) {
        val target = File(romsDir, entry.romFileName)
        if (romFile.absolutePath != target.absolutePath) {
            if (target.exists()) target.delete()
            romFile.copyTo(target, overwrite = true)
            if (romFile.exists()) romFile.delete()
        }
        val all = (list() + entry).distinctBy { it.id }
        save(all)
    }

    /**
     * Delete [id] from the index and remove its ROM + SRAM + state files via
     * [uninstallHackFiles]. Returns true when the index entry was removed and the
     * per-hack files were cleared successfully (or were already absent).
     */
    fun remove(id: String): Boolean {
        val all = list().toMutableList()
        val removed = all.removeIf { it.id == id }
        save(all)
        val filesCleared = uninstallHackFiles(romsDir, id)
        return removed && filesCleared
    }

    private fun save(all: List<RandomizedSeedEntry>) {
        val arr = JSONArray()
        all.forEach { arr.put(it.toJson()) }
        indexFile.writeText(arr.toString(2))
    }
}
