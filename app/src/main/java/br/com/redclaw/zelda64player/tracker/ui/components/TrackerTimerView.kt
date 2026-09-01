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

import android.app.AlertDialog
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.AttributeSet
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.Zelda64PlayerApp
import br.com.redclaw.zelda64player.tracker.ui.TrackerViewModel
import br.com.redclaw.zelda64player.ui.switchui.AccentManager

/** Integrated run timer with start/pause/reset, persisted via [TrackerViewModel]. */
class TrackerTimerView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) :
        LinearLayout(context, attrs, defStyleAttr) {

    private val timeView: TextView
    private val toggleButton: Button
    private val resetButton: Button
    private val sfx = runCatching { Zelda64PlayerApp.sfxManager }.getOrNull()
    private val handler = Handler(Looper.getMainLooper())
    private var viewModel: TrackerViewModel? = null

    private val tick =
            object : Runnable {
                override fun run() {
                    updateDisplay()
                    if (viewModel?.isTimerRunning() == true) handler.postDelayed(this, 500)
                }
            }

    init {
        orientation = HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        val pad = (8 * resources.displayMetrics.density).toInt()
        setPadding(pad, pad / 2, pad, pad / 2)

        timeView =
                TextView(context).apply {
                    setTextColor(context.getColor(R.color.switch_text_primary))
                    textSize = 22f
                    typeface = android.graphics.Typeface.MONOSPACE
                    text = "00:00:00"
                    isLongClickable = true
                    setOnLongClickListener {
                        openEditDialog()
                        true
                    }
                }
        toggleButton =
                Button(context).apply {
                    setText(R.string.tracker_start)
                    isAllCaps = false
                    background = createAccentButtonBg()
                    setTextColor(android.graphics.Color.WHITE)
                    setOnClickListener {
                        sfx?.select()
                        val vm = viewModel ?: return@setOnClickListener
                        if (vm.isTimerRunning()) vm.pauseTimer() else vm.startTimer()
                        updateDisplay()
                        handler.removeCallbacks(tick)
                        handler.post(tick)
                    }
                }
        resetButton =
                Button(context).apply {
                    setText(R.string.tracker_reset)
                    isAllCaps = false
                    background = createAccentButtonBg()
                    setTextColor(android.graphics.Color.WHITE)
                    setOnClickListener {
                        sfx?.back()
                        viewModel?.resetTimer()
                        updateDisplay()
                    }
                }

        addView(timeView, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(toggleButton, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        addView(resetButton, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    fun bind(vm: TrackerViewModel) {
        viewModel = vm
        updateDisplay()
        handler.removeCallbacks(tick)
        if (vm.isTimerRunning()) handler.post(tick)
    }

    private fun updateDisplay() {
        val ms = viewModel?.getElapsedMs() ?: 0L
        timeView.text = format(ms)
        toggleButton.setText(
                if (viewModel?.isTimerRunning() == true) R.string.tracker_pause
                else R.string.tracker_start
        )
    }

    private fun format(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return "%02d:%02d:%02d".format(h, m, s)
    }

    private fun openEditDialog() {
        val vm = viewModel ?: return
        val current = format(vm.getElapsedMs())
        val edit =
                EditText(context).apply {
                    inputType = InputType.TYPE_CLASS_DATETIME
                    hint = "HH:MM:SS"
                    setText(current)
                    setSelection(text.length)
                }
        val outerPad = (4 * resources.displayMetrics.density).toInt()
        val dialogView =
                LinearLayout(context).apply {
                    orientation = VERTICAL
                    setBackgroundResource(R.drawable.bg_switch_dialog)
                    setPadding(outerPad, outerPad, outerPad, outerPad)
                    val inner =
                            LinearLayout(context).apply {
                                orientation = VERTICAL
                                val pad = (16 * resources.displayMetrics.density).toInt()
                                setPadding(pad, pad, pad, pad)
                                addView(
                                        TextView(context).apply {
                                            text =
                                                    context.getString(
                                                            R.string.tracker_edit_time_title
                                                    )
                                            setTextColor(
                                                    context.getColor(R.color.switch_text_primary)
                                            )
                                            textSize = 16f
                                            typeface =
                                                    android.graphics.Typeface.create(
                                                            "sans-serif-medium",
                                                            android.graphics.Typeface.BOLD
                                                    )
                                        }
                                )
                                edit.setTextColor(context.getColor(R.color.switch_text_primary))
                                edit.setHintTextColor(
                                        context.getColor(R.color.switch_text_secondary)
                                )
                                addView(edit)
                            }
                    addView(inner)
                }
        AlertDialog.Builder(context, R.style.SwitchDialogTheme)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    val parsed = parseTime(edit.text.toString())
                    if (parsed != null) {
                        sfx?.select()
                        vm.setElapsedMs(parsed)
                        updateDisplay()
                        handler.removeCallbacks(tick)
                        if (vm.isTimerRunning()) handler.post(tick)
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
    }

    private fun parseTime(input: String): Long? {
        val parts = input.trim().split(":")
        return try {
            val (h, m, s) =
                    when (parts.size) {
                        3 -> Triple(parts[0].toLong(), parts[1].toLong(), parts[2].toLong())
                        2 -> Triple(0L, parts[0].toLong(), parts[1].toLong())
                        1 -> Triple(0L, 0L, parts[0].toLong())
                        else -> return null
                    }
            if (m !in 0..59 || s !in 0..59 || h < 0) return null
            ((h * 3600 + m * 60 + s) * 1000)
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun createAccentButtonBg(): GradientDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(AccentManager.getAccentColor(context))
                cornerRadius = 4f * resources.displayMetrics.density
            }

    fun stop() {
        handler.removeCallbacks(tick)
    }
}
