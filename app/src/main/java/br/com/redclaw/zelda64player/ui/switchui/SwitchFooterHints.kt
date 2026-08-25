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
import android.util.AttributeSet
import android.view.InputDevice
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import br.com.redclaw.zelda64player.R

/**
 * Footer hints bar for the Switch home screen. The left side shows a live
 * connected-gamepad indicator (name + count, or a disconnected state); the
 * right side shows the "(i) Sobre" and "+ Opções" hints. Both hints are
 * clickable (touch) and invoke the supplied callbacks; they are intentionally
 * not focusable so D-pad navigation stays on the game row and dock (the dock
 * already exposes the About destination).
 */
class SwitchFooterHints @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var onAbout: (() -> Unit)? = null
    private var onOptions: (() -> Unit)? = null

    private lateinit var gamepadText: TextView
    private lateinit var gamepadIcon: ImageView

    private val inputManager by lazy { context.getSystemService(InputManager::class.java) }

    private val deviceListener = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = updateGamepadState()
        override fun onInputDeviceRemoved(deviceId: Int) = updateGamepadState()
        override fun onInputDeviceChanged(deviceId: Int) = updateGamepadState()
    }

    init {
        LayoutInflater.from(context).inflate(R.layout.switch_footer_hints, this, true)
        orientation = HORIZONTAL
        gamepadText = findViewById(R.id.footer_gamepad_text)
        gamepadIcon = findViewById(R.id.footer_gamepad_icon)
        findViewById<View>(R.id.footer_about).setOnClickListener { onAbout?.invoke() }
        findViewById<View>(R.id.footer_options).setOnClickListener { onOptions?.invoke() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        inputManager.registerInputDeviceListener(deviceListener, null)
        updateGamepadState()
    }

    override fun onDetachedFromWindow() {
        inputManager.unregisterInputDeviceListener(deviceListener)
        super.onDetachedFromWindow()
    }

    /**
     * Reads the currently connected gamepads using the same criterion as
     * [br.com.redclaw.zelda64player.gamepad.GamePad] (a device whose sources
     * include SOURCE_GAMEPAD), then updates the footer text and icon tint.
     */
    private fun updateGamepadState() {
        val names = mutableListOf<String>()
        for (id in InputDevice.getDeviceIds()) {
            val device = InputDevice.getDevice(id) ?: continue
            if (device.sources and InputDevice.SOURCE_GAMEPAD == InputDevice.SOURCE_GAMEPAD) {
                names.add(device.name)
            }
        }

        val text = when (names.size) {
            0 -> context.getString(R.string.footer_gamepad_disconnected)
            1 -> context.getString(R.string.footer_gamepad_connected, names[0])
            else -> context.getString(
                R.string.footer_gamepad_connected_multiple,
                names[0],
                names.size - 1
            )
        }
        gamepadText.text = text

        val tint = if (names.isEmpty()) {
            R.color.switch_text_secondary
        } else {
            R.color.switch_accent
        }
        gamepadIcon.setColorFilter(context.getColor(tint))
    }

    fun setOnAbout(callback: () -> Unit) {
        onAbout = callback
    }

    fun setOnOptions(callback: () -> Unit) {
        onOptions = callback
    }
}
