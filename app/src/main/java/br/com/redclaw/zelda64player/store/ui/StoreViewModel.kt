package br.com.redclaw.zelda64player.store.ui

import android.app.Application
import br.com.redclaw.zelda64player.R
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import br.com.redclaw.zelda64player.data.local.BaseRomRepository
import br.com.redclaw.zelda64player.data.local.InstalledHacksRepository
import br.com.redclaw.zelda64player.data.local.MergedCatalogRepository
import br.com.redclaw.zelda64player.data.local.PatchRepository
import br.com.redclaw.zelda64player.data.model.HackEntry
import br.com.redclaw.zelda64player.store.CatalogFetcher
import br.com.redclaw.zelda64player.store.DownloadManager
import br.com.redclaw.zelda64player.store.StoreException
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
    private val cache = appContext.externalCacheDir ?: appContext.cacheDir

    private val patchRepository = PatchRepository(File(external, "patches"))
    private val installedRepository =
        InstalledHacksRepository(File(appContext.filesDir, "installed_hacks.json"))
    private val mergedCatalogRepository =
        MergedCatalogRepository(File(appContext.filesDir, "merged_catalog.json"))
    private val baseRomRepository = BaseRomRepository(
        importDir = File(external, "base_roms"),
        storageDir = File(cache, "base_roms"),
        registryFile = File(appContext.filesDir, "base_roms.json")
    )
    private val catalogFetcher = CatalogFetcher(okHttpClient, File(cache, "catalog"))
    private val downloadManager =
        DownloadManager(okHttpClient, patchRepository, installedRepository)

    private val _catalog = MutableLiveData<CatalogUiState>(CatalogUiState.Loading)
    val catalog: LiveData<CatalogUiState> = _catalog

    private val _install = MutableLiveData<InstallUiState>()
    val install: LiveData<InstallUiState> = _install

    init {
        // Render the cached catalog instantly; a refresh updates it from network.
        val cached = mergedCatalogRepository.load()
        if (cached.isNotEmpty()) _catalog.value = CatalogUiState.Loaded(cached)
    }

    fun refresh() {
        _catalog.value = CatalogUiState.Loading
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { catalogFetcher.fetch() }
            result.onSuccess { fetchResult ->
                mergedCatalogRepository.save(fetchResult.hacks)
                _catalog.postValue(CatalogUiState.Loaded(fetchResult.hacks))
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
        _install.value = InstallUiState.Progress(hack.id, 0, 0)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                downloadManager.download(hack) { downloaded, total ->
                    _install.postValue(InstallUiState.Progress(hack.id, downloaded, total))
                }
            }
            result.onSuccess {
                _install.postValue(InstallUiState.Success(hack.id))
            }.onFailure { e ->
                val message = when (e) {
                    is StoreException.NetworkError ->
                        appContext.getString(R.string.detail_error_network)
                    is StoreException.ChecksumMismatch ->
                        appContext.getString(R.string.detail_error_checksum)
                    else -> appContext.getString(R.string.detail_error_generic)
                }
                _install.postValue(InstallUiState.Error(hack.id, message))
            }
        }
    }

    sealed class CatalogUiState {
        object Loading : CatalogUiState()
        data class Loaded(val hacks: List<HackEntry>) : CatalogUiState()
        data class Error(val message: String) : CatalogUiState()
    }

    sealed class InstallUiState {
        abstract val hackId: String
        data class Progress(override val hackId: String, val downloaded: Long, val total: Long) : InstallUiState()
        data class Success(override val hackId: String) : InstallUiState()
        data class Error(override val hackId: String, val message: String) : InstallUiState()
    }
}
