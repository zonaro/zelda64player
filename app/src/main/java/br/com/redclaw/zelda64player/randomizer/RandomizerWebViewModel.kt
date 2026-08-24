package br.com.redclaw.zelda64player.randomizer

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import br.com.redclaw.zelda64player.data.local.AppRepositories
import br.com.redclaw.zelda64player.data.model.BaseRom
import br.com.redclaw.zelda64player.repositories.Storage
import br.com.redclaw.zelda64player.randomizer.repository.RandomizedSeedEntry
import br.com.redclaw.zelda64player.randomizer.repository.RandomizedSeedRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * State for [RandomizerWebActivity].
 *
 * Holds the selected vanilla OoT ROM (from [br.com.redclaw.zelda64player.data
 * .local.BaseRomRepository]), the `content://` [Uri] that the WebView's file
 * chooser will receive, the current seed id (parsed from the `/seed/get?id=`
 * URL), and the capture result. The patched ROM bytes are persisted through
 * [RandomizedSeedRepository] (which moves them to [Storage.rom]) so the seed
 * shows up in the Library "Randomizadores" section exactly as before.
 */
class RandomizerWebViewModel(application: Application) : AndroidViewModel(application) {

    private val baseRomRepository = AppRepositories.baseRomRepository(application)
    private val seedRepository = AppRepositories.randomizedSeedRepository(application)
    private val storage = Storage.getInstance(application)

    private val _ootRoms = MutableStateFlow<List<BaseRom>>(emptyList())
    val ootRoms: StateFlow<List<BaseRom>> = _ootRoms.asStateFlow()

    private val _selectedRom = MutableStateFlow<BaseRom?>(null)
    val selectedRom: StateFlow<BaseRom?> = _selectedRom.asStateFlow()

    private val _romUri = MutableStateFlow<Uri?>(null)
    val romUri: StateFlow<Uri?> = _romUri.asStateFlow()

    private val _seedId = MutableStateFlow<String?>(null)
    val seedId: StateFlow<String?> = _seedId.asStateFlow()

    private val _captureState = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val captureState: StateFlow<CaptureState> = _captureState.asStateFlow()

    /** Load OoT vanilla ROMs; auto-select when exactly one is available. */
    fun loadOotRoms() {
        val roms = baseRomRepository.getAll()
            .filter { it.gameCode.startsWith("CZL", ignoreCase = true) }
        _ootRoms.value = roms
        if (roms.size == 1) selectRom(roms.first())
    }

    /** Select a vanilla OoT ROM and prepare its `content://` URI for the WebView. */
    fun selectRom(rom: BaseRom) {
        _selectedRom.value = rom
        val tempDir = File(getApplication<Application>().cacheDir, "randomizer_rom")
        tempDir.mkdirs()
        val prepared = prepareRomFile(rom, tempDir)
        _romUri.value = prepared?.let { uriForFile(it) }
    }

    /**
     * Copy (or, for a `.zip`, extract) the selected ROM into [outDir] so it can
     * be served by [RomFileProvider]. Streaming copy avoids loading the ROM into
     * the heap.
     */
    private fun prepareRomFile(rom: BaseRom, outDir: File): File? {
        val source = File(rom.path)
        if (!source.exists()) return null
        val lower = rom.path.lowercase()
        return try {
            if (lower.endsWith(".zip")) {
                RomZipExtractor.extractZ64(source, outDir)
            } else {
                val target = File(outDir, "oot_vanilla.z64")
                source.inputStream().use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                target
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun uriForFile(file: File): Uri {
        val ctx = getApplication<Application>()
        val uri = FileProvider.getUriForFile(
            ctx,
            "${ctx.packageName}.randomizer.romfileprovider",
            file
        )
        // The WebView runs in the same app process; grant it read access.
        ctx.grantUriPermission(ctx.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return uri
    }

    fun setSeedId(id: String?) {
        _seedId.value = id
    }

    /**
     * Persist the captured patched ROM as a [RandomizedSeedEntry]. Idempotent:
     * if a seed with the same id was already saved (e.g. a duplicate capture),
     * the existing entry is returned. Returns `null` when required state
     * (selected ROM / seed id / captured file) is missing.
     */
    fun consumeCapture(file: File, fileName: String?): RandomizedSeedEntry? {
        val rom = _selectedRom.value ?: return null
        val seedId = _seedId.value ?: return null
        val id = "randomizer_$seedId"
        seedRepository.get(id)?.let { existing ->
            _captureState.value = CaptureState.Success(existing)
            return existing
        }
        val displayName = (fileName ?: "OoTR $seedId")
            .removeSuffix(".z64").removeSuffix(".n64")
            .take(60).ifBlank { "OoTR $seedId" }
        val entry = RandomizedSeedEntry(
            id = id,
            name = displayName,
            ootrSeedId = seedId,
            ootrVersion = "web",
            createdAt = System.currentTimeMillis(),
            hasPlandomizer = false,
            romFileName = "rom_$id",
            baseRomLabel = rom.displayName
        )
        seedRepository.add(entry, file)
        _captureState.value = CaptureState.Success(entry)
        return entry
    }

    fun resetCapture() {
        _captureState.value = CaptureState.Idle
    }

    sealed class CaptureState {
        object Idle : CaptureState()
        object Capturing : CaptureState()
        data class Success(val entry: RandomizedSeedEntry) : CaptureState()
        object Error : CaptureState()
    }
}
