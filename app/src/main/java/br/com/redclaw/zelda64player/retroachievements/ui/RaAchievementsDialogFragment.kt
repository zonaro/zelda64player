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

package br.com.redclaw.zelda64player.retroachievements.ui

import android.app.Dialog
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.Zelda64PlayerApp
import br.com.redclaw.zelda64player.retroachievements.data.RaGameData
import br.com.redclaw.zelda64player.ui.switchui.AccentManager
import br.com.redclaw.zelda64player.ui.switchui.SwitchDialog
import br.com.redclaw.zelda64player.views.InstalledLibrary
import coil.load
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * RetroAchievements popup — DialogFragment version of [AchievementsActivity].
 *
 * Shown as a centered Switch-style dialog on a scrim so the underlying
 * [br.com.redclaw.zelda64player.views.GameActivity] never leaves the foreground: no onPause/onStop,
 * no GL-context loss, no emulation restart on dismiss.
 *
 * Supports the same two modes as the Activity:
 * - Single game (ARG_HACK_ID present): header card + that hack's achievements.
 * - All games (no arg): one section per tracked installed game.
 *
 * The Activity is kept for Library/dock entry points; in-game menu must use this dialog via
 * [GameActivityViewModel.openRaAchievements].
 */
class RaAchievementsDialogFragment : DialogFragment() {

    private val sfx = runCatching { Zelda64PlayerApp.sfxManager }.getOrNull()
    private val adapter = RaAchievementAdapter()

    private var headerView: View? = null
    private var messageView: TextView? = null
    private var listView: RecyclerView? = null
    private var titleView: TextView? = null
    private var headerTitleView: TextView? = null
    private var badgeView: ImageView? = null
    private var summaryView: TextView? = null
    private var searchRow: View? = null
    private var searchInput: EditText? = null

