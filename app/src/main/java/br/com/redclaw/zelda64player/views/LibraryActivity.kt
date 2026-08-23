package br.com.redclaw.zelda64player.views

import android.app.Service
import android.content.Intent
import android.hardware.input.InputManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsets
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.databinding.ActivityLibraryBinding
import br.com.redclaw.zelda64player.data.local.AppRepositories
import br.com.redclaw.zelda64player.data.local.InstalledHacksRepository
import br.com.redclaw.zelda64player.randomizer.repository.RandomizedSeedRepository
import br.com.redclaw.zelda64player.repositories.SaveBackupManager
import br.com.redclaw.zelda64player.repositories.Storage
import br.com.redclaw.zelda64player.repositories.uninstallHackFiles
import br.com.redclaw.zelda64player.settings.ui.SettingsActivity
import br.com.redclaw.zelda64player.shortcuts.GamePlayHistoryStore
import br.com.redclaw.zelda64player.shortcuts.GameShortcutsManager
import br.com.redclaw.zelda64player.store.ui.StoreActivity
import br.com.redclaw.zelda64player.viewmodels.LibraryMenuController
import br.com.redclaw.zelda64player.viewmodels.LibraryMenuHost
import br.com.redclaw.zelda64player.randomizer.ui.RandomizerActivity
import coil.load
import java.io.File

class LibraryActivity : AppCompatActivity(), LibraryMenuHost {
    private lateinit var binding: ActivityLibraryBinding

    /* Stateless: rebuilt from the source on every (re)create, so process
       death / configuration changes need no saved instance state. */
    private lateinit var items: List<HackLibraryEntry>

    /* Debounce for grid taps so a rapid double-tap fires only ONE launch
       intent (prevents two GameActivity instances racing the same install). */
    private var lastLaunchClickTime = 0L

    private lateinit var menuController: LibraryMenuController

    /* Entry whose save operation is pending in a SAF picker (one-shot). */
    private var pendingExportEntry: HackLibraryEntry? = null
    private var pendingImportEntry: HackLibraryEntry? = null

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val entry = pendingExportEntry ?: return@registerForActivityResult
        pendingExportEntry = null
        if (uri == null) return@registerForActivityResult
        val storage = Storage.getInstance(this)
        try {
            contentResolver.openOutputStream(uri)?.use { out ->
                SaveBackupManager.exportToStream(out, storage.sram(entry.id), storage.state(entry.id))
            }
            showToast(R.string.menu_export_success)
        } catch (e: Exception) {
            Log.e(TAG, "exportSaves failed for ${entry.id}", e)
            showToast(R.string.menu_export_failure)
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        val entry = pendingImportEntry ?: return@registerForActivityResult
        pendingImportEntry = null
        if (uri == null) return@registerForActivityResult
        val storage = Storage.getInstance(this)
        try {
            contentResolver.openInputStream(uri)?.use { input ->
                val summary = SaveBackupManager.importFromStream(
                    input, storage.sram(entry.id), storage.state(entry.id)
                )
                if (summary.ok) showToast(R.string.menu_import_success)
                else showToast(R.string.menu_import_failure)
            } ?: showToast(R.string.menu_import_failure)
        } catch (e: Exception) {
            Log.e(TAG, "importSaves failed for ${entry.id}", e)
            showToast(R.string.menu_import_failure)
        }
    }

    companion object {
        private const val TAG = "LibraryActivity"

        /** Minimum interval between grid launches, in milliseconds. */
        private const val LAUNCH_CLICK_DEBOUNCE_MS = 700L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.decorView.setOnApplyWindowInsetsListener { view, windowInsets ->
            view.post { immersive(window) }
            windowInsets
        }

        menuController = LibraryMenuController(this)

        items = InstalledLibrary.entries(this)

        setupGrid()
        binding.librarySettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.libraryStore.setOnClickListener {
            startActivity(Intent(this, StoreActivity::class.java))
        }
        binding.libraryRandomizer.setOnClickListener {
            startActivity(Intent(this, RandomizerActivity::class.java))
        }

        registerInputListener()
    }

    override fun onResume() {
        super.onResume()
        // Rebuild the list so hacks installed in the Store appear on return
        // without needing to recreate the activity.
        items = InstalledLibrary.entries(this)
        (binding.libraryGrid.adapter as? LibraryAdapter)?.update(items)
            ?: setupGrid()
        updateEmptyState()
        syncShortcuts()
    }

