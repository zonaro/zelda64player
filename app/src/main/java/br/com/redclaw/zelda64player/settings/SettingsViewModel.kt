package br.com.redclaw.zelda64player.settings

import android.app.Application
import android.content.ContentResolver
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import br.com.redclaw.zelda64player.data.local.BaseRomRepository
import br.com.redclaw.zelda64player.data.local.RegisterResult
import br.com.redclaw.zelda64player.data.model.BaseRom
import br.com.redclaw.zelda64player.patcher.n64.RomNormalizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Drives the Settings screen: importing base ROMs via the Storage Access
 * Framework, listing and deleting imported ROMs, and managing custom catalog
 * URLs.
 *
 * Repositories and preferences are constructed from the application context
 * (matching the manual service-locator style used elsewhere in the project).
 * The base-ROM directories and registry file are identical to those used by
 * GameActivityViewModel so both share one registry.
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext

    private val external = appContext.getExternalFilesDir(null) ?: appContext.filesDir
    private val cache = appContext.externalCacheDir ?: appContext.cacheDir

    private val baseRomRepository = BaseRomRepository(
        importDir = File(external, "base_roms"),
        storageDir = File(cache, "base_roms"),
        registryFile = File(appContext.filesDir, "base_roms.json")
    )

    private val launchPrefs: SharedPreferences =
        appContext.getSharedPreferences("zelda64_launch", Context.MODE_PRIVATE)

    private val catalogUrlStore = CatalogUrlStore(
        SharedPreferencesStore(
            appContext.getSharedPreferences(CatalogUrlStore.PREFS_NAME, Context.MODE_PRIVATE)
        ),
        CatalogUrlStore.KEY
    )

    // ---- Section A: import ----

    private val _importState = MutableLiveData<ImportUiState>(ImportUiState.Idle)
    val importState: LiveData<ImportUiState> = _importState

    sealed class ImportUiState {
        object Idle : ImportUiState()
        object Importing : ImportUiState()
        data class Batch(val result: ImportBatchResult) : ImportUiState()
    }

    data class ImportBatchResult(
        val successes: List<BaseRom>,
        val duplicates: List<BaseRom>,
        val invalids: List<String>
    )

    /** Import one or more ROMs selected through the SAF picker. */
    fun importRomsFromUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _importState.value = ImportUiState.Importing
        viewModelScope.launch {
            val batch = withContext(Dispatchers.IO) { buildBatch(uris) }
            _importState.postValue(ImportUiState.Batch(batch))
        }
    }

    private fun buildBatch(uris: List<Uri>): ImportBatchResult {
        val successes = mutableListOf<BaseRom>()
        val duplicates = mutableListOf<BaseRom>()
        val invalids = mutableListOf<String>()
        for (uri in uris) {
            when (val r = runCatching { doImport(uri) }.getOrElse { e ->
                RegisterResult.Invalid(e.message ?: "import failed")
            }) {
                is RegisterResult.Success -> successes.add(r.rom)
                is RegisterResult.Duplicate -> duplicates.add(r.existing)
                is RegisterResult.Invalid -> invalids.add(r.reason)
            }
        }
        return ImportBatchResult(successes, duplicates, invalids)
    }

    private fun doImport(uri: Uri): RegisterResult {
        val resolver = appContext.contentResolver
        val sourceName = queryDisplayName(resolver, uri) ?: "imported_rom"
        val sanitized = FileNameSanitizer.sanitize(sourceName)
        val tempFile = baseRomRepository.newImportTempFile(sanitized)

        // Copy raw bytes from the URI to a temp file first.
        try {
            resolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return RegisterResult.Invalid("unreadable")
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            return RegisterResult.Invalid(e.message ?: "unreadable")
        }

        // If the file is a ZIP archive, extract the first N64 ROM inside it.
        if (isZipFile(tempFile)) {
            val extracted = extractRomFromZip(tempFile)
            tempFile.delete()
            if (extracted == null) {
                return RegisterResult.Invalid("zip contains no ROM file")
            }
            // Move extracted file into the tempFile path for downstream processing.
            extracted.renameTo(tempFile)
        }

        // Normalize the (possibly extracted) ROM data.
        val normalizedTemp = baseRomRepository.newImportTempFile("${sanitized}_norm")
        try {
            tempFile.inputStream().use { input ->
                normalizedTemp.outputStream().use { output ->
                    RomNormalizer.normalize(input, output)
                }
            }
        } catch (e: Exception) {
            if (normalizedTemp.exists()) normalizedTemp.delete()
            if (tempFile.exists()) tempFile.delete()
            return RegisterResult.Invalid(e.message ?: "invalid rom")
        }
        tempFile.delete()
        return baseRomRepository.registerNormalizedFile(normalizedTemp, sourceName)
    }

    /** Check the ZIP magic signature (PK\x03\x04). */
    private fun isZipFile(file: File): Boolean {
        val magic = ByteArray(4)
        return try {
            file.inputStream().use { it.read(magic) } == 4 &&
                magic[0] == 0x50.toByte() && magic[1] == 0x4B.toByte() &&
                magic[2] == 0x03.toByte() && magic[3] == 0x04.toByte()
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Walk a ZIP archive and return the first extracted N64 ROM as a temp file,
     * or null if the archive contains no recognized ROM entry.
     */
    private fun extractRomFromZip(zipFile: File): File? {
        val romExtensions = setOf("z64", "n64", "v64", "bin", "rom", "vmi", "nmp")
        java.util.zip.ZipFile(zipFile).use { zip ->
            // Prefer entries whose name ends with a known ROM extension.
            val entries = zip.entries().toList()
            val target = entries.firstOrNull { entry ->
                !entry.isDirectory && entry.name.substringAfterLast('.').lowercase() in romExtensions
            } ?: entries.firstOrNull { entry ->
                !entry.isDirectory && entry.size > 1_000_000
            } ?: return null
            val outFile = baseRomRepository.newImportTempFile(
                FileNameSanitizer.sanitize(target.name.substringAfterLast('/'))
            )
            zip.getInputStream(target).use { zis ->
                outFile.outputStream().use { out -> zis.copyTo(out) }
            }
            return outFile
        }
    }

    private fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? {
        val fromQuery = runCatching {
            resolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        }.getOrNull()
        return fromQuery ?: uri.lastPathSegment
    }

    // ---- Section B: list and delete ----

    fun getBaseRoms(): List<BaseRom> = baseRomRepository.getAll()

    /**
     * Delete a base ROM and invalidate any cached hackId to baseRomId mappings
     * in the launch preferences that point at it (keys of the form
     * "base_rom:<hackId>").
     */
    fun deleteBaseRom(id: String): Boolean {
        val removed = baseRomRepository.deleteById(id)
        if (removed) invalidateLaunchMappings(id)
        return removed
    }

    private fun invalidateLaunchMappings(deletedId: String) {
        val toRemove = launchPrefs.all
            .filterValues { it == deletedId }
            .keys
            .filter { it.startsWith("base_rom:") }
        if (toRemove.isNotEmpty()) {
            launchPrefs.edit().apply {
                toRemove.forEach { remove(it) }
                apply()
            }
        }
    }

    // ---- Section C: catalog URLs ----

    fun getCatalogUrls(): List<String> = catalogUrlStore.getUrls()

    fun addCatalogUrl(url: String): Boolean = catalogUrlStore.addUrl(url)

    fun removeCatalogUrl(url: String): Boolean = catalogUrlStore.removeUrl(url)
}
