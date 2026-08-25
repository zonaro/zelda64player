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

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.Zelda64PlayerApp
import br.com.redclaw.zelda64player.ui.switchui.SwitchImmersive
import br.com.redclaw.zelda64player.databinding.ActivitySwitchGridBinding
import br.com.redclaw.zelda64player.utils.CorePrefs
import br.com.redclaw.zelda64player.viewmodels.LibraryMenuController
import br.com.redclaw.zelda64player.viewmodels.LibraryMenuHostDelegate
import br.com.redclaw.zelda64player.views.GridSortMode
import br.com.redclaw.zelda64player.views.HackLibraryEntry
import br.com.redclaw.zelda64player.views.InstalledLibrary

/**
 * Fullscreen "Todos os Jogos" (All Games) grid, opened from the home row's
 * circular card. Mirrors the Nintendo Switch HOME menu's All Software screen:
 * a header (back button + "Todos os Jogos" icon/title + full-width separator),
 * a live search/filter bar with a sort button, and a grid of square
 * [SwitchGameCard]s showing every installed entry with one extra decorative row
 * of ghost placeholders.
 *
 * Entries are aggregated by [InstalledLibrary.sortedEntries], which reuses the
 * exact same source of truth the home row and shortcut sync use (DRY) and applies
 * the user's chosen sort: alphabetical by default, with "last played" and
 * "download date" alternatives. The chosen sort is persisted in
 * [CorePrefs] (key `pref_grid_sort`) and re-applied whenever the library reloads
 * (e.g. returning from a game). The active search filter is applied on top of the
 * sorted list.
 *
 * The per-game context menu reuses [LibraryMenuHostDelegate] + [LibraryMenuController],
 * so the grid's long-press actions (uninstall, delete-seed, export/import saves,
 * pin, achievements) are identical to LibraryActivity's with zero duplicated logic.
 *
 * Click launches the game through the same [LibraryMenuHostDelegate.launchGame]
 * path the home row uses (which ends at [br.com.redclaw.zelda64player.views.GameActivity]
 * and the shared [br.com.redclaw.zelda64player.repositories.GameRomResolver]).
 */
class SwitchGridActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySwitchGridBinding
    private lateinit var menuHost: LibraryMenuHostDelegate
    private lateinit var menuController: LibraryMenuController
    private lateinit var adapter: GridAdapter

    private val sfx = runCatching { Zelda64PlayerApp.sfxManager }.getOrNull()

    /** All installed entries (unfiltered), the source for live search filtering. */
    private var allEntries: List<HackLibraryEntry> = emptyList()

    /** Entries matching the current search query. */
    private var filtered: List<HackLibraryEntry> = emptyList()

    /** Active grid sort mode, restored from [CorePrefs] (default alphabetical). */
    private var sortMode: GridSortMode = GridSortMode.ALPHA

    /**
     * Sort options shown in the sort dialog, in display order. The dialog's
     * checked index maps 1:1 to this list's indices.
     */
    private val sortOptions = listOf(
        GridSortMode.LAST_PLAYED,
        GridSortMode.DOWNLOAD_DATE,
        GridSortMode.ALPHA
    )

    /** Computed grid metrics (responsive: cards stay ~170dp, columns fill width). */
    private var cardSizePx = 0
    private var cardGapPx = 0
    private var spanCount = 2

    /** Suppresses the focus-move "toc" on the very first (programmatic) focus. */
    private var initialFocusDone = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySwitchGridBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SwitchImmersive.enterFullscreen(this)

        menuHost = LibraryMenuHostDelegate(this) { refreshGrid() }
        menuController = LibraryMenuController(menuHost)

        computeGridMetrics()
        sortMode = GridSortMode.fromPref(CorePrefs.getGridSort(this))
        allEntries = InstalledLibrary.sortedEntries(this, sortMode)

        binding.gridBack.setOnClickListener { goBack() }
        binding.gridSort.setOnClickListener { openSortMenu() }
        binding.gridSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) = refreshGrid()
        })

        setupGrid()
        refreshGrid()

        // Focus the first card on cold start (matches the home row); never steal
        // focus back while the user is typing in the search field.
        binding.gridRecycler.post {
            if (allEntries.isNotEmpty()) {
                binding.gridRecycler.findViewHolderForAdapterPosition(0)
                    ?.itemView?.requestFocus()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-apply the chosen sort after returning from a game (or any reload) so
        // newly installed entries and the persisted sort order are reflected.
        sortMode = GridSortMode.fromPref(CorePrefs.getGridSort(this))
        allEntries = InstalledLibrary.sortedEntries(this, sortMode)
        refreshGrid()
    }

    /** Derives the card size, gap and column count from the current screen width. */
    private fun computeGridMetrics() {
        val screenW = resources.displayMetrics.widthPixels
        val pad = resources.getDimensionPixelSize(R.dimen.switch_grid_padding)
        val gap = resources.getDimensionPixelSize(R.dimen.switch_grid_card_gap)
        val card = resources.getDimensionPixelSize(R.dimen.switch_card_size_grid)
        cardSizePx = card
        cardGapPx = gap
        val usable = screenW - pad * 2
        spanCount = maxOf(2, (usable + gap) / (card + gap))
    }

    private fun setupGrid() {
        binding.gridRecycler.layoutManager = GridLayoutManager(this, spanCount)
        adapter = GridAdapter(
            onActivate = { menuHost.launchGame(it) },
            onMenu = { menuController.openMenu(it) }
        )
        binding.gridRecycler.adapter = adapter
    }

    /**
     * Re-applies the current search filter, pushes the result to the adapter and
     * toggles the empty / no-results / grid visibility. Called on search input
     * and after a library mutation reported by [menuHost].
     */
    private fun refreshGrid() {
        val query = binding.gridSearch.text?.toString().orEmpty().lowercase().trim()
        filtered = if (query.isEmpty()) {
            allEntries
        } else {
            allEntries.filter { it.title.lowercase().contains(query) }
        }
        // One extra decorative row of ghosts, only when there is something to show.
        val ghostCount = if (allEntries.isEmpty()) 0 else spanCount
        adapter.submit(filtered, ghostCount)
        updateVisibility()
    }

    private fun updateVisibility() {
        val hasEntries = allEntries.isNotEmpty()
        val hasResults = filtered.isNotEmpty()
        binding.gridRecycler.visibility = if (hasResults) View.VISIBLE else View.GONE
        binding.gridEmpty.visibility = if (!hasEntries) View.VISIBLE else View.GONE
        binding.gridNoResults.visibility = if (hasEntries && !hasResults) View.VISIBLE else View.GONE
    }

    private fun goBack() {
        sfx?.back()
        finish()
    }

    /**
     * Open the reusable [SwitchDialog] single-choice list to pick the grid sort
     * order. The active option shows a check mark; selecting an option persists
     * the choice in [CorePrefs], re-sorts the list live (respecting the active
     * search filter) and closes the dialog. The select SFX is played by the
     * dialog itself, matching every other single-choice dialog in the app.
     */
    private fun openSortMenu() {
        val labels = sortOptions.map { labelForSort(it) }
        val checkedIndex = sortOptions.indexOf(sortMode).coerceAtLeast(0)
        SwitchDialog(this)
            .title(getString(R.string.grid_sort_button))
            .icon(R.drawable.ic_tune)
            .singleChoice(labels, checkedIndex) { index ->
                val chosen = sortOptions[index]
                sortMode = chosen
                CorePrefs.setGridSort(this, chosen.prefValue)
                allEntries = InstalledLibrary.sortedEntries(this, sortMode)
                refreshGrid()
            }
            .show()
    }

    /** Localized label for a [GridSortMode] option. */
    private fun labelForSort(mode: GridSortMode): String = when (mode) {
        GridSortMode.LAST_PLAYED -> getString(R.string.grid_sort_last_played)
        GridSortMode.DOWNLOAD_DATE -> getString(R.string.grid_sort_download_date)
        GridSortMode.ALPHA -> getString(R.string.grid_sort_alpha)
    }

    override fun onBackPressed() {
        if (menuController.isMenuShowing()) {
            menuController.dismissMenu()
            return
        }
        sfx?.back()
        super.onBackPressed()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) SwitchImmersive.enterFullscreen(this)
    }

    /**
     * Physical-controller key handling (mirrors LibraryActivity): A activates the
     * focused tile; SELECT/X/Y open its context menu. The search field is left to
     * handle its own keys (cursor / focus movement) when it holds focus.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (menuController.isMenuShowing()) {
            return super.dispatchKeyEvent(event)
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            val focused = currentFocus
            val tag = focused?.tag
            when (event.keyCode) {
                KeyEvent.KEYCODE_BUTTON_A -> {
                    if (tag is HackLibraryEntry) {
                        focused.performClick()
                        return true
                    }
                }
                KeyEvent.KEYCODE_BUTTON_SELECT,
                KeyEvent.KEYCODE_BUTTON_X,
                KeyEvent.KEYCODE_BUTTON_Y -> {
                    if (tag is HackLibraryEntry) {
                        menuController.openMenu(tag)
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    /**
     * Grid adapter rendering [SwitchGameCard]s for real entries plus a trailing
     * row of non-interactive ghost silhouettes. Reuses [SwitchGameCard] so focus
     * borders, dimming and cover/badge binding are identical to the home row.
     */
    private inner class GridAdapter(
        private val onActivate: (HackLibraryEntry) -> Unit,
        private val onMenu: (HackLibraryEntry) -> Unit
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val VIEW_GAME = 0
        private val VIEW_GHOST = 1

        private var entries: List<HackLibraryEntry> = emptyList()
        private var ghostCount = 0

        fun submit(list: List<HackLibraryEntry>, ghosts: Int) {
            entries = list
            ghostCount = ghosts
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = entries.size + ghostCount

        override fun getItemViewType(position: Int): Int =
            if (position < entries.size) VIEW_GAME else VIEW_GHOST

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val cardH = SwitchGameCard.coverHeight(cardSizePx)
            val lp = RecyclerView.LayoutParams(cardSizePx, cardH).apply {
                val g = cardGapPx / 2
                marginStart = g
                marginEnd = g
                topMargin = g
                bottomMargin = g
            }
            return if (viewType == VIEW_GAME) {
                val card = SwitchGameCard(parent.context)
                card.layoutParams = lp
                GameViewHolder(card)
            } else {
                val ghost = LayoutInflater.from(parent.context)
                    .inflate(R.layout.switch_grid_ghost, parent, false)
                ghost.layoutParams = lp
                ghost.isFocusable = false
                ghost.isClickable = false
                ghost.alpha = GHOST_ALPHA
                GhostViewHolder(ghost)
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is GameViewHolder) {
                val entry = entries[position]
                holder.card.bind(
                    entry,
                    onClick = { onActivate(entry) },
                    onLongClick = { onMenu(entry) }
                )
                holder.card.onFocusGained = {
                    if (initialFocusDone) sfx?.focusMove() else initialFocusDone = true
                }
            }
            // Ghosts are pure decoration and need no binding.
        }

        private inner class GameViewHolder(val card: SwitchGameCard) :
            RecyclerView.ViewHolder(card)

        private inner class GhostViewHolder(view: View) : RecyclerView.ViewHolder(view)
    }

    companion object {
        /** Opacity of the decorative ghost placeholder cards (10-15%). */
        private const val GHOST_ALPHA = 0.12f
    }
}