    /**
     * Keep the launcher's dynamic shortcuts in sync with the installed library
     * (and disable any stale pinned shortcuts) whenever the Library is shown.
     */
    private fun syncShortcuts() {
        val history = GamePlayHistoryStore(File(filesDir, "game_play_history.json"))
        GameShortcutsManager(this, history).sync(items)
    }

    /**
     * Offer to pin [entry] to the home screen. If the device/launcher does not
     * support pinning, explain it with a localized toast instead of failing
     * silently.
     */
    override fun pinShortcut(entry: HackLibraryEntry) {
        val history = GamePlayHistoryStore(File(filesDir, "game_play_history.json"))
        val ok = GameShortcutsManager(this, history).requestPin(entry)
        if (!ok) {
            Toast.makeText(this, R.string.shortcut_pin_unsupported, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Launch [hackId] the same way tapping its tile does, respecting the shared
     * debounce so a rapid repeat (e.g. physical A + tile tap) fires only once.
     */
    override fun launchGame(hackId: String) {
        val now = System.currentTimeMillis()
        if (now - lastLaunchClickTime < LAUNCH_CLICK_DEBOUNCE_MS) return
        lastLaunchClickTime = now
        val intent = Intent(this, GameActivity::class.java).apply {
            putExtra("hack_id", hackId)
        }
        startActivity(intent)
    }

    /** Open the RetroAchievements screen for [entry]. */
    override fun openAchievements(entry: HackLibraryEntry) {
        startActivity(
            Intent(this, AchievementsActivity::class.java).apply {
                putExtra(AchievementsActivity.EXTRA_HACK_ID, entry.id)
            }
        )
    }

    override fun requestExportSaves(entry: HackLibraryEntry) {
        val storage = Storage.getInstance(this)
        val hasSaves = storage.sram(entry.id).exists() || storage.state(entry.id).exists()
        if (!hasSaves) {
            showToast(R.string.menu_export_nothing)
            return
        }
        pendingExportEntry = entry
        val safeName = entry.title.replace(Regex("[^a-zA-Z0-9 _-]"), "_").trim()
        exportLauncher.launch("$safeName${getString(R.string.menu_export_filename_suffix)}")
    }

    override fun requestImportSaves(entry: HackLibraryEntry) {
        if (!Storage.getInstance(this).rom(entry.id).exists()) {
            showToast(R.string.menu_import_not_installed)
            return
        }
        pendingImportEntry = entry
        importLauncher.launch(arrayOf("application/zip"))
    }

    override fun confirmUninstall(entry: HackLibraryEntry) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.menu_uninstall_title, entry.title))
            .setMessage(R.string.menu_uninstall_message)
            .setPositiveButton(R.string.menu_uninstall_button) { _, _ -> performUninstall(entry) }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    override fun confirmDeleteSeed(entry: HackLibraryEntry) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.randomizer_delete_title, entry.title))
            .setMessage(R.string.randomizer_delete_message)
            .setPositiveButton(R.string.randomizer_delete_button) { _, _ -> performDeleteSeed(entry) }
            .setNegativeButton(R.string.dialog_cancel, null)
            .show()
    }

    /**
     * Delete the game's files, unmark it as installed and drop its play-history
     * entry, then rebuild the grid so the tile disappears. Orchestrated here
     * (the caller of the pure [uninstallHackFiles]); the file deletion itself is
     * a JVM-testable pure function.
     */
    private fun performUninstall(entry: HackLibraryEntry) {
        val storage = Storage.getInstance(this)
        uninstallHackFiles(File(storage.storagePath), entry.id)
        InstalledHacksRepository(File(filesDir, "installed_hacks.json")).unmarkInstalled(entry.id)
        GamePlayHistoryStore(File(filesDir, "game_play_history.json")).remove(entry.id)

        items = InstalledLibrary.entries(this)
        (binding.libraryGrid.adapter as? LibraryAdapter)?.update(items)
        updateEmptyState()
        syncShortcuts()
        showToast(R.string.menu_uninstall_done)
    }

    /**
     * Delete a generated randomizer seed: remove its index entry + ROM/SRAM/state
     * files via [RandomizedSeedRepository.remove] (which reuses the same
     * per-hack file cleanup as store-hack uninstall), then rebuild the grid.
     */
    private fun performDeleteSeed(entry: HackLibraryEntry) {
        val repository: RandomizedSeedRepository = AppRepositories.randomizedSeedRepository(this)
        val ok = repository.remove(entry.id)
        GamePlayHistoryStore(File(filesDir, "game_play_history.json")).remove(entry.id)

        items = InstalledLibrary.entries(this)
        (binding.libraryGrid.adapter as? LibraryAdapter)?.update(items)
        updateEmptyState()
        syncShortcuts()
        showToast(if (ok) R.string.randomizer_delete_done else R.string.randomizer_delete_failed)
    }

    override fun showToast(resId: Int) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show()
    }

    override fun context(): android.app.Activity = this

    private fun setupGrid() {
        val spanCount = resources.getInteger(R.integer.library_span_count)
        binding.libraryGrid.layoutManager = GridLayoutManager(this, spanCount)
        binding.libraryGrid.adapter = LibraryAdapter(
            items,
            onItemClick = { item -> launchGame(item.id) },
            onItemLongClick = { item -> menuController.openMenu(item) }
        )
        updateEmptyState()
    }

    private fun updateEmptyState() {
        val isEmpty = items.isEmpty()
        binding.libraryEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.libraryGrid.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    /**
     * Route physical gamepad keys when no menu is open: A launches the focused
     * tile, SELECT/X/Y open the context menu for the focused tile. While the
     * menu is showing, its own decor-view key listener handles keys, so we let
     * those events fall through to the system.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (menuController.isMenuShowing()) {
            return super.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            val focused = currentFocus
            val entry = focused?.tag as? HackLibraryEntry
            if (entry != null) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_BUTTON_A -> {
                        focused.performClick()
                        return true
                    }
                    KeyEvent.KEYCODE_BUTTON_SELECT,
                    KeyEvent.KEYCODE_BUTTON_X,
                    KeyEvent.KEYCODE_BUTTON_Y -> {
                        menuController.openMenu(entry)
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun registerInputListener() {
        val inputManager = getSystemService(Service.INPUT_SERVICE) as InputManager
        inputManager.registerInputDeviceListener(
            object : InputManager.InputDeviceListener {
                override fun onInputDeviceAdded(deviceId: Int) {
                    menuController.refreshBadges()
                }
                override fun onInputDeviceRemoved(deviceId: Int) {
                    menuController.refreshBadges()
                }
                override fun onInputDeviceChanged(deviceId: Int) {
                    menuController.refreshBadges()
                }
            },
            null
        )
    }

    /** Hide the system bars when the config permits it (mirrors GameActivity). */
    @Suppress("DEPRECATION")
    private fun immersive(window: Window) {
        if (!resources.getBoolean(R.bool.config_fullscreen))
            return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                hide(WindowInsets.Type.systemBars())
                systemBarsBehavior =
                    android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN
        }
    }

    private class LibraryAdapter(
        private var items: List<HackLibraryEntry>,
        private val onItemClick: (HackLibraryEntry) -> Unit,
        private val onItemLongClick: (HackLibraryEntry) -> Unit
    ) : RecyclerView.Adapter<LibraryAdapter.ViewHolder>() {

        fun update(newItems: List<HackLibraryEntry>) {
            items = newItems
            notifyDataSetChanged()
        }

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.tile_title)
            val cover: ImageView = view.findViewById(R.id.tile_cover)
            val badge: TextView = view.findViewById(R.id.tile_badge)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.library_tile, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.title.text = item.title
            if (item.badgeText != null) {
                holder.badge.visibility = View.VISIBLE
                holder.badge.text = item.badgeText
            } else {
                holder.badge.visibility = View.GONE
            }
            if (item.coverUrl != null) {
                holder.cover.load(item.coverUrl) {
                    placeholder(R.drawable.placeholder_cover)
                    error(R.drawable.placeholder_cover)
                    crossfade(true)
                }
            } else {
                holder.cover.setImageResource(R.drawable.placeholder_cover)
            }
            /* Tag the tile root with its entry so physical-key handling in
               dispatchKeyEvent can identify the focused tile. */
            holder.itemView.tag = item
            holder.itemView.setOnClickListener { onItemClick(item) }
            holder.itemView.setOnLongClickListener {
                onItemLongClick(item)
                true
            }
            /* Overflow button opens the context menu for this tile. It is NOT
               focusable (so D-pad grid navigation is undisturbed) and consumes
               its own click so the tile's click never fires. */
            holder.itemView.findViewById<View>(R.id.tile_overflow)
                .setOnClickListener { onItemLongClick(item) }
        }

        override fun getItemCount() = items.size
    }
}
