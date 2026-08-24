package br.com.redclaw.zelda64player.store.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.data.model.HackEntry
import br.com.redclaw.zelda64player.databinding.ActivityStoreBinding
import br.com.redclaw.zelda64player.ocarina.OcarinaGame
import br.com.redclaw.zelda64player.store.DownloadPhase
import br.com.redclaw.zelda64player.store.DownloadQueueManager
import br.com.redclaw.zelda64player.store.ImportPatchResult
import br.com.redclaw.zelda64player.ui.switchui.SwitchImmersive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class StoreActivity : AppCompatActivity() {
    private lateinit var binding: ActivityStoreBinding
    internal lateinit var viewModel: StoreViewModel
    private lateinit var adapter: StoreAdapter

    private var currentPageHacks: List<HackEntry> = emptyList()

    /** Picks a patch file (BPS/IPS) from the document provider for import. */
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val rawName = queryDisplayName(uri) ?: "hack"
        val suggestedName = rawName.substringBeforeLast('.', missingDelimiterValue = rawName)
        val temp = File(cacheDir, "import_${System.currentTimeMillis()}.bps.tmp")
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                temp.outputStream().use { out -> input.copyTo(out) }
            } ?: run {
                temp.delete()
                return@registerForActivityResult
            }
        } catch (_: Exception) {
            temp.delete()
            return@registerForActivityResult
        }

        val progress = showProgressDialog()
        progress.show()
        lifecycleScope.launch(Dispatchers.Main) {
            val result = viewModel.importBps(temp, suggestedName)
            temp.delete()
            progress.dismiss()
            showResultDialog(result)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStoreBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SwitchImmersive.enterFullscreen(this)

        setSupportActionBar(binding.storeToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.store_title)
        binding.storeToolbar.setNavigationOnClickListener { finish() }

        viewModel = ViewModelProvider(this)[StoreViewModel::class.java]

        val spanCount = resources.getInteger(R.integer.library_span_count)
        binding.storeGrid.layoutManager = GridLayoutManager(this, spanCount)
        adapter = StoreAdapter { hack -> openDetail(hack) }
        binding.storeGrid.adapter = adapter

        binding.storeSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                viewModel.setQuery(s?.toString().orEmpty())
            }
        })

        binding.storePrev.setOnClickListener { viewModel.prevPage() }
        binding.storeNext.setOnClickListener { viewModel.nextPage() }

        viewModel.catalog.observe(this) { state ->
            when (state) {
                is StoreViewModel.CatalogUiState.Loading -> {
                    binding.storeProgress.visibility = View.VISIBLE
                    binding.storeStatus.visibility = View.GONE
                    binding.storeEmpty.visibility = View.GONE
                    binding.storeGrid.visibility = View.GONE
                    binding.storePagination.visibility = View.GONE
                }
                is StoreViewModel.CatalogUiState.Loaded -> {
                    binding.storeProgress.visibility = View.GONE
                }
                is StoreViewModel.CatalogUiState.Error -> {
                    binding.storeProgress.visibility = View.GONE
                    binding.storeStatus.text = state.message
                    binding.storeStatus.visibility = View.VISIBLE
                    binding.storeGrid.visibility = View.GONE
                    binding.storeEmpty.visibility = View.GONE
                    binding.storePagination.visibility = View.GONE
                }
            }
        }

        // The derived paged state drives the actual grid + pagination rendering.
        viewModel.pagedItems.observe(this) { state ->
            if (state == null) return@observe
            renderPage(state)
        }

        // Re-render the grid badges whenever the download queue changes
        // (queued / downloading / patching / finished states).
        DownloadQueueManager.queue.observe(this) { renderItems() }

        viewModel.refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) SwitchImmersive.enterFullscreen(this)
    }

    private fun renderPage(state: StorePageState) {
        when {
            state.catalogEmpty -> {
                binding.storeGrid.visibility = View.GONE
                binding.storePagination.visibility = View.GONE
                binding.storeEmpty.setText(R.string.store_empty)
                binding.storeEmpty.visibility = View.VISIBLE
            }
            state.filteredEmpty -> {
                binding.storeGrid.visibility = View.GONE
                binding.storePagination.visibility = View.GONE
                binding.storeEmpty.setText(R.string.store_no_results)
                binding.storeEmpty.visibility = View.VISIBLE
            }
            else -> {
                binding.storeEmpty.visibility = View.GONE
                binding.storeGrid.visibility = View.VISIBLE
                currentPageHacks = state.items
                renderItems()
                if (state.totalPages > 1) {
                    binding.storePagination.visibility = View.VISIBLE
                    binding.storePageIndicator.text = getString(
                        R.string.store_page_indicator,
                        state.pageIndex + 1,
                        state.totalPages
                    )
                    val atStart = state.pageIndex <= 0
                    val atEnd = state.pageIndex >= state.totalPages - 1
                    binding.storePrev.isEnabled = !atStart
                    binding.storeNext.isEnabled = !atEnd
                    binding.storePrev.alpha = if (atStart) 0.4f else 1f
                    binding.storeNext.alpha = if (atEnd) 0.4f else 1f
                } else {
                    binding.storePagination.visibility = View.GONE
                }
            }
        }
    }

    private fun renderItems() {
        val phaseByHack = DownloadQueueManager.queue.value
            ?.associate { it.hackId to it.phase }
            .orEmpty()
        val items = currentPageHacks.map { hack ->
            val phase = phaseByHack[hack.id]
            val downloadPhase = if (phase != null && phase != DownloadPhase.SUCCESS) phase else null
            StoreItem(hack, viewModel.statusFor(hack), downloadPhase)
        }
        adapter.update(items)
    }

    private fun openDetail(hack: HackEntry) {
        HackDetailBottomSheet.newInstance(hack)
            .show(supportFragmentManager, "hack_detail")
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.store_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_downloads -> {
                startActivity(Intent(this, DownloadQueueActivity::class.java))
                true
            }
            R.id.action_refresh -> {
                viewModel.refresh()
                true
            }
            R.id.action_import_bps -> {
                importLauncher.launch(arrayOf("*/*"))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /** Indeterminate spinner shown while the patch is being applied. */
    private fun showProgressDialog(): AlertDialog {
        val size = (48 * resources.displayMetrics.density).toInt()
        val progressBar = ProgressBar(this).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(size, size)
        }
        return AlertDialog.Builder(this)
            .setTitle(R.string.store_import_bps)
            .setMessage(R.string.import_patch_progress)
            .setView(progressBar)
            .setCancelable(false)
            .create()
    }

    /** Present the result of an import to the user. */
    private fun showResultDialog(result: ImportPatchResult) {
        val titleRes: Int
        val message: String
        when (result) {
            is ImportPatchResult.Success -> {
                titleRes = R.string.import_success_title
                message = getString(
                    R.string.import_success_message,
                    result.title,
                    gameName(result.family)
                )
            }
            is ImportPatchResult.NoCompatibleRom -> {
                titleRes = R.string.import_no_rom_title
                message = if (result.targetDescription != null) {
                    getString(
                        R.string.import_no_rom_message,
                        result.targetDescription,
                        result.expectedCrc32
                    )
                } else {
                    getString(R.string.import_no_rom_unknown_message, result.expectedCrc32)
                }
            }
            is ImportPatchResult.InvalidPatch -> {
                titleRes = R.string.import_invalid_title
                message = getString(R.string.import_invalid_message, result.message)
            }
            is ImportPatchResult.UnsupportedFormat -> {
                titleRes = R.string.import_invalid_title
                message = getString(R.string.import_unsupported_message)
            }
        }
        AlertDialog.Builder(this)
            .setTitle(titleRes)
            .setMessage(message)
            .setPositiveButton(R.string.dialog_ok, null)
            .show()
    }

    /** Human-readable game family name for success messages. */
    private fun gameName(family: OcarinaGame?): String = when (family) {
        OcarinaGame.OOT -> getString(R.string.game_oot)
        OcarinaGame.MM -> getString(R.string.game_mm)
        null -> getString(R.string.game_unknown)
    }

    /** Best-effort display name of a content URI (falls back to "hack"). */
    private fun queryDisplayName(uri: Uri): String? {
        var name: String? = null
        try {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) name = cursor.getString(index)
                }
            }
        } catch (_: Exception) {
            // Ignore; fall back to the default name below.
        }
        return name
    }
}
