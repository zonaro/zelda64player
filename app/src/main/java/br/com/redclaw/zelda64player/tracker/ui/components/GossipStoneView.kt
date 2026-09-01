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

package br.com.redclaw.zelda64player.tracker.ui.components

import android.content.Context
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import br.com.redclaw.zelda64player.R

/** Purple Gossip Stone card with a stone icon (no "Pedra" text) + hint text. */
class GossipStoneView(context: Context) : FrameLayout(context) {

    private val iconView: ImageView
    private val textView: TextView

    init {
        val pad = (8 * resources.displayMetrics.density).toInt()
        setPadding(pad, pad, pad, pad)
        setBackgroundResource(R.drawable.tracker_stone_bg)
        val iconSize = (28 * resources.displayMetrics.density).toInt()
        iconView =
                ImageView(context).apply {
                    // Use an existing drawable as stone icon; fallback to ic_info if missing.
                    val res =
                            try {
                                R.drawable.ic_info
                            } catch (_: Exception) {
                                android.R.drawable.ic_menu_info_details
                            }
                    setImageResource(res)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                }
        textView =
                TextView(context).apply {
                    gravity = Gravity.CENTER
                    setTextColor(context.getColor(android.R.color.white))
                    textSize = 11f
                    maxLines = 3
                }
        val inner =
                android.widget.LinearLayout(context).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    addView(iconView, android.widget.LinearLayout.LayoutParams(iconSize, iconSize))
                    addView(textView)
                }
        addView(inner, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    /** [text] is the hint content; icon is always shown, no label text. */
    fun bind(text: String, onClick: () -> Unit) {
        textView.text = if (text.isBlank()) "" else text
        textView.visibility = if (text.isBlank()) GONE else VISIBLE
        setOnClickListener { onClick() }
    }

    /** Legacy overload kept for callers still passing a label. */
    fun bind(label: String, text: String, onClick: () -> Unit) = bind(text, onClick)
}