    private var currentQuery: String = ""
    private var currentSort: RaSortMode = RaSortMode.DEFAULT
    private var singleGameRows: List<RaAchievementRow> = emptyList()
    private var allGamesData: List<GameAchievements> = emptyList()
    private var isSingleGame: Boolean = true

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = AppCompatDialog(requireContext(), R.style.SwitchDialogTheme)
        val view = LayoutInflater.from(dialog.context).inflate(R.layout.dialog_achievements, null)
        dialog.setContentView(view)
        dialog.window?.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
        )

        // Scrim tap dismisses; box consumes taps.
        view.setOnClickListener { dismiss() }
        val box = view.findViewById<View>(R.id.achievements_box)
        box?.let {
            it.isClickable = true
            val dm = resources.displayMetrics
            val minW = resources.getDimensionPixelSize(R.dimen.switch_side_panel_min_width)
            val maxW = resources.getDimensionPixelSize(R.dimen.dialog_menu_max_width)
            val target = (dm.widthPixels * 0.94f).toInt().coerceIn(minW, maxW)
            val lp = it.layoutParams as? FrameLayout.LayoutParams
            if (lp != null) {
                lp.width = target
                // Limit height to ~85% of screen so list scrolls instead of overflowing.
                lp.height = (dm.heightPixels * 0.85f).toInt()
                it.layoutParams = lp
            }
        }

        headerView = view.findViewById(R.id.dialog_achievements_header)
        messageView = view.findViewById(R.id.dialog_achievements_message)
        listView =
                view.findViewById<RecyclerView>(R.id.dialog_achievements_list).apply {
                    layoutManager = LinearLayoutManager(requireContext())
                    adapter = this@RaAchievementsDialogFragment.adapter
                }
        titleView = view.findViewById(R.id.dialog_achievements_title)
        headerTitleView = view.findViewById(R.id.dialog_achievements_game_title)
        badgeView = view.findViewById(R.id.dialog_achievements_game_badge)
        summaryView = view.findViewById(R.id.dialog_achievements_progress_summary)

        val hackId = arguments?.getString(ARG_HACK_ID)
        // Title reflects mode.
        titleView?.setText(
                if (hackId.isNullOrBlank()) R.string.achievements_title_all
                else R.string.achievements_title
        )

        searchRow = view.findViewById(R.id.dialog_achievements_search_row)
        searchInput = view.findViewById<EditText>(R.id.dialog_achievements_search).apply {
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    currentQuery = s?.toString().orEmpty()
                    applyFilterAndSort()
                }
            })
        }
        view.findViewById<ImageButton>(R.id.dialog_achievements_sort)?.setOnClickListener {
            showSortDialog()
        }

        val accent = AccentManager.getAccentColor(requireContext())
        val closeBtn = view.findViewById<Button>(R.id.dialog_achievements_close)
        closeBtn.background = createSwitchButtonBg(accent)
        closeBtn.setOnClickListener {
            sfx?.back()
            dismiss()
        }

        // Kick off load after dialog is shown (view is attached).
        view.post { if (hackId.isNullOrBlank()) loadAllGames() else loadSingle(hackId) }

        return dialog
    }

    // --- Single game mode -------------------------------------------------

    private fun loadSingle(hackId: String) {
        val credentials = Zelda64PlayerApp.raCredentialStore
        val repository = Zelda64PlayerApp.raCatalogRepository
        showMessage(R.string.ra_loading)

        lifecycleScope.launch {
            val identity =
                    withContext(Dispatchers.IO) {
                        Zelda64PlayerApp.raHashService.ensureIdentity(requireContext(), hackId)
                    }
            if (identity == null || !identity.isResolved) {
                showMessage(R.string.ra_error_untracked)
                return@launch
            }

            val gameData =
                    withContext(Dispatchers.IO) {
                        repository.fetchGameData(
                                identity.gameId,
                                credentials.getUsername().orEmpty(),
                                credentials.getToken().orEmpty()
                        )
                    }
            if (gameData == null) {
                showMessage(R.string.ra_error_network)
                return@launch
            }

            val unlockedIds =
                    withContext(Dispatchers.IO) {
                        repository.fetchUserUnlocks(
                                gameId = identity.gameId,
                                username = credentials.getUsername().orEmpty(),
                                apiToken = credentials.getToken().orEmpty(),
                                hardcore = false
                        )
                    }
            if (unlockedIds == null) {
                showMessage(R.string.ra_error_network)
                return@launch
            }
            render(gameData, unlockedIds, hackTitle(hackId))
        }
    }

    private fun render(gameData: RaGameData, unlockedIds: Set<Long>, fallbackTitle: String) {
        if (!isAdded) return
        messageView?.visibility = View.GONE
        headerView?.visibility = View.VISIBLE

        val title = gameData.title.ifBlank { fallbackTitle }
        headerTitleView?.text = title

        val achievements = gameData.coreAchievements
        val unlockedCount = achievements.count { it.id in unlockedIds }
        val totalPoints = achievements.sumOf { it.points }
        val earnedPoints = achievements.filter { it.id in unlockedIds }.sumOf { it.points }
        summaryView?.text =
                getString(
                        R.string.ra_progress_summary,
                        unlockedCount,
                        achievements.size,
                        earnedPoints,
                        totalPoints
                )

        if (gameData.imageUrl != null) {
            badgeView?.load(gameData.imageUrl) { crossfade(true) }
        }

        isSingleGame = true
        singleGameRows = achievements.map { RaAchievementRow(it, it.id in unlockedIds) }
        searchRow?.visibility = View.VISIBLE
        applyFilterAndSort()
        if (singleGameRows.isEmpty()) {
            showMessage(R.string.ra_empty)
        }
    }

    private fun applyFilterAndSort() {
        if (!isAdded) return
        if (isSingleGame) {
            val filtered = filterAndSortRows(singleGameRows, currentQuery, currentSort)
            if (filtered.isEmpty() && singleGameRows.isNotEmpty()) {
                adapter.submitList(emptyList())
                messageView?.setText(R.string.ra_no_results)
                messageView?.visibility = View.VISIBLE
            } else {
                messageView?.visibility = View.GONE
                adapter.submitList(filtered)
                if (filtered.isEmpty()) showMessage(R.string.ra_empty)
            }
        } else {
            val rows = buildFilteredSectionedRows(allGamesData, currentQuery, currentSort)
            if (rows.isEmpty() && allGamesData.isNotEmpty()) {
                adapter.submitList(emptyList())
                messageView?.setText(R.string.ra_no_results)
                messageView?.visibility = View.VISIBLE
            } else {
                messageView?.visibility = View.GONE
                adapter.submitList(rows)
                if (rows.isEmpty()) showMessage(R.string.ra_all_empty)
            }
        }
    }

    private fun showSortDialog() {
        val labels = listOf(
            getString(R.string.ra_sort_default),
            getString(R.string.ra_sort_name_az),
            getString(R.string.ra_sort_name_za),
            getString(R.string.ra_sort_points_desc),
            getString(R.string.ra_sort_points_asc),
            getString(R.string.ra_sort_rarity_rare),
            getString(R.string.ra_sort_rarity_common),
        )
        val modes = RaSortMode.values()
        val checked = modes.indexOf(currentSort).coerceAtLeast(0)
        SwitchDialog(requireContext())
            .title(getString(R.string.ra_sort_button))
            .icon(R.drawable.ic_tune)
            .singleChoice(labels, checked) { index ->
                currentSort = modes[index]
                applyFilterAndSort()
            }
            .show()
    }

    // --- All games mode ---------------------------------------------------

    private fun loadAllGames() {
        val credentials = Zelda64PlayerApp.raCredentialStore
        val repository = Zelda64PlayerApp.raCatalogRepository

        headerView?.visibility = View.GONE
        showMessage(R.string.ra_loading)

        lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) { InstalledLibrary.entries(requireContext()) }

            val identities =
                    withContext(Dispatchers.IO) {
                        buildMap<
                                String,
                                br.com.redclaw.zelda64player.retroachievements.data.RaGameIdentity> {
                            for (entry in entries) {
                                val identity =
                                        Zelda64PlayerApp.raHashService.ensureIdentity(
                                                requireContext(),
                                                entry.romId
                                        )
                                if (identity != null) put(entry.romId, identity)
                            }
                        }
                    }

            val resolvedByGame = collectResolvedGames(entries, identities)
            if (resolvedByGame.isEmpty()) {
                showMessage(R.string.ra_all_empty)
                return@launch
            }

            val username = credentials.getUsername().orEmpty()
            val token = credentials.getToken().orEmpty()

            val loadedGames = mutableListOf<GameAchievements>()
            for ((gameId, fallbackTitle) in resolvedByGame) {
                val gameData =
                        runCatching {
                                    withContext(Dispatchers.IO) {
                                        repository.fetchGameData(gameId, username, token)
                                    }
                                }
                                .getOrNull()
                                ?: continue
                val unlockedIds =
                        runCatching {
                                    withContext(Dispatchers.IO) {
                                        repository.fetchUserUnlocks(
                                                gameId = gameId,
                                                username = username,
                                                apiToken = token,
                                                hardcore = false
                                        )
                                    }
                                }
                                .getOrNull()
                                ?: continue
                loadedGames += GameAchievements(gameId, fallbackTitle, gameData, unlockedIds)
            }

            if (loadedGames.isEmpty()) {
                showMessage(R.string.ra_error_network)
                return@launch
            }

            isSingleGame = false
            allGamesData = loadedGames
            searchRow?.visibility = View.VISIBLE
            val rows = buildFilteredSectionedRows(loadedGames, currentQuery, currentSort)
            if (rows.isEmpty() && loadedGames.isNotEmpty()) {
                messageView?.setText(R.string.ra_no_results)
                messageView?.visibility = View.VISIBLE
                adapter.submitList(emptyList())
            } else {
                messageView?.visibility = View.GONE
                adapter.submitList(rows)
            }
        }
    }

    private fun hackTitle(hackId: String): String =
            InstalledLibrary.entries(requireContext())
                    .firstOrNull { it.id == hackId }
                    ?.title
                    .orEmpty()

    private fun showMessage(resId: Int) {
        headerView?.visibility = View.GONE
        messageView?.setText(resId)
        messageView?.visibility = View.VISIBLE
    }

    private fun createSwitchButtonBg(accent: Int): GradientDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(accent)
                cornerRadius = 4f
            }

    companion object {
        private const val ARG_HACK_ID = "hack_id"

        fun newInstance(hackId: String? = null): RaAchievementsDialogFragment =
                RaAchievementsDialogFragment().apply {
                    arguments =
                            Bundle().apply {
                                if (!hackId.isNullOrBlank()) putString(ARG_HACK_ID, hackId)
                            }
                }
    }
}
