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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import br.com.redclaw.zelda64player.Zelda64PlayerApp
import br.com.redclaw.zelda64player.tracker.ui.TrackerDialogFragment
import br.com.redclaw.zelda64player.tracker.ui.TrackerViewModel
import br.com.redclaw.zelda64player.tracker.ui.components.LocationRowView
import br.com.redclaw.zelda64player.ui.switchui.AccentManager

/** Locations grouped by region, each with a checkbox per check. */
class LocationsTab : Fragment() {

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
        val locations = viewModel.locations
        val grouped = locations.groupBy { getString(it.regionRes) }
        val gap = (6 * resources.displayMetrics.density).toInt()
        grouped.forEach { (region, locs) ->
            val header =
                    TextView(requireContext()).apply {
                        text = region
                        setTextColor(AccentManager.getAccentColor(requireContext()))
                        textSize = 15f
                        typeface =
                                android.graphics.Typeface.create(
                                        "sans-serif-medium",
                                        android.graphics.Typeface.BOLD
                                )
                        setPadding(0, gap * 2, 0, gap)
                    }
            container.addView(
                    header,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            )
            locs.forEach { loc ->
                val row = LocationRowView(requireContext())
                row.bind(getString(loc.nameRes), viewModel.isLocationChecked(loc.id)) {
                    sfx?.select()
                    viewModel.toggleLocation(loc.id)
                    row.setChecked(viewModel.isLocationChecked(loc.id))
                }
                container.addView(
                        row,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
            }
        }
    }
}
