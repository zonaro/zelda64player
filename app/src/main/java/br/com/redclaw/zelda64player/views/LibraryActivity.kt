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

package br.com.redclaw.zelda64player.views

import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.input.InputManager
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.Zelda64PlayerApp
import br.com.redclaw.zelda64player.databinding.ActivityLibraryBinding
import br.com.redclaw.zelda64player.viewmodels.LibraryMenuHostDelegate
import br.com.redclaw.zelda64player.retroachievements.ui.AchievementsActivity
import br.com.redclaw.zelda64player.retroachievements.ui.RaProfileActivity
import br.com.redclaw.zelda64player.shortcuts.GamePlayHistoryStore
import br.com.redclaw.zelda64player.shortcuts.GameShortcutsManager
import br.com.redclaw.zelda64player.store.ui.StoreActivity
import br.com.redclaw.zelda64player.settings.ui.SettingsActivity
import br.com.redclaw.zelda64player.ui.switchui.SwitchDock
import br.com.redclaw.zelda64player.ui.switchui.SwitchHomeRow
import br.com.redclaw.zelda64player.ui.switchui.SwitchSidePanel
import br.com.redclaw.zelda64player.ui.switchui.SwitchDialog
import br.com.redclaw.zelda64player.ui.switchui.ThemeManager
import br.com.redclaw.zelda64player.utils.CorePrefs
import br.com.redclaw.zelda64player.viewmodels.LibraryMenuController
import br.com.redclaw.zelda64player.ui.switchui.SwitchGridActivity
import br.com.redclaw.zelda64player.ui.switchui.SwitchImmersive
import java.io.File
import coil.load
import coil.transform.CircleCropTransformation
import kotlinx.coroutines.launch

/**
 * Library home screen, rebuilt in Phase B to match the Nintendo Switch HOME menu
 * aesthetic: a horizontal row of landscape game cards showing the 5 most-recently
 * played installed entries (newest first), with a focused-game label above, a
 * circular "Todos os Jogos" card at the end of the row, a bottom dock of four
 * circular buttons, and a footer hints bar.
 *
 * The home row order is produced by [InstalledLibrary.recentEntries] (which ranks
 * by last-played timestamp descending, played-only, capped at 5, and falls back to
 * the default [InstalledLibrary.entries] order capped at 5 on a fresh install when
 * nothing has ever been played). The full, sortable library lives in
 * [br.com.redclaw.zelda64player.ui.switchui.SwitchGridActivity].
 *
 * All data flow is preserved from the previous grid implementation: the entry
     * list is still produced by [InstalledLibrary.entries] (which merges the vanilla
     * and store sources in the required order), and every existing behavior
     * is kept — import/export save flows, the per-game context menu (long-press /
     * overflow / physical SELECT-X-Y), uninstall, RetroAchievements
 * deep-link, shortcut sync, empty state, and immersive mode. Only the presentation
 * layer changed; the [LibraryMenuController] and [LibraryMenuHost] contracts are
 * untouched.
 */
class LibraryActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLibraryBinding

    /* Stateless: rebuilt from the source on every (re)create, so process
       death / configuration changes need no saved instance state. */
    private lateinit var items: List<HackLibraryEntry>

    private lateinit var menuController: LibraryMenuController

    /* Shared host logic (launch, SAF save pickers, uninstall, pin,
       achievements). Extracted to [LibraryMenuHostDelegate] so the full-screen
       grid screen reuses the exact same context-menu actions (DRY). */
    private lateinit var menuHost: LibraryMenuHostDelegate

    /** Live quick-options side panel (Phase D); null when not showing. */
    private var optionsPanel: SwitchSidePanel? = null

    companion object {
        private const val TAG = "LibraryActivity"

        /** Index of the SFX row within the quick-options panel (for live suffix). */
        private const val SFX_ROW_INDEX = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLibraryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.decorView.setOnApplyWindowInsetsListener { view, windowInsets ->
            view.post { SwitchImmersive.enterFullscreen(this) }
            windowInsets
        }

        menuHost = LibraryMenuHostDelegate(this) { onLibraryChanged() }
        menuController = LibraryMenuController(menuHost)

        items = InstalledLibrary.recentEntries(this)

        setupHomeRow()
        setupDock()
        setupFooter()
        setupProfileAvatar()
        updateEmptyState()
        syncShortcuts()

        registerInputListener()
    }

    override fun onResume() {
        super.onResume()
        // Rebuild the list so hacks installed in the Store appear on return
        // without needing to recreate the activity. Uses the shared recent-5
        // helper so the home row stays consistent with the context-menu refresh.
        items = InstalledLibrary.recentEntries(this)
        binding.libraryHomeRow.submitList(items)
        loadProfileAvatar()
        updateEmptyState()
        syncShortcuts()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) SwitchImmersive.enterFullscreen(this)
    }

    /**
     * Wire the home row: entry ordering comes from [InstalledLibrary.recentEntries]
     * (the 5 most-recently-played entries, newest first; falls back to the default
     * order on a fresh install). Click launches, long-press opens the context menu,
     * and the trailing "Todos os Jogos" card opens the grid.
     */
    private fun setupHomeRow() {
        binding.libraryHomeRow.setOnEntryActivate { menuHost.launchGame(it) }
        binding.libraryHomeRow.setOnEntryMenu { menuController.openMenu(it) }
        binding.libraryHomeRow.setOnAllGamesActivate {
            startActivity(Intent(this, SwitchGridActivity::class.java))
        }
        binding.libraryHomeRow.submitList(items)
    }

    /** Build the dock destinations (Loja, RA, Teste de Controle, Configurações). */
    private fun setupDock() {
        val dockItems = listOf(
            SwitchDock.DockItem(
                R.drawable.ic_store,
                R.string.dock_store,
                R.color.switch_accent_amber
            ) { startActivity(Intent(this, StoreActivity::class.java)) },
            SwitchDock.DockItem(
                R.drawable.ic_trophy,
                R.string.dock_achievements,
                R.color.switch_accent_amber
            ) { startActivity(Intent(this, AchievementsActivity::class.java)) },
            SwitchDock.DockItem(
                R.drawable.ic_gamepad,
                R.string.dock_gamepad_tester,
                R.color.switch_accent_focus
            ) {
                if (GamepadTesterActivity.hasConnectedController()) {
                    startActivity(Intent(this, GamepadTesterActivity::class.java))
                } else {
                    Toast.makeText(this, R.string.gamepad_tester_connect_controller, Toast.LENGTH_SHORT).show()
                }
            },
            SwitchDock.DockItem(
                R.drawable.ic_settings,
                R.string.dock_settings,
                R.color.switch_text_primary
            ) { startActivity(Intent(this, SettingsActivity::class.java)) }
        )
        binding.libraryDock.setItems(dockItems)
    }

    /** Wire the footer hints: "(i) Sobre" opens the About dialog, "+ Opções" is a
     *  Phase D stub hook (quick side panel). */
    private fun setupFooter() {
        binding.libraryFooter.setOnAbout { showAboutDialog() }
        binding.libraryFooter.setOnOptions { openOptionsPanel() }
    }

    /**
     * Puts the signed-in RetroAchievements player at the fixed account entry
     * point in the upper-left corner. The button remains available while logged
     * out so its profile screen can direct the player to sign in.
     */
    private fun setupProfileAvatar() {
        binding.libraryRaAvatar.setOnClickListener {
            Zelda64PlayerApp.sfxManager.select()
            startActivity(Intent(this, RaProfileActivity::class.java))
        }
        binding.libraryRaAvatar.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) Zelda64PlayerApp.sfxManager.focusMove()
        }
        loadProfileAvatar()
    }

    /** Loads the cached avatar immediately, then refreshes it from RA. */
    private fun loadProfileAvatar() {
        val credentials = Zelda64PlayerApp.raCredentialStore
        if (!credentials.hasCredentials()) {
            binding.libraryRaAvatar.setImageResource(R.drawable.ic_trophy)
            return
        }

        val repository = Zelda64PlayerApp.raUserProfileRepository
        repository.cachedAvatarUrl()?.let(::displayProfileAvatar)
        lifecycleScope.launch {
            repository.refreshProfile().getOrNull()?.avatarUrl?.let(::displayProfileAvatar)
        }
    }

    private fun displayProfileAvatar(url: String) {
        binding.libraryRaAvatar.load(url) {
            crossfade(true)
            placeholder(R.drawable.ic_trophy)
            error(R.drawable.ic_trophy)
            transformations(CircleCropTransformation())
        }
    }

    /**
     * Keep the launcher's dynamic shortcuts in sync with the installed library
     * (and disable any stale pinned shortcuts) whenever the Library is shown.
     */
    private fun syncShortcuts() {
        val history = GamePlayHistoryStore(File(filesDir, "game_play_history.json"))
        GameShortcutsManager(this, history).sync(items)
    }

    /**
     * Rebuild the library list after a mutation performed by [menuHost] (uninstall)
     * and refresh the dependent UI: the home row, the empty state
     * and the dynamic shortcuts. Centralized here so the shared
     * [LibraryMenuHostDelegate] only has to invoke this single callback (DRY).
     */
    private fun onLibraryChanged() {
        // Rebuild via the shared recent-5 helper so a delete/uninstall refresh
        // stays consistent with onCreate/onResume (DRY).
        items = InstalledLibrary.recentEntries(this)
        binding.libraryHomeRow.submitList(items)
        updateEmptyState()
        syncShortcuts()
    }

    /** Shows the About & Licenses dialog (GPL-3.0 + rcheevos MIT notice). */
    private fun showAboutDialog() {
        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: PackageManager.NameNotFoundException) {
            "?"
        }
        val appName = getString(R.string.config_name)
        val body = buildString {
            append(appName)
            append("\n")
            append(getString(R.string.settings_about_version, version))
            append("\n\n")
            append(getString(R.string.settings_about_licenses))
        }
        SwitchDialog(this)
            .title(getString(R.string.about_dialog_title))
            .message(body)
            .positiveButton(getString(R.string.about_dialog_close))
            .show()
    }

    /**
     * Opens the quick Options side panel (Phase D): theme toggle (amber focus
     * border), interface-sound on/off with a live suffix, RetroAchievements
     * login status (drills into Settings where login lives), and a link to the
     * full Settings screen. All rows reuse the reusable [SwitchSidePanel].
     */
    private fun openOptionsPanel() {
        // Toggle: a second tap on the footer hint closes an open panel instead
        // of stacking a second overlay.
        if (optionsPanel?.isShowing == true) {
            optionsPanel?.dismiss()
            return
        }
        val isLight = ThemeManager.isLight(this)
        val sfxOn = CorePrefs.getSwitchSfxEnabled(this)
        val raCredentials = Zelda64PlayerApp.raCredentialStore
        val raLabel = if (raCredentials.hasCredentials()) {
            getString(R.string.options_ra_status_connected, raCredentials.getUsername().orEmpty())
        } else {
            getString(R.string.options_ra_status_disconnected)
        }

        val rows = listOf(
            SwitchSidePanel.Row(
                iconRes = if (isLight) R.drawable.ic_moon else R.drawable.ic_sun,
                label = getString(
                    if (isLight) R.string.options_theme_to_dark
                    else R.string.options_theme_to_light
                ),
                amberFocus = true,
                onClick = { ThemeManager.toggle(this) /* auto-recreates */ }
            ),
            SwitchSidePanel.Row(
                iconRes = R.drawable.ic_volume_up,
                label = getString(R.string.options_sfx),
                suffix = getString(
                    if (sfxOn) R.string.options_sfx_on else R.string.options_sfx_off
                ),
                onClick = {
                    val newOn = !CorePrefs.getSwitchSfxEnabled(this)
                    Zelda64PlayerApp.sfxManager.setEnabled(newOn)
                    optionsPanel?.setRowSuffix(
                        SFX_ROW_INDEX,
                        getString(if (newOn) R.string.options_sfx_on else R.string.options_sfx_off)
                    )
                }
            ),
            SwitchSidePanel.Row(
                iconRes = R.drawable.ic_trophy,
                label = raLabel,
                showChevron = true,
                onClick = {
                    optionsPanel?.dismiss()
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
            ),
            SwitchSidePanel.Row(
                iconRes = R.drawable.ic_settings,
                label = getString(R.string.options_settings_full),
                showChevron = true,
                onClick = {
                    optionsPanel?.dismiss()
                    startActivity(Intent(this, SettingsActivity::class.java))
                }
            )
        )

        optionsPanel = SwitchSidePanel(this)
        optionsPanel?.show(getString(R.string.options_panel_title), R.drawable.ic_tune, rows)
    }

    private fun updateEmptyState() {
        val isEmpty = items.isEmpty()
        binding.libraryHomeRow.visibility = if (isEmpty) View.GONE else View.VISIBLE
        binding.libraryEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
    }

    /**
     * Route physical gamepad keys when no menu is open: A activates the focused
     * tile / dock button / "Todos os Jogos" card; SELECT/X/Y open the context
     * menu for the focused tile; the gamepad HOME / guide button toggles the
     * quick-options side panel open/closed. While the menu is showing, its own
     * decor-view key listener handles keys, so we let those events fall through
     * to the system. The HOME toggle is guarded against key repeats so holding
     * the button does not spam open/close.
     *
     * Note on the keycode: the physical gamepad "home"/"guide" button is
     * delivered as [KeyEvent.KEYCODE_GUIDE] (value 172, added in API 11, safe
     * on minSdk 24). The names KEYCODE_BUTTON_HOME and KEYCODE_HOMEPAGE from
     * the original spec do not exist in the Android SDK; the Linux KEY_HOMEPAGE
     * usage is translated by Android into KEYCODE_HOME (3), which is
     * system-reserved and never delivered to apps, so it cannot be intercepted
     * here. KEYCODE_GUIDE is therefore the correct and only interceptable
     * constant for this button.
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
                    when (tag) {
                        is HackLibraryEntry -> {
                            focused.performClick()
                            return true
                        }
                        is SwitchDock.DockItem -> {
                            focused.performClick()
                            return true
                        }
                        SwitchHomeRow.ALL_GAMES_TAG -> {
                            focused.performClick()
                            return true
                        }
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
                // Gamepad HOME / guide button toggles the quick-options side
                // panel. The physical button is delivered as KEYCODE_GUIDE
                // (value 172, API 11) — the Android SDK has no KEYCODE_BUTTON_HOME
                // or KEYCODE_HOMEPAGE constant; KEY_HOMEPAGE is translated by the
                // framework into the system-reserved KEYCODE_HOME (3), which is
                // never delivered to apps. The repeat guard prevents holding the
                // button from spamming open/close toggles.
                KeyEvent.KEYCODE_GUIDE -> {
                    if (event.repeatCount == 0) {
                        openOptionsPanel()
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun registerInputListener() {
        val inputManager = getSystemService(INPUT_SERVICE) as InputManager
        inputManager.registerInputDeviceListener(
            object : InputManager.InputDeviceListener {
                override fun onInputDeviceAdded(deviceId: Int) {
                    menuController.refreshBadges()
                }
                override fun onInputDeviceRemoved(deviceId: Int) {
                    menuController.refreshBadges()
                }
                override fun onInputDeviceChanged(deviceId: Int) {
                    menuController.refreshBadges()
                }
            },
            null
        )
    }
}
