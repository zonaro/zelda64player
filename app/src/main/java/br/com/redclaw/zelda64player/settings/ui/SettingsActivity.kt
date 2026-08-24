package br.com.redclaw.zelda64player.settings.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.Zelda64PlayerApp
import br.com.redclaw.zelda64player.data.local.InstalledHacksRepository
import br.com.redclaw.zelda64player.data.local.SaveBackupManager
import br.com.redclaw.zelda64player.data.model.BaseRom
import br.com.redclaw.zelda64player.databinding.ActivitySettingsBinding
import br.com.redclaw.zelda64player.databinding.SettingsBaseRomItemBinding
import br.com.redclaw.zelda64player.databinding.SettingsCatalogUrlItemBinding
import br.com.redclaw.zelda64player.randomizer.api.OotrApiKeyStore
import br.com.redclaw.zelda64player.repositories.Storage
import br.com.redclaw.zelda64player.retroachievements.auth.RaCredentialStore
import br.com.redclaw.zelda64player.settings.SettingsViewModel
import br.com.redclaw.zelda64player.store.CatalogFetcher
import br.com.redclaw.zelda64player.ui.switchui.SwitchDialog
import br.com.redclaw.zelda64player.ui.switchui.SwitchImmersive
import br.com.redclaw.zelda64player.utils.CorePrefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var viewModel: SettingsViewModel

    /** Shared Switch UI sound-effects manager (null-safe if not yet ready). */
    private val sfx = runCatching { Zelda64PlayerApp.sfxManager }.getOrNull()

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

    private val exportBackupLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> if (uri != null) runExportBackup(uri) }

    private val importBackupLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) runImportBackup(uri) }

    private val installedRepository by lazy {
        InstalledHacksRepository(File(filesDir, "installed_hacks.json"))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SwitchImmersive.enterFullscreen(this)

        setSupportActionBar(binding.settingsToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setTitle(R.string.settings_title)
        binding.settingsToolbar.setNavigationOnClickListener { finish() }

        viewModel = SettingsViewModel(application)

        setupImportSection()
        setupBaseRomList()
        setupCatalogSection()
        setupRandomizerSection()
        setupRetroAchievementsSection()
        setupCoreSection()
        setupBackupSection()
        setupAboutSection()
        wireSettingsSfx()
        observeImport()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) SwitchImmersive.enterFullscreen(this)
    }

    /**
     * Wires the Switch UI sound effects to the primary Settings controls so the
     * screen stays consistent with the other Switch-style surfaces (home row,
     * dock, grid, side panel). Focus traversal plays the focus-move "toc" and
     * activation plays the select blip. This is additive only — no control flow
     * is changed.
     */
    private fun wireSettingsSfx() {
        val focusViews = listOf(
            binding.settingsImportButton,
            binding.settingsBackupExport,
            binding.settingsBackupImport,
            binding.settingsCatalogAdd,
            binding.settingsRandomizerSave,
            binding.settingsRandomizerClear,
            binding.settingsRaLogin,
            binding.settingsRaLogout,
            binding.settingsCoreButton,
            binding.settingsAboutRepo,
            binding.settingsAboutCatalog
        )
        for (view in focusViews) {
            view.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                if (hasFocus) sfx?.focusMove()
            }
        }
    }

    private fun setupBackupSection() {
        binding.settingsBackupExport.setOnClickListener {
            sfx?.select()
            val name = "zelda64_saves_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.zip"
            exportBackupLauncher.launch(name)
        }
        binding.settingsBackupImport.setOnClickListener {
            sfx?.select()
            importBackupLauncher.launch(arrayOf("application/zip"))
        }
    }

    private fun runExportBackup(uri: Uri) {
        val installed = installedRepository.load().keys.toList()
        if (installed.isEmpty()) {
            showBackupResult(getString(R.string.backup_export_empty))
            return
        }
        val storage = Storage.getInstance(this)
        val saves = installed.associateWith { storage.saveFiles(it) }
        val version = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "?"
        binding.settingsBackupProgress.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val summary = try {
                contentResolver.openOutputStream(uri)?.use { out ->
                    SaveBackupManager.export(out, saves, version)
                } ?: SaveBackupManager.BackupSummary(0, 0, 0, listOf(getString(R.string.backup_error_stream)))
            } catch (e: Exception) {
                SaveBackupManager.BackupSummary(0, 0, 0, listOf(e.message ?: "error"))
            }
            withContext(Dispatchers.Main) {
                binding.settingsBackupProgress.visibility = View.GONE
                showBackupResult(getString(R.string.backup_export_summary, summary.hacks, summary.files))
            }
        }
    }

    private fun runImportBackup(uri: Uri) {
        val storage = Storage.getInstance(this)
        val resolver: (String, String) -> File? = { hackId, fileName ->
            when {
                fileName.startsWith("sram_") -> storage.sram(hackId)
                fileName.startsWith("state_") -> storage.state(hackId)
                else -> null
            }
        }
        binding.settingsBackupProgress.visibility = View.VISIBLE
        lifecycleScope.launch(Dispatchers.IO) {
            val summary = try {
                contentResolver.openInputStream(uri)?.use { input ->
                    SaveBackupManager.restore(input, resolver)
                } ?: SaveBackupManager.BackupSummary(0, 0, 0, listOf(getString(R.string.backup_error_stream)))
            } catch (e: Exception) {
                SaveBackupManager.BackupSummary(0, 0, 0, listOf(e.message ?: "error"))
            }
            withContext(Dispatchers.Main) {
                binding.settingsBackupProgress.visibility = View.GONE
                val message = buildString {
                    append(getString(R.string.backup_import_summary, summary.hacks, summary.files, summary.skipped))
                    if (summary.errors.isNotEmpty()) {
                        append("\n\n")
                        append(summary.errors.joinToString("\n"))
                    }
                }
                showBackupResult(message)
            }
        }
    }

    private fun showBackupResult(message: String) {
        SwitchDialog(this)
            .title(getString(R.string.backup_title))
            .message(message)
            .positiveButton(getString(android.R.string.ok))
            .show()
    }

    private fun setupImportSection() {
        binding.settingsImportButton.setOnClickListener {
            sfx?.select()
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
        SwitchDialog(this)
            .title(getString(R.string.settings_import_result_title))
            .message("$summary\n\n${lines.joinToString("\n")}")
            .positiveButton(getString(android.R.string.ok))
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
        SwitchDialog(this)
            .title(getString(R.string.settings_baserom_delete_confirm_title))
            .message(
                getString(
                    R.string.settings_baserom_delete_confirm_message,
                    rom.displayName, rom.crc32
                )
            )
            .positiveButton(getString(android.R.string.ok)) {
                viewModel.deleteBaseRom(rom.id)
                refreshBaseRomList()
            }
            .negativeButton(getString(android.R.string.cancel))
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
            sfx?.select()
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
        SwitchDialog(this)
            .title(getString(R.string.settings_catalog_remove_confirm_title))
            .message(url)
            .positiveButton(getString(android.R.string.ok)) {
                viewModel.removeCatalogUrl(url)
                refreshCatalogList()
            }
            .negativeButton(getString(android.R.string.cancel))
            .show()
    }

    private fun setupRandomizerSection() {
        val keyStore: OotrApiKeyStore = Zelda64PlayerApp.ootrApiKeyStore
        updateRandomizerStatus(keyStore)

        binding.settingsRandomizerSave.setOnClickListener {
            sfx?.select()
            val key = binding.settingsRandomizerInput.text.toString().trim()
            if (key.isEmpty()) {
                binding.settingsRandomizerStatus.setText(R.string.settings_randomizer_empty)
                return@setOnClickListener
            }
            keyStore.setKey(key)
            // Never keep the secret visible on screen after saving.
            binding.settingsRandomizerInput.text.clear()
            updateRandomizerStatus(keyStore)
        }

        binding.settingsRandomizerClear.setOnClickListener {
            sfx?.select()
            keyStore.clear()
            binding.settingsRandomizerInput.text.clear()
            updateRandomizerStatus(keyStore)
        }
    }

    private fun updateRandomizerStatus(keyStore: OotrApiKeyStore) {
        val res = if (keyStore.hasKey()) {
            R.string.settings_randomizer_status_configured
        } else {
            R.string.settings_randomizer_status_none
        }
        binding.settingsRandomizerStatus.setText(res)
    }

    /**
     * RetroAchievements section: master enable switch plus username/password
     * login. The password is used once for the credential exchange and never
     * persisted; only the issued token is stored (encrypted).
     */
    private fun setupRetroAchievementsSection() {
        val credentials = Zelda64PlayerApp.raCredentialStore
        val authService = Zelda64PlayerApp.raAuthService

        binding.settingsRaEnabledSwitch.isChecked =
            CorePrefs.getRetroAchievementsEnabled(this)
        binding.settingsRaEnabledSwitch.setOnCheckedChangeListener { _, checked ->
            CorePrefs.setRetroAchievementsEnabled(this, checked)
            updateRaStatus(credentials)
        }

        binding.settingsRaLogin.setOnClickListener {
            sfx?.select()
            val username = binding.settingsRaUsername.text.toString().trim()
            val password = binding.settingsRaPassword.text.toString()
            if (username.isEmpty() || password.isEmpty()) {
                binding.settingsRaStatus.setText(R.string.settings_ra_error_missing)
                return@setOnClickListener
            }
            binding.settingsRaLogin.isEnabled = false
            binding.settingsRaStatus.setText(R.string.settings_ra_logging_in)
            lifecycleScope.launch {
                val result = authService.login(username, password)
                binding.settingsRaLogin.isEnabled = true
                // Never keep the secret visible on screen after use.
                binding.settingsRaPassword.text.clear()
                result.fold(
                    onSuccess = {
                        binding.settingsRaUsername.text.clear()
                        updateRaStatus(credentials)
                    },
                    onFailure = { e ->
                        // Surface the sanitized server detail; do NOT call
                        // updateRaStatus here or it would overwrite the error.
                        val detail = e.message?.takeIf { it.isNotBlank() }
                        binding.settingsRaStatus.text = if (detail != null) {
                            getString(R.string.settings_ra_status_failed_detail, detail)
                        } else {
                            getString(R.string.settings_ra_status_failed)
                        }
                    }
                )
            }
        }

        binding.settingsRaLogout.setOnClickListener {
            sfx?.select()
            authService.logout()
            updateRaStatus(credentials)
        }

        updateRaStatus(credentials)
    }

    /** Refreshes the RA status line from stored credential state + toggle. */
    private fun updateRaStatus(credentials: RaCredentialStore) {
        val enabled = CorePrefs.getRetroAchievementsEnabled(this)
        binding.settingsRaStatus.setText(
            when {
                !enabled -> R.string.settings_ra_status_disabled
                credentials.hasCredentials() ->
                    R.string.settings_ra_status_logged_in
                else -> R.string.settings_ra_status_logged_out
            }
        )
    }

    private fun setupCoreSection() {
        updateCoreLabel()
        binding.settingsCoreButton.setOnClickListener { sfx?.select(); showCoreDialog() }
    }

    private fun updateCoreLabel() {
        val index = CorePrefs.getSelectedCoreIndex(this)
        val name = CorePrefs.options.getOrElse(index) { CorePrefs.options[0] }
        binding.settingsCoreCurrent.text = getString(R.string.settings_core_current, name)
    }

    private fun showCoreDialog() {
        val currentIndex = CorePrefs.getSelectedCoreIndex(this)
        SwitchDialog(this)
            .title(getString(R.string.settings_core_change))
            .singleChoice(CorePrefs.options.toList(), currentIndex) { which ->
                CorePrefs.setSelectedCoreIndex(this, which)
                updateCoreLabel()
            }
            .negativeButton(getString(android.R.string.cancel))
            .show()
    }

    private fun setupAboutSection() {
        val versionName = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrNull() ?: "?"
        binding.settingsAboutVersion.text =
            getString(R.string.settings_about_version, versionName)
        binding.settingsAboutRepo.setOnClickListener {
            sfx?.select()
            openLink("https://github.com/zonaro/zelda64player")
        }
        binding.settingsAboutCatalog.setOnClickListener {
            sfx?.select()
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
            holder.binding.itemBaseromDelete.setOnClickListener {
                runCatching { Zelda64PlayerApp.sfxManager }.getOrNull()?.select()
                onDelete(rom)
            }
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
            holder.binding.itemCatalogDelete.setOnClickListener {
                runCatching { Zelda64PlayerApp.sfxManager }.getOrNull()?.select()
                onDelete(url)
            }
        }

        override fun getItemCount() = items.size
    }
}
