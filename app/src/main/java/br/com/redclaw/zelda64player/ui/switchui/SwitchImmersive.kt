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

import android.app.Activity
import android.os.Build
import android.view.WindowManager
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Reusable helper that puts a Switch-style activity into sticky immersive mode.
 *
 * Hides the status and navigation bars (they re-appear on a transient swipe) and
 * extends the drawing area into the display cutout so the Switch UI fills the whole
 * screen edge-to-edge. Centralised here so every top-level activity shares one
 * implementation instead of re-implementing the insets logic (DRY).
 */
object SwitchImmersive {

    /**
     * Enters sticky immersive mode for [activity].
     *
     * Uses [WindowInsetsControllerCompat] to hide [WindowInsetsCompat.Type.systemBars]
     * with [WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE], and
     * sets [WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES] so
     * content is not letterboxed around a notch (guarded to API 28+).
     *
     * Safe to call multiple times (e.g. from [Activity.onWindowFocusChanged] after a
     * dialog drops immersive mode); repeated calls are idempotent.
     */
    fun enterFullscreen(activity: Activity) {
        val window = activity.window
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
    }
}
