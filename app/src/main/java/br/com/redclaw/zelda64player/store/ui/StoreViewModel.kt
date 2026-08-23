package br.com.redclaw.zelda64player.store.ui

import android.app.Application
import br.com.redclaw.zelda64player.R
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import br.com.redclaw.zelda64player.data.local.AppRepositories
import br.com.redclaw.zelda64player.data.local.InstalledHacksRepository
import br.com.redclaw.zelda64player.data.local.MergedCatalogRepository
import br.com.redclaw.zelda64player.data.model.HackEntry
import br.com.redclaw.zelda64player.repositories.Storage
import br.com.redclaw.zelda64player.shortcuts.GamePlayHistoryStore
import br.com.redclaw.zelda64player.shortcuts.GameShortcutsManager
import br.com.redclaw.zelda64player.store.CatalogRefresher
import br.com.redclaw.zelda64player.store.DownloadManager
import br.com.redclaw.zelda64player.store.InstallPhase
import br.com.redclaw.zelda64player.store.StoreException
import br.com.redclaw.zelda64player.views.HackLibraryEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.io.File

/**
 * Drives the Hack Store: fetches/merges the catalog, exposes install state, and
 * runs patch downloads. Repositories are constructed from the application
 * context (matching the existing manual service-locator style in the project).
 */
class StoreViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext

    private val okHttpClient = OkHttpClient.Builder().build()

    private val external = appContext.getExternalFilesDir(null) ?: appContext.filesDir

    private val patchRepository = AppRepositories.patchRepository(appContext)
    private val installedRepository =
        InstalledHacksRepository(File(appContext.filesDir, "installed_hacks.json"))
    private val mergedCatalogRepository =
        MergedCatalogRepository(File(appContext.filesDir, "merged_catalog.json"))
    private val baseRomRepository = AppRepositories.baseRomRepository(appContext)
    private val downloadManager =
        DownloadManager(
            appContext,
            okHttpClient,
            patchRepository,
            installedRepository,
            baseRomRepository,
            Storage.getInstance(appContext)
        )

    private val _catalog = MutableLiveData<CatalogUiState>(CatalogUiState.Loading)
    val catalog: LiveData<CatalogUiState> = _catalog

    private val _install = MutableLiveData<InstallUiState>()
    val install: LiveData<InstallUiState> = _install

    /** Hack ids with an install currently in flight, to ignore re-entry. */
    private val installing = mutableSetOf<String>()

    init {
        // Render the cached catalog instantly; a refresh updates it from network.
        val cached = mergedCatalogRepository.load()
        if (cached.isNotEmpty()) _catalog.value = CatalogUiState.Loaded(cached)
    }

    fun refresh() {
        _catalog.value = CatalogUiState.Loading
        viewModelScope.launch {
            val result = CatalogRefresher(getApplication()).refresh()
            result.onSuccess { hacks ->
                _catalog.postValue(CatalogUiState.Loaded(hacks))
            }.onFailure { e ->
                val cached = mergedCatalogRepository.load()
                if (cached.isNotEmpty()) {
                    _catalog.postValue(CatalogUiState.Loaded(cached))
                } else {
                    _catalog.postValue(CatalogUiState.Error(e.message ?: "unknown error"))
                }
            }
        }
    }

    fun statusFor(hack: HackEntry): StoreStatus {
        val installed = installedRepository.installedVersion(hack.id)
        return when {
            installed == null -> StoreStatus.NotInstalled
            installed != hack.version -> StoreStatus.UpdateAvailable(installed, hack.version)
            else -> StoreStatus.Installed(installed)
        }
    }

    fun isInstalled(hackId: String): Boolean = installedRepository.isInstalled(hackId)

    /** Whether any imported base ROM matches the hack's required CRC32. */
    fun baseRomMatches(crc32: String): Boolean =
        baseRomRepository.getAll().any { it.crc32.equals(crc32, ignoreCase = true) }

    fun install(hack: HackEntry) {
        // Guard against concurrent installs of the same hack (rapid re-taps on
        // the download button would otherwise race the network + patch apply).
        if (installing.contains(hack.id)) return
        installing.add(hack.id)
        _install.value = InstallUiState.Progress(hack.id, InstallPhase.DOWNLOADING, 0, 0)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                downloadManager.download(hack) { phase, downloaded, total ->
                    _install.postValue(InstallUiState.Progress(hack.id, phase, downloaded, total))
                }
            }
            installing.remove(hack.id)
            result.onSuccess {
                _install.postValue(InstallUiState.Success(hack.id))
                publishShortcut(hack)
            }.onFailure { e ->
                val message = when (e) {
                    is StoreException.NetworkError ->
                        appContext.getString(R.string.detail_error_network)
                    is StoreException.ChecksumMismatch ->
                        appContext.getString(R.string.detail_error_checksum)
                    is StoreException.InvalidPatch ->
                        appContext.getString(R.string.detail_error_patch_invalid)
                    is StoreException.BaseRomMissing ->
                        appContext.getString(R.string.detail_error_base_rom_missing, e.expectedCrc32)
                    else -> appContext.getString(R.string.detail_error_generic)
                }
                _install.postValue(InstallUiState.Error(hack.id, message))
            }
        }
    }

    /**
     * Publish or update the launcher shortcut for [hack] right after a
     * successful install, so the new game appears in the launcher's long-press
     * menu without waiting for the next app restart.
     */
    private fun publishShortcut(hack: HackEntry) {
        val entry = HackLibraryEntry(hack.id, hack.name, hack.coverImageUrl)
        val history = GamePlayHistoryStore(File(appContext.filesDir, "game_play_history.json"))
        GameShortcutsManager(appContext, history).publishOrUpdate(entry)
    }

    sealed class CatalogUiState {
        object Loading : CatalogUiState()
        data class Loaded(val hacks: List<HackEntry>) : CatalogUiState()
        data class Error(val message: String) : CatalogUiState()
    }

    sealed class InstallUiState {
        abstract val hackId: String
        data class Progress(
            override val hackId: String,
            val phase: InstallPhase,
            val downloaded: Long,
            val total: Long
        ) : InstallUiState()
        data class Success(override val hackId: String) : InstallUiState()
        data class Error(override val hackId: String, val message: String) : InstallUiState()
    }
}
