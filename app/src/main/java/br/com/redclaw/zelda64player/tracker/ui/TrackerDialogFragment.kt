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

package br.com.redclaw.zelda64player.tracker.ui

import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.widget.Switch
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.Zelda64PlayerApp
import br.com.redclaw.zelda64player.tracker.data.TrackerExporter
import br.com.redclaw.zelda64player.tracker.model.TrackerGame
import br.com.redclaw.zelda64player.tracker.ui.components.TrackerTimerView
import br.com.redclaw.zelda64player.tracker.ui.tabs.HintsTab
import br.com.redclaw.zelda64player.tracker.ui.tabs.ItemsTab
import br.com.redclaw.zelda64player.tracker.ui.tabs.LocationsTab
import br.com.redclaw.zelda64player.tracker.ui.tabs.SongsTab
import br.com.redclaw.zelda64player.ui.switchui.AccentManager
import br.com.redclaw.zelda64player.utils.CorePrefs

/**
 * Switch-style modal hosting the item tracker. A tab strip switches between four child
 * fragments (Items / Locations / Songs / Hints) and an integrated run timer lives at the bottom. No
 * core RAM is read by this dialog; gameplay owns optional automatic updates.
 */
class TrackerDialogFragment : DialogFragment() {

    private var contentView: View? = null
    lateinit var viewModel: TrackerViewModel
    private val autoTrackingPreferenceListener =
            SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == CorePrefs.PREF_TRACKER_AUTO_TRACKING) syncAutoTrackingSwitch()
            }

    private fun syncAutoTrackingSwitch() {
        dialog?.findViewById<Switch>(R.id.tracker_auto_tracking)?.isChecked =
                CorePrefs.getTrackerAutoTracking(requireContext())
    }

    private val sfx = runCatching { Zelda64PlayerApp.sfxManager }.getOrNull()

    private val tabFactories =
            listOf<Pair<Int, () -> Fragment>>(
                    R.string.tracker_tab_items to { ItemsTab() },
                    R.string.tracker_tab_locations to { LocationsTab() },
                    R.string.tracker_tab_songs to { SongsTab() },
                    R.string.tracker_tab_hints to { HintsTab() }
            )
    private val tabButtons = mutableListOf<Button>()
    private var selected = 0

    private val exportLauncher =
            registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) {
                    uri ->
                if (uri == null) return@registerForActivityResult
                runCatching {
                    requireContext().contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(TrackerExporter.exportToJson(viewModel.state).toByteArray())
                    }
                }
            }

    private val importLauncher =
            registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri == null) return@registerForActivityResult
                val json =
                        runCatching {
                                    requireContext().contentResolver.openInputStream(uri)?.use {
                                        it.bufferedReader().readText()
                                    }
                                }
                                .getOrNull()
                                ?: return@registerForActivityResult
                val imported =
                        TrackerExporter.importFromJson(json) ?: return@registerForActivityResult
                viewModel.importState(imported)
                selectTab(selected)
                contentView?.findViewById<TrackerTimerView>(R.id.tracker_timer)?.bind(viewModel)
            }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val game = TrackerGame.valueOf(requireArguments().getString(ARG_GAME)!!)
        val hackId = arguments?.getString(ARG_HACK_ID)
        viewModel = TrackerViewModel(requireContext(), game, hackId)

        val dialog = AppCompatDialog(requireContext(), R.style.SwitchDialogTheme)
        val view = LayoutInflater.from(dialog.context).inflate(R.layout.tracker_dialog, null)
        contentView = view
        tabButtons.clear()
        dialog.setContentView(view)
        dialog.window?.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
        )
        // Scrim tap dismisses; box consumes taps.
        view.setOnClickListener { dismiss() }
        view.findViewById<View>(R.id.tracker_box)?.let { box ->
            box.isClickable = true
            // Constrain box width like SwitchDialog (40% width, 320..560dp).
            val dm = resources.displayMetrics
            val minW = resources.getDimensionPixelSize(R.dimen.switch_side_panel_min_width)
            val maxW = resources.getDimensionPixelSize(R.dimen.dialog_menu_max_width)
            val target = (dm.widthPixels * 0.94f).toInt().coerceIn(minW, maxW)
            // Bound the weighted tab content to the visible window in either orientation.
            val lp = box.layoutParams as? FrameLayout.LayoutParams
            if (lp != null) {
                lp.width = target.coerceAtMost(dm.widthPixels)
                lp.height = (dm.heightPixels * 0.9f).toInt()
                box.layoutParams = lp
            }
        }

        val accent = AccentManager.getAccentColor(requireContext())
        view.findViewById<Switch>(R.id.tracker_auto_tracking).apply {
            val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
            thumbTintList = ColorStateList(states, intArrayOf(accent,
                    requireContext().getColor(R.color.switch_text_primary)))
            trackTintList = ColorStateList(states, intArrayOf(accent,
                    requireContext().getColor(R.color.switch_text_secondary)))
            isChecked = CorePrefs.getTrackerAutoTracking(requireContext())
            setOnCheckedChangeListener { _, checked ->
                if (checked != CorePrefs.getTrackerAutoTracking(requireContext())) {
                    CorePrefs.setTrackerAutoTracking(requireContext(), checked)
                    sfx?.select()
                }
            }
        }
        view.findViewById<ImageView>(R.id.tracker_icon)?.setColorFilter(accent)
        val titleRes =
                if (game == TrackerGame.OOT) R.string.tracker_title_oot
                else R.string.tracker_title_mm
        view.findViewById<TextView>(R.id.tracker_title).setText(titleRes)
        val closeBtn = view.findViewById<Button>(R.id.tracker_close)
        closeBtn.background = createSwitchButtonBg(accent)
        closeBtn.setOnClickListener {
            sfx?.back()
            dismiss()
        }

        val tabStrip = view.findViewById<LinearLayout>(R.id.tracker_tabs)
        tabFactories.forEachIndexed { index, (labelRes, _) ->
            val btn =
                    Button(requireContext()).apply {
                        text = getString(labelRes)
                        isAllCaps = false
                        layoutParams =
                                LinearLayout.LayoutParams(
                                                0,
                                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                                1f
                                        )
                                        .apply {
                                            marginEnd =
                                                    (4 * resources.displayMetrics.density).toInt()
                                        }
                        setOnClickListener { selectTab(index) }
                    }
            tabButtons.add(btn)
            tabStrip.addView(btn)
        }
        applyTabStyles(accent)

        view.findViewById<TrackerTimerView>(R.id.tracker_timer).bind(viewModel)

        view.findViewById<ImageButton>(R.id.tracker_clear).setOnClickListener {
            sfx?.back()
            viewModel.clearAll()
            selectTab(selected)
            view.findViewById<TrackerTimerView>(R.id.tracker_timer).bind(viewModel)
        }
        view.findViewById<ImageButton>(R.id.tracker_export).setOnClickListener {
            sfx?.select()
            val key =
                    viewModel.hackId?.takeIf { it.isNotBlank() }?.lowercase()
                            ?: viewModel.game.name.lowercase()
            exportLauncher.launch("zelda64player_tracker_$key.json")
        }
        view.findViewById<ImageButton>(R.id.tracker_import).setOnClickListener {
            sfx?.select()
            importLauncher.launch(arrayOf("application/json"))
        }

        return dialog
    }

    /**
     * Deferred initial tab selection.
     *
     * Previously [selectTab] was called at the end of [onCreateDialog], but
     * [childFragmentManager.commitNow] during dialog creation can race with the parent
     * FragmentManager's own transaction, causing an IllegalStateException crash. Moving it here
     * (after the Fragment is fully STARTED) is safe.
     */
    override fun onStart() {
        super.onStart()
        requireContext().getSharedPreferences("ludere_prefs", Context.MODE_PRIVATE)
                .registerOnSharedPreferenceChangeListener(autoTrackingPreferenceListener)
        syncAutoTrackingSwitch()
        // Window is MATCH_PARENT for scrim; box is sized in onCreateDialog.
        // Defer the first tab selection to after onStart() fully completes.
        // Calling childFragmentManager.commitNow() inside onStart() triggers
        // nested FragmentManager execution (the parent is still mid-dispatch),
        // which can throw IllegalStateException and crash the tracker.
        // Posting to the view and using an async commit avoids the race.
        if (childFragmentManager.findFragmentByTag("tab_$selected") == null) {
            contentView?.post {
                if (isAdded && !childFragmentManager.isStateSaved) selectTab(selected)
            }
        }
        // Kick off ROM asset extraction early (before ItemsTab is created) so
        // the cache is ready when the grid builds. Use lifecycleScope (not
        // viewLifecycleOwner — DialogFragment with onCreateDialog has no view).
        lifecycleScope.launchWhenStarted {
            viewModel.ensureAssetsExtracted()
            if (isAdded && childFragmentManager.findFragmentByTag("tab_0") is ItemsTab) {
                selectTab(0)
            }
        }
    }

    private fun selectTab(index: Int) {
        if (!isAdded || childFragmentManager.isStateSaved) return
        selected = index
        sfx?.select()
        val accent = AccentManager.getAccentColor(requireContext())
        tabButtons.forEachIndexed { i, btn ->
            btn.isSelected = i == index
            btn.background = if (i == index) createSwitchButtonBg(accent) else createTabIdleBg()
            btn.setTextColor(
                    if (i == index) android.graphics.Color.WHITE
                    else requireContext().getColor(R.color.switch_text_primary)
            )
            btn.alpha = 1f
        }
        val frag = tabFactories[index].second()
        childFragmentManager
                .beginTransaction()
                .replace(R.id.tracker_content, frag, "tab_$index")
                .commit()
    }

    private fun applyTabStyles(accent: Int) {
        tabButtons.forEachIndexed { i, btn ->
            val selectedTab = i == selected
            btn.background = if (selectedTab) createSwitchButtonBg(accent) else createTabIdleBg()
            btn.setTextColor(
                    if (selectedTab) android.graphics.Color.WHITE
                    else requireContext().getColor(R.color.switch_text_primary)
            )
        }
    }

    private fun createSwitchButtonBg(accent: Int): GradientDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(accent)
                cornerRadius = 4f
            }

    private fun createTabIdleBg(): GradientDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(requireContext().getColor(R.color.switch_panel))
                setStroke(
                        resources.getDimensionPixelSize(R.dimen.switch_focus_border_width),
                        requireContext().getColor(R.color.switch_text_secondary)
                )
                cornerRadius = 4f
            }

    override fun onStop() {
        requireContext().getSharedPreferences("ludere_prefs", Context.MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(autoTrackingPreferenceListener)
        super.onStop()
    }

    override fun onDestroyView() {
        contentView?.findViewById<TrackerTimerView>(R.id.tracker_timer)?.stop()
        contentView = null
        tabButtons.clear()
        super.onDestroyView()
    }

    companion object {
        private const val ARG_GAME = "tracker_game"
        private const val ARG_HACK_ID = "tracker_hack_id"
        fun newInstance(game: TrackerGame, hackId: String? = null): TrackerDialogFragment =
                TrackerDialogFragment().apply {
                    arguments =
                            Bundle().apply {
                                putString(ARG_GAME, game.name)
                                if (!hackId.isNullOrBlank()) putString(ARG_HACK_ID, hackId)
                            }
                }
    }
}
