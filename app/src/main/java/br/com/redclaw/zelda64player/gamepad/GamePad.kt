package br.com.redclaw.zelda64player.gamepad

import android.app.Activity
import android.app.Service
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Display
import android.view.InputDevice
import android.view.KeyEvent
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.input.InputMapper
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.radialgamepad.library.RadialGamePad
import com.swordfish.radialgamepad.library.event.Event
import io.reactivex.disposables.CompositeDisposable

class GamePad(
    context: Context,
    val placement: PadPlacement,
) {
    val pad = RadialGamePad(placement.config, 0f, context)

    companion object {
        /**
         * Should the user see the on-screen controls?
         */
        @Suppress("DEPRECATION")
        fun shouldShowGamePads(activity: Activity): Boolean {
            /* Config says we shouldn't use virtual controls */
            if (!activity.resources.getBoolean(R.bool.config_gamepad))
                return false

            /* Devices without a touchscreen don't need a GamePad */
            val hasTouchScreen = activity.packageManager?.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
            if (hasTouchScreen == null || hasTouchScreen == false)
                return false

            /* Fetch the current display that the game is running on */
            val currentDisplayId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
                activity.display!!.displayId
            else {
                val wm = activity.getSystemService(AppCompatActivity.WINDOW_SERVICE) as WindowManager
                wm.defaultDisplay.displayId
            }

            /* Are we presenting this screen on a TV or display? */
            val dm = activity.getSystemService(Service.DISPLAY_SERVICE) as DisplayManager
            if (dm.getDisplay(currentDisplayId)?.flags?.and(Display.FLAG_PRESENTATION) == Display.FLAG_PRESENTATION)
                return false

            /* If a GamePad is connected, we definitely don't need touch controls */
            for (id in InputDevice.getDeviceIds()) {
                InputDevice.getDevice(id)?.apply {
                    if (sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD)
                        return false
                }
            }

            return true
        }
    }

    /**
     * Send inputs to the RetroView
     */
    private fun eventHandler(event: Event, retroView: GLRetroView, onButtonDown: ((Int) -> Unit)?) {
        when (event) {
            is Event.Button -> {
                if (event.action == KeyEvent.ACTION_DOWN)
                    onButtonDown?.invoke(event.id)
                retroView.sendKeyEvent(event.action, InputMapper.mapKeyCode(event.id))
            }
            is Event.Direction -> when (event.id) {
                GLRetroView.MOTION_SOURCE_DPAD -> retroView.sendMotionEvent(GLRetroView.MOTION_SOURCE_DPAD, event.xAxis, event.yAxis)
                GLRetroView.MOTION_SOURCE_ANALOG_LEFT -> retroView.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, event.xAxis, event.yAxis)
                GLRetroView.MOTION_SOURCE_ANALOG_RIGHT -> retroView.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_RIGHT, event.xAxis, event.yAxis)
            }
        }
    }

    /**
     * Register input events to the RetroView. [onButtonDown] (raw, pre-InputMapper keycode) lets
     * callers track button presses -- e.g. ButtonStick's Auto mode following the last C-button.
     */
    fun subscribe(compositeDisposable: CompositeDisposable, retroView: GLRetroView, onButtonDown: ((Int) -> Unit)? = null) {
        val inputDisposable = pad.events().subscribe {
            eventHandler(it, retroView, onButtonDown)
        }
        compositeDisposable.add(inputDisposable)
    }
}
