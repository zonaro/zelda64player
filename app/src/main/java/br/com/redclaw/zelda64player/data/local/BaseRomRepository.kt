package br.com.redclaw.zelda64player.data.local

import br.com.redclaw.zelda64player.data.model.BaseRom
import br.com.redclaw.zelda64player.patcher.n64.ChecksumCalculator
import br.com.redclaw.zelda64player.patcher.n64.RomHeader
import br.com.redclaw.zelda64player.patcher.n64.RomNormalizer
import java.io.File

/**
 * Imports and persists the user's N64 base ROMs (Phase 1 MVP — no settings UI yet;
 * ROMs are dropped manually into the import directory).
 *
 * On every [scanAndRegister] the import directory is scanned for `.z64`/`.n64`/
 * `.v64`/`.rom` files; each is normalized to big-endian `.z64`, identified via
 * its header, checksummed, de-duplicated by CRC32, and the normalized copy is
 * stored in [storageDir]. The registry is persisted as JSON at [registryFile].
 *
 * The repository takes explicit directories (not an Android [android.content.Context])
 * so it is unit-testable on the JVM with temporary folders.
 */
/** Outcome of registering a single normalized ROM file. */
sealed class RegisterResult {
    data class Success(val rom: BaseRom) : RegisterResult()
    data class Duplicate(val existing: BaseRom) : RegisterResult()
    data class Invalid(val reason: String) : RegisterResult()
}

