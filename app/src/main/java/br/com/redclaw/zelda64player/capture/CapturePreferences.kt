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

package br.com.redclaw.zelda64player.capture

import android.content.Context
import android.content.SharedPreferences

/**
 * Reads and writes the screen-capture / recording preferences.
 *
 * Follows the project's existing SharedPreferences convention (a named
 * preference file accessed with [Context.MODE_PRIVATE]). The only setting today
 * is [pref_capture_include_overlay], which decides whether a screen recording
 * includes the on-screen controls (true) or hides them (false). Screenshots
 * always capture both variants regardless of this flag.
 */
object CapturePreferences {

    private const val PREFS_NAME = "capture_prefs"
    const val KEY_INCLUDE_OVERLAY = "pref_capture_include_overlay"

    /** Default: include the controls in recordings. */
    private const val DEFAULT_INCLUDE_OVERLAY = true

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Whether recordings should include the on-screen control overlays. */
    fun getIncludeOverlay(context: Context): Boolean =
        prefs(context).getBoolean(KEY_INCLUDE_OVERLAY, DEFAULT_INCLUDE_OVERLAY)

    /** Persist the recording overlay preference. */
    fun setIncludeOverlay(context: Context, include: Boolean) {
        prefs(context).edit().putBoolean(KEY_INCLUDE_OVERLAY, include).apply()
    }
}
