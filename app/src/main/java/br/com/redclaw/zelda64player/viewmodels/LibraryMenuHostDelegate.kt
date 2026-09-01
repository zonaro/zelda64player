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

package br.com.redclaw.zelda64player.viewmodels

import android.app.Activity
import android.content.Intent
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.data.local.AppRepositories
import br.com.redclaw.zelda64player.data.local.InstalledHacksRepository
import br.com.redclaw.zelda64player.repositories.GameRomResolver
import br.com.redclaw.zelda64player.repositories.SaveBackupManager
import br.com.redclaw.zelda64player.repositories.Storage
import br.com.redclaw.zelda64player.repositories.uninstallHackFiles
import br.com.redclaw.zelda64player.retroachievements.ui.AchievementsActivity
import br.com.redclaw.zelda64player.shortcuts.GamePlayHistoryStore
import br.com.redclaw.zelda64player.shortcuts.GameShortcutsManager
import br.com.redclaw.zelda64player.views.GameActivity
import br.com.redclaw.zelda64player.views.HackLibraryEntry
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shared [LibraryMenuHost] implementation used by both
 * [br.com.redclaw.zelda64player.views.LibraryActivity] and the full-screen
 * [br.com.redclaw.zelda64player.ui.switchui.SwitchGridActivity] so the per-game context menu
 * actions (launch, export/import saves, uninstall, pin, achievements) are defined exactly once
 * (DRY).
 *
 * Owns the SAF document pickers, the debounced launch, and the uninstall flow. After a library
 * mutation (uninstall) it invokes [onLibraryChanged] so the host can rebuild its grid and re-sync
 * shortcuts without this class knowing about either screen's UI.
 *
 * The menu *structure* (which items appear, in which sections) lives in
 * [LibraryMenuController.buildSections]; this class only performs the actions.
 */
