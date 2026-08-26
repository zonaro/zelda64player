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
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import br.com.redclaw.zelda64player.repositories.Storage
import br.com.redclaw.zelda64player.utils.CorePrefs
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Entry point called from the save-write paths (SRAM / save-state) to schedule a
 * background cloud sync.
 *
 * Design: saves are written on the UI / emulation thread, so we must NOT upload
 * inline (that would block and risk an ANR). Instead we mark the file "dirty" in
 * [SyncMetaStore] and enqueue [CloudSyncWorker] with [ExistingWorkPolicy.REPLACE]
 * — WorkManager coalesces repeated marks into a single deferred run (30s backoff,
 * network-connected constraint), so rapid successive saves do not spawn a worker
 * per save. The worker later reads the dirty set and uploads only those files.
 */
object SyncTrigger {

    /** Unique work name so repeated enqueues replace rather than stack. */
    const val WORK_NAME = "cloud_sync"

    /** Mark the SRAM file for [hackId] dirty and schedule a sync. */
    fun markDirtySram(context: Context, hackId: String) =
        markDirty(context, Storage.getInstance(context).sram(hackId))

    /** Mark the save-state file for [hackId] dirty and schedule a sync. */
    fun markDirtyState(context: Context, hackId: String) =
        markDirty(context, Storage.getInstance(context).state(hackId))

    /**
     * Mark [file] dirty (if cloud sync is enabled) and enqueue the worker.
     * No-op when cloud sync is disabled, so non-synced installs pay nothing.
     */
    fun markDirty(context: Context, file: File) {
        if (!CorePrefs.getCloudSyncEnabled(context)) return
        SyncMetaStore(context).markDirty(file.name)
        enqueue(context)
    }

    /** Enqueue (or replace) the one-shot sync worker. */
    fun enqueue(context: Context) {
        val networkType = if (CorePrefs.getCloudSyncWifiOnly(context)) {
            NetworkType.UNMETERED
        } else {
            NetworkType.CONNECTED
        }
        val request = OneTimeWorkRequestBuilder<CloudSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(networkType)
                    // Battery-friendly: never require charging; exponential backoff.
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request)
    }
}
