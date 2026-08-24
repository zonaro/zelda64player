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
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import br.com.redclaw.zelda64player.R

/**
 * Footer hints bar for the Switch home screen. The left side shows a static
 * connected-gamepad indicator; the right side shows the "(i) Sobre" and
 * "+ Opções" hints. Both hints are clickable (touch) and invoke the supplied
 * callbacks; they are intentionally not focusable so D-pad navigation stays on
 * the game row and dock (the dock already exposes the About destination).
 */
class SwitchFooterHints @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var onAbout: (() -> Unit)? = null
    private var onOptions: (() -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.switch_footer_hints, this, true)
        orientation = HORIZONTAL
        findViewById<View>(R.id.footer_about).setOnClickListener { onAbout?.invoke() }
        findViewById<View>(R.id.footer_options).setOnClickListener { onOptions?.invoke() }
    }

    fun setOnAbout(callback: () -> Unit) {
        onAbout = callback
    }

    fun setOnOptions(callback: () -> Unit) {
        onOptions = callback
    }
}
