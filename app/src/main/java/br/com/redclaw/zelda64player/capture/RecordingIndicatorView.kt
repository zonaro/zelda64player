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
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import br.com.redclaw.zelda64player.R

/**
 * Small Switch-style on-screen indicator shown while a screen recording is
 * active. A pulsing red dot plus a "REC" label, drawn with theme-token colors
 * and a rounded panel background consistent with the Nintendo Switch UI.
 *
 * Visibility is driven by [br.com.redclaw.zelda64player.views.GameActivity]
 * observing the ViewModel's `isRecording` state. The dot pulses via a short
 * repeating alpha animation on the main thread.
 */
class RecordingIndicatorView @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val dot: ImageView
    private val label: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var pulsing = false

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val pad = (8 * resources.displayMetrics.density).toInt()
        setPadding(pad, pad / 2, pad, pad / 2)
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = (14 * resources.displayMetrics.density)
            setColor(ContextCompat.getColor(context, R.color.switch_panel))
        }

        dot = ImageView(context).apply {
            val size = (12 * resources.displayMetrics.density).toInt()
            layoutParams = LayoutParams(size, size).apply {
                marginEnd = (8 * resources.displayMetrics.density).toInt()
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#FF3B30"))
            }
            contentDescription = null
        }
        label = TextView(context).apply {
            text = context.getString(R.string.recording_label)
            setTextColor(ContextCompat.getColor(context, R.color.switch_text_primary))
            textSize = 13f
            gravity = Gravity.CENTER_VERTICAL
        }
        addView(dot)
        addView(label)
        visibility = GONE
    }

    /** Show the indicator and start the pulse animation. */
    fun show() {
        visibility = VISIBLE
        if (pulsing) return
        pulsing = true
        pulse()
    }

    /** Hide the indicator and stop the pulse animation. */
    fun hide() {
        visibility = GONE
        pulsing = false
        handler.removeCallbacksAndMessages(null)
        dot.alpha = 1f
    }

    private fun pulse() {
        if (!pulsing) return
        dot.animate()
            .alpha(if (dot.alpha < 0.5f) 1f else 0.3f)
            .setDuration(600L)
            .withEndAction { pulse() }
            .start()
    }
}