class BaseRomRepository(
    private val importDir: File,
    private val storageDir: File,
    private val registryFile: File,
    private val legacyStorageDirs: List<File> = emptyList()
) {
    private val supportedExtensions = setOf("z64", "n64", "v64", "rom")

    init {
        importDir.mkdirs()
        storageDir.mkdirs()
        migrateLegacyEntries()
    }

    /**
     * One-time relocation of ROM files that were previously stored under a
     * cache directory (which the OS may wipe at any time). Any registry entry
     * pointing inside a legacy directory is moved into [storageDir] and its
     * stored path updated; entries whose files are already gone are left for
     * [scanAndRegister] to prune.
     */
    private fun migrateLegacyEntries() {
        if (legacyStorageDirs.isEmpty() || !registryFile.exists()) return
        var changed = false
        val migrated = loadRegistry().map { rom ->
            val current = File(rom.path)
            val parent = current.parentFile ?: return@map rom
            val isLegacy = legacyStorageDirs.any { it.absolutePath == parent.absolutePath }
            if (!isLegacy) return@map rom
            val dest = File(storageDir, current.name)
            if (!current.exists() || dest.exists()) return@map rom
            try {
                current.inputStream().use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                current.delete()
                changed = true
                rom.copy(path = dest.absolutePath)
            } catch (_: Exception) {
                rom
            }
        }
        if (changed) saveRegistry(migrated)
    }

    /** Scan for new ROMs, normalize and register them, and return the full registry. */
    fun scanAndRegister(): List<BaseRom> {
        val existing = loadRegistry().filter { File(it.path).exists() }
            .associateBy { it.crc32 }.toMutableMap()

        importDir.listFiles { f ->
            f.isFile && f.extension.lowercase() in supportedExtensions
        }?.forEach { file ->
            try {
                val temp = File(storageDir, "__import_${file.nameWithoutExtension}.z64.tmp")
                file.inputStream().use { input ->
                    temp.outputStream().use { output ->
                        RomNormalizer.normalize(input, output)
                    }
                }
                val crc32 = ChecksumCalculator.crc32(temp)
                if (existing.containsKey(crc32)) {
                    temp.delete()
                    return@forEach
                }
                val header = RomHeader.fromNormalizedZ64(temp)
                val md5 = ChecksumCalculator.md5(temp)
                val sha1 = ChecksumCalculator.sha1(temp)

                val finalFile = File(storageDir, "$crc32.z64")
                move(temp, finalFile)

                val displayName = header.title.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension
                existing[crc32] = BaseRom(
                    id = crc32,
                    displayName = displayName,
                    path = finalFile.absolutePath,
                    gameCode = header.gameCode,
                    versionByte = header.versionByte,
                    sizeBytes = finalFile.length(),
                    crc32 = crc32,
                    md5 = md5,
                    sha1 = sha1,
                    sourceName = file.nameWithoutExtension
                )
            } catch (_: Exception) {
                // Skip unreadable or unrecognized files silently.
            }
        }

        val list = existing.values.toList()
        saveRegistry(list)
        return list
    }

    fun getAll(): List<BaseRom> = loadRegistry()

    fun findByCrc32(crc32: String): BaseRom? =
        loadRegistry().firstOrNull { it.crc32.equals(crc32, ignoreCase = true) }

    fun getById(id: String): BaseRom? =
        loadRegistry().firstOrNull { it.id == id }

    /**
     * Register a ROM that has already been normalized to big-endian `.z64`
     * (e.g. imported via the Storage Access Framework). Reuses the same
     * checksum/header/dedupe/registry logic as [scanAndRegister] so both import
     * paths stay consistent. The [normalizedFile] is consumed (moved on success,
     * deleted otherwise) and must not be used by the caller afterwards.
     */
    fun registerNormalizedFile(normalizedFile: File, sourceName: String): RegisterResult {
        return try {
            val crc32 = ChecksumCalculator.crc32(normalizedFile)
            val existing = loadRegistry().firstOrNull { it.crc32.equals(crc32, ignoreCase = true) }
            if (existing != null) {
                return RegisterResult.Duplicate(existing)
            }
            val header = RomHeader.fromNormalizedZ64(normalizedFile)
            val md5 = ChecksumCalculator.md5(normalizedFile)
            val sha1 = ChecksumCalculator.sha1(normalizedFile)

            val finalFile = File(storageDir, "$crc32.z64")
            move(normalizedFile, finalFile)

            val displayName = header.title.takeIf { it.isNotBlank() } ?: sourceName
            val rom = BaseRom(
                id = crc32,
                displayName = displayName,
                path = finalFile.absolutePath,
                gameCode = header.gameCode,
                versionByte = header.versionByte,
                sizeBytes = finalFile.length(),
                crc32 = crc32,
                md5 = md5,
                sha1 = sha1,
                sourceName = sourceName
            )
            val list = (loadRegistry() + rom).distinctBy { it.crc32 }
            saveRegistry(list)
            RegisterResult.Success(rom)
        } catch (e: Exception) {
            RegisterResult.Invalid(e.message ?: "invalid rom")
        } finally {
            if (normalizedFile.exists()) normalizedFile.delete()
        }
    }

    /** Remove a registered base ROM (deletes the file and its registry entry). */
    fun deleteById(id: String): Boolean {
        val all = loadRegistry().toMutableList()
        val found = all.firstOrNull { it.id == id } ?: return false
        runCatching { File(found.path).delete() }
        all.removeIf { it.id == id }
        saveRegistry(all)
        return true
    }

    /** A private, collision-safe temp file inside the storage directory for SAF imports. */
    fun newImportTempFile(sanitizedName: String): File =
        File(storageDir, "__import_${sanitizedName}.z64.tmp")

    private fun loadRegistry(): List<BaseRom> {
        if (!registryFile.exists()) return emptyList()
        return try {
            val arr = org.json.JSONArray(registryFile.readText())
            (0 until arr.length()).mapNotNull { i ->
                runCatching { BaseRom.fromJson(arr.getJSONObject(i)) }.getOrNull()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun saveRegistry(list: List<BaseRom>) {
        val arr = org.json.JSONArray()
        list.forEach { arr.put(it.toJson()) }
        registryFile.writeText(arr.toString(2))
    }

    private fun move(from: File, to: File) {
        if (from.renameTo(to)) return
        from.inputStream().use { input ->
            to.outputStream().use { output -> input.copyTo(output) }
        }
        from.delete()
    }
}
