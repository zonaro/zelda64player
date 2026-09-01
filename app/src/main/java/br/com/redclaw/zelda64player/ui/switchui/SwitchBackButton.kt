/*
 * Zelda 64 Player - native Android N64 emulator frontend for Zelda ROM hacks.
 * Copyright (C) 2026 RedClaw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package br.com.redclaw.zelda64player.ui.switchui

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import br.com.redclaw.zelda64player.Zelda64PlayerApp
import br.com.redclaw.zelda64player.input.InputDeviceUtils

/**
 * Reusable on-screen back-button behaviour for non-HOME Switch-style screens.
 *
 * Wires a [View] so that:
 *  - tapping it plays the Switch "back" SFX and runs [onBack] (the call sites
 *    pass [AppCompatActivity.finish], since these are top-level navigable
 *    screens launched from HOME/dock);
 *  - when a physical controller is connected the button hides (it is redundant
 *    with the controller's B / back key); and
 *  - any touch on the screen makes the button reappear and stay visible, so a
 *    touch user always gets the affordance back.
 *
 * The [InputManager.InputDeviceListener] is cleaned up automatically via a
 * [LifecycleEventObserver] on [Lifecycle.Event.ON_DESTROY], so host activities
 * need no manual teardown. The helper is self-contained and holds no screen
 * specific logic.
 */
class SwitchBackButton {

    private var button: View? = null
    private var inputManager: InputManager? = null

    /** True only while the button is hidden because a controller is present. */
    private var hiddenByController = false

    private val deviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) {
            if (InputDeviceUtils.isPhysicalController(InputDevice.getDevice(deviceId))) hide()
        }

        override fun onInputDeviceRemoved(deviceId: Int) {
            // Only reveal the button once no physical controller remains.
            if (!InputDeviceUtils.hasConnectedController()) show()
        }

        override fun onInputDeviceChanged(deviceId: Int) {
            // A device may have become (or stopped being) a controller.
            if (InputDeviceUtils.hasConnectedController()) hide() else show()
        }
    }

    /**
     * Binds [button] to the back action. Registers a controller listener and
     * applies the initial visibility based on whether a controller is already
     * connected at attach time.
     */
    fun attach(activity: AppCompatActivity, button: View, onBack: () -> Unit) {
        this.button = button
        button.setOnClickListener {
            Zelda64PlayerApp.sfxManager?.back()
            onBack()
        }

        inputManager = activity.getSystemService(Context.INPUT_SERVICE) as InputManager
        inputManager?.registerInputDeviceListener(deviceListener, null)

        if (InputDeviceUtils.hasConnectedController()) hide() else show()

        activity.lifecycle.addObserver(object : LifecycleEventObserver {
            override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
                if (event == Lifecycle.Event.ON_DESTROY) {
                    inputManager?.unregisterInputDeviceListener(deviceListener)
                    activity.lifecycle.removeObserver(this)
                }
            }
        })
    }

    /**
     * Call from the host activity's [AppCompatActivity.dispatchTouchEvent]
     * override. Any touch reveals a button that was hidden because a physical
     * controller was connected, satisfying "touch usage => button visible".
     */
    fun onTouch(event: MotionEvent) {
        if (hiddenByController) show()
    }

    private fun hide() {
        val b = button ?: return
        hiddenByController = true
        b.visibility = View.INVISIBLE
        b.isClickable = false
    }

    private fun show() {
        val b = button ?: return
        hiddenByController = false
        b.visibility = View.VISIBLE
        b.isClickable = true
    }
}
