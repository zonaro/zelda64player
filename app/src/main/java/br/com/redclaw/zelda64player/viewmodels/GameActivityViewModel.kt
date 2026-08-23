package br.com.redclaw.zelda64player.viewmodels

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.ColorStateList
import android.os.Build
import android.util.Log
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import java.io.File
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.gamepad.ButtonStick
import br.com.redclaw.zelda64player.gamepad.ButtonStickMode
import br.com.redclaw.zelda64player.gamepad.DoubleTapContainer
import br.com.redclaw.zelda64player.gamepad.FloatingJoystick
import br.com.redclaw.zelda64player.gamepad.GamePad
import br.com.redclaw.zelda64player.gamepad.GamePadConfig
import br.com.redclaw.zelda64player.input.ControllerInput
import br.com.redclaw.zelda64player.input.InputMapper
import br.com.redclaw.zelda64player.data.local.MergedCatalogRepository
import br.com.redclaw.zelda64player.ocarina.OcarinaGame
import br.com.redclaw.zelda64player.ocarina.OcarinaMacroCallbacks
import br.com.redclaw.zelda64player.ocarina.OcarinaMacroPlayer
import br.com.redclaw.zelda64player.ocarina.OcarinaSong
import br.com.redclaw.zelda64player.ocarina.OcarinaSongCatalog
import br.com.redclaw.zelda64player.ocarina.ui.OcarinaHudView
import br.com.redclaw.zelda64player.patcher.n64.RomHeader
import br.com.redclaw.zelda64player.repositories.Storage
import br.com.redclaw.zelda64player.retroachievements.api.RaHttpClient
import br.com.redclaw.zelda64player.retroachievements.api.RaUserAgent
import br.com.redclaw.zelda64player.retroachievements.auth.RaCredentialStore
import br.com.redclaw.zelda64player.retroachievements.data.RaInstallMetadataStore
import br.com.redclaw.zelda64player.retroachievements.session.RaSessionManager
import br.com.redclaw.zelda64player.retroview.RetroView
import br.com.redclaw.zelda64player.utils.CorePrefs
import br.com.redclaw.zelda64player.utils.MenuActionItem
import br.com.redclaw.zelda64player.utils.MenuGridBuilder
import br.com.redclaw.zelda64player.utils.MenuSection
import br.com.redclaw.zelda64player.utils.MenuToggleEntry
import br.com.redclaw.zelda64player.utils.RetroViewUtils
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.LibretroDroid
import io.reactivex.disposables.CompositeDisposable
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class GameActivityViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "GameLaunch"
    }

    private val resources = application.resources
    private val appContext = application.applicationContext

    var retroView: RetroView? = null
    private var retroViewUtils: RetroViewUtils? = null

    // ---- RetroAchievements session ----
    /** RA session for the running game; null until first start. Torn down
     *  before the native core dies (see stopRaSession, called from
     *  GameActivity.onDestroy ahead of super.onDestroy()). */
    private var raSession: RaSessionManager? = null
    private var raFrameCollector: Job? = null

    private var gamePads: List<GamePad> = emptyList()
    private var floatingJoystick: FloatingJoystick? = null
    private var buttonStick: ButtonStick? = null
    private var buttonStickMode: ButtonStickMode = ButtonStickMode.C_RIGHT
    /** Last of C-Right/C-Left/C-Down pressed elsewhere on screen; used by [ButtonStickMode.AUTO].
     *  Also drives the physical right stick's target when it's standing in for a button, since
     *  it shares the same mode as the touch Button Stick (see [ControllerInput.buttonStickTargetKeyCode]). */
    private var lastCButtonKeyCode: Int = KeyEvent.KEYCODE_BUTTON_R1

    /** True while Z is held from a double-tap on the analog stick (see [onStickDoubleTap]). */
    private var zHeldViaDoubleTap = false
    private var autoZEnabled = true

    private var menuDialog: AlertDialog? = null
    /** Reference to the view built in [prepareMenu], kept so [showMenu] can measure
     *  its natural height before the dialog is shown and size the window to wrap
     *  (or scroll) it. */
    private var menuView: View? = null
    private var buttonStickDialog: AlertDialog? = null
    private var sensitivityDialog: AlertDialog? = null
    private var activityContext: Activity? = null

    // ---- Auto-Ocarina feature ----
    /** Detected Ocarina game for the running ROM, or null when unsupported. */
    private var ocarinaGame: OcarinaGame? = null
    /** hackId of the currently running hack (for custom-song lookup). */
    private var currentHackId: String? = null
    /** Active macro player, or null when idle. */
    private var ocarinaPlayer: OcarinaMacroPlayer? = null
    /** HUD view attached by GameActivity (null until attached). */
    private var ocarinaHud: OcarinaHudView? = null
    /** Song-list dialog shown from the in-game menu. */
    private var ocarinaSongDialog: AlertDialog? = null
    /** Songs currently displayed in the song-list dialog (for physical-key select). */
    private var ocarinaCurrentSongs: List<OcarinaSong>? = null
    /** Physical-controller key handler for the song-list dialog. */
    private val menuKeyListenerOcarina =
        View.OnKeyListener { _, keyCode, event -> handleOcarinaKey(keyCode, event) }

    /** Live references to toggle item cells, rebuilt every [prepareMenu]. */
    private val toggleEntries = mutableListOf<MenuToggleEntry>()

    /** Live references to badge TextViews, rebuilt every [prepareMenu]. */
    private val badgeViews = mutableListOf<TextView>()

    /** Last-built menu sections, kept so physical-key actions can reuse the same
     *  item action lambdas as tapping a cell (DRY: one code path). */
    private var menuSections: List<MenuSection>? = null

    /** Shared physical-controller key handler, attached to every menu dialog.
     *  Each instance is bound to its own dialog so back/close semantics know
     *  which layer is active. */
    private val menuKeyListenerMain =
        View.OnKeyListener { _, keyCode, event -> handleMenuKey(menuDialog, keyCode, event) }
    private val menuKeyListenerButtonStick =
        View.OnKeyListener { _, keyCode, event -> handleMenuKey(buttonStickDialog, keyCode, event) }
    private val menuKeyListenerSensitivity =
        View.OnKeyListener { _, keyCode, event -> handleMenuKey(sensitivityDialog, keyCode, event) }

    private var compositeDisposable = CompositeDisposable()
    private val controllerInput = ControllerInput()

    /**
     * Core readiness state, driven by RetroView events (see [setupRetroView]).
     *
     * [coreReady] becomes true once the first frame is rendered (the core finished
     * loading the ROM and is running). [coreFailed] becomes true when the core reports
     * a fatal error (LibretroDroid then aborts and skips destroy internally).
     *
     * These two flags guard the Activity teardown paths (exit / background-return
     * recreate / pause) against the SIGSEGV race in LibretroDroid's retro_deinit():
     * finishing the Activity while the core is still loading tears down the native
     * core concurrently with its initialization, corrupting internal state.
     */
    private val _coreReady = MutableLiveData(false)
    val coreReady: LiveData<Boolean> = _coreReady

    private val _coreFailed = MutableLiveData(false)
    val coreFailed: LiveData<Boolean> = _coreFailed

    /** Set when a background-return recreate was deferred because the core was still
     *  loading; consumed once the first frame renders (see [handleBackgroundReturn]
     *  and the frame observer in [setupRetroView]). */
    private var pendingRecreate = false

    private val buttonStickOptions = arrayOf("Off", "C-Right", "C-Left", "C-Down", "A", "B", "Auto")
    private val buttonStickPrefsKey = "button_stick_mode"
    private val autoZPrefsKey = "auto_z_enabled"
    private val n64StickSensitivityPrefsKey = "n64_stick_sensitivity"
    private val buttonStickSensitivityPrefsKey = "button_stick_sensitivity"

    init {
        controllerInput.menuCallback = { showMenu() }
    }

    /**
     * Create an instance of a menu dialog
     *
     * Always rebuilds: this is called on every onCreate(), including after the
     * activity is recreated to recover from a lost GL context, and a dialog built
     * with a stale (destroyed) Activity context would leak / fail to show.
     *
     * The dialog view is a scrollable grid grouped into category sections, each
     * item a tappable icon+label cell wired to its own action lambda (see
     * [buildMenuSections]). Toggle items (mute, fast-forward) reflect their
     * current state when the menu is shown via [updateToggleStates]. The grid is
     * built with nested weighted [LinearLayout]s (see [buildMenuView]) so it
     * measures correctly inside the dialog's ScrollView.
     */
    fun prepareMenu(context: Context) {
        activityContext = context as Activity
        toggleEntries.clear()
        badgeViews.clear()
        val view = buildMenuView(context)
        menuView = view
        menuDialog = AlertDialog.Builder(context)
            .setView(view)
            .create()
    }

    /** Builds the custom grid-menu view by delegating to the shared
     *  [MenuGridBuilder] (used by both this in-game menu and the Library
     *  per-game context menu), so the two stay visually and behaviourally in
     *  sync. Cell taps run the item action then dismiss this dialog. */
    private fun buildMenuView(context: Context): View {
        val sections = buildMenuSections()
        menuSections = sections
        val built = MenuGridBuilder.build(context, sections) { item ->
            item.action()
            menuDialog?.dismiss()
        }
        toggleEntries.clear()
        toggleEntries.addAll(built.toggleEntries)
        badgeViews.clear()
        badgeViews.addAll(built.badgeViews)
        return built.view
    }

    /** Returns the menu's category sections and their action items. Actions are
     *  lambdas bound to this ViewModel's state, so no click-handler switch is
     *  needed (DRY: one cell layout, one click path). */
    private fun buildMenuSections(): List<MenuSection> {
        val fastForwardSpeed = resources.getInteger(R.integer.config_fast_forward_multiplier)
        val game = MenuSection(
            R.string.menu_category_game,
            listOf(
                MenuActionItem("reset", R.string.menu_reset, R.drawable.ic_refresh, badgeRes = R.string.badge_x) {
                    retroView?.view?.reset()
                },
                MenuActionItem("save_state", R.string.menu_save_state, R.drawable.ic_save_state, badgeRes = R.string.badge_select) {
                    retroView?.let { retroViewUtils?.saveState(it) }
                },
                MenuActionItem("load_state", R.string.menu_load_state, R.drawable.ic_load_state, badgeRes = R.string.badge_start) {
                    retroView?.let { retroViewUtils?.loadState(it) }
                },
                MenuActionItem("exit", R.string.menu_exit, R.drawable.ic_exit, badgeRes = R.string.badge_y) {
                    exitGame()
                }
            )
        )
        val audioVideo = MenuSection(
            R.string.menu_category_audio_video,
            listOf(
                MenuActionItem(
                    "mute",
                    R.string.menu_mute,
                    R.drawable.ic_volume_up,
                    R.drawable.ic_volume_off,
                    isToggle = true,
                    isActive = { retroView?.view?.audioEnabled == false },
                    badgeRes = R.string.badge_lb
                ) {
                    retroView?.let { it.view.audioEnabled = !it.view.audioEnabled }
                },
                MenuActionItem(
                    "fast_forward",
                    R.string.menu_fast_forward,
                    R.drawable.ic_fast_forward,
                    isToggle = true,
                    isActive = { retroView?.view?.frameSpeed == fastForwardSpeed },
                    badgeRes = R.string.badge_rb
                ) {
                    retroView?.let { retroViewUtils?.fastForward(it) }
                }
            )
        )
        val controls = MenuSection(
            R.string.menu_category_controls,
            listOf(
                MenuActionItem("auto_z", R.string.menu_auto_z, R.drawable.ic_target, badgeRes = R.string.badge_l3) {
                    autoZEnabled = !autoZEnabled
                    controllerInput.autoZEnabled = autoZEnabled
                    appContext.getSharedPreferences("ludere_prefs", Context.MODE_PRIVATE)
                        .edit().putBoolean(autoZPrefsKey, autoZEnabled).apply()
                    if (!autoZEnabled && zHeldViaDoubleTap) {
                        zHeldViaDoubleTap = false
                        retroView?.view?.sendKeyEvent(
                            KeyEvent.ACTION_UP,
                            InputMapper.mapKeyCode(KeyEvent.KEYCODE_BUTTON_L2)
                        )
                    }
                },
                MenuActionItem("button_stick", R.string.menu_button_stick, R.drawable.ic_gamepad, badgeRes = R.string.badge_r3) {
                    showButtonStickDialog()
                },
                MenuActionItem("sensitivity", R.string.menu_sensitivity, R.drawable.ic_tune) {
                    showSensitivityDialog()
                }
            )
        )
        val sections = mutableListOf(game, audioVideo, controls)
        /* Auto-Ocarina is only meaningful for OoT / MM (the games with an
           in-game Ocarina). Hide the item entirely when detection failed. */
        if (ocarinaGame != null) {
            sections.add(
                MenuSection(
                    R.string.menu_category_ocarina,
                    listOf(
                        MenuActionItem(
                            "auto_ocarina",
                            R.string.menu_auto_ocarina,
                            R.drawable.ic_ocarina,
                            tintIcon = false
                        ) {
                            showOcarinaSongList()
                        }
                    )
                )
            )
        }
        return sections
    }

    /** Refreshes toggle items' icon + highlight to match live emulator state.
     * Called from [showMenu] so the menu reflects mute / fast-forward status
     * each time it opens. */
    private fun updateToggleStates() {
        val context = activityContext ?: return
        val primary = ContextCompat.getColor(context, R.color.color_primary)
        val onSurfaceVariant = ContextCompat.getColor(context, R.color.color_on_surface_variant)
        for ((item, cell, icon) in toggleEntries) {
            val active = item.isActive()
            icon.setImageResource(if (active) item.activeIconRes else item.iconRes)
            icon.imageTintList = ColorStateList.valueOf(if (active) primary else onSurfaceVariant)
            cell.background = ContextCompat.getDrawable(
                context,
                if (active) R.drawable.bg_menu_item_active else R.drawable.bg_menu_item
            )
        }
    }

    /**
     * Physical-controller key handler shared by all three menu dialogs.
     *
     * Wired via [View.OnKeyListener] on each dialog's decor view, so it only
     * fires while that dialog owns input focus (when no menu is open, key events
     * flow through GameActivity -> [processKeyEvent] -> the frozen ControllerInput
     * instead, so there is no conflict with in-game L3/R3 functions).
     *
     * Policy: act on ACTION_DOWN, consume (return true) on both ACTION_DOWN and
     * ACTION_UP for every mapped keycode so nothing leaks to the core while a
     * menu is open. D-pad is left unmapped so the system keeps handling focus
     * navigation between cells. Actions reuse the exact same lambdas as tapping
     * a cell (see [runMenuAction]) -- no duplicated logic.
     *
     * @param dialog the dialog this listener is bound to (used to decide back /
     *   close semantics for the active layer).
     */
    private fun handleMenuKey(dialog: AlertDialog?, keyCode: Int, event: KeyEvent): Boolean {
        if (dialog?.isShowing != true)
            return false

        /* Consume ACTION_UP (and any other action) for mapped keys to avoid
           double-fires; only ACTION_DOWN performs the action. */
        when (event.action) {
            KeyEvent.ACTION_UP -> return true
            KeyEvent.ACTION_DOWN -> { /* proceed */ }
            else -> return true
        }

        val isSub = dialog == buttonStickDialog || dialog == sensitivityDialog

        when (keyCode) {
            /* Close EVERYTHING and return to game -- works from any layer. */
            KeyEvent.KEYCODE_BUTTON_MODE -> {
                closeAllMenus()
                return true
            }
            /* Back semantics: dismiss the active sub-dialog (revealing the main
               menu) or, on the main menu, dismiss it (return to game). */
            KeyEvent.KEYCODE_BUTTON_B -> {
                if (isSub) dialog.dismiss() else menuDialog?.dismiss()
                return true
            }
        }

        /* While a sub-dialog is on top, the main-menu action keys must not reach
           the (obscured) main menu items. Consume them as no-ops; let A /
           DPAD_CENTER / ENTER and the D-pad fall through so the sub-dialog's own
           list navigation/selection still works. */
        if (isSub) {
            return when (keyCode) {
                KeyEvent.KEYCODE_BUTTON_SELECT,
                KeyEvent.KEYCODE_BUTTON_START,
                KeyEvent.KEYCODE_BUTTON_L1,
                KeyEvent.KEYCODE_BUTTON_R1,
                KeyEvent.KEYCODE_BUTTON_THUMBL,
                KeyEvent.KEYCODE_BUTTON_X,
                KeyEvent.KEYCODE_BUTTON_Y,
                KeyEvent.KEYCODE_BUTTON_THUMBR -> true
                else -> false
            }
        }

        /* Main menu active: map physical buttons to the same item actions. */
        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_SELECT -> { runMenuAction("save_state"); return true }
            KeyEvent.KEYCODE_BUTTON_START -> { runMenuAction("load_state"); return true }
            KeyEvent.KEYCODE_BUTTON_L1 -> { runMenuAction("mute"); return true }
            KeyEvent.KEYCODE_BUTTON_R1 -> { runMenuAction("fast_forward"); return true }
            KeyEvent.KEYCODE_BUTTON_THUMBL -> { runMenuAction("auto_z"); return true }
            KeyEvent.KEYCODE_BUTTON_THUMBR -> { showButtonStickDialog(); return true }
            KeyEvent.KEYCODE_BUTTON_X -> { runMenuAction("reset"); return true }
            KeyEvent.KEYCODE_BUTTON_Y -> { runMenuAction("exit"); return true }
            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_DPAD_CENTER,
            KeyEvent.KEYCODE_ENTER -> {
                val tag = dialog.window?.currentFocus?.tag as? String
                if (tag != null) {
                    runMenuAction(tag)
                    return true
                }
                return false
            }
            else -> return false
        }
    }

    /**
     * Run a menu item's action by id, reusing the exact lambda bound in
     * [buildMenuSections] (the same path a cell tap takes). For toggles we
     * refresh [updateToggleStates] so the change is reflected, then dismiss the
     * menu -- mirroring a cell tap.
     */
    private fun runMenuAction(id: String) {
        val sections = menuSections ?: return
        for (section in sections) {
            section.items.firstOrNull { it.id == id }?.let { item ->
                item.action()
                if (item.isToggle) updateToggleStates()
                menuDialog?.dismiss()
                return
            }
        }
    }

    /** Dismiss every menu layer and return to the game. */
    private fun closeAllMenus() {
        buttonStickDialog?.dismiss()
        sensitivityDialog?.dismiss()
        menuDialog?.dismiss()
    }

    // ---- Auto-Ocarina ----

    /** Attach the HUD view created by GameActivity (called once per onCreate). */
    fun attachOcarinaHud(hud: OcarinaHudView) {
        ocarinaHud = hud
    }

    /**
     * Detect the running game's Ocarina support from its ROM header and store
     * the result. Called once during launch (after the patched ROM path is
     * known) so the in-game menu can conditionally show the Auto-Ocarina item.
     * Detection reads only header ranges (see [RomHeader.fromNormalizedZ64]),
     * never the full ROM.
     */
    fun prepareOcarinaDetection(hackId: String) {
        currentHackId = hackId
        ocarinaGame = runCatching {
            OcarinaSongCatalog.detectGame(
                RomHeader.fromNormalizedZ64(Storage.getInstance(appContext).rom(hackId))
            )
        }.getOrNull()
    }

    /**
     * Show the song-list dialog for the detected game. Built-in songs are listed
     * first, followed by any catalog-provided custom songs for this hackId.
     * Physical-controller support: B closes, A/DPAD_CENTER selects the focused
     * item (see [handleOcarinaKey]); dpad navigates the standard list.
     */
    private fun showOcarinaSongList() {
        val context = activityContext ?: return
        val game = ocarinaGame ?: return
        val hackId = currentHackId ?: return
        val songs = OcarinaSongCatalog.getSongs(game, getCustomOcarinaSongs(hackId))
        ocarinaCurrentSongs = songs
        val titles = songs.map { it.displayName(context) }.toTypedArray()
        ocarinaSongDialog = AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.menu_auto_ocarina))
            .setItems(titles) { dialog, which ->
                dialog.dismiss()
                playOcarinaSong(songs[which])
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()
        ocarinaSongDialog?.show()
        ocarinaSongDialog?.window?.setBackgroundDrawable(
            AppCompatResources.getDrawable(context, R.drawable.bg_menu_dialog)
        )
        // Ensure the list can be navigated/selected with a physical controller.
        ocarinaSongDialog?.listView?.apply {
            requestFocus()
            setSelection(0)
        }
        ocarinaSongDialog?.window?.decorView?.setOnKeyListener(menuKeyListenerOcarina)
    }

    /** Look up catalog-provided custom songs for [hackId] (tolerant, may be empty). */
    private fun getCustomOcarinaSongs(hackId: String): List<OcarinaSong> {
        return runCatching {
            MergedCatalogRepository(File(appContext.filesDir, "merged_catalog.json"))
                .asMap()[hackId]?.ocarinaSongs ?: emptyList()
        }.getOrDefault(emptyList())
    }

    /**
     * Play [song]: show the HUD and start the macro player. Any prior playback
     * is cancelled first by [OcarinaMacroPlayer.play].
     */
    private fun playOcarinaSong(song: OcarinaSong) {
        val rv = retroView?.view ?: return
        ocarinaHud?.show(song)
        ocarinaPlayer = OcarinaMacroPlayer(rv, viewModelScope).apply {
            play(song, object : OcarinaMacroCallbacks {
                override fun onStarted(song: OcarinaSong) {
                    // HUD is already shown by playOcarinaSong before the initial
                    // delay, so nothing else is needed here.
                }
                override fun onNoteStart(index: Int) {
                    ocarinaHud?.setActiveNote(index)
                }
                override fun onNoteComplete(index: Int) {
                    ocarinaHud?.markComplete(index)
                }
                override fun onFinished() {
                    ocarinaHud?.hide()
                    ocarinaPlayer = null
                }
            })
        }
    }

    /**
     * Cancel any running Auto-Ocarina playback, releasing the held button and
     * hiding the HUD. Safe to call when idle.
     */
    fun cancelOcarina() {
        ocarinaPlayer?.cancel()
        ocarinaPlayer = null
        ocarinaHud?.hide()
    }

    /**
     * Physical-controller key handler for the song-list dialog. B closes;
     * A/DPAD_CENTER/ENTER selects the focused list item (falling back to the
     * first item when nothing is focused). All other keys fall through to the
     * system for normal list navigation.
     */
    private fun handleOcarinaKey(keyCode: Int, event: KeyEvent): Boolean {
        if (ocarinaSongDialog?.isShowing != true) return false
        if (event.action != KeyEvent.ACTION_DOWN) return true
        return when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_MODE -> {
                ocarinaSongDialog?.dismiss()
                true
            }
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                val songs = ocarinaCurrentSongs ?: return true
                val lv = ocarinaSongDialog?.listView
                val pos = (lv?.selectedItemPosition ?: -1).let { if (it < 0) 0 else it }
                if (pos in songs.indices) {
                    ocarinaSongDialog?.dismiss()
                    playOcarinaSong(songs[pos])
                }
                true
            }
            else -> false
        }
    }

    /**
     * Show/hide the physical-controller key badges live as controllers connect
     * or disconnect, without rebuilding the menu. Badges appear only when a
     * physical controller is present (i.e. [GamePad.shouldShowGamePads] is
     * false). Safe to call when the menu is not showing.
     */
    fun refreshMenuBadges() {
        val activity = activityContext ?: return
        val show = !GamePad.shouldShowGamePads(activity)
        for (badge in badgeViews) {
            badge.visibility = if (show) View.VISIBLE else View.GONE
        }
    }

    /**
     * Preserve emulator state, then finish GameActivity so LibraryActivity
     * (which launched it via startActivity) is revealed again. No System.exit.
     *
     * Guard: refuse to exit while the core is still loading. Finishing the Activity
     * during ROM load triggers ON_DESTROY -> LibretroDroid.destroy() -> retro_deinit()
     * concurrently with core initialization, which SIGSEGVs the mupen64plus_next core
     * (the race documented in AGENTS.md). Once the core has failed (aborted) exiting is
     * safe because LibretroDroid skips destroy internally.
     */
    private fun exitGame() {
        val context = activityContext ?: return
        if (coreReady.value != true && coreFailed.value != true) {
            Toast.makeText(context, R.string.msg_game_still_loading, Toast.LENGTH_SHORT).show()
            return
        }
        if (coreReady.value == true) {
            retroView?.let { retroViewUtils?.preserveEmulatorState(it) }
        }
        context.finish()
    }

    private fun getSavedButtonStickMode(): ButtonStickMode {
        val prefs = appContext.getSharedPreferences("ludere_prefs", Context.MODE_PRIVATE)
        val index = prefs.getInt(buttonStickPrefsKey, ButtonStickMode.C_RIGHT.ordinal)
        return ButtonStickMode.values().getOrElse(index) { ButtonStickMode.C_RIGHT }
    }

    private fun showButtonStickDialog() {
        val context = activityContext ?: return
        val prefs = appContext.getSharedPreferences("ludere_prefs", Context.MODE_PRIVATE)

        buttonStickDialog = AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.menu_button_stick))
            .setSingleChoiceItems(buttonStickOptions, buttonStickMode.ordinal) { dialog, which ->
                prefs.edit().putInt(buttonStickPrefsKey, which).apply()
                applyButtonStickMode(ButtonStickMode.values()[which])
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()
        buttonStickDialog?.show()
        buttonStickDialog?.window?.setBackgroundDrawable(
            AppCompatResources.getDrawable(context, R.drawable.bg_menu_dialog)
        )
        buttonStickDialog?.window?.decorView?.setOnKeyListener(menuKeyListenerButtonStick)
    }

    /** Applies live -- no activity recreate needed, unlike the core switch. */
    private fun applyButtonStickMode(mode: ButtonStickMode) {
        buttonStickMode = mode
        val button = buttonStick ?: return

        if (mode == ButtonStickMode.OFF) {
            button.visibility = View.GONE
            return
        }

        button.visibility = View.VISIBLE
        button.targetKeyCode = mode.keyCode ?: lastCButtonKeyCode
    }

    private fun getSavedAutoZEnabled(): Boolean {
        val prefs = appContext.getSharedPreferences("ludere_prefs", Context.MODE_PRIVATE)
        return prefs.getBoolean(autoZPrefsKey, true)
    }

    private fun getSavedN64StickSensitivity(): Float {
        val prefs = appContext.getSharedPreferences("ludere_prefs", Context.MODE_PRIVATE)
        return prefs.getFloat(n64StickSensitivityPrefsKey, 1f)
    }

    private fun getSavedButtonStickSensitivity(): Float {
        val prefs = appContext.getSharedPreferences("ludere_prefs", Context.MODE_PRIVATE)
        return prefs.getFloat(buttonStickSensitivityPrefsKey, 0.5f)
    }

    /** A slider (0%-200%) per stick, persisted and applied live to the running controls. */
    private fun showSensitivityDialog() {
        val context = activityContext ?: return
        val prefs = appContext.getSharedPreferences("ludere_prefs", Context.MODE_PRIVATE)
        val density = context.resources.displayMetrics.density
        val padding = (16 * density).toInt()

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }

        fun addSlider(label: String, initialPercent: Int, onChange: (Float) -> Unit) {
            val title = TextView(context).apply {
                text = "$label: $initialPercent%"
                setPadding(0, padding, 0, 0)
            }
            val seekBar = SeekBar(context).apply {
                max = 200
                progress = initialPercent
            }
            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    title.text = "$label: $progress%"
                    if (fromUser) onChange(progress / 100f)
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
            container.addView(title)
            container.addView(seekBar)
        }

        addSlider(
            context.getString(R.string.sensitivity_n64_stick),
            (getSavedN64StickSensitivity() * 100).toInt()
        ) {
            floatingJoystick?.sensitivity = it
            prefs.edit().putFloat(n64StickSensitivityPrefsKey, it).apply()
        }
        addSlider(
            context.getString(R.string.sensitivity_button_stick),
            (getSavedButtonStickSensitivity() * 100).toInt()
        ) {
            buttonStick?.sensitivity = it
            controllerInput.buttonStickSensitivity = it
            prefs.edit().putFloat(buttonStickSensitivityPrefsKey, it).apply()
        }

        sensitivityDialog = AlertDialog.Builder(context)
            .setTitle(context.getString(R.string.menu_sensitivity))
            .setView(container)
            .setPositiveButton(R.string.dialog_ok, null)
            .create()
        sensitivityDialog?.show()
        sensitivityDialog?.window?.setBackgroundDrawable(
            AppCompatResources.getDrawable(context, R.drawable.bg_menu_dialog)
        )
        sensitivityDialog?.window?.decorView?.setOnKeyListener(menuKeyListenerSensitivity)
    }

    /**
     * Double-tapping the analog stick toggles Z held on/off (released independently of the
     * stick itself). Toggling the Auto-Z menu item off while it's held releases Z immediately.
     */
    private fun onStickDoubleTap() {
        if (!autoZEnabled)
            return

        zHeldViaDoubleTap = !zHeldViaDoubleTap
        val action = if (zHeldViaDoubleTap) KeyEvent.ACTION_DOWN else KeyEvent.ACTION_UP
        retroView?.view?.sendKeyEvent(action, InputMapper.mapKeyCode(KeyEvent.KEYCODE_BUTTON_L2))
    }

    /**
     * Tracks a C-Right/C-Left/C-Down/Z press from any input source (touch or physical) so
     * ButtonStickMode.AUTO can follow whichever was pressed most recently, and so a real Z press
     * cancels a double-tap-held Z.
     */
    private fun trackCButtonPress(keyCode: Int) {
        when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_L2 -> zHeldViaDoubleTap = false
            else -> {
                lastCButtonKeyCode = keyCode
                if (buttonStickMode == ButtonStickMode.AUTO)
                    buttonStick?.targetKeyCode = keyCode
            }
        }
    }

    /**
     * Show the menu
     *
     * Sizes the dialog window so it wraps the content when it fits and scrolls
     * internally (via the root ScrollView, which is match_parent) when it would
     * exceed 90% of the screen height. Without this cap the AlertDialog sizes its
     * window to the full content height, overflows the display, and clips the
     * bottom section because the ScrollView (wrap_content) never becomes
     * height-constrained and therefore never scrolls.
     */
    fun showMenu() {
        /* Opening the menu again cancels any in-progress Auto-Ocarina playback. */
        cancelOcarina()
        if (retroView?.frameRendered?.value == true) {
            retroView?.let { retroViewUtils?.preserveEmulatorState(it) }
            updateToggleStates()

            val dialog = menuDialog ?: return
            val view = menuView ?: return
            val context = activityContext ?: return
            val metrics = context.resources.displayMetrics
            val screenWidth = metrics.widthPixels
            val screenHeight = metrics.heightPixels

            val maxWidthPx = context.resources.getDimensionPixelSize(R.dimen.dialog_menu_max_width)
            val dialogWidth = minOf((screenWidth * 0.92f).toInt(), maxWidthPx)

            /* Measure the content at the dialog's actual width (AT_MOST) so wrapped
               labels reflect the real height. A bare UNSPECIFIED width would let the
               weight-based cells expand and under-report the height. */
            val widthSpec = View.MeasureSpec.makeMeasureSpec(dialogWidth, View.MeasureSpec.AT_MOST)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            view.measure(widthSpec, heightSpec)
            val contentHeight = view.measuredHeight

            val verticalInset = context.resources.getDimensionPixelSize(R.dimen.dialog_menu_vertical_inset)
            val maxHeight = (screenHeight * 0.90f).toInt()
            val dialogHeight = minOf(contentHeight + verticalInset, maxHeight)

            dialog.show()
            dialog.window?.setLayout(dialogWidth, dialogHeight)
            dialog.window?.setBackgroundDrawable(
                AppCompatResources.getDrawable(context, R.drawable.bg_menu_dialog)
            )
            dialog.window?.decorView?.setOnKeyListener(menuKeyListenerMain)
        }
    }

    /**
     * Dismiss the menu
     */
    fun dismissMenu() {
        if (menuDialog?.isShowing == true)
            menuDialog?.dismiss()
    }

    /**
     * Save the state of the emulator.
     *
     * Skips serialization while the core is still loading (!coreReady): there is no
     * user-visible state to persist before the first frame, and calling
     * preserveEmulatorState would block the main thread on runOnEmulationThread until
     * the GL thread finishes loading (ANR risk during load). SRAM was loaded from file
     * at start anyway, so nothing is lost.
     */
    fun preserveState() {
        if (coreReady.value == true)
            retroView?.let { retroViewUtils?.preserveEmulatorState(it) }
    }

    /**
     * Handle the Activity returning to the foreground after its first launch
     * (GameActivity.onStart, hasStarted == true).
     *
     * GL-context recovery path: a full Activity recreate rebuilds the RetroView from
     * scratch -- the only supported way to recover a lost GL context for
     * hardware-rendered cores (mupen64plus_next). This is ONLY safe once a frame has
     * rendered (coreReady). If the core is still loading (!coreReady && !coreFailed) we
     * defer the recreate via [pendingRecreate] and let it fire when the first frame
     * arrives (see setupRetroView's frame observer) -- destroying a mid-load core via
     * ON_DESTROY -> retro_deinit SIGSEGVs. If the core already failed (coreFailed) we
     * never recreate: there is nothing to recover and LibretroDroid already skipped
     * destroy internally.
     */
    fun handleBackgroundReturn(activity: ComponentActivity) {
        if (coreReady.value == true) {
            preserveState()
            activity.recreate()
        } else if (coreFailed.value != true) {
            pendingRecreate = true
        }
    }

    /**
     * Hide the system bars
     */
    @Suppress("DEPRECATION")
    fun immersive(window: Window) {
        /* Check if the config permits it */
        if (!resources.getBoolean(R.bool.config_fullscreen))
            return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            with (window.insetsController!!) {
                hide(WindowInsets.Type.systemBars())
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
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

    // ---- RetroAchievements session ----

    /**
     * Starts the RetroAchievements session once the first frame rendered and
     * the core is running. No-op when the feature is disabled in settings or
     * already active. The memory region is captured lazily by the session at
     * game-load time (it is only valid while the core holds the ROM).
     */
    private fun startRaSessionIfNeeded(hackId: String) {
        if (!CorePrefs.getRetroAchievementsEnabled(appContext)) return
        if (raSession != null) return

        val view = retroView?.view ?: return
        val romFile = Storage.getInstance(appContext).rom(hackId)
        val app = getApplication<Application>()

        val session = RaSessionManager(
            context = app,
            http = RaHttpClient(RaUserAgent.build(app)),
            credentials = RaCredentialStore(app),
            metadataStore = RaInstallMetadataStore(app)
        )
        raSession = session

        /* Per-frame evaluation ticks: FrameRendered fires on the main thread
           for every rendered frame; the session gates internally on a loaded
           game, so this stays cheap while achievements are inactive. */
        raFrameCollector = viewModelScope.launch {
            view.getGLRetroEvents().collect { event ->
                if (event is GLRetroView.GLRetroEvents.FrameRendered) {
                    session.onFrame()
                }
            }
        }

        session.start(romFile) {
            view.getMemoryRegion(LibretroDroid.MEMORY_SYSTEM_RAM)
        }
    }

    /**
     * Stops the RA session. MUST run before the native core is destroyed
     * (GameActivity.onDestroy calls this ahead of super.onDestroy()) because
     * the aliased memory region dies with the core.
     */
    fun stopRaSession() {
        raFrameCollector?.cancel()
        raFrameCollector = null
        raSession?.stop()
        raSession = null
    }

    /**
     * Hook the RetroView with the GLRetroView instance
     */
    fun setupRetroView(activity: ComponentActivity, container: FrameLayout, hackId: String) {
        /* A new RetroView is being created; release any keys held by a previous
           (now stale) macro player and reset playback state. */
        cancelOcarina()
        val coreLib = CorePrefs.getSelectedCoreLib(appContext)
        retroView = RetroView(activity, compositeDisposable, hackId, coreLib)
        retroViewUtils = RetroViewUtils(activity, hackId)
        container.removeAllViews()

        /* The ViewModel survives Activity recreation, so clear any readiness state
           left over from a previous RetroView instance before wiring the new one. */
        _coreReady.value = false
        _coreFailed.value = false
        pendingRecreate = false

        retroView?.let { retroView ->
            container.addView(retroView.view)
            activity.lifecycle.addObserver(retroView.view)
            retroView.registerFrameRenderedListener()
            retroView.registerCoreErrorListener()

            /* Restore state after first frame loaded */
            retroView.frameRendered.observe(activity) {
                if (it != true)
                    return@observe

                _coreReady.value = true
                retroViewUtils?.restoreEmulatorState(retroView)

                startRaSessionIfNeeded(hackId)

                /* A background-return recreate was deferred while the core was still
                    loading (see handleBackgroundReturn). Now that a frame has rendered
                    the GL context can be safely rebuilt via a full Activity recreate. */
                if (pendingRecreate) {
                    pendingRecreate = false
                    activity.recreate()
                }
            }

            /* Track fatal core errors so the exit/recreate guards know the core
                aborted (LibretroDroid then skips destroy internally, making teardown
                safe even mid-load). */
            retroView.coreError.observe(activity) { error ->
                _coreFailed.value = error != null
            }
        }
    }

    /**
     * Create the on-screen GamePads, each positioned independently to match referencia.png.
     * Must run after setupRetroView() -- each pad is subscribed to the current [retroView] as
     * soon as it's created (subscribing later, e.g. back in setupRetroView(), would iterate this
     * class's gamePads list before it's populated and silently wire up nothing).
     */
    fun setupGamePads(overlay: FrameLayout) {
        val context = getApplication<Application>().applicationContext
        val density = overlay.resources.displayMetrics.density
        val config = GamePadConfig(context, resources)

        /* RadialGamePad caches its on-screen position/size on its first layout pass, and
           doesn't refresh that cache on a later resize -- it keeps drawing in the new spot
           but hit-tests touches against the stale one. So each pad must be created with its
           final size and position already known (from the overlay's real, post-inset
           dimensions) instead of being added small and resized once layout completes. */
        overlay.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                if (overlay.width == 0 || overlay.height == 0)
                    return

                overlay.viewTreeObserver.removeOnGlobalLayoutListener(this)

                buttonStickMode = getSavedButtonStickMode()
                autoZEnabled = getSavedAutoZEnabled()

                /* Added first so it sits at the lowest z-order -- every real button placed after
                   it (Select, D-pad) naturally claims its own touches first, leaving the
                   joystick only the genuinely empty area to react to. */
                if (resources.getBoolean(R.bool.config_left_analog)) {
                    val regionWidthPx = (GamePadConfig.FLOATING_JOYSTICK_REGION_RIGHT_FRACTION * overlay.width).toInt()
                    val joystickParams = FrameLayout.LayoutParams(regionWidthPx, overlay.height)

                    floatingJoystick = FloatingJoystick(context).also { joystick ->
                        joystick.retroView = retroView?.view
                        joystick.sensitivity = getSavedN64StickSensitivity()
                        joystick.hintX = GamePadConfig.FLOATING_JOYSTICK_HINT_GRAVITY_X * overlay.width
                        joystick.hintY = GamePadConfig.FLOATING_JOYSTICK_HINT_GRAVITY_Y * overlay.height
                        joystick.hintRadius = GamePadConfig.FLOATING_JOYSTICK_HINT_SIZE_FRACTION * overlay.height / 2f
                        joystick.maxReachPx = GamePadConfig.FLOATING_JOYSTICK_MAX_REACH_FRACTION * overlay.width

                        /* onInterceptTouchEvent-only wrapper: drag/analog motion on the
                           joystick itself is completely unaffected. */
                        val container = DoubleTapContainer(context) { onStickDoubleTap() }
                        container.addView(joystick, FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        ))
                        overlay.addView(container, joystickParams)
                    }
                }

                gamePads = config.placements.map { placement ->
                    val sizePx = (placement.sizeFraction * overlay.height).toInt()
                    val params = FrameLayout.LayoutParams(sizePx, sizePx)
                    params.leftMargin = (placement.gravityX * overlay.width - sizePx / 2f).toInt()
                    params.topMargin = (placement.gravityY * overlay.height - sizePx / 2f).toInt()

                    GamePad(context, placement).also {
                        it.pad.primaryDialMaxSizeDp = sizePx / density
                        overlay.addView(it.pad, params)

                        retroView?.let { rv ->
                            /* Tag along C-Right/C-Left/C-Down presses so ButtonStickMode.AUTO can
                               follow whichever was pressed most recently, and Z presses so they
                               cancel a double-tap-held Z. */
                            val onButtonDown: ((Int) -> Unit)? = placement.buttonKeyCode?.let { keyCode ->
                                { trackCButtonPress(keyCode) }
                            }
                            it.subscribe(compositeDisposable, rv.view, onButtonDown)
                        }
                    }
                }

                val buttonStickSizePx = (GamePadConfig.BUTTON_STICK_SIZE_FRACTION * overlay.height).toInt()
                val buttonStickParams = FrameLayout.LayoutParams(buttonStickSizePx, buttonStickSizePx)
                buttonStickParams.leftMargin = (GamePadConfig.BUTTON_STICK_GRAVITY_X * overlay.width - buttonStickSizePx / 2f).toInt()
                buttonStickParams.topMargin = (GamePadConfig.BUTTON_STICK_GRAVITY_Y * overlay.height - buttonStickSizePx / 2f).toInt()
                buttonStick = ButtonStick(context).also {
                    it.retroView = retroView?.view
                    it.sensitivity = getSavedButtonStickSensitivity()
                    it.targetKeyCode = buttonStickMode.keyCode ?: lastCButtonKeyCode
                    it.visibility = if (buttonStickMode == ButtonStickMode.OFF) View.GONE else View.VISIBLE
                    overlay.addView(it, buttonStickParams)
                }

                /* The physical right stick mirrors the touch Button Stick's own mode/sensitivity
                   live -- this lambda is re-evaluated on every motion event, so it always reflects
                   the current mode/Auto target without needing to be re-wired on change. */
                controllerInput.buttonStickTargetKeyCode = {
                    if (buttonStickMode == ButtonStickMode.OFF) null
                    else buttonStickMode.keyCode ?: lastCButtonKeyCode
                }
                controllerInput.buttonStickSensitivity = getSavedButtonStickSensitivity()
                controllerInput.autoZEnabled = autoZEnabled
                controllerInput.onCButtonDown = { keyCode -> trackCButtonPress(keyCode) }
            }
        })
    }

    /**
     * Hide the on-screen GamePads
     */
    fun updateGamePadVisibility(activity: Activity, overlay: FrameLayout) {
        /* INVISIBLE, not GONE -- a GONE view is skipped during layout and never gets real
           width/height, so setupGamePads()'s ViewTreeObserver.OnGlobalLayoutListener (which
           waits for overlay.width/height != 0) would never fire when a physical gamepad is
           connected at launch, silently skipping ButtonStick/FloatingJoystick creation and the
           ControllerInput wiring the physical controller itself depends on. INVISIBLE still
           gets measured/laid out normally while staying undrawn and untouchable. */
        overlay.visibility = if (GamePad.shouldShowGamePads(activity))
            View.VISIBLE
        else
            View.INVISIBLE
    }

    /**
     * Process a key event and return the result
     */
    fun processKeyEvent(keyCode: Int, event: KeyEvent): Boolean? {
        /* Any physical key during playback cancels the macro but still reaches
           the game normally (we do not swallow it). */
        if (ocarinaPlayer?.isRunning == true) cancelOcarina()
        retroView?.let {
            return controllerInput.processKeyEvent(keyCode, event, it)
        }

        return false
    }

    /**
     * Process a motion event and return the result
     */
    fun processMotionEvent(event: MotionEvent): Boolean? {
        if (ocarinaPlayer?.isRunning == true) cancelOcarina()
        retroView?.let {
            return controllerInput.processMotionEvent(event, it)
        }

        return false
    }

    /**
     * Deallocate the old RetroView
     */
    fun detachRetroView(activity: ComponentActivity) {
        retroView?.let { activity.lifecycle.removeObserver(it.view) }
        retroView = null
    }

    /**
     * Set the screen orientation based on the config
     */
    fun setConfigOrientation(activity: Activity) {
        when (resources.getInteger(R.integer.config_orientation)) {
            1 -> ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
            2 -> ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE
            3 -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            else -> return
        }.also {
            activity.requestedOrientation = it
        }
    }

    /**
     * Dispose the composite disposable; call on onDestroy
     */
    fun dispose() {
        compositeDisposable.dispose()
        compositeDisposable = CompositeDisposable()
    }

    // ---- Install-time patching: the ROM is already patched and stored ----

    /**
     * Play [hackId]. The patched ROM is produced at install time (see
     * [br.com.redclaw.zelda64player.store.DownloadManager]) and stored durably
     * via [Storage.rom]; launching just loads it. If the ROM is missing (hack
     * not installed, or a prior install failed) we log the miss, hide the
     * progress overlay, and show an i18n'd error that finishes the activity --
     * there is nothing to play without the ROM.
     */
    fun launchHack(
        activity: ComponentActivity,
        container: FrameLayout,
        overlay: FrameLayout,
        progress: View,
        hackId: String
    ) {
        val romFile = Storage.getInstance(appContext).rom(hackId)
        if (romFile.exists() && romFile.length() > 0) {
            currentHackId = hackId
            if (ocarinaGame == null) {
                ocarinaGame = runCatching {
                    OcarinaSongCatalog.detectGame(RomHeader.fromNormalizedZ64(romFile))
                }.getOrNull()
            }
            progress.visibility = View.GONE
            setupRetroView(activity, container, hackId)
            setupGamePads(overlay)
        } else {
            Log.e(TAG, "launchHack: patched ROM not installed for $hackId (expected ${romFile.absolutePath})")
            progress.visibility = View.GONE
            showError(R.string.error_rom_not_installed)
        }
    }

    private fun showError(messageRes: Int) {
        val context = activityContext ?: return
        AlertDialog.Builder(context)
            .setMessage(messageRes)
            .setPositiveButton(android.R.string.ok, null)
            /* A failed launch leaves no RetroView behind, so the game screen
               would just sit black under the gamepad overlay -- close it. */
            .setOnDismissListener { (context as? Activity)?.finish() }
            .show()
    }

}
