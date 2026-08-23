package br.com.redclaw.zelda64player.views

import android.app.Service
import android.hardware.input.InputManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import br.com.redclaw.zelda64player.databinding.ActivityGameBinding
import br.com.redclaw.zelda64player.viewmodels.GameActivityViewModel

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
        viewModel.prepareMenu(this)

        val hackId = intent.getStringExtra("hack_id")
            ?: throw IllegalStateException("No hack_id provided to launch")
        viewModel.launchHack(
            this,
            binding.retroviewContainer,
            binding.gamepadOverlay,
            binding.patchingProgress,
            hackId
        )
    }

    private fun registerInputListener() {
        val inputManager = getSystemService(Service.INPUT_SERVICE) as InputManager
        inputManager.registerInputDeviceListener(object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) {
                viewModel.updateGamePadVisibility(this@GameActivity, binding.gamepadOverlay)
            }
            override fun onInputDeviceRemoved(deviceId: Int) {
                viewModel.updateGamePadVisibility(this@GameActivity, binding.gamepadOverlay)
            }
            override fun onInputDeviceChanged(deviceId: Int) {
                viewModel.updateGamePadVisibility(this@GameActivity, binding.gamepadOverlay)
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
           directly -- it races with the GL render thread and can crash. */
        if (hasStarted) {
            viewModel.preserveState()
            recreate()
            return
        }

        hasStarted = true
    }

    override fun onDestroy() {
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
        super.onPause()
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
