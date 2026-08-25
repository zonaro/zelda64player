package br.com.redclaw.zelda64player.store.ui

import android.app.Application
import br.com.redclaw.zelda64player.R
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import br.com.redclaw.zelda64player.data.local.AppRepositories
import br.com.redclaw.zelda64player.data.local.InstalledHacksRepository
import br.com.redclaw.zelda64player.data.local.MergedCatalogRepository
import br.com.redclaw.zelda64player.data.local.UserHacksRepository
import br.com.redclaw.zelda64player.data.model.HackEntry
import br.com.redclaw.zelda64player.repositories.Storage
import br.com.redclaw.zelda64player.store.CatalogRefresher
import br.com.redclaw.zelda64player.store.DownloadPhase
import br.com.redclaw.zelda64player.store.DownloadQueueManager
import br.com.redclaw.zelda64player.store.ImportedPatchInstaller
import br.com.redclaw.zelda64player.store.ImportedRomInstaller
import br.com.redclaw.zelda64player.store.ImportPatchResult
import br.com.redclaw.zelda64player.store.QueueItemUi
import kotlinx.coroutines.launch
import java.io.File

/**
 * Drives the Hack Store: fetches/merges the catalog, exposes install state, and
 * runs patch downloads. Repositories are constructed from the application
 * context (matching the existing manual service-locator style in the project).
 */
class StoreViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext

    private val patchRepository = AppRepositories.patchRepository(appContext)
    private val installedRepository =
        InstalledHacksRepository(File(appContext.filesDir, "installed_hacks.json"))
    private val mergedCatalogRepository =
        MergedCatalogRepository(File(appContext.filesDir, "merged_catalog.json"))
    private val baseRomRepository = AppRepositories.baseRomRepository(appContext)

    private val _catalog = MutableLiveData<CatalogUiState>(CatalogUiState.Loading)
    val catalog: LiveData<CatalogUiState> = _catalog

    /** Active, trimmed search query (empty string means "no filter"). */
    private val _query = MutableLiveData<String>("")
    val query: LiveData<String> = _query

    /** Active sidebar category filter. */
    private val _category = MutableLiveData<StoreCategory>(StoreCategory.All)
    val category: LiveData<StoreCategory> = _category

    /** Current 0-based page index. */
    private val _page = MutableLiveData<Int>(0)
    val page: LiveData<Int> = _page

    /**
     * Derived rendering state combining the loaded catalog, the active query
     * and the current page. Null while the catalog is not in the Loaded
     * state (Loading/Error), so the Activity can let the [catalog] observer
     * own those states.
     */
    private val _pagedItems = MediatorLiveData<StorePageState?>()
    val pagedItems: LiveData<StorePageState?> = _pagedItems

    init {
        _pagedItems.addSource(_catalog) { recomputePaged() }
        _pagedItems.addSource(_query) { recomputePaged() }
        _pagedItems.addSource(_category) { recomputePaged() }
        _pagedItems.addSource(_page) { recomputePaged() }
    }

    /**
     * Recomputes the filtered + paginated view state. When the catalog is not
     * Loaded the derived state is cleared (null) so the [catalog] observer
     * drives Loading/Error rendering instead.
     */
    private fun recomputePaged() {
        val state = _catalog.value
        if (state !is CatalogUiState.Loaded) {
            _pagedItems.value = null
            return
        }
        val q = _query.value ?: ""
        val byQuery = StorePager.filter(state.hacks, q)
        val byCategory = filterByCategory(byQuery, _category.value ?: StoreCategory.All)
        val result = StorePager.page(byCategory, _page.value ?: 0)
        _pagedItems.value = StorePageState(
            items = result.items,
            pageIndex = result.pageIndex,
            totalPages = result.totalPages,
            query = q,
            catalogEmpty = state.hacks.isEmpty(),
            filteredEmpty = byCategory.isEmpty() && state.hacks.isNotEmpty()
        )
    }

    /**
     * Filters [hacks] by the active [StoreCategory]. The category predicates are
     * derived from data already on each hack: install status (for [StoreCategory.Installed]
     * and [StoreCategory.Updates]) and the base ROM game code prefix (for [StoreCategory.Oot]
     * and [StoreCategory.Mm]).
     */
    private fun filterByCategory(hacks: List<HackEntry>, category: StoreCategory): List<HackEntry> {
        return when (category) {
            StoreCategory.All -> hacks
            StoreCategory.Installed -> hacks.filter {
                statusFor(it) is StoreStatus.Installed || statusFor(it) is StoreStatus.UpdateAvailable
            }
            StoreCategory.Updates -> hacks.filter { statusFor(it) is StoreStatus.UpdateAvailable }
            StoreCategory.Oot -> hacks.filter { it.baseRom.gameCode.startsWith("CZL", ignoreCase = true) }
            StoreCategory.Mm -> hacks.filter {
                it.baseRom.gameCode.startsWith("NZL", ignoreCase = true) ||
                    it.baseRom.gameCode.startsWith("NSM", ignoreCase = true)
            }
        }
    }

    /** Updates the search query, resetting to page 1 when it actually changes. */
    fun setQuery(raw: String) {
        val trimmed = raw.trim()
        if (_query.value == trimmed) return
        _query.value = trimmed
        _page.value = 0
    }

    /** Switches the active sidebar category, resetting to the first page. */
    fun setCategory(cat: StoreCategory) {
        if (_category.value == cat) return
        _category.value = cat
        _page.value = 0
    }

    /** Moves to the previous page (no-op when already on the first page). */
    fun prevPage() {
        val current = _page.value ?: 0
        if (current > 0) _page.value = current - 1
    }

    /** Moves to the next page (guarded by the disabled Next button at bounds). */
    fun nextPage() {
        _page.value = (_page.value ?: 0) + 1
    }

    init {
        // Render the cached catalog instantly; a refresh updates it from network.
        val cached = mergedCatalogRepository.load()
        if (cached.isNotEmpty()) _catalog.value = CatalogUiState.Loaded(cached)
    }

    fun refresh() {
        // Keep the active query but reset to the first page for deterministic
        // behavior after a (re)fetch.
        _page.value = 0
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

    /** Enqueues [hack] for download + patch via the shared queue manager. */
    fun enqueue(hack: HackEntry) = DownloadQueueManager.enqueue(hack)

    /** Cancels an in-flight download/patch for [hackId]. */
    fun cancel(hackId: String) = DownloadQueueManager.cancel(hackId)

    /** LiveData of the queue state for a single hack (null when not queued). */
    fun queueStateFor(hackId: String): LiveData<QueueItemUi?> =
        DownloadQueueManager.stateFor(hackId)

    /**
     * Install a user-imported patch (BPS/IPS) file into the Library. Builds the
     * [ImportedPatchInstaller] from the application context and runs it. The
     * resulting hack launches exactly like a catalog hack (same `rom_<id>` +
     * installed record), so no launch-flow changes are needed.
     */
    suspend fun importFile(file: File, displayName: String): ImportPatchResult {
        if (isDirectRomFile(displayName)) {
            return ImportedRomInstaller(getApplication(), baseRomRepository).install(
                file,
                romDisplayName(displayName)
            )
        }
        val installer = ImportedPatchInstaller(
            getApplication(),
            AppRepositories.baseRomRepository(getApplication()),
            InstalledHacksRepository(File(appContext.filesDir, "installed_hacks.json")),
            AppRepositories.userHacksRepository(getApplication()),
            Storage.getInstance(getApplication())
        )
        return installer.install(file, displayName.substringBeforeLast('.', displayName))
    }

    /** Synchronous current phase for [hackId], or null when not queued. */
    fun queuePhaseFor(hackId: String): DownloadPhase? =
        DownloadQueueManager.queuePhaseFor(hackId)

    private fun isDirectRomFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".n64") || lower.endsWith(".z64") || lower.endsWith(".z.64")
    }

    private fun romDisplayName(name: String): String {
        val lower = name.lowercase()
        return when {
            lower.endsWith(".z.64") -> name.dropLast(5)
            lower.endsWith(".n64") || lower.endsWith(".z64") -> name.dropLast(4)
            else -> name
        }
    }

    sealed class CatalogUiState {
        object Loading : CatalogUiState()
        data class Loaded(val hacks: List<HackEntry>) : CatalogUiState()
        data class Error(val message: String) : CatalogUiState()
    }
}
