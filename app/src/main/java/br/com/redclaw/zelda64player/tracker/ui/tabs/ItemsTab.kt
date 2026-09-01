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

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.fragment.app.Fragment
import br.com.redclaw.zelda64player.Zelda64PlayerApp
import br.com.redclaw.zelda64player.tracker.model.TrackerItem
import br.com.redclaw.zelda64player.tracker.ui.TrackerDialogFragment
import br.com.redclaw.zelda64player.tracker.ui.TrackerViewModel
import br.com.redclaw.zelda64player.tracker.ui.components.ItemIconView

/** Manual inventory grid. Tap an item to cycle obtained count (0..maxCount). */
class ItemsTab : Fragment() {

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
        val parent = parentFragment as? TrackerDialogFragment
        if (parent == null) {
            // Parent not attached yet; skip building the grid this time.
            return
        }
        viewModel = parent.viewModel
        buildGrid()
    }

    private fun buildGrid() {
        container.removeAllViews()
        val items = viewModel.items
        val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val columns = if (isLandscape) 6 else 3
        val gap = (8 * resources.displayMetrics.density).toInt()
        var row: LinearLayout? = null
        items.forEachIndexed { index, item ->
            if (index % columns == 0) {
                row =
                        LinearLayout(requireContext()).apply {
                            orientation = LinearLayout.HORIZONTAL
                            setPadding(0, 0, 0, gap)
                        }
                container.addView(
                        row,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
            val cell = ItemIconView(requireContext())
            cell.bind(
                    item,
                    getString(item.nameRes),
                    viewModel.isItemObtained(item.id),
                    viewModel.getItemCount(item.id)
            )
            cell.layoutParams =
                    LinearLayout.LayoutParams(
                                    0,
                                    (96 * resources.displayMetrics.density).toInt(),
                                    1f
                            )
                            .apply { marginEnd = gap }
            cell.setRupeesMode(false)
            cell.setOnMinusClickListener(null)
            cell.setOnPlusClickListener(null)
            cell.setOnLongClickListener(null)
            cell.setOnClickListener {
                sfx?.select()
                viewModel.cycleItem(item.id, item.maxCount)
                rebindCell(cell, item)
            }
            row?.addView(cell)
        }
    }

    private fun rebindCell(cell: ItemIconView, item: TrackerItem) {
        cell.bind(
                item,
                getString(item.nameRes),
                viewModel.isItemObtained(item.id),
                viewModel.getItemCount(item.id)
        )
    }
}
