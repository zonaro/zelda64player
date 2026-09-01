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
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.tracker.model.TrackerItem
import br.com.redclaw.zelda64player.ui.switchui.AccentManager

/**
 * Square, Switch-style card representing one inventory item. Tap toggles the obtained state (and
 * cycles stackable counts). Shows a check overlay + count badge when obtained.
 */
class ItemIconView(context: Context) : FrameLayout(context) {

    private val iconView: ImageView
    private val nameView: TextView
    private val badgeView: TextView
    private val checkView: TextView
    private val minusView: TextView
    private val plusView: TextView

    init {
        val pad = (6 * resources.displayMetrics.density).toInt()
        setPadding(pad, pad, pad, pad)
        background = createCardBg()
        iconView =
                ImageView(context).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    adjustViewBounds = true
                }
        nameView =
                TextView(context).apply {
                    gravity = Gravity.CENTER
                    setTextColor(context.getColor(R.color.switch_text_primary))
                    textSize = 10f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                }
        badgeView =
                TextView(context).apply {
                    gravity = Gravity.CENTER
                    setTextColor(context.getColor(android.R.color.white))
                    textSize = 12f
                    background = createBadgeBg()
                    visibility = GONE
                }
        checkView =
                TextView(context).apply {
                    text = "✓"
                    gravity = Gravity.CENTER
                    setTextColor(AccentManager.getAccentColor(context))
                    textSize = 16f
                    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                    visibility = GONE
                }
        val stepperSize = (32 * resources.displayMetrics.density).toInt()
        val stepperMargin = (4 * resources.displayMetrics.density).toInt()
        minusView =
                TextView(context).apply {
                    text = "−"
                    gravity = Gravity.CENTER
                    setTextColor(context.getColor(android.R.color.white))
                    textSize = 18f
                    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                    background = createStepperBg()
                    visibility = GONE
                    isClickable = true
                    isFocusable = true
                }
        plusView =
                TextView(context).apply {
                    text = "+"
                    gravity = Gravity.CENTER
                    setTextColor(context.getColor(android.R.color.white))
                    textSize = 18f
                    typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                    background = createStepperBg()
                    visibility = GONE
                    isClickable = true
                    isFocusable = true
                }
        addView(iconView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        addView(nameView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(badgeView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(checkView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(
                minusView,
                LayoutParams(stepperSize, stepperSize).apply {
                    gravity = Gravity.BOTTOM or Gravity.START
                    setMargins(stepperMargin, 0, 0, stepperMargin)
                }
        )
        addView(
                plusView,
                LayoutParams(stepperSize, stepperSize).apply {
                    gravity = Gravity.BOTTOM or Gravity.END
                    setMargins(0, 0, stepperMargin, stepperMargin)
                }
        )
        (nameView.layoutParams as LayoutParams).gravity = Gravity.BOTTOM
        (badgeView.layoutParams as LayoutParams).gravity = Gravity.TOP or Gravity.END
        (checkView.layoutParams as LayoutParams).gravity = Gravity.TOP or Gravity.START
    }

    fun bind(item: TrackerItem, displayName: String, obtained: Boolean, count: Int) {
        // Cyclic items (hookshot, ocarina) show the variant icon/label for the current count.
        val effectiveIcon =
                if (item.isCyclic && count in 1..item.cycleIcons.size) item.cycleIcons[count - 1]
                else item.iconRes
        val effectiveLabel =
                if (item.isCyclic && count in 1..item.cycleLabels.size)
                        context.getString(item.cycleLabels[count - 1])
                else displayName
        if (effectiveIcon != 0) {
            iconView.setImageResource(effectiveIcon)
            iconView.visibility = VISIBLE
        } else {
            iconView.visibility = GONE
        }
        nameView.text = effectiveLabel
        val nameLp = nameView.layoutParams as LayoutParams
        nameLp.gravity = if (effectiveIcon != 0) Gravity.BOTTOM else Gravity.CENTER
        nameView.layoutParams = nameLp
        alpha = if (obtained) 1f else 0.45f
        checkView.visibility = if (obtained) VISIBLE else GONE
        if (item.isStackable && count > 0) {
            badgeView.visibility = VISIBLE
            badgeView.text = count.toString()
        } else {
            badgeView.visibility = GONE
        }
    }

    fun setRupeesMode(enabled: Boolean) {
        minusView.visibility = if (enabled) VISIBLE else GONE
        plusView.visibility = if (enabled) VISIBLE else GONE
        // Rupees uses stepper + long-press; hide the generic check overlay to avoid clutter
        // (badge still shows the count). Keep alpha handling in bind().
        if (enabled) checkView.visibility = GONE
        isLongClickable = enabled
    }

    fun setOnMinusClickListener(listener: OnClickListener?) {
        minusView.setOnClickListener(listener)
    }

    fun setOnPlusClickListener(listener: OnClickListener?) {
        plusView.setOnClickListener(listener)
    }

    private fun createCardBg(): GradientDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(context.getColor(R.color.switch_panel))
                setStroke(
                        (2 * resources.displayMetrics.density).toInt(),
                        AccentManager.getAccentColor(context)
                )
                cornerRadius = 8f * resources.displayMetrics.density
            }

    private fun createBadgeBg(): GradientDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(AccentManager.getAccentColor(context))
                cornerRadius = 12f * resources.displayMetrics.density
            }

    private fun createStepperBg(): GradientDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(AccentManager.getAccentColor(context))
            }
}
