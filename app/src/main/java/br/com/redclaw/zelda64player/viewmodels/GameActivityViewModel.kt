package br.com.redclaw.zelda64player.viewmodels

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.DialogInterface
import android.content.pm.ActivityInfo
import android.os.Build
import android.view.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.AndroidViewModel
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.gamepad.ButtonStick
import br.com.redclaw.zelda64player.gamepad.ButtonStickMode
import br.com.redclaw.zelda64player.gamepad.DoubleTapContainer
import br.com.redclaw.zelda64player.gamepad.FloatingJoystick
import br.com.redclaw.zelda64player.gamepad.GamePad
import br.com.redclaw.zelda64player.gamepad.GamePadConfig
import br.com.redclaw.zelda64player.input.ControllerInput
import br.com.redclaw.zelda64player.input.InputMapper
import br.com.redclaw.zelda64player.retroview.RetroView
import br.com.redclaw.zelda64player.utils.CorePrefs
import br.com.redclaw.zelda64player.utils.RetroViewUtils
import io.reactivex.disposables.CompositeDisposable

class GameActivityViewModel(application: Application) : AndroidViewModel(application) {
    private val resources = application.resources
    private val appContext = application.applicationContext

    var retroView: RetroView? = null
    private var retroViewUtils: RetroViewUtils? = null

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
    private var buttonStickDialog: AlertDialog? = null
    private var sensitivityDialog: AlertDialog? = null
    private var activityContext: Context? = null

    private var compositeDisposable = CompositeDisposable()
    private val controllerInput = ControllerInput()

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
     */
    fun prepareMenu(context: Context) {
        activityContext = context

        val menuOnClickListener = MenuOnClickListener()
        menuDialog = AlertDialog.Builder(context)
            .setItems(menuOnClickListener.menuOptions, menuOnClickListener)
            .create()
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
            .setNegativeButton("Cancel", null)
            .create()
        buttonStickDialog?.show()
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
            .setPositiveButton("OK", null)
            .create()
        sensitivityDialog?.show()
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
     */
    fun showMenu() {
        if (retroView?.frameRendered?.value == true) {
            retroView?.let { retroViewUtils?.preserveEmulatorState(it) }
            menuDialog?.show()
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
     * Save the state of the emulator
     */
    fun preserveState() {
        if (retroView?.frameRendered?.value == true)
            retroView?.let { retroViewUtils?.preserveEmulatorState(it) }
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

    /**
     * Hook the RetroView with the GLRetroView instance
     */
    fun setupRetroView(activity: ComponentActivity, container: FrameLayout, hackId: String) {
        val coreLib = CorePrefs.getSelectedCoreLib(appContext)
        retroView = RetroView(activity, compositeDisposable, hackId, coreLib)
        retroViewUtils = RetroViewUtils(activity, hackId)
        container.removeAllViews()

        retroView?.let { retroView ->
            container.addView(retroView.view)
            activity.lifecycle.addObserver(retroView.view)
            retroView.registerFrameRenderedListener()

            /* Restore state after first frame loaded */
            retroView.frameRendered.observe(activity) {
                if (it != true)
                    return@observe

                retroViewUtils?.restoreEmulatorState(retroView)
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
        retroView?.let {
            return controllerInput.processKeyEvent(keyCode, event, it)
        }

        return false
    }

    /**
     * Process a motion event and return the result
     */
    fun processMotionEvent(event: MotionEvent): Boolean? {
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

    /**
     * Class to handle the menu dialog actions
     */
    inner class MenuOnClickListener : DialogInterface.OnClickListener {
        private val context = getApplication<Application>().applicationContext

        val menuOptions = arrayOf(
            context.getString(R.string.menu_reset),
            context.getString(R.string.menu_save_state),
            context.getString(R.string.menu_load_state),
            context.getString(R.string.menu_mute),
            context.getString(R.string.menu_fast_forward),
            context.getString(R.string.menu_button_stick),
            context.getString(R.string.menu_auto_z),
            context.getString(R.string.menu_sensitivity)
        )

        override fun onClick(dialog: DialogInterface?, which: Int) {
            when (menuOptions[which]) {
                context.getString(R.string.menu_reset) -> retroView?.view?.reset()
                context.getString(R.string.menu_save_state) -> retroView?.let {
                    retroViewUtils?.saveState(it)
                }
                context.getString(R.string.menu_load_state) -> retroView?.let{
                    retroViewUtils?.loadState(it)
                }
                context.getString(R.string.menu_mute) -> retroView?.let {
                    it.view.audioEnabled = !it.view.audioEnabled
                }
                context.getString(R.string.menu_fast_forward) -> retroView?.let {
                    retroViewUtils?.fastForward(it)
                }
                context.getString(R.string.menu_button_stick) -> {
                    showButtonStickDialog()
                }
                context.getString(R.string.menu_auto_z) -> {
                    autoZEnabled = !autoZEnabled
                    controllerInput.autoZEnabled = autoZEnabled
                    appContext.getSharedPreferences("ludere_prefs", Context.MODE_PRIVATE)
                        .edit().putBoolean(autoZPrefsKey, autoZEnabled).apply()

                    if (!autoZEnabled && zHeldViaDoubleTap) {
                        zHeldViaDoubleTap = false
                        retroView?.view?.sendKeyEvent(KeyEvent.ACTION_UP, InputMapper.mapKeyCode(KeyEvent.KEYCODE_BUTTON_L2))
                    }
                }
                context.getString(R.string.menu_sensitivity) -> {
                    showSensitivityDialog()
                }
            }
        }
    }
}
