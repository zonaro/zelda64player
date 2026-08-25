package br.com.redclaw.zelda64player.settings.ui

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
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
import br.com.redclaw.zelda64player.capture.CapturePreferences
import br.com.redclaw.zelda64player.repositories.Storage
import br.com.redclaw.zelda64player.retroachievements.auth.RaCredentialStore
import br.com.redclaw.zelda64player.settings.SettingsViewModel
import br.com.redclaw.zelda64player.store.CatalogFetcher
import br.com.redclaw.zelda64player.ui.switchui.SwitchDialog
import br.com.redclaw.zelda64player.ui.switchui.SwitchImmersive
import br.com.redclaw.zelda64player.utils.CorePrefs
import br.com.redclaw.zelda64player.utils.LanguageManager
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

    /** Portrait-only conventional navigation drawer and its tap-to-dismiss scrim. */
    private var settingsDrawerScrim: View? = null
    private var isSettingsDrawerOpen = false

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
        supportActionBar?.setTitle(R.string.settings_title)
        setupSwitchNavigation()

        viewModel = SettingsViewModel(application)

        setupImportSection()
        setupBaseRomList()
        setupCatalogSection()
        setupRetroAchievementsSection()
        setupCoreSection()
        setupBackupSection()
        setupLanguageSection()
        setupAboutSection()
        setupCaptureSection()
        wireSettingsSfx()
        observeImport()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) SwitchImmersive.enterFullscreen(this)
    }

    /**
     * Matches the System Settings information architecture: landscape keeps the
     * categories permanently visible on the left, while a portrait phone gets a
     * familiar hamburger drawer. The same navigation view is moved at runtime,
     * so both modes always expose exactly the same localized categories.
     */
    private fun setupSwitchNavigation() {
        val navigationTargets = listOf(
            binding.settingsNavImport to binding.settingsSectionImport,
            binding.settingsNavBaseroms to binding.settingsSectionBaseroms,
            binding.settingsNavCatalog to binding.settingsSectionCatalog,
            binding.settingsNavRa to binding.settingsSectionRa,
            binding.settingsNavCore to binding.settingsSectionCore,
            binding.settingsNavBackup to binding.settingsSectionBackup,
            binding.settingsNavLanguage to binding.settingsSectionLanguage,
            binding.settingsNavAbout to binding.settingsSectionAbout,
            binding.settingsNavCapture to binding.settingsSectionCapture
        )

        navigationTargets.forEach { (row, target) ->
            row.setOnClickListener {
                sfx?.select()
                selectSettingsSection(row)
                // Delay until async section content (e.g. the base-ROM RecyclerView)
                // has laid out AND settled; scrolling earlier gets reset to top when
                // that list populates and changes the layout height.
                binding.settingsScroll.postDelayed({
                    binding.settingsScroll.scrollTo(0, target.top)
                }, 600)
                if (isPortraitSettings()) closeSettingsDrawer()
            }
            row.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) sfx?.focusMove()
            }
        }
        selectSettingsSection(binding.settingsNavImport)

        if (isPortraitSettings()) {
            installPortraitNavigationDrawer()
        } else {
            // A non-interactive settings glyph makes the toolbar match the
            // System Settings header without presenting a misleading back action.
            binding.settingsToolbar.navigationIcon = AppCompatResources.getDrawable(
                this, R.drawable.ic_settings
            )
            binding.settingsToolbar.navigationContentDescription = null
            binding.settingsToolbar.setNavigationOnClickListener(null)
        }
    }

    private fun isPortraitSettings(): Boolean =
        resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    private fun selectSettingsSection(selectedRow: View) {
        val rows = listOf(
            binding.settingsNavImport,
            binding.settingsNavBaseroms,
            binding.settingsNavCatalog,
            binding.settingsNavRa,
            binding.settingsNavCore,
            binding.settingsNavBackup,
            binding.settingsNavLanguage,
            binding.settingsNavAbout,
            binding.settingsNavCapture
        )
        rows.forEach { it.isSelected = it === selectedRow }
    }

    private fun installPortraitNavigationDrawer() {
        val navigation = binding.settingsNavigation
        binding.settingsBody.removeView(navigation)
        binding.settingsNavigationDivider.visibility = View.GONE

        val drawerWidth = minOf(
            (resources.displayMetrics.widthPixels * 0.86f).toInt(),
            (320 * resources.displayMetrics.density).toInt()
        )
        val scrim = View(this).apply {
            background = ColorDrawable(Color.TRANSPARENT)
            contentDescription = getString(R.string.settings_close_navigation)
            isClickable = true
            isFocusable = true
            visibility = View.GONE
            setOnClickListener { closeSettingsDrawer() }
        }
        settingsDrawerScrim = scrim
        binding.settingsRoot.addView(
            scrim,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        navigation.visibility = View.GONE
        navigation.elevation = 12f * resources.displayMetrics.density
        binding.settingsRoot.addView(
            navigation,
            FrameLayout.LayoutParams(
                drawerWidth,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.START
            )
        )
        binding.settingsToolbar.navigationIcon = AppCompatResources.getDrawable(this, R.drawable.ic_menu)
        binding.settingsToolbar.navigationContentDescription =
            getString(R.string.settings_open_navigation)
        binding.settingsToolbar.setNavigationOnClickListener { openSettingsDrawer() }
    }

    private fun openSettingsDrawer() {
        if (!isPortraitSettings() || isSettingsDrawerOpen) return
        val navigation = binding.settingsNavigation
        val scrim = settingsDrawerScrim ?: return
        isSettingsDrawerOpen = true
        scrim.apply {
            setBackgroundColor(getColor(R.color.switch_scrim))
            alpha = 0f
            visibility = View.VISIBLE
            animate().alpha(1f).setDuration(220L).start()
        }
        navigation.apply {
            visibility = View.VISIBLE
            translationX = if (width > 0) {
                -width.toFloat()
            } else {
                -resources.displayMetrics.widthPixels.toFloat()
            }
            animate().translationX(0f).setDuration(220L).start()
            binding.settingsNavImport.requestFocus()
        }
        sfx?.panelOpen()
    }

    private fun closeSettingsDrawer() {
        if (!isSettingsDrawerOpen) return
        val navigation = binding.settingsNavigation
        val scrim = settingsDrawerScrim ?: return
        isSettingsDrawerOpen = false
        navigation.animate()
            .translationX(-navigation.width.toFloat())
            .setDuration(180L)
            .withEndAction { navigation.visibility = View.GONE }
            .start()
        scrim.animate()
            .alpha(0f)
            .setDuration(180L)
            .withEndAction { scrim.visibility = View.GONE }
            .start()
        sfx?.panelClose()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (isSettingsDrawerOpen) {
            closeSettingsDrawer()
        } else {
            super.onBackPressed()
        }
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
            binding.settingsRaLogin,
            binding.settingsRaLogout,
            binding.settingsCoreButton,
            binding.settingsLanguageButton,
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

    private fun setupLanguageSection() {
        updateLanguageLabel()
        binding.settingsLanguageButton.setOnClickListener {
            sfx?.select()
            showLanguageDialog()
        }
    }

    private fun updateLanguageLabel() {
        val code = LanguageManager.getLanguage(this)
        binding.settingsLanguageCurrent.text = LanguageManager.labelFor(this, code)
    }

    private fun showLanguageDialog() {
        val codes = LanguageManager.CODES
        val currentIndex = codes.indexOf(LanguageManager.getLanguage(this)).coerceAtLeast(0)
        val labels = codes.map { LanguageManager.labelFor(this, it) }
        SwitchDialog(this)
            .title(getString(R.string.settings_language_title))
            .singleChoice(labels, currentIndex) { which ->
                LanguageManager.setLanguage(this, codes[which])
                updateLanguageLabel()
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

    /**
     * Captura section: a single toggle controlling whether screen recordings
     * include the on-screen control overlays. Screenshots always capture both
     * variants regardless of this setting. Persisted via [CapturePreferences].
     */
    private fun setupCaptureSection() {
        binding.settingsCaptureIncludeOverlaySwitch.isChecked =
            CapturePreferences.getIncludeOverlay(this)
        binding.settingsCaptureIncludeOverlaySwitch.setOnCheckedChangeListener { _, checked ->
            CapturePreferences.setIncludeOverlay(this, checked)
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
