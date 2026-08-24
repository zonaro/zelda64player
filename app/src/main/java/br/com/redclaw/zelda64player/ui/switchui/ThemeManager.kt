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
import androidx.appcompat.app.AppCompatDelegate
import br.com.redclaw.zelda64player.utils.CorePrefs

/**
 * Runtime dark/light theme controller for the Nintendo Switch UI.
 *
 * The choice is persisted in [CorePrefs] (key `pref_switch_theme`) and applied
 * through [AppCompatDelegate.setDefaultNightMode]. Because the app uses a
 * DayNight theme, changing the night mode automatically recreates every
 * running activity, so callers do not need to call `Activity.recreate()`
 * themselves (doing so is harmless). The project default is dark.
 */
object ThemeManager {

    /** Applies the persisted theme as early as possible (call from [android.app.Application.onCreate]). */
    fun applyAtStartup(context: Context) {
        val isLight = CorePrefs.getSwitchTheme(context) == THEME_LIGHT
        AppCompatDelegate.setDefaultNightMode(
            if (isLight) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
        )
    }

    /** Sets the theme explicitly and persists the choice. */
    fun setTheme(context: Context, isLight: Boolean) {
        CorePrefs.setSwitchTheme(context, if (isLight) THEME_LIGHT else THEME_DARK)
        AppCompatDelegate.setDefaultNightMode(
            if (isLight) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
        )
    }

    /**
     * Toggles between dark and light, returning the new light state so callers
     * can update their own UI labels.
     */
    fun toggle(context: Context): Boolean {
        val newLight = CorePrefs.getSwitchTheme(context) != THEME_LIGHT
        setTheme(context, newLight)
        return newLight
    }

    /** Returns true when the light theme is currently selected. */
    fun isLight(context: Context): Boolean =
        CorePrefs.getSwitchTheme(context) == THEME_LIGHT

    private const val THEME_DARK = "dark"
    private const val THEME_LIGHT = "light"
}
