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

package br.com.redclaw.zelda64player.tracker.ui.tabs

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.Zelda64PlayerApp
import br.com.redclaw.zelda64player.tracker.ui.TrackerDialogFragment
import br.com.redclaw.zelda64player.tracker.ui.TrackerViewModel
import br.com.redclaw.zelda64player.ui.switchui.AccentManager

/** Upgrade chains (strength, bombs, wallet, ...). Tap a pip to set the level. */
class UpgradesTab : Fragment() {

    private lateinit var viewModel: TrackerViewModel
    private val sfx = runCatching { Zelda64PlayerApp.sfxManager }.getOrNull()
    private lateinit var container: LinearLayout

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, state: Bundle?): View {
        val scroll = ScrollView(requireContext())
        container =
                LinearLayout(requireContext()).apply {
                    orientation = LinearLayout.VERTICAL
                    val pad = (8 * resources.displayMetrics.density).toInt()
                    setPadding(pad, pad, pad, pad)
                }
        scroll.addView(
                container,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        )
        return scroll
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val parent = parentFragment as? TrackerDialogFragment ?: return
        viewModel = parent.viewModel
        buildList()
    }

    private fun buildList() {
        container.removeAllViews()
        val gap = (8 * resources.displayMetrics.density).toInt()
        viewModel.upgrades.forEach { upg ->
            val row =
                    LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.VERTICAL
                        setPadding(0, gap, 0, gap)
                    }
            val name =
                    TextView(requireContext()).apply {
                        text = getString(upg.nameRes)
                        setTextColor(context.getColor(R.color.switch_text_primary))
                        textSize = 14f
                    }
            row.addView(name)
            val pips =
                    LinearLayout(requireContext()).apply {
                        orientation = LinearLayout.HORIZONTAL
                        setPadding(0, gap / 2, 0, 0)
                    }
            val current = viewModel.getUpgradeLevel(upg.id)
            for (level in 0..upg.maxLevel) {
                val pip =
                        Button(requireContext()).apply {
                            text = level.toString()
                            val size = (40 * resources.displayMetrics.density).toInt()
                            layoutParams =
                                    LinearLayout.LayoutParams(size, size).apply {
                                        marginEnd = gap / 2
                                    }
                            val isSelected = level == current && current > 0
                            background =
                                    if (isSelected)
                                            GradientDrawable().apply {
                                                shape = GradientDrawable.RECTANGLE
                                                setColor(
                                                        AccentManager.getAccentColor(
                                                                requireContext()
                                                        )
                                                )
                                                cornerRadius =
                                                        12f * resources.displayMetrics.density
                                            }
                                    else
                                            GradientDrawable().apply {
                                                shape = GradientDrawable.RECTANGLE
                                                setColor(
                                                        requireContext()
                                                                .getColor(R.color.switch_panel)
                                                )
                                                setStroke(
                                                        (1 * resources.displayMetrics.density)
                                                                .toInt(),
                                                        requireContext()
                                                                .getColor(
                                                                        R.color
                                                                                .switch_text_secondary
                                                                )
                                                )
                                                cornerRadius =
                                                        12f * resources.displayMetrics.density
                                            }
                            setTextColor(
                                    if (isSelected) android.graphics.Color.WHITE
                                    else requireContext().getColor(R.color.switch_text_primary)
                            )
                            setOnClickListener {
                                sfx?.select()
                                viewModel.setUpgradeLevel(upg.id, level)
                                buildList()
                            }
                        }
                pips.addView(pip)
            }
            row.addView(pips)
            container.addView(row)
        }
    }
}