class LibraryMenuHostDelegate(
        private val activity: AppCompatActivity,
        private val onLibraryChanged: () -> Unit
) : LibraryMenuHost {

    private var lastLaunchClickTime = 0L

    /* Entry whose save operation is pending in a SAF picker (one-shot). */
    private var pendingExportEntry: HackLibraryEntry? = null
    private var pendingImportEntry: HackLibraryEntry? = null
    private var pendingCoverEntry: HackLibraryEntry? = null

    private val exportLauncher =
            activity.registerForActivityResult(
                    ActivityResultContracts.CreateDocument("application/zip")
            ) { uri ->
                val entry = pendingExportEntry ?: return@registerForActivityResult
                pendingExportEntry = null
                if (uri == null) return@registerForActivityResult
                val storage = Storage.getInstance(activity)
                try {
                    activity.contentResolver.openOutputStream(uri)?.use { out ->
                        SaveBackupManager.exportToStream(
                                out,
                                storage.sram(entry.romId),
                                storage.state(entry.romId)
                        )
                    }
                    showToast(R.string.menu_export_success)
                } catch (e: Exception) {
                    Log.e(TAG, "exportSaves failed for ${entry.romId}", e)
                    showToast(R.string.menu_export_failure)
                }
            }

    private val importLauncher =
            activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                val entry = pendingImportEntry ?: return@registerForActivityResult
                pendingImportEntry = null
                if (uri == null) return@registerForActivityResult
                val storage = Storage.getInstance(activity)
                try {
                    activity.contentResolver.openInputStream(uri)?.use { input ->
                        val summary =
                                SaveBackupManager.importFromStream(
                                        input,
                                        storage.sram(entry.romId),
                                        storage.state(entry.romId)
                                )
                        if (summary.ok) showToast(R.string.menu_import_success)
                        else showToast(R.string.menu_import_failure)
                    }
                            ?: showToast(R.string.menu_import_failure)
                } catch (e: Exception) {
                    Log.e(TAG, "importSaves failed for ${entry.romId}", e)
                    showToast(R.string.menu_import_failure)
                }
            }

    private val coverLauncher =
            activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                val entry = pendingCoverEntry ?: return@registerForActivityResult
                pendingCoverEntry = null
                if (uri == null || !entry.isUserImported) return@registerForActivityResult
                activity.lifecycleScope.launch(Dispatchers.IO) {
                    val saved =
                            runCatching {
                                        activity.contentResolver.openInputStream(uri)?.use { input
                                            ->
                                            AppRepositories.userHackCoverRepository(activity)
                                                    .replace(entry.romId, input)
                                                    .getOrThrow()
                                        }
                                                ?: error("Unable to open selected image")
                                    }
                                    .getOrNull()
                    val updated =
                            saved != null &&
                                    AppRepositories.userHacksRepository(activity)
                                            .updateCover(entry.romId, saved)
                    withContext(Dispatchers.Main) {
                        if (updated) {
                            onLibraryChanged()
                            showToast(R.string.manual_cover_success)
                        } else {
                            showToast(R.string.manual_cover_invalid)
                        }
                    }
                }
            }

    override fun context(): Activity = activity

    /**
     * Launch [entry] the same way tapping its tile does, respecting the shared debounce so a rapid
     * repeat (e.g. physical A + tile tap) fires only once.
     */
    override fun launchGame(entry: HackLibraryEntry) {
        val now = System.currentTimeMillis()
        if (now - lastLaunchClickTime < LAUNCH_CLICK_DEBOUNCE_MS) return
        lastLaunchClickTime = now
        val intent =
                Intent(activity, GameActivity::class.java).apply {
                    putExtra("hack_id", entry.romId)
                }
        activity.startActivity(intent)
    }

    /** Open the RetroAchievements screen for [entry]. */
    override fun openAchievements(entry: HackLibraryEntry) {
        activity.startActivity(
                Intent(activity, AchievementsActivity::class.java).apply {
                    putExtra(AchievementsActivity.EXTRA_HACK_ID, entry.romId)
                }
        )
    }

    override fun requestExportSaves(entry: HackLibraryEntry) {
        val storage = Storage.getInstance(activity)
        val hasSaves = storage.sram(entry.romId).exists() || storage.state(entry.romId).exists()
        if (!hasSaves) {
            showToast(R.string.menu_export_nothing)
            return
        }
        pendingExportEntry = entry
        val safeName = entry.title.replace(Regex("[^a-zA-Z0-9 _-]"), "_").trim()
        exportLauncher.launch(
                "$safeName${activity.getString(R.string.menu_export_filename_suffix)}"
        )
    }

    override fun requestImportSaves(entry: HackLibraryEntry) {
        val romFile = GameRomResolver.resolveRomFile(activity, entry.romId)
        if (romFile == null || !romFile.exists()) {
            showToast(R.string.menu_import_not_installed)
            return
        }
        pendingImportEntry = entry
        importLauncher.launch(arrayOf("application/zip"))
    }

    override fun requestChangeCover(entry: HackLibraryEntry) {
        if (!entry.isUserImported) return
        pendingCoverEntry = entry
        coverLauncher.launch(arrayOf("image/*"))
    }

    override fun confirmUninstall(entry: HackLibraryEntry) {
        AlertDialog.Builder(activity)
                .setTitle(activity.getString(R.string.menu_uninstall_title, entry.title))
                .setMessage(R.string.menu_uninstall_message)
                .setPositiveButton(R.string.menu_uninstall_button) { _, _ ->
                    performUninstall(entry)
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
    }

    /**
     * Delete the game's files, unmark it as installed and drop its play-history entry, then notify
     * the host to rebuild its list. Orchestrated here (the caller of the pure [uninstallHackFiles]
     * ); the file deletion itself is a JVM-testable pure function.
     */
    private fun performUninstall(entry: HackLibraryEntry) {
        val storage = Storage.getInstance(activity)
        uninstallHackFiles(File(storage.storagePath), entry.romId)
        InstalledHacksRepository(File(activity.filesDir, "installed_hacks.json"))
                .unmarkInstalled(entry.romId)
        if (entry.isUserImported) {
            AppRepositories.userHacksRepository(activity).remove(entry.romId)
            AppRepositories.userHackCoverRepository(activity).remove(entry.romId)
        }
        GamePlayHistoryStore(File(activity.filesDir, "game_play_history.json")).remove(entry.romId)
        onLibraryChanged()
        showToast(R.string.menu_uninstall_done)
    }

    /** Offer to pin [entry] to the home screen, or explain if unsupported. */
    override fun pinShortcut(entry: HackLibraryEntry) {
        val history = GamePlayHistoryStore(File(activity.filesDir, "game_play_history.json"))
        val ok = GameShortcutsManager(activity, history).requestPin(entry)
        if (!ok) {
            showToast(R.string.shortcut_pin_unsupported)
        }
    }

    override fun showToast(resId: Int) {
        android.widget.Toast.makeText(activity, resId, android.widget.Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val TAG = "LibraryMenuHostDelegate"

        /** Minimum interval between launches, in milliseconds. */
        private const val LAUNCH_CLICK_DEBOUNCE_MS = 700L
    }
}
