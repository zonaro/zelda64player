package br.com.redclaw.zelda64player.store.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.Zelda64PlayerApp
import br.com.redclaw.zelda64player.data.model.HackEntry
import br.com.redclaw.zelda64player.databinding.ActivityStoreBinding
import br.com.redclaw.zelda64player.ocarina.OcarinaGame
import br.com.redclaw.zelda64player.store.DownloadPhase
import br.com.redclaw.zelda64player.store.DownloadQueueManager
import br.com.redclaw.zelda64player.store.ImportPatchInvalid
import br.com.redclaw.zelda64player.store.ImportPatchNoCompatibleRom
import br.com.redclaw.zelda64player.store.ImportPatchResult
import br.com.redclaw.zelda64player.store.ImportPatchSuccess
import br.com.redclaw.zelda64player.store.ImportPatchUnsupported
import br.com.redclaw.zelda64player.store.ImportRomDuplicate
import br.com.redclaw.zelda64player.store.ImportRomInvalid
import br.com.redclaw.zelda64player.store.ImportRomSuccess
import br.com.redclaw.zelda64player.ui.switchui.SwitchImmersive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class StoreActivity : AppCompatActivity() {
    private lateinit var binding: ActivityStoreBinding
    internal lateinit var viewModel: StoreViewModel
    private lateinit var adapter: StoreAdapter

    private val sfx = runCatching { Zelda64PlayerApp.sfxManager }.getOrNull()

    /** Sidebar category rows, rebuilt once in [onCreate]. */
    private val categoryRows = mutableListOf<CategoryRowUi>()

    private var currentPageHacks: List<HackEntry> = emptyList()

    /** Picks a BPS/IPS patch or .n64/.z64/.z.64 base ROM from the document provider. */
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val rawName = queryDisplayName(uri) ?: "hack"
        val extension = rawName.substringAfterLast('.', "bin").lowercase()
        val temp = File(cacheDir, "import_${System.currentTimeMillis()}.$extension.tmp")
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

        val progress = showProgressDialog(isDirectRomFile(rawName))
        progress.show()
        lifecycleScope.launch(Dispatchers.Main) {
            val result = viewModel.importFile(temp, rawName)
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

        val spanCount = computeStoreSpanCount()
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

        buildCategoryRows()
        binding.storeSearchIcon.setOnClickListener { toggleSearch() }

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

        // Keep the sidebar highlight + section header in sync with the active
        // category (also covers programmatic category changes).
        viewModel.category.observe(this) { cat ->
            updateCategoryHighlight(cat)
            binding.storeSectionTitle.text = getString(cat.labelRes)
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
        HackDetailDialog.newInstance(hack)
            .show(supportFragmentManager, "hack_detail")
    }

    /**
     * Responsive column count for the main-content grid: at least 4 columns,
     * more on wider screens (capped so ultra-wide layouts don't shrink cards to
     * an unreadable size). Derived from the content area width (screen minus the
     * fixed sidebar) divided by a ~170dp target card width. This keeps Store
     * thumbnails compact when the activity has a wide content area.
     */
    private fun computeStoreSpanCount(): Int {
        val density = resources.displayMetrics.density
        val sidebarPx = resources.getDimensionPixelSize(R.dimen.store_sidebar_width).toFloat()
        val contentPx = resources.displayMetrics.widthPixels - sidebarPx
        val targetCardPx = 170f * density
        return maxOf(4, minOf(6, (contentPx / targetCardPx).toInt()))
    }

    /** Builds the sidebar category rows once and wires their click handlers. */
    private fun buildCategoryRows() {
        val density = resources.displayMetrics.density
        categoryRows.clear()
        binding.storeCategoryList.removeAllViews()
        StoreCategory.ALL.forEach { cat ->
            val accent = View(this).apply {
                layoutParams = LinearLayout.LayoutParams(
                    (4 * density).toInt(),
                    LinearLayout.LayoutParams.MATCH_PARENT
                ).apply { marginEnd = (12 * density).toInt() }
                setBackgroundResource(R.color.switch_accent)
                visibility = View.GONE
            }
            val label = TextView(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                text = getString(cat.labelRes)
                setTextColor(ContextCompat.getColor(this@StoreActivity, R.color.switch_text_primary))
                textSize = 16f
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isFocusable = true
                isFocusableInTouchMode = true
                isClickable = true
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (8 * density).toInt() }
                setPadding(
                    (16 * density).toInt(),
                    (12 * density).toInt(),
                    (16 * density).toInt(),
                    (12 * density).toInt()
                )
                addView(accent)
                addView(label)
                setOnClickListener { onCategorySelected(cat) }
                onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) sfx?.focusMove()
                }
            }
            binding.storeCategoryList.addView(row)
            categoryRows.add(CategoryRowUi(cat, row, accent, label))
        }
        updateCategoryHighlight(viewModel.category.value ?: StoreCategory.All)
    }

    /** Highlights the active category row (accent bar + bg + accent text). */
    private fun updateCategoryHighlight(active: StoreCategory) {
        categoryRows.forEach { (cat, row, accent, label) ->
            val isActive = cat == active
            accent.visibility = if (isActive) View.VISIBLE else View.GONE
            row.setBackgroundColor(
                ContextCompat.getColor(
                    this,
                    if (isActive) R.color.switch_bg else android.R.color.transparent
                )
            )
            label.setTextColor(
                ContextCompat.getColor(
                    this,
                    if (isActive) R.color.switch_accent else R.color.switch_text_primary
                )
            )
        }
    }

    private fun onCategorySelected(cat: StoreCategory) {
        sfx?.select()
        viewModel.setCategory(cat)
    }

    /** Toggles the search field: show + focus, or hide + clear the query. */
    private fun toggleSearch() {
        if (binding.storeSearch.visibility == View.VISIBLE) {
            binding.storeSearch.setText("")
            viewModel.setQuery("")
            binding.storeSearch.visibility = View.GONE
            binding.storeGrid.requestFocus()
        } else {
            binding.storeSearch.visibility = View.VISIBLE
            binding.storeSearch.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(binding.storeSearch, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    /** Small tuple holding the views of a single sidebar category row. */
    private data class CategoryRowUi(
        val category: StoreCategory,
        val row: LinearLayout,
        val accent: View,
        val label: TextView
    )

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

    /** Indeterminate spinner shown while a patch is applied or a ROM is normalized. */
    private fun showProgressDialog(importingRom: Boolean): AlertDialog {
        val size = (48 * resources.displayMetrics.density).toInt()
        val progressBar = ProgressBar(this).apply {
            isIndeterminate = true
            layoutParams = LinearLayout.LayoutParams(size, size)
        }
        return AlertDialog.Builder(this)
            .setTitle(R.string.store_import_bps)
            .setMessage(if (importingRom) R.string.import_rom_progress else R.string.import_patch_progress)
            .setView(progressBar)
            .setCancelable(false)
            .create()
    }

    /** Present the result of an import to the user. */
    private fun showResultDialog(result: ImportPatchResult) {
        val titleRes: Int
        val message: String
        when (result) {
            is ImportPatchSuccess -> {
                titleRes = R.string.import_success_title
                message = getString(
                    R.string.import_success_message,
                    result.title,
                    gameName(result.family)
                )
            }
            is ImportPatchNoCompatibleRom -> {
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
            is ImportPatchInvalid -> {
                titleRes = R.string.import_invalid_title
                message = getString(R.string.import_invalid_message, result.message)
            }
            is ImportPatchUnsupported -> {
                titleRes = R.string.import_invalid_title
                message = getString(R.string.import_unsupported_message)
            }
            is ImportRomSuccess -> {
                titleRes = R.string.import_rom_success_title
                message = getString(R.string.import_rom_success_message, result.title)
            }
            is ImportRomDuplicate -> {
                titleRes = R.string.import_rom_success_title
                message = getString(R.string.import_rom_success_message, result.title)
            }
            is ImportRomInvalid -> {
                titleRes = R.string.import_invalid_rom_title
                message = result.message
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

    private fun isDirectRomFile(name: String): Boolean {
        val lower = name.lowercase()
        return lower.endsWith(".n64") || lower.endsWith(".z64") || lower.endsWith(".z.64")
    }
}
