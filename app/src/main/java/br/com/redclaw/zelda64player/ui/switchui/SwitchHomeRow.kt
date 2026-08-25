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

package br.com.redclaw.zelda64player.ui.switchui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.Zelda64PlayerApp
import br.com.redclaw.zelda64player.views.HackLibraryEntry

/**
 * Switch-style home row: a focused-game label above a horizontal RecyclerView of
 * landscape [SwitchGameCard]s followed by the circular [SwitchAllGamesCard].
 *
 * Owns the focus traversal and sound-effect wiring for the row: when a child card
 * gains focus the label above the row is updated with that entry's title and the
 * focus-move "toc" is played (suppressed only for the very first, programmatic
 * focus so the UI does not tick on cold start). The parent (LibraryActivity)
 * supplies the data and the activate/menu/all-games callbacks; this component
 * stays presentation-only.
 *
 * Entry ordering (vanilla -> store hacks) is produced
 * upstream by [br.com.redclaw.zelda64player.views.InstalledLibrary] and passed in
 * via [submitList]; this class does not reorder or re-source the data.
 */
class SwitchHomeRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val label: TextView
    private val recycler: RecyclerView
    private val sfx = runCatching { Zelda64PlayerApp.sfxManager }.getOrNull()

    private var entries: List<HackLibraryEntry> = emptyList()
    private var onActivate: ((HackLibraryEntry) -> Unit)? = null
    private var onMenu: ((HackLibraryEntry) -> Unit)? = null
    private var onAllGames: (() -> Unit)? = null
    private var initialFocusDone = false

    init {
        LayoutInflater.from(context).inflate(R.layout.switch_home_row, this, true)
        orientation = VERTICAL
        label = findViewById(R.id.home_row_label)
        recycler = findViewById(R.id.home_row_recycler)
        recycler.layoutManager =
            LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        // The row height must grow with the portrait cover ratio (5:7) so the
        // rectangular cards are not clipped. The ratio lives in SwitchGameCard (DRY).
        val homeSize = resources.getDimensionPixelSize(R.dimen.switch_card_size_home)
        recycler.layoutParams.height = SwitchGameCard.coverHeight(homeSize)
        // A card's half-gap is part of its layout params. Offset RecyclerView
        // padding by that amount so the selected-title baseline and first cover
        // align, as they do on the Switch HOME row.
        val screenMargin = resources.getDimensionPixelSize(R.dimen.switch_screen_margin)
        val halfGap = resources.getDimensionPixelSize(R.dimen.switch_home_card_gap) / 2
        recycler.setPadding(
            screenMargin - halfGap,
            recycler.paddingTop,
            screenMargin - halfGap,
            recycler.paddingBottom
        )
        recycler.adapter = HomeAdapter()
    }

    fun setOnEntryActivate(callback: (HackLibraryEntry) -> Unit) {
        onActivate = callback
    }

    fun setOnEntryMenu(callback: (HackLibraryEntry) -> Unit) {
        onMenu = callback
    }

    fun setOnAllGamesActivate(callback: () -> Unit) {
        onAllGames = callback
    }

    /** Replaces the row contents and requests focus on the first card. */
    fun submitList(list: List<HackLibraryEntry>) {
        entries = list
        recycler.adapter?.notifyDataSetChanged()
        if (list.isNotEmpty()) {
            // Make the first title visible before RecyclerView finishes its
            // first focus pass; the focus callback keeps it current afterwards.
            label.text = list.first().title
            recycler.post {
                val vh = recycler.findViewHolderForAdapterPosition(0)
                vh?.itemView?.requestFocus()
            }
        } else {
            label.text = ""
        }
    }

    private fun playFocusTick() {
        if (initialFocusDone) sfx?.focusMove() else initialFocusDone = true
    }

    private inner class HomeAdapter :
        RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val VIEW_GAME = 0
        private val VIEW_ALL_GAMES = 1

        override fun getItemCount(): Int = entries.size + 1

        override fun getItemViewType(position: Int): Int =
            if (position < entries.size) VIEW_GAME else VIEW_ALL_GAMES

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int
        ): RecyclerView.ViewHolder {
            val size = resources.getDimensionPixelSize(R.dimen.switch_card_size_home)
            val gap = resources.getDimensionPixelSize(R.dimen.switch_home_card_gap)
            val cardH = SwitchGameCard.coverHeight(size)
            val lp = RecyclerView.LayoutParams(size, cardH).apply {
                marginStart = gap / 2
                marginEnd = gap / 2
            }
            return if (viewType == VIEW_GAME) {
                val card = SwitchGameCard(parent.context)
                card.layoutParams = lp
                GameViewHolder(card)
            } else {
                val card = SwitchAllGamesCard(parent.context)
                // Circular "Todos os Jogos" card: diameter matches the new card
                // height so the row aligns visually with the rectangular game cards.
                card.layoutParams = RecyclerView.LayoutParams(cardH, cardH).apply {
                    marginStart = gap / 2
                    marginEnd = gap / 2
                }
                AllGamesViewHolder(card)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is GameViewHolder) {
                val entry = entries[position]
                holder.card.bind(
                    entry,
                    onClick = { onActivate?.invoke(entry) },
                    onLongClick = { onMenu?.invoke(entry) }
                )
                holder.card.onFocusGained = {
                    label.text = entry.title
                    playFocusTick()
                }
            } else if (holder is AllGamesViewHolder) {
                holder.card.bind(onClick = { onAllGames?.invoke() })
                holder.card.onFocusGained = {
                    label.text = context.getString(R.string.all_games)
                    playFocusTick()
                }
            }
        }
    }

    private class GameViewHolder(val card: SwitchGameCard) :
        RecyclerView.ViewHolder(card)

    private class AllGamesViewHolder(val card: SwitchAllGamesCard) :
        RecyclerView.ViewHolder(card)

    companion object {
        /** Sentinel tag for the circular "Todos os Jogos" card. */
        val ALL_GAMES_TAG = Any()
    }
}
