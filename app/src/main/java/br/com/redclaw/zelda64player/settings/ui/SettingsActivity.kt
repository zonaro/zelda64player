package br.com.redclaw.zelda64player.settings.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.data.model.BaseRom
import br.com.redclaw.zelda64player.databinding.ActivitySettingsBinding
import br.com.redclaw.zelda64player.databinding.SettingsBaseRomItemBinding
import br.com.redclaw.zelda64player.databinding.SettingsCatalogUrlItemBinding
import br.com.redclaw.zelda64player.settings.SettingsViewModel
import br.com.redclaw.zelda64player.store.CatalogFetcher
import br.com.redclaw.zelda64player.utils.CorePrefs
import java.text.DecimalFormat

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var viewModel: SettingsViewModel

    private lateinit var baseRomAdapter: BaseRomAdapter
    private lateinit var catalogUrlAdapter: CatalogUrlAdapter

    private val pickRomLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val data = result.data ?: return@registerForActivityResult
        val uris = mutableListOf<Uri>()
        data.data?.let { uris.add(it) }
        data.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) uris.add(clip.getItemAt(i).uri)
        }
        if (uris.isNotEmpty()) viewModel.importRomsFromUris(uris)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.settingsToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.settings_title)
        binding.settingsToolbar.setNavigationOnClickListener { finish() }

        viewModel = SettingsViewModel(application)

        setupImportSection()
        setupBaseRomList()
        setupCatalogSection()
        setupCoreSection()
        setupAboutSection()
        observeImport()
    }

    private fun setupImportSection() {
        binding.settingsImportButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                // .z64 has no registered MIME type, so accept everything and let
                // RomNormalizer reject non-N64 files during import.
                type = "*/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            pickRomLauncher.launch(intent)
        }
    }

    private fun observeImport() {
        viewModel.importState.observe(this) { state ->
            when (state) {
                is SettingsViewModel.ImportUiState.Idle -> {
                    binding.settingsImportProgress.visibility = View.GONE
                }
                is SettingsViewModel.ImportUiState.Importing -> {
                    binding.settingsImportProgress.visibility = View.VISIBLE
                }
                is SettingsViewModel.ImportUiState.Batch -> {
                    binding.settingsImportProgress.visibility = View.GONE
                    showImportResult(state.result)
                    refreshBaseRomList()
                }
            }
        }
    }

    private fun showImportResult(result: SettingsViewModel.ImportBatchResult) {
        val lines = mutableListOf<String>()
        result.successes.forEach { rom ->
            lines.add(
                getString(
                    R.string.settings_import_success,
                    rom.displayName, rom.gameCode, rom.versionByte.toString(), rom.crc32
                )
            )
        }
        result.duplicates.forEach { rom ->
            lines.add(getString(R.string.settings_import_duplicate, rom.displayName, rom.crc32))
        }
        result.invalids.forEach { reason ->
            lines.add(getString(R.string.settings_import_invalid, reason))
        }
        val summary = getString(
            R.string.settings_import_summary,
            result.successes.size, result.duplicates.size, result.invalids.size
        )
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_import_result_title)
            .setMessage("$summary\n\n${lines.joinToString("\n")}")
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun setupBaseRomList() {
        baseRomAdapter = BaseRomAdapter(mutableListOf()) { rom ->
            confirmDeleteBaseRom(rom)
        }
        binding.settingsBaseromList.layoutManager = LinearLayoutManager(this)
        binding.settingsBaseromList.adapter = baseRomAdapter
        refreshBaseRomList()
    }

    private fun refreshBaseRomList() {
        val roms = viewModel.getBaseRoms()
        baseRomAdapter.update(roms)
        binding.settingsBaseromEmpty.visibility =
            if (roms.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun confirmDeleteBaseRom(rom: BaseRom) {
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_baserom_delete_confirm_title)
            .setMessage(
                getString(
                    R.string.settings_baserom_delete_confirm_message,
                    rom.displayName, rom.crc32
                )
            )
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.deleteBaseRom(rom.id)
                refreshBaseRomList()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun setupCatalogSection() {
        catalogUrlAdapter = CatalogUrlAdapter(mutableListOf()) { url ->
            confirmDeleteCatalogUrl(url)
        }
        binding.settingsCatalogList.layoutManager = LinearLayoutManager(this)
        binding.settingsCatalogList.adapter = catalogUrlAdapter
        refreshCatalogList()

        binding.settingsCatalogAdd.setOnClickListener {
            val url = binding.settingsCatalogInput.text.toString()
            binding.settingsCatalogError.visibility = View.GONE
            if (viewModel.addCatalogUrl(url)) {
                binding.settingsCatalogInput.text.clear()
                refreshCatalogList()
            } else {
                binding.settingsCatalogError.setText(R.string.settings_catalog_invalid)
                binding.settingsCatalogError.visibility = View.VISIBLE
            }
        }
    }

    private fun refreshCatalogList() {
        val urls = viewModel.getCatalogUrls()
        catalogUrlAdapter.update(urls)
        binding.settingsCatalogEmpty.visibility =
            if (urls.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun confirmDeleteCatalogUrl(url: String) {
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_catalog_remove_confirm_title)
            .setMessage(url)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                viewModel.removeCatalogUrl(url)
                refreshCatalogList()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun setupCoreSection() {
        updateCoreLabel()
        binding.settingsCoreButton.setOnClickListener { showCoreDialog() }
    }

    private fun updateCoreLabel() {
        val index = CorePrefs.getSelectedCoreIndex(this)
        val name = CorePrefs.options.getOrElse(index) { CorePrefs.options[0] }
        binding.settingsCoreCurrent.text = getString(R.string.settings_core_current, name)
    }

    private fun showCoreDialog() {
        val currentIndex = CorePrefs.getSelectedCoreIndex(this)
        AlertDialog.Builder(this)
            .setTitle(R.string.settings_core_change)
            .setSingleChoiceItems(CorePrefs.options, currentIndex) { dialog, which ->
                CorePrefs.setSelectedCoreIndex(this, which)
                updateCoreLabel()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun setupAboutSection() {
        val versionName = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "?"
        binding.settingsAboutVersion.text =
            getString(R.string.settings_about_version, versionName)
        binding.settingsAboutRepo.setOnClickListener {
            openLink("https://github.com/zonaro/zelda64player")
        }
        binding.settingsAboutCatalog.setOnClickListener {
            openLink(CatalogFetcher.DEFAULT_CATALOG_URL)
        }
    }

    private fun openLink(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    private class BaseRomAdapter(
        private val items: MutableList<BaseRom>,
        private val onDelete: (BaseRom) -> Unit
    ) : RecyclerView.Adapter<BaseRomAdapter.ViewHolder>() {

        class ViewHolder(val binding: SettingsBaseRomItemBinding) :
            RecyclerView.ViewHolder(binding.root)

        fun update(newItems: List<BaseRom>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = SettingsBaseRomItemBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val rom = items[position]
            holder.binding.itemBaseromName.text = rom.displayName
            holder.binding.itemBaseromDetails.text = holder.itemView.context.getString(
                R.string.settings_baserom_details,
                rom.gameCode,
                rom.versionByte.toString(),
                rom.crc32,
                formatSize(rom.sizeBytes),
                rom.sourceName ?: rom.displayName
            )
            holder.binding.itemBaseromDelete.setOnClickListener { onDelete(rom) }
        }

        override fun getItemCount() = items.size

        private fun formatSize(bytes: Long): String {
            val mb = bytes / (1024.0 * 1024.0)
            return if (mb >= 1.0) "${DecimalFormat("#0.0").format(mb)} MB" else "$bytes B"
        }
    }

    private class CatalogUrlAdapter(
        private val items: MutableList<String>,
        private val onDelete: (String) -> Unit
    ) : RecyclerView.Adapter<CatalogUrlAdapter.ViewHolder>() {

        class ViewHolder(val binding: SettingsCatalogUrlItemBinding) :
            RecyclerView.ViewHolder(binding.root)

        fun update(newItems: List<String>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val binding = SettingsCatalogUrlItemBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return ViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val url = items[position]
            holder.binding.itemCatalogUrl.text = url
            holder.binding.itemCatalogDelete.setOnClickListener { onDelete(url) }
        }

        override fun getItemCount() = items.size
    }
}
