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

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.tracker.model.TrackerHintType
import br.com.redclaw.zelda64player.tracker.ui.TrackerDialogFragment
import br.com.redclaw.zelda64player.tracker.ui.TrackerViewModel
import br.com.redclaw.zelda64player.ui.switchui.AccentManager

/** WOTH (5), Barren (3) and free Gossip Stone hint slots, all editable. */
class HintsTab : Fragment() {

        private lateinit var viewModel: TrackerViewModel
        private lateinit var container: LinearLayout

        override fun onCreateView(
                inflater: LayoutInflater,
                parent: ViewGroup?,
                state: Bundle?
        ): View {
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
                buildList(container)
        }

        private fun buildList(container: LinearLayout) {
                container.removeAllViews()
                val gap = (6 * resources.displayMetrics.density).toInt()
                val woth = viewModel.getHints().filter { it.type == TrackerHintType.WOTH }
                val barren = viewModel.getHints().filter { it.type == TrackerHintType.BARREN }
                val stones = viewModel.getHints().filter { it.type == TrackerHintType.STONE }

                container.addView(sectionHeader(getString(R.string.tracker_hint_woth)))
                woth.forEachIndexed { i, hint ->
                        container.addView(
                                hintRow(
                                        "${getString(R.string.tracker_hint_woth)} ${i + 1}",
                                        hint.id,
                                        gap
                                )
                        )
                }

                container.addView(sectionHeader(getString(R.string.tracker_hint_barren)))
                barren.forEachIndexed { i, hint ->
                        container.addView(
                                hintRow(
                                        "${getString(R.string.tracker_hint_barren)} ${i + 1}",
                                        hint.id,
                                        gap
                                )
                        )
                }

                // Gossip Stones moved here from SongsTab — purple cards with stone icon.
                container.addView(sectionHeader(getString(R.string.tracker_gossip_stones)))
                val stoneGridGap = (8 * resources.displayMetrics.density).toInt()
                val stoneSize = (88 * resources.displayMetrics.density).toInt()
                val grid =
                        android.widget.LinearLayout(requireContext()).apply {
                                orientation = android.widget.LinearLayout.HORIZONTAL
                                setPadding(0, stoneGridGap, 0, stoneGridGap)
                        }
                stones.forEach { hint ->
                        val stone =
                                br.com.redclaw.zelda64player.tracker.ui.components.GossipStoneView(
                                        requireContext()
                                )
                        stone.bind(hint.text) { openStoneEditor(hint.id) }
                        stone.layoutParams =
                                android.widget.LinearLayout.LayoutParams(stoneSize, stoneSize, 1f)
                                        .apply { marginEnd = stoneGridGap }
                        grid.addView(stone)
                }
                container.addView(grid)
        }

        private fun openStoneEditor(hintId: String) {
                val edit =
                        android.widget.EditText(requireContext()).apply {
                                inputType = android.text.InputType.TYPE_CLASS_TEXT
                                setText(viewModel.getHints().find { it.id == hintId }?.text ?: "")
                                setTextColor(context.getColor(R.color.switch_text_primary))
                                setHintTextColor(context.getColor(R.color.switch_text_secondary))
                        }
                val pad = (16 * resources.displayMetrics.density).toInt()
                val inner =
                        android.widget.LinearLayout(requireContext()).apply {
                                orientation = android.widget.LinearLayout.VERTICAL
                                setPadding(pad, pad, pad, pad)
                                addView(edit)
                        }
                val dialogView =
                        android.widget.LinearLayout(requireContext()).apply {
                                orientation = android.widget.LinearLayout.VERTICAL
                                setBackgroundResource(R.drawable.bg_switch_dialog)
                                val innerPad = (4 * resources.displayMetrics.density).toInt()
                                setPadding(innerPad, innerPad, innerPad, innerPad)
                                addView(inner)
                        }
                android.app.AlertDialog.Builder(requireContext(), R.style.SwitchDialogTheme)
                        .setTitle(R.string.tracker_stone)
                        .setView(dialogView)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                                viewModel.setHintText(hintId, edit.text.toString())
                                buildList(container)
                        }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
        }

        private fun hintRow(label: String, hintId: String, gap: Int): LinearLayout {
                val row =
                        LinearLayout(requireContext()).apply {
                                orientation = LinearLayout.VERTICAL
                                setPadding(0, gap, 0, gap)
                        }
                val title =
                        TextView(requireContext()).apply {
                                text = label
                                setTextColor(context.getColor(R.color.switch_text_secondary))
                                textSize = 13f
                        }
                val edit =
                        EditText(requireContext()).apply {
                                setText(viewModel.getHints().find { it.id == hintId }?.text ?: "")
                                setTextColor(context.getColor(R.color.switch_text_primary))
                                addTextChangedListener(
                                        object : TextWatcher {
                                                override fun beforeTextChanged(
                                                        s: CharSequence?,
                                                        start: Int,
                                                        before: Int,
                                                        count: Int
                                                ) {}
                                                override fun onTextChanged(
                                                        s: CharSequence?,
                                                        start: Int,
                                                        before: Int,
                                                        count: Int
                                                ) {}
                                                override fun afterTextChanged(s: Editable?) {
                                                        viewModel.setHintText(
                                                                hintId,
                                                                s?.toString() ?: ""
                                                        )
                                                }
                                        }
                                )
                        }
                row.addView(title)
                row.addView(edit)
                return row
        }

        private fun sectionHeader(text: String): TextView =
                TextView(requireContext()).apply {
                        this.text = text
                        setTextColor(AccentManager.getAccentColor(requireContext()))
                        textSize = 15f
                        typeface =
                                android.graphics.Typeface.create(
                                        "sans-serif-medium",
                                        android.graphics.Typeface.BOLD
                                )
                        setPadding(
                                0,
                                (12 * resources.displayMetrics.density).toInt(),
                                0,
                                (6 * resources.displayMetrics.density).toInt()
                        )
                }
}
