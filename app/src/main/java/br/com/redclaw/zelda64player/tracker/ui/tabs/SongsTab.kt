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
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.Zelda64PlayerApp
import br.com.redclaw.zelda64player.tracker.ui.TrackerDialogFragment
import br.com.redclaw.zelda64player.tracker.ui.TrackerViewModel
import br.com.redclaw.zelda64player.ui.switchui.AccentManager

/** Songs list (toggle found). Gossip Stones now live in HintsTab. */
class SongsTab : Fragment() {

        private lateinit var viewModel: TrackerViewModel
        private val sfx = runCatching { Zelda64PlayerApp.sfxManager }.getOrNull()
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
                buildList()
        }

        private fun buildList() {
                container.removeAllViews()
                val gap = (6 * resources.displayMetrics.density).toInt()

                val songsHeader = sectionHeader(getString(R.string.tracker_songs))
                container.addView(songsHeader)

                viewModel.songs.forEach { song ->
                        val row =
                                LinearLayout(requireContext()).apply {
                                        orientation = LinearLayout.HORIZONTAL
                                        gravity = Gravity.CENTER_VERTICAL
                                }
                        val name =
                                TextView(requireContext()).apply {
                                        text = getString(song.nameRes)
                                        setTextColor(context.getColor(R.color.switch_text_primary))
                                        textSize = 14f
                                        layoutParams =
                                                LinearLayout.LayoutParams(
                                                        0,
                                                        LinearLayout.LayoutParams.WRAP_CONTENT,
                                                        1f
                                                )
                                }
                        val toggle =
                                TextView(requireContext()).apply {
                                        setPadding(gap, gap / 2, gap, gap / 2)
                                        setTextColor(context.getColor(android.R.color.white))
                                        background =
                                                GradientDrawable().apply {
                                                        shape = GradientDrawable.RECTANGLE
                                                        setColor(
                                                                AccentManager.getAccentColor(
                                                                        requireContext()
                                                                )
                                                        )
                                                        cornerRadius =
                                                                12f *
                                                                        resources
                                                                                .displayMetrics
                                                                                .density
                                                }
                                        text =
                                                if (viewModel.isSongFound(song.id))
                                                        getString(R.string.tracker_found)
                                                else getString(R.string.tracker_not_found)
                                }
                        row.addView(name)
                        row.addView(toggle)
                        row.setOnClickListener {
                                sfx?.select()
                                viewModel.toggleSong(song.id)
                                toggle.text =
                                        if (viewModel.isSongFound(song.id))
                                                getString(R.string.tracker_found)
                                        else getString(R.string.tracker_not_found_badge)
                        }
                        container.addView(
                                row,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                }
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
