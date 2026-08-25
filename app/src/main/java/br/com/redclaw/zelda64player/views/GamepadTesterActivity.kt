/*
 * Zelda 64 Player - native Android N64 emulator frontend for Zelda ROM hacks.
 * Copyright (C) 2026 RedClaw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package br.com.redclaw.zelda64player.views

import android.content.Context
import android.hardware.input.InputManager
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.databinding.ActivityGamepadTesterBinding
import br.com.redclaw.zelda64player.ui.switchui.SwitchImmersive

/**
 * A safe place to inspect a connected physical controller. The screen consumes
 * controller events exclusively for visualization, so no emulation core is
 * created and the game's RadialGamePad layout remains untouched.
 */
class GamepadTesterActivity : AppCompatActivity(), InputManager.InputDeviceListener {
    private lateinit var binding: ActivityGamepadTesterBinding
    private lateinit var inputManager: InputManager
    private var currentDeviceId: Int? = null
    private var connectionLossHandled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        inputManager = getSystemService(Context.INPUT_SERVICE) as InputManager
        val device = findConnectedController()
        if (device == null) {
            Toast.makeText(this, R.string.gamepad_tester_connect_controller, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        binding = ActivityGamepadTesterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.testerBack.setOnClickListener { finish() }
        binding.testerPhysical.setOnClickListener { selectMode(GamepadTesterView.Mode.PHYSICAL) }
        binding.testerN64.setOnClickListener { selectMode(GamepadTesterView.Mode.N64) }
        selectMode(GamepadTesterView.Mode.PHYSICAL)
        updateInputDevice(device)

        window.decorView.setOnApplyWindowInsetsListener { _, insets ->
            window.decorView.post { SwitchImmersive.enterFullscreen(this) }
            insets
        }
    }

    override fun onStart() {
        super.onStart()
        inputManager.registerInputDeviceListener(this, null)
    }

    override fun onResume() {
        super.onResume()
        // onCreate can finish early when launched through a stale deep link
        // without a controller. In that path there is no bound content and the
        // launch toast is the only message the user should receive.
        if (!::binding.isInitialized) return
        if (!hasCurrentController()) {
            finishForDisconnect()
            return
        }
        binding.testerSurface.refreshProfile()
    }

    override fun onStop() {
        inputManager.unregisterInputDeviceListener(this)
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && ::binding.isInitialized) SwitchImmersive.enterFullscreen(this)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!::binding.isInitialized) return super.dispatchKeyEvent(event)
        if (event.keyCode != KeyEvent.KEYCODE_BACK && isController(event.device)) {
            updateInputDevice(event.device)
            binding.testerSurface.handleKeyEvent(event)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!::binding.isInitialized) return super.onGenericMotionEvent(event)
        if (isController(event.device)) {
            updateInputDevice(event.device)
            binding.testerSurface.handleMotionEvent(event)
            return true
        }
        return super.onGenericMotionEvent(event)
    }

    override fun onInputDeviceAdded(deviceId: Int) = Unit

    override fun onInputDeviceRemoved(deviceId: Int) {
        if (deviceId == currentDeviceId) finishForDisconnect()
    }

    override fun onInputDeviceChanged(deviceId: Int) {
        if (deviceId == currentDeviceId && !hasCurrentController()) finishForDisconnect()
    }

    private fun selectMode(mode: GamepadTesterView.Mode) {
        binding.testerPhysical.isSelected = mode == GamepadTesterView.Mode.PHYSICAL
        binding.testerN64.isSelected = mode == GamepadTesterView.Mode.N64
        binding.testerSurface.setMode(mode)
    }

    private fun updateInputDevice(device: InputDevice?) {
        if (device?.id == currentDeviceId) return
        currentDeviceId = device?.id
        binding.testerSurface.setInputDevice(device)
        binding.testerDevice.text = if (device == null) {
            getString(R.string.gamepad_tester_disconnected)
        } else {
            getString(R.string.gamepad_tester_connected, device.name)
        }
    }

    private fun isController(device: InputDevice?): Boolean {
        return isPhysicalController(device)
    }

    private fun hasCurrentController(): Boolean =
        currentDeviceId?.let(InputDevice::getDevice)?.let(::isPhysicalController) == true

    private fun finishForDisconnect() {
        if (connectionLossHandled || isFinishing) return
        connectionLossHandled = true
        Toast.makeText(this, R.string.gamepad_tester_controller_disconnected, Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        /** Used by the Home dock to avoid opening an invalid tester screen. */
        fun hasConnectedController(): Boolean = findConnectedController() != null

        private fun findConnectedController(): InputDevice? = InputDevice.getDeviceIds()
            .asSequence()
            .mapNotNull(InputDevice::getDevice)
            .firstOrNull(::isPhysicalController)

        private fun isPhysicalController(device: InputDevice?): Boolean {
            val sources = device?.sources ?: return false
            val controllerSources =
                InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_DPAD
            return (sources and controllerSources) != 0
        }
    }
}
