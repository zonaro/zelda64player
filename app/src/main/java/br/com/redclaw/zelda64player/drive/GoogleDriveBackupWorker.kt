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
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import br.com.redclaw.zelda64player.repositories.Storage
import br.com.redclaw.zelda64player.utils.CorePrefs
import br.com.redclaw.zelda64player.views.InstalledLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Periodic / one-off background backup of saves, screenshots and recordings to
 * Google Drive.
 *
 * Reads every relevant preference (enabled, account, categories, last-backup
 * cutoff) and delegates the actual work to [GoogleDriveBackup]. Any failure is
 * swallowed (logged) and reported as a failure so WorkManager applies its
 * default backoff; the worker never throws into the app process. When backup is
 * disabled, no account is connected, or no category is selected, it succeeds
 * immediately (nothing to do).
 */
class GoogleDriveBackupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val WORK_NAME = "gdrive_backup"
        private const val TAG = "GdriveBackupWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext

        if (!CorePrefs.getGdriveEnabled(context)) return@withContext Result.success()
        val accountName = CorePrefs.getGdriveAccountName(context)
        if (accountName == null) {
            Log.w(TAG, "Drive backup skipped: no account connected")
            return@withContext Result.success()
        }
        val categories = buildCategories(context)
        if (categories.isEmpty()) {
            Log.w(TAG, "Drive backup skipped: no category enabled")
            return@withContext Result.success()
        }

        val storage = Storage.getInstance(context)
        val hackIds = InstalledLibrary.entries(context).map { it.romId }
        val since = CorePrefs.getGdriveLastBackup(context)

        return@withContext try {
            val summary = GoogleDriveBackup.run(
                context = context,
                accountName = accountName,
                saveDir = File(storage.storagePath),
                galleryDir = storage.galleryDir(),
                hackIds = hackIds,
                categories = categories,
                sinceMillis = since
            )
            if (summary.uploaded > 0 || summary.deleted > 0) {
                CorePrefs.setGdriveLastBackup(context, System.currentTimeMillis())
            }
            Log.i(TAG, "Drive backup done: ${summary.uploaded} uploaded, ${summary.deleted} pruned")
            Result.success()
        } catch (e: Exception) {
            // Never crash the app; let WorkManager retry with its default backoff.
            Log.w(TAG, "Drive backup failed: ${e.message}")
            Result.failure()
        }
    }

    private fun buildCategories(context: Context): Set<BackupCategory> {
        val set = mutableSetOf<BackupCategory>()
        if (CorePrefs.getGdriveBackupSaves(context)) set.add(BackupCategory.SAVES)
        if (CorePrefs.getGdriveBackupImages(context)) set.add(BackupCategory.IMAGES)
        if (CorePrefs.getGdriveBackupVideos(context)) set.add(BackupCategory.VIDEOS)
        return set
    }
}
