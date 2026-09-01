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

package br.com.redclaw.zelda64player.input

import android.view.InputDevice

/**
 * Shared detection of physically-connected game controllers.
 *
 * Extracted from [br.com.redclaw.zelda64player.views.GamepadTesterActivity] so
 * every screen that needs to react to a controller (e.g. hiding its on-screen
 * back button when a physical pad is present) reuses one implementation
 * instead of re-deriving the source mask (DRY).
 */
object InputDeviceUtils {

    /**
     * True when [device] exposes gamepad/joystick/dpad sources, i.e. it is a
     * physical controller rather than a keyboard, mouse or touchscreen.
     */
    fun isPhysicalController(device: InputDevice?): Boolean {
        val sources = device?.sources ?: return false
        val controllerSources =
            InputDevice.SOURCE_GAMEPAD or InputDevice.SOURCE_JOYSTICK or InputDevice.SOURCE_DPAD
        return (sources and controllerSources) != 0
    }

    /** True when any currently attached [InputDevice] is a physical controller. */
    fun hasConnectedController(): Boolean {
        return InputDevice.getDeviceIds()
            .asSequence()
            .mapNotNull(InputDevice::getDevice)
            .any(::isPhysicalController)
    }
}
