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
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import br.com.redclaw.zelda64player.R

/** A single checkable location row (checkbox + name). */
class LocationRowView(context: Context) : LinearLayout(context) {

    private val checkBox: CheckBox
    private val nameView: TextView

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val pad = (8 * resources.displayMetrics.density).toInt()
        setPadding(pad, pad / 2, pad, pad / 2)
        checkBox = CheckBox(context).apply { isClickable = false }
        nameView =
                TextView(context).apply {
                    setTextColor(context.getColor(R.color.switch_text_primary))
                    textSize = 14f
                }
        addView(checkBox, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(
                nameView,
                LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = pad }
        )
    }

    fun bind(displayName: String, checked: Boolean, onToggle: () -> Unit) {
        nameView.text = displayName
        checkBox.isChecked = checked
        setOnClickListener {
            onToggle()
            // Reflect the new persisted state immediately (LocationsTab's row was not rebinding).
            checkBox.isChecked = !checked
        }
    }

    fun setChecked(checked: Boolean) {
        checkBox.isChecked = checked
    }
}
