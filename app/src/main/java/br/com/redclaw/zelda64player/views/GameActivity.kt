package br.com.redclaw.zelda64player.views

import android.app.Service
import android.hardware.input.InputManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.RelativeLayout
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import br.com.redclaw.zelda64player.capture.RecordingIndicatorView
import br.com.redclaw.zelda64player.databinding.ActivityGameBinding
import br.com.redclaw.zelda64player.ocarina.ui.OcarinaHudView
import br.com.redclaw.zelda64player.retroachievements.ui.RaOverlayView
import br.com.redclaw.zelda64player.shortcuts.GamePlayHistoryStore
import br.com.redclaw.zelda64player.shortcuts.GameShortcutsManager
import br.com.redclaw.zelda64player.viewmodels.GameActivityViewModel
import br.com.redclaw.zelda64player.views.InstalledLibrary
import java.io.File

class GameActivity : AppCompatActivity() {
    private lateinit var binding: ActivityGameBinding
    private val viewModel: GameActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.decorView.setOnApplyWindowInsetsListener { view, windowInsets ->
            view.post { viewModel.immersive(window) }
            return@setOnApplyWindowInsetsListener windowInsets
        }

        registerInputListener()
        viewModel.setConfigOrientation(this)
        viewModel.updateGamePadVisibility(this, binding.gamepadOverlay)

        val hackId = intent.getStringExtra("hack_id")
            ?: throw IllegalStateException("No hack_id provided to launch")

        // Bump recency and re-rank dynamic shortcuts so the most recently played
        // game surfaces first in the launcher's long-press menu.
        val history = GamePlayHistoryStore(File(filesDir, "game_play_history.json"))
        GameShortcutsManager(this, history).apply {
            markPlayed(hackId)
            sync(InstalledLibrary.entries(this@GameActivity))
        }

        // Detect Ocarina support BEFORE building the menu so the Auto-Ocarina
        // item can be shown conditionally (hidden for unsupported base ROMs).
        viewModel.prepareOcarinaDetection(hackId)
        viewModel.prepareMenu(this)

        // Build and attach the Auto-Ocarina HUD (hidden until a song is played).
        // Added last so it sits above the gamepad overlay in z-order; it is
        // non-interactive so touches fall through to the controls beneath.
        val hud = OcarinaHudView(this)
        val hudParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            addRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
            addRule(RelativeLayout.CENTER_HORIZONTAL)
            val margin = (16 * resources.displayMetrics.density).toInt()
            bottomMargin = margin
        }
        binding.root.addView(hud, hudParams)
        viewModel.attachOcarinaHud(hud)

        // Build and attach the RetroAchievements overlay (unlock popups,
        // challenge/progress indicators). Added after the Ocarina HUD so it
        // sits above everything; non-interactive like the HUD.
        val raOverlay = RaOverlayView(this)
        val raParams = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.MATCH_PARENT,
            RelativeLayout.LayoutParams.MATCH_PARENT
        )
        binding.root.addView(raOverlay, raParams)
        viewModel.attachRaOverlay(raOverlay)

        // Recording indicator (Switch-style), shown while a capture is active.
        setupRecordingIndicator()

        viewModel.launchHack(
            this,
            binding.retroviewContainer,
            binding.gamepadOverlay,
            binding.patchingProgress,
            hackId
        )
    }

    /** Add the [RecordingIndicatorView] to the root and observe recording state. */
    private fun setupRecordingIndicator() {
        val indicator = RecordingIndicatorView(this)
        val size = RelativeLayout.LayoutParams.WRAP_CONTENT
        val params = RelativeLayout.LayoutParams(size, size).apply {
            addRule(RelativeLayout.ALIGN_PARENT_TOP)
            addRule(RelativeLayout.ALIGN_PARENT_END)
            val margin = (12 * resources.displayMetrics.density).toInt()
            topMargin = margin
            marginEnd = margin
        }
        binding.root.addView(indicator, params)
        viewModel.isRecording.observe(this) { recording ->
            if (recording) indicator.show() else indicator.hide()
        }
    }

    private fun registerInputListener() {
        val inputManager = getSystemService(Service.INPUT_SERVICE) as InputManager
        inputManager.registerInputDeviceListener(object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) {
                viewModel.updateGamePadVisibility(this@GameActivity, binding.gamepadOverlay)
                viewModel.refreshMenuBadges()
            }
            override fun onInputDeviceRemoved(deviceId: Int) {
                viewModel.updateGamePadVisibility(this@GameActivity, binding.gamepadOverlay)
                viewModel.refreshMenuBadges()
            }
            override fun onInputDeviceChanged(deviceId: Int) {
                viewModel.updateGamePadVisibility(this@GameActivity, binding.gamepadOverlay)
                viewModel.refreshMenuBadges()
            }
        }, null)
    }

    override fun onBackPressed() = viewModel.showMenu()

    /* Tracks whether this Activity instance has already been started once;
       false only right after onCreate(), true after returning from onStop() */
    private var hasStarted = false

    override fun onStart() {
        super.onStart()

        /* Some hardware-rendered cores (e.g. mupen64plus_next) can't recover
           their GL context after the app is backgrounded, leaving a black
           screen. GLRetroView only supports being created once during
           Activity.onCreate(), so the safe fix is a full activity recreate:
           the old RetroView is torn down by the normal ON_DESTROY lifecycle
           dispatch (it's still a registered observer) as this instance goes
           away, and onCreate() builds a fresh one, exactly like a normal
           (working) cold launch. Do NOT call retroView.view.onDestroy() here
           directly -- it races with the GL render thread and can crash.

           The recreate is delegated to GameActivityViewModel.handleBackgroundReturn,
           which only recreates once a frame has rendered (coreReady). If the core is
           still loading it defers the recreate until the first frame arrives, avoiding
           destruction of a mid-load core (SIGSEGV in retro_deinit). */
        if (hasStarted) {
            viewModel.handleBackgroundReturn(this)
            return
        }

        hasStarted = true
    }

    override fun onDestroy() {
        /* Cancel any Auto-Ocarina playback (releases the held button) before the
            native core is torn down by super.onDestroy(). */
        viewModel.cancelOcarina()
        /* Stop the RetroAchievements session before the core dies: the RA
            client aliases the emulated memory region, which becomes invalid
            once super.onDestroy() releases the native core. */
        viewModel.stopRaSession()
        /* Leaving the game (not a GL-context recreate) stops any active
            recording so we never keep capturing a dead surface. */
        if (isFinishing) viewModel.stopRecording()
        /* super.onDestroy() dispatches ON_DESTROY to the still-registered
            RetroView observer, releasing its native core (~90MB+). Cleaning up
            the observer beforehand (as this used to) skips that dispatch
            entirely, leaking native memory on every recreate(). */
        super.onDestroy()
        viewModel.dismissMenu()
        viewModel.dispose()
        viewModel.detachRetroView(this)
    }

    override fun onPause() {
        viewModel.preserveState()
        viewModel.cancelOcarina()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return viewModel.processKeyEvent(keyCode, event) ?: super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        return viewModel.processKeyEvent(keyCode, event) ?: super.onKeyUp(keyCode, event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        return viewModel.processMotionEvent(event) ?: super.onGenericMotionEvent(event)
    }
}
