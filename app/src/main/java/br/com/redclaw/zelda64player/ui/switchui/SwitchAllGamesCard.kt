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
import android.widget.FrameLayout
import android.widget.ImageView
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.Zelda64PlayerApp
import br.com.redclaw.zelda64player.ui.switchui.AccentManager

/**
 * Circular "Todos os Jogos" (All Games) card that terminates the Switch home row.
 *
 * Charcoal fill with a centered accent-colored grid glyph and a round focus
 * border. While focused or pressed the circle itself brightens via a round glow
 * overlay; the default square elevation/ripple feedback is disabled so no
 * square artifacts ever appear over the circle.
 * Tagged with [SwitchHomeRow.ALL_GAMES_TAG] so physical-key handling in
 * [br.com.redclaw.zelda64player.views.LibraryActivity] can activate it like any
 * other tile. Click plays the select sound then invokes [onClick].
 */
class SwitchAllGamesCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val border: View
    private val glow: View
    private val sfx = runCatching { Zelda64PlayerApp.sfxManager }.getOrNull()

    /** Invoked when this card gains focus (after the border update). */
    var onFocusGained: (() -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.switch_all_games_card, this, true)
        border = findViewById(R.id.border)
        glow = findViewById(R.id.circle_glow)
        val icon = findViewById<ImageView>(R.id.icon)
        // Apply dynamic accent color to round focus border and icon
        border.background = AccentManager.createRoundFocusBorder(context)
        icon.setColorFilter(AccentManager.getAccentColor(context))
        // Kill the default square elevation/ripple feedback; the round glow
        // overlay below is the only selection/hover indication.
        stateListAnimator = null
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
    }

    /** Wires the activate callback (opens the fullscreen grid). */
    fun bind(onClick: () -> Unit) {
        tag = SwitchHomeRow.ALL_GAMES_TAG
        setOnClickListener {
            sfx?.select()
            onClick()
        }
    }

    override fun onFocusChanged(
        gainFocus: Boolean,
        direction: Int,
        previouslyFocusedRect: android.graphics.Rect?
    ) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        border.visibility = if (gainFocus) View.VISIBLE else View.GONE
        glow.visibility = if (gainFocus || isPressed) View.VISIBLE else View.INVISIBLE
        if (gainFocus) onFocusGained?.invoke()
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
