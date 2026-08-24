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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.Zelda64PlayerApp

/**
 * Bottom dock of the Switch home screen: a centered row of circular buttons
 * (Loja, Randomizador, RetroAchievements, Sobre e Licenças). Each button shows a
 * white glyph on a dock-circle background and gains a round cyan focus border when
 * focused; the glyph brightens slightly on focus. Click plays the select sound
 * then runs the button's [DockItem.action].
 *
 * Buttons are described by [DockItem] data objects (icon + label + action) so the
 * dock stays reusable and the parent only supplies the destination callbacks.
 */
class SwitchDock @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val sfx = runCatching { Zelda64PlayerApp.sfxManager }.getOrNull()

    /** A single dock destination. */
    data class DockItem(
        val iconRes: Int,
        val labelRes: Int,
        val action: () -> Unit
    )

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
    }

    /** Rebuilds the dock with [items]. */
    fun setItems(items: List<DockItem>) {
        removeAllViews()
        val size = resources.getDimensionPixelSize(R.dimen.switch_dock_button_size)
        val gap = resources.getDimensionPixelSize(R.dimen.switch_dock_gap)
        for (item in items) {
            val button = DockButton(context)
            button.layoutParams = LayoutParams(size, size).apply {
                marginStart = gap / 2
                marginEnd = gap / 2
            }
            button.bind(item)
            addView(button)
        }
    }

    private inner class DockButton(context: Context) : FrameLayout(context) {
        private val icon: ImageView
        private val border: View
        private val glow: View

        init {
            LayoutInflater.from(context).inflate(R.layout.switch_dock_button, this, true)
            icon = findViewById(R.id.dock_icon)
            border = findViewById(R.id.dock_border)
            glow = findViewById(R.id.dock_glow)
            // Kill the default square elevation/ripple feedback; the round glow
            // overlay is the only focus/press indication.
            stateListAnimator = null
            isFocusable = true
            isFocusableInTouchMode = true
            isClickable = true
        }

        fun bind(item: DockItem) {
            icon.setImageResource(item.iconRes)
            icon.contentDescription = context.getString(item.labelRes)
            tag = item
            setOnClickListener {
                sfx?.select()
                item.action()
            }
        }

        override fun onFocusChanged(
            gainFocus: Boolean,
            direction: Int,
            previouslyFocusedRect: android.graphics.Rect?
        ) {
            super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
            border.visibility = if (gainFocus) View.VISIBLE else View.GONE
            icon.alpha = if (gainFocus) 1.0f else 0.85f
            glow.visibility = if (gainFocus || isPressed) View.VISIBLE else View.INVISIBLE
        }

        override fun drawableStateChanged() {
            super.drawableStateChanged()
            if (glow.visibility == View.VISIBLE && !isPressed && !isFocused) {
                glow.visibility = View.INVISIBLE
            } else if (isPressed) {
                glow.visibility = View.VISIBLE
            }
        }
    }
}
