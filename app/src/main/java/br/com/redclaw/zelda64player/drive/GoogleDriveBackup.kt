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

package br.com.redclaw.zelda64player.drive

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Orchestrates a full Google Drive backup run, shared by the periodic
 * [GoogleDriveBackupWorker] and the "Back up now" button in Settings.
 *
 * Builds the auth token provider + failure callback from a stored account name,
 * collects the files to upload via [collectBackupItems], and delegates the
 * network work to [GoogleDriveBackupService]. On an auth failure (HTTP 401) the
 * cached token is invalidated and the whole run is retried exactly once.
 */
object GoogleDriveBackup {

    /**
     * Build a [GoogleDriveBackupService] bound to [accountName], or null when no
     * such Google account exists on the device anymore. Centralizes the token
     * plumbing so both [run] and one-off operations (e.g. opening the folder)
     * share identical auth handling.
     */
    fun buildService(context: Context, accountName: String): GoogleDriveBackupService? {
        val account = GoogleDriveAuth.getAccount(context, accountName) ?: return null
        var lastToken: String? = null
        val tokenProvider: suspend () -> String = {
            lastToken = GoogleDriveAuth.getToken(context, account)
            lastToken!!
        }
        val onAuthFailure: () -> Unit = {
            lastToken?.let { GoogleDriveAuth.invalidateToken(context, it) }
        }
        return GoogleDriveBackupService(tokenProvider, onAuthFailure)
    }

    /**
     * @param context application context.
     * @param accountName connected Google account name (from [br.com.redclaw.zelda64player.utils.CorePrefs]).
     * @param saveDir directory with per-hack save files.
     * @param galleryDir directory with screenshots/recordings.
     * @param hackIds installed hack ids.
     * @param categories enabled categories.
     * @param sinceMillis incremental cutoff (0 = everything).
     * @param keepPerCategory how many recent files to keep per category.
     * @param onItemProgress (done, total) progress callback.
     * @return the backup summary.
     * @throws Exception if the account is missing or auth cannot be obtained
     *   (including [com.google.android.gms.auth.UserRecoverableAuthException] when
     *   the user must grant consent).
     */
    suspend fun run(
        context: Context,
        accountName: String,
        saveDir: File,
        galleryDir: File,
        hackIds: List<String>,
        categories: Set<BackupCategory>,
        sinceMillis: Long,
        keepPerCategory: Int = GoogleDriveBackupService.DEFAULT_KEEP,
        onItemProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): GoogleDriveBackupService.BackupSummary = withContext(Dispatchers.IO) {
        val service = buildService(context, accountName)
            ?: throw IllegalStateException("gdrive account missing")
        val items = collectBackupItems(saveDir, galleryDir, hackIds, categories, sinceMillis)
        try {
            service.backup(items, keepPerCategory, onItemProgress)
        } catch (e: GoogleDriveBackupService.DriveAuthException) {
            // Retry the whole run once with a fresh token after invalidation.
            service.backup(items, keepPerCategory, onItemProgress)
        }
    }
}
