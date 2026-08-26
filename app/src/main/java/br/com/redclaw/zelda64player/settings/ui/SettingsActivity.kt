package br.com.redclaw.zelda64player.settings.ui

import android.accounts.AccountManager
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
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
import br.com.redclaw.zelda64player.drive.BackupCategory
import br.com.redclaw.zelda64player.drive.ConflictResolveActivity
import br.com.redclaw.zelda64player.drive.ConflictStore
import br.com.redclaw.zelda64player.drive.GoogleDriveAuth
import br.com.redclaw.zelda64player.drive.GoogleDriveBackup
import br.com.redclaw.zelda64player.drive.GoogleDriveBackupWorker
import br.com.redclaw.zelda64player.repositories.Storage
import br.com.redclaw.zelda64player.retroachievements.auth.RaCredentialStore
import br.com.redclaw.zelda64player.views.InstalledLibrary
import br.com.redclaw.zelda64player.settings.SettingsViewModel
import br.com.redclaw.zelda64player.store.CatalogFetcher
import br.com.redclaw.zelda64player.ui.switchui.SwitchDialog
import br.com.redclaw.zelda64player.ui.switchui.SwitchImmersive
import br.com.redclaw.zelda64player.ui.switchui.AccentManager
import br.com.redclaw.zelda64player.utils.CorePrefs
import br.com.redclaw.zelda64player.utils.LanguageManager
import com.google.android.gms.auth.UserRecoverableAuthException
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
    private var settingsDrawer: View? = null
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

    /** Google account chooser for the Drive backup feature. */
    private val pickDriveAccountLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
        val name = result.data?.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)
        if (name != null) {
            CorePrefs.setGdriveAccountName(this, name)
            updateGdriveAccountUi()
        }
    }

    /** OAuth consent recovery intent launched when the token needs user approval. */
    private val driveConsentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* User can tap "Back up now" again after granting consent. */ }

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
        applyDynamicAccentToSwitches()

        viewModel = SettingsViewModel(application)

        setupImportSection()
        setupBaseRomList()
        setupCatalogSection()
        setupRetroAchievementsSection()
        setupCoreSection()
        setupBackupSection()
        setupGdriveSection()
        setupCloudSyncSection()
        setupLanguageSection()
        setupAboutSection()
        wireSettingsSfx()
        observeImport()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) SwitchImmersive.enterFullscreen(this)
    }

    override fun onResume() {
        super.onResume()
        // Refresh the cloud-sync status (e.g. after resolving a conflict in the
        // resolver activity) so the pending-conflict count stays accurate.
        updateCloudSyncStatus()
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
            binding.settingsNavCloudsync to binding.settingsSectionCloudsync,
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
            binding.settingsNavCloudsync,
            binding.settingsNavLanguage,
            binding.settingsNavAbout,
            binding.settingsNavCapture
        )
        val accentColor = AccentManager.getAccentColor(this)
        rows.forEach { row ->
            row.isSelected = row === selectedRow
            // Apply dynamic accent color to selected row background
            if (row === selectedRow) {
                row.background = createSelectedNavBackground(accentColor)
            } else {
                row.background = createDefaultNavBackground()
            }
        }
    }

    /** Creates a background drawable for the selected navigation row (left accent bar). */
    private fun createSelectedNavBackground(accentColor: Int): android.graphics.drawable.LayerDrawable {
        val accentBar = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(accentColor)
        }
        val transparent = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(android.graphics.Color.TRANSPARENT)
        }
        val layerDrawable = android.graphics.drawable.LayerDrawable(arrayOf(transparent, accentBar))
        // Inset the accent bar to be a 3dp wide bar on the left
        layerDrawable.setLayerInset(1, 0, 0, 0, 0)
        return layerDrawable
    }

    /** Creates a default transparent background for non-selected navigation rows. */
    private fun createDefaultNavBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(android.graphics.Color.TRANSPARENT)
        }
    }

    /** Applies the dynamic accent color to all Switch widgets in the settings. */
    private fun applyDynamicAccentToSwitches() {
        val accentColor = AccentManager.getAccentColor(this)
        val switches = listOf(
            binding.settingsRaEnabledSwitch,
            binding.settingsGdriveEnabled,
            binding.settingsGdriveSaves,
            binding.settingsGdriveImages,
            binding.settingsGdriveVideos,
            binding.settingsGdriveAuto,
            binding.settingsCloudsyncEnabled,
            binding.settingsCloudsyncWifi,
            binding.settingsCloudsyncNotify
        )
        switches.forEach { switch ->
            // Create dynamic thumb and track color state lists
            val thumbStateList = android.content.res.ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf()
                ),
                intArrayOf(accentColor, ContextCompat.getColor(this, R.color.switch_text_primary))
            )
            val trackStateList = android.content.res.ColorStateList(
                arrayOf(
                    intArrayOf(android.R.attr.state_checked),
                    intArrayOf()
                ),
                intArrayOf(accentColor, ContextCompat.getColor(this, R.color.switch_text_secondary))
            )
            switch.thumbTintList = thumbStateList
            switch.trackTintList = trackStateList
        }
    }

    private fun installPortraitNavigationDrawer() {
        val navigation = binding.settingsNavigation
        // The navigation itself is inside a ScrollView. Move that container to
        // the overlay root so the drawer keeps its scrolling behavior and the
        // child is detached before it is re-parented.
        val drawer = navigation.parent as? ViewGroup ?: return
        binding.settingsBody.removeView(drawer)
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
        drawer.visibility = View.GONE
        drawer.elevation = 12f * resources.displayMetrics.density
        settingsDrawer = drawer
        binding.settingsRoot.addView(
            drawer,
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
        val drawer = settingsDrawer ?: return
        val scrim = settingsDrawerScrim ?: return
        isSettingsDrawerOpen = true
        scrim.apply {
            setBackgroundColor(getColor(R.color.switch_scrim))
            alpha = 0f
            visibility = View.VISIBLE
            animate().alpha(1f).setDuration(220L).start()
        }
        drawer.apply {
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
        val drawer = settingsDrawer ?: return
        val scrim = settingsDrawerScrim ?: return
        isSettingsDrawerOpen = false
        drawer.animate()
            .translationX(-drawer.width.toFloat())
            .setDuration(180L)
            .withEndAction { drawer.visibility = View.GONE }
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
            binding.settingsGdriveConnect,
            binding.settingsGdriveBackupNow,
            binding.settingsGdriveView,
            binding.settingsGdriveFrequency,
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

    // ---- Google Drive cloud backup section ----

    private fun setupGdriveSection() {
        binding.settingsGdriveEnabled.isChecked = CorePrefs.getGdriveEnabled(this)
        binding.settingsGdriveEnabled.setOnCheckedChangeListener { _, checked ->
            CorePrefs.setGdriveEnabled(this, checked)
            updateGdriveEnabledUi()
        }
        binding.settingsGdriveSaves.isChecked = CorePrefs.getGdriveBackupSaves(this)
        binding.settingsGdriveSaves.setOnCheckedChangeListener { _, c ->
            CorePrefs.setGdriveBackupSaves(this, c)
        }
        binding.settingsGdriveImages.isChecked = CorePrefs.getGdriveBackupImages(this)
        binding.settingsGdriveImages.setOnCheckedChangeListener { _, c ->
            CorePrefs.setGdriveBackupImages(this, c)
        }
        binding.settingsGdriveVideos.isChecked = CorePrefs.getGdriveBackupVideos(this)
        binding.settingsGdriveVideos.setOnCheckedChangeListener { _, c ->
            CorePrefs.setGdriveBackupVideos(this, c)
        }
        binding.settingsGdriveAuto.isChecked = CorePrefs.getGdriveAutoBackup(this)
        binding.settingsGdriveAuto.setOnCheckedChangeListener { _, c ->
            CorePrefs.setGdriveAutoBackup(this, c)
        }

        binding.settingsGdriveConnect.setOnClickListener {
            sfx?.select()
            val intent = GoogleDriveAuth.accountPickerIntent()
            if (intent != null) {
                pickDriveAccountLauncher.launch(intent)
            } else {
                showGdriveDialog(getString(R.string.gdrive_need_account))
            }
        }
        binding.settingsGdriveBackupNow.setOnClickListener { startManualDriveBackup() }
        binding.settingsGdriveView.setOnClickListener { viewDriveBackups() }
        binding.settingsGdriveFrequency.setOnClickListener {
            sfx?.select()
            showFrequencyDialog()
        }

        updateGdriveAccountUi()
        updateGdriveStatus()
        updateGdriveEnabledUi()
        updateGdriveFrequencyUi()
    }

    /** Enable/disable the dependent controls based on the master switch. */
    private fun updateGdriveEnabledUi() {
        val enabled = CorePrefs.getGdriveEnabled(this)
        binding.settingsGdriveGroup.isEnabled = enabled
        for (i in 0 until binding.settingsGdriveGroup.childCount) {
            binding.settingsGdriveGroup.getChildAt(i).isEnabled = enabled
        }
    }

    /** Reflect the connected account name (or the "none" hint) in the status. */
    private fun updateGdriveAccountUi() {
        val name = CorePrefs.getGdriveAccountName(this)
        binding.settingsGdriveAccount.text = if (name != null) {
            getString(R.string.gdrive_connected, name)
        } else {
            getString(R.string.gdrive_not_connected)
        }
    }

    /** Show the last successful backup time, or "Nunca" when never run. */
    private fun updateGdriveStatus() {
        val last = CorePrefs.getGdriveLastBackup(this)
        binding.settingsGdriveStatus.text = if (last > 0) {
            getString(R.string.gdrive_last_backup, formatBackupDate(last))
        } else {
            getString(R.string.gdrive_last_backup_never)
        }
    }

    private fun updateGdriveFrequencyUi() {
        binding.settingsGdriveFrequency.text = frequencyLabel(
            CorePrefs.getGdriveBackupFrequency(this)
        )
    }

    private fun frequencyLabel(value: String): String = when (value) {
        CorePrefs.GDRIVE_FREQ_WEEKLY -> getString(R.string.gdrive_frequency_weekly)
        CorePrefs.GDRIVE_FREQ_MANUAL -> getString(R.string.gdrive_frequency_manual)
        else -> getString(R.string.gdrive_frequency_daily)
    }

    /** "Back up now": runs the same orchestrator as the periodic worker, inline,
     *  so the progress bar can reflect real upload progress. */
    private fun startManualDriveBackup() {
        sfx?.select()
        if (!CorePrefs.getGdriveEnabled(this)) {
            showGdriveDialog(getString(R.string.gdrive_need_enable))
            return
        }
        val accountName = CorePrefs.getGdriveAccountName(this)
        if (accountName == null) {
            showGdriveDialog(getString(R.string.gdrive_need_account))
            return
        }
        val categories = gdriveCategories()
        if (categories.isEmpty()) {
            showGdriveDialog(getString(R.string.gdrive_backup_none))
            return
        }
        val storage = Storage.getInstance(this)
        val hackIds = InstalledLibrary.entries(this).map { it.id }
        val since = CorePrefs.getGdriveLastBackup(this)

        binding.settingsGdriveProgress.visibility = View.VISIBLE
        binding.settingsGdriveProgress.isIndeterminate = true
        binding.settingsGdriveBackupNow.isEnabled = false
        binding.settingsGdriveView.isEnabled = false

        lifecycleScope.launch(Dispatchers.IO) {
            val summary = try {
                GoogleDriveBackup.run(
                    context = this@SettingsActivity,
                    accountName = accountName,
                    saveDir = File(storage.storagePath),
                    galleryDir = storage.galleryDir(),
                    hackIds = hackIds,
                    categories = categories,
                    sinceMillis = since,
                    onItemProgress = { done, total ->
                        runOnUiThread {
                            binding.settingsGdriveProgress.isIndeterminate = false
                            binding.settingsGdriveProgress.max = total.coerceAtLeast(1)
                            binding.settingsGdriveProgress.progress = done
                        }
                    }
                )
            } catch (e: UserRecoverableAuthException) {
                runOnUiThread { driveConsentLauncher.launch(e.intent) }
                null
            } catch (e: Exception) {
                runOnUiThread {
                    showGdriveDialog(getString(R.string.gdrive_backup_failed, e.message ?: "error"))
                }
                null
            }
            withContext(Dispatchers.Main) {
                binding.settingsGdriveProgress.visibility = View.GONE
                binding.settingsGdriveBackupNow.isEnabled = true
                binding.settingsGdriveView.isEnabled = true
                if (summary != null) {
                    CorePrefs.setGdriveLastBackup(this@SettingsActivity, System.currentTimeMillis())
                    updateGdriveStatus()
                    val msg = if (summary.uploaded == 0 && summary.deleted == 0) {
                        getString(R.string.gdrive_backup_none)
                    } else {
                        getString(R.string.gdrive_backup_summary, summary.uploaded)
                    }
                    showGdriveDialog(msg)
                }
            }
        }
    }

    /** Open the app backup folder in the Drive app / browser. */
    private fun viewDriveBackups() {
        sfx?.select()
        val accountName = CorePrefs.getGdriveAccountName(this)
        if (accountName == null) {
            showGdriveDialog(getString(R.string.gdrive_need_account))
            return
        }
        val service = GoogleDriveBackup.buildService(this, accountName)
        if (service == null) {
            showGdriveDialog(getString(R.string.gdrive_need_account))
            return
        }
        binding.settingsGdriveProgress.visibility = View.VISIBLE
        binding.settingsGdriveProgress.isIndeterminate = true
        lifecycleScope.launch(Dispatchers.IO) {
            val link = try {
                val id = service.ensureAppFolder { folderId ->
                    CorePrefs.setGdriveFolderId(this@SettingsActivity, folderId)
                }
                service.folderLink(id)
            } catch (e: UserRecoverableAuthException) {
                runOnUiThread { driveConsentLauncher.launch(e.intent) }
                null
            } catch (e: Exception) {
                runOnUiThread { showGdriveDialog(getString(R.string.gdrive_open_failed)) }
                null
            }
            withContext(Dispatchers.Main) {
                binding.settingsGdriveProgress.visibility = View.GONE
                if (link != null) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))
                    } catch (_: Exception) {
                        showGdriveDialog(getString(R.string.gdrive_open_failed))
                    }
                }
            }
        }
    }

    private fun gdriveCategories(): Set<BackupCategory> {
        val set = mutableSetOf<BackupCategory>()
        if (CorePrefs.getGdriveBackupSaves(this)) set.add(BackupCategory.SAVES)
        if (CorePrefs.getGdriveBackupImages(this)) set.add(BackupCategory.IMAGES)
        if (CorePrefs.getGdriveBackupVideos(this)) set.add(BackupCategory.VIDEOS)
        return set
    }

    private fun showFrequencyDialog() {
        val values = listOf(
            CorePrefs.GDRIVE_FREQ_DAILY,
            CorePrefs.GDRIVE_FREQ_WEEKLY,
            CorePrefs.GDRIVE_FREQ_MANUAL
        )
        val labels = values.map { frequencyLabel(it) }
        val current = values.indexOf(CorePrefs.getGdriveBackupFrequency(this)).coerceAtLeast(0)
        SwitchDialog(this)
            .title(getString(R.string.gdrive_frequency))
            .singleChoice(labels, current) { which ->
                CorePrefs.setGdriveBackupFrequency(this, values[which])
                updateGdriveFrequencyUi()
            }
            .negativeButton(getString(android.R.string.cancel))
            .show()
    }

    private fun showGdriveDialog(message: String) {
        SwitchDialog(this)
            .title(getString(R.string.gdrive_subtitle))
            .message(message)
            .positiveButton(getString(android.R.string.ok))
            .show()
    }

    // ---- Cloud Sync (automatic, per-save) section ----

    private fun setupCloudSyncSection() {
        binding.settingsCloudsyncEnabled.isChecked = CorePrefs.getCloudSyncEnabled(this)
        binding.settingsCloudsyncEnabled.setOnCheckedChangeListener { _, checked ->
            CorePrefs.setCloudSyncEnabled(this, checked)
            updateCloudSyncStatus()
        }
        binding.settingsCloudsyncWifi.isChecked = CorePrefs.getCloudSyncWifiOnly(this)
        binding.settingsCloudsyncWifi.setOnCheckedChangeListener { _, checked ->
            CorePrefs.setCloudSyncWifiOnly(this, checked)
        }
        binding.settingsCloudsyncNotify.isChecked = CorePrefs.getCloudSyncNotifications(this)
        binding.settingsCloudsyncNotify.setOnCheckedChangeListener { _, checked ->
            CorePrefs.setCloudSyncNotifications(this, checked)
        }

        binding.settingsCloudsyncViewConflicts.setOnClickListener {
            sfx?.select()
            startActivity(Intent(this, ConflictResolveActivity::class.java))
        }

        updateCloudSyncStatus()
    }

    /** Reflect sync state: master switch, connected account, last sync time and
     *  pending conflict count. Also toggles the "view conflicts" button. */
    private fun updateCloudSyncStatus() {
        val enabled = CorePrefs.getCloudSyncEnabled(this)
        val account = CorePrefs.getGdriveAccountName(this)
        val conflicts = ConflictStore(this).count()

        binding.settingsCloudsyncViewConflicts.isEnabled = conflicts > 0

        val text = when {
            !enabled -> getString(R.string.cloudsync_status_disabled)
            account == null -> getString(R.string.cloudsync_status_no_account)
            conflicts > 0 -> getString(R.string.cloudsync_conflicts_count, conflicts)
            else -> {
                val last = CorePrefs.getCloudSyncLastSync(this)
                if (last > 0) {
                    getString(R.string.cloudsync_status_last, formatBackupDate(last))
                } else {
                    getString(R.string.cloudsync_status_never)
                }
            }
        }
        binding.settingsCloudsyncStatus.text = text
    }

    private fun formatBackupDate(epoch: Long): String =
        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US).format(Date(epoch))

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
