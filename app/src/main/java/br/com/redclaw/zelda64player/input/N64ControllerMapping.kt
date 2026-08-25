/*
 * Zelda 64 Player - native Android N64 emulator frontend for Zelda ROM hacks.
 * Copyright (C) 2026 RedClaw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package br.com.redclaw.zelda64player.input

import android.content.Context
import android.view.KeyEvent
import br.com.redclaw.zelda64player.gamepad.ButtonStickMode

/**
 * Single description of the physical-controller mapping used by the N64 core.
 *
 * [ControllerInput] uses [effectivePhysicalKeyCode] before forwarding a key to
 * the core. The Gamepad Tester uses [n64ControlForPhysicalKey] to present that
 * same mapping without creating a RetroView or sending any input to a game.
 */
object N64ControllerMapping {
    const val PREFERENCES_NAME = "ludere_prefs"
    const val BUTTON_STICK_MODE_PREFERENCE = "button_stick_mode"
    const val AUTO_Z_PREFERENCE = "auto_z_enabled"

    /** N64 control names represented by the tester's N64-shaped layout. */
    enum class Control {
        A, B, C_UP, C_DOWN, C_LEFT, C_RIGHT, Z, L, R, START,
        DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT, ANALOG_STICK
    }

    /**
     * Physical-controller-only C-button arrangement. Touch controls never use
     * this map; their events are already laid out as N64 controls.
     */
    private val physicalCButtonRemap = mapOf(
        KeyEvent.KEYCODE_BUTTON_L1 to KeyEvent.KEYCODE_BUTTON_Y,
        KeyEvent.KEYCODE_BUTTON_Y to KeyEvent.KEYCODE_BUTTON_R1,
        KeyEvent.KEYCODE_BUTTON_R1 to KeyEvent.KEYCODE_BUTTON_L1
    )

    fun effectivePhysicalKeyCode(keyCode: Int): Int = physicalCButtonRemap[keyCode] ?: keyCode

    fun currentProfile(context: Context): Profile {
        val prefs = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
        val modeIndex = prefs.getInt(
            BUTTON_STICK_MODE_PREFERENCE,
            ButtonStickMode.C_RIGHT.ordinal
        )
        return Profile(
            buttonStickMode = ButtonStickMode.values().getOrElse(modeIndex) {
                ButtonStickMode.C_RIGHT
            },
            autoZEnabled = prefs.getBoolean(AUTO_Z_PREFERENCE, true)
        )
    }

    data class Profile(
        val buttonStickMode: ButtonStickMode,
        val autoZEnabled: Boolean
    )

    /**
     * Resolves a raw physical key to the N64 control that [ControllerInput]
     * sends to the core. [autoTarget] is the latest C-button in Auto mode;
     * game sessions and the tester both start it at C-Right.
     */
    fun n64ControlForPhysicalKey(
        keyCode: Int,
        profile: Profile,
        autoTarget: Control = Control.C_RIGHT
    ): Control? = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> Control.A
        KeyEvent.KEYCODE_BUTTON_B -> Control.B
        KeyEvent.KEYCODE_BUTTON_X -> Control.C_DOWN
        KeyEvent.KEYCODE_BUTTON_Y -> Control.C_RIGHT
        KeyEvent.KEYCODE_BUTTON_L1 -> Control.C_UP
        KeyEvent.KEYCODE_BUTTON_R1 -> Control.C_LEFT
        KeyEvent.KEYCODE_BUTTON_L2 -> Control.Z
        KeyEvent.KEYCODE_BUTTON_R2 -> Control.R
        KeyEvent.KEYCODE_BUTTON_SELECT -> Control.L
        KeyEvent.KEYCODE_BUTTON_START -> Control.START
        KeyEvent.KEYCODE_DPAD_UP -> Control.DPAD_UP
        KeyEvent.KEYCODE_DPAD_DOWN -> Control.DPAD_DOWN
        KeyEvent.KEYCODE_DPAD_LEFT -> Control.DPAD_LEFT
        KeyEvent.KEYCODE_DPAD_RIGHT -> Control.DPAD_RIGHT
        KeyEvent.KEYCODE_BUTTON_THUMBL -> if (profile.autoZEnabled) Control.Z else null
        KeyEvent.KEYCODE_BUTTON_THUMBR -> profile.buttonStickMode.toN64Control(autoTarget)
        else -> null
    }

    /** The physical right stick only becomes an N64 analog stick with Button Stick on. */
    fun rightStickControlsN64Analog(profile: Profile): Boolean =
        profile.buttonStickMode != ButtonStickMode.OFF

    private fun ButtonStickMode.toN64Control(autoTarget: Control): Control? = when (this) {
        ButtonStickMode.OFF -> null
        ButtonStickMode.C_RIGHT -> Control.C_RIGHT
        ButtonStickMode.C_LEFT -> Control.C_LEFT
        ButtonStickMode.C_DOWN -> Control.C_DOWN
        ButtonStickMode.A -> Control.A
        ButtonStickMode.B -> Control.B
        ButtonStickMode.AUTO -> autoTarget
    }
}
