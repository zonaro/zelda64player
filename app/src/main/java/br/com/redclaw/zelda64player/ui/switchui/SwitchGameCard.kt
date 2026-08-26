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
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.content.ContextCompat
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.Zelda64PlayerApp
import br.com.redclaw.zelda64player.ui.switchui.AccentManager
import br.com.redclaw.zelda64player.views.HackLibraryEntry
import coil.load
import kotlin.math.roundToInt

/**
 * Reusable landscape game card for the Switch home row and grid screen.
 *
 * Shows the cover art fitted inside a 13:9 card (whole image visible, letterboxed
 * by the card background when the source ratio differs), an optional family/type
 * badge, a 10% black dim overlay while unfocused, and an accent focus border while
 * focused. Focus changes also fire [onFocusGained] so the parent row can update its
 * label and play the focus-move sound effect. Click plays the select sound then
 * invokes [onClick].
 *
 * The view is fully self-contained: callers only supply the [HackLibraryEntry]
 * and the click/long-click callbacks. Sizing is controlled by the parent
 * (RecyclerView layout params), so this class stays layout-agnostic.
 */
class SwitchGameCard @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val cover: ImageView
    private val badge: ImageView
    private val dim: View
    private val border: View
    private val sfx = runCatching { Zelda64PlayerApp.sfxManager }.getOrNull()

    /** Invoked when this card gains focus (after the border/dim update). */
    var onFocusGained: (() -> Unit)? = null

    init {
        LayoutInflater.from(context).inflate(R.layout.switch_game_card, this, true)
        cover = findViewById(R.id.card_cover)
        badge = findViewById(R.id.card_badge)
        dim = findViewById(R.id.card_dim)
        border = findViewById(R.id.card_focus_border)
        // Apply dynamic accent color to focus border
        border.background = AccentManager.createFocusBorder(context)
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
    }

    /** Binds [entry] and wires the activate/context-menu callbacks. */
    fun bind(
        entry: HackLibraryEntry,
        onClick: () -> Unit,
        onLongClick: () -> Unit
    ) {
        if (entry.coverUrl != null) {
            cover.load(entry.coverUrl) {
                placeholder(R.drawable.placeholder_cover_wide)
                error(R.drawable.placeholder_cover_wide)
                crossfade(true)
            }
        } else {
            cover.setImageResource(R.drawable.placeholder_cover_wide)
        }
        BadgeBinder.bind(badge, entry)
        tag = entry
        setOnClickListener {
            sfx?.select()
            onClick()
        }
        setOnLongClickListener {
            onLongClick()
            true
        }
    }

    override fun onFocusChanged(
        gainFocus: Boolean,
        direction: Int,
        previouslyFocusedRect: android.graphics.Rect?
    ) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        border.visibility = if (gainFocus) View.VISIBLE else View.GONE
        dim.visibility = if (gainFocus) View.GONE else View.VISIBLE
        if (gainFocus) onFocusGained?.invoke()
    }

    /**
     * Enforces the cover-art aspect ratio (width:height = [COVER_ASPECT_RATIO]) so
     * the card is always a landscape rectangle regardless of how a parent sizes it.
     * The parent (home row / grid) still sets an explicit width; the height is
     * derived here. When the width is unconstrained we defer to the superclass so
     * scroll containers can measure us normally.
     */
    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        if (MeasureSpec.getMode(widthSpec) != MeasureSpec.UNSPECIFIED) {
            val width = MeasureSpec.getSize(widthSpec)
            val height = coverHeight(width)
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
            )
        } else {
            super.onMeasure(widthSpec, heightSpec)
        }
    }

    companion object {
        /**
         * Cover-art aspect ratio (width:height = 13:9, landscape), matching the
         * catalog cover thumbnails (measured mean of the actual assets is ~1.44).
         * Defined once here so the home row, the grid and the "All Games" diameter
         * all stay in sync (DRY).
         */
        const val COVER_ASPECT_RATIO = 13f / 9f

        /** Derives the card height (px) for a given card width (px). */
        fun coverHeight(widthPx: Int): Int = (widthPx / COVER_ASPECT_RATIO).roundToInt()
    }
}
