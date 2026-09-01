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
import br.com.redclaw.zelda64player.patcher.n64.ChecksumCalculator
import br.com.redclaw.zelda64player.repositories.Storage
import br.com.redclaw.zelda64player.utils.CorePrefs
import br.com.redclaw.zelda64player.views.InstalledLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** How a [ConflictRecord] should be resolved by the user. */
enum class ResolutionChoice {
    /** Push the local copy to the cloud, overwriting the remote. */
    LOCAL,
    /** Pull the cloud copy down, overwriting the local. */
    CLOUD,
    /** Keep the cloud as primary and rename the local copy as a `.bak` backup. */
    BOTH
}

/**
 * Background worker that performs the incremental cloud sync.
 *
 * Triggered by [SyncTrigger] whenever an SRAM or save-state is written locally.
 * It reads the dirty set from [SyncMetaStore], and for each file:
 * 1. computes the local [SyncMeta] (CRC32 + timestamp + size);
 * 2. queries Drive for the remote copy via [GoogleDriveBackupService.findSaveFile];
 * 3. runs [decideSync] to pick FIRST_UPLOAD / EQUAL / UPLOAD_LOCAL /
 *    DOWNLOAD_CLOUD / CONFLICT;
 * 4. acts accordingly, updating the local metadata; or, on CONFLICT, records a
 *    [ConflictRecord] and raises a notification (never auto-resolving).
 *
 * The worker is idempotent: re-running it is a no-op for files already in sync,
 * and a file is only removed from the dirty set after a successful transfer.
 */
class CloudSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val WORK_NAME = "cloud_sync"
        private const val TAG = "CloudSyncWorker"

        /**
         * Apply a user-chosen [choice] to [record]. Performs the actual file
         * transfer against Drive and updates the local [SyncMetaStore] + removes
         * the conflict. Returns true on success. Called from
         * [ConflictResolveActivity].
         */
        suspend fun applyResolution(
            context: Context,
            record: ConflictRecord,
            choice: ResolutionChoice
        ): Boolean = withContext(Dispatchers.IO) {
            val account = CorePrefs.getGdriveAccountName(context) ?: return@withContext false
            val service = GoogleDriveBackup.buildService(context, account)
                ?: return@withContext false
            val storage = Storage.getInstance(context)
            val store = SyncMetaStore(context)
            val localFile = if (record.fileType == "SRAM") {
                storage.sram(record.hackId)
            } else {
                storage.state(record.hackId)
            }
            val remotePath = "saves/${record.hackId}/${record.fileName}"

            when (choice) {
                ResolutionChoice.LOCAL -> {
                    val id = service.uploadFile(
                        localFile,
                        remotePath,
                        appPropertiesFor(localFile),
                        record.cloudMeta.driveFileId
                    )
                    store.putMeta(
                        SyncMeta(
                            filePath = record.fileName,
                            crc32 = ChecksumCalculator.crc32(localFile),
                            lastModified = localFile.lastModified(),
                            size = localFile.length(),
                            driveFileId = id,
                            driveModifiedTime = formatNowRfc3339()
                        )
                    )
                }
                ResolutionChoice.CLOUD -> {
                    service.downloadFile(record.cloudMeta.driveFileId!!, localFile)
                    store.putMeta(
                        SyncMeta(
                            filePath = record.fileName,
                            crc32 = record.cloudMeta.crc32,
                            lastModified = localFile.lastModified(),
                            size = localFile.length(),
                            driveFileId = record.cloudMeta.driveFileId,
                            driveModifiedTime = record.cloudMeta.driveModifiedTime
                        )
                    )
                }
                ResolutionChoice.BOTH -> {
                    // Rename the local copy so it is preserved as a backup, then
                    // pull the cloud copy down as the new primary.
                    val backup = File(
                        localFile.parent,
                        conflictBackupName(record.fileName, System.currentTimeMillis())
                    )
                    localFile.renameTo(backup)
                    service.downloadFile(record.cloudMeta.driveFileId!!, localFile)
                    store.putMeta(
                        SyncMeta(
                            filePath = record.fileName,
                            crc32 = record.cloudMeta.crc32,
                            lastModified = localFile.lastModified(),
                            size = localFile.length(),
                            driveFileId = record.cloudMeta.driveFileId,
                            driveModifiedTime = record.cloudMeta.driveModifiedTime
                        )
                    )
                }
            }
            ConflictStore(context).remove(record.id)
            true
        }

        /** Build the Drive `appProperties` map for a local file. */
        private fun appPropertiesFor(file: File): Map<String, String> = mapOf(
            "crc32" to ChecksumCalculator.crc32(file),
            "size" to file.length().toString()
        )
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext

        if (!CorePrefs.getCloudSyncEnabled(context)) return@withContext Result.success()
        val account = CorePrefs.getGdriveAccountName(context) ?: return@withContext Result.success()
        val storage = Storage.getInstance(context)
        val store = SyncMetaStore(context)
        val dirty = store.getDirty()
        if (dirty.isEmpty()) return@withContext Result.success()

        val service = GoogleDriveBackup.buildService(context, account)
            ?: return@withContext Result.success()

        for (name in dirty) {
            runCatching { processFile(name, storage, store, service, context) }
                .onFailure { e -> Log.w(TAG, "sync failed for $name: ${e.message}") }
        }

        CorePrefs.setCloudSyncLastSync(context, System.currentTimeMillis())
        Result.success()
    }

    /**
     * Process one dirty file. Returns true if a conflict was recorded (so the
     * caller could track counts), false otherwise.
     */
    private suspend fun processFile(
        name: String,
        storage: Storage,
        store: SyncMetaStore,
        service: GoogleDriveBackupService,
        context: Context
    ): Boolean {
        val (hackId, type, localFile) = when {
            name.startsWith("sram_") ->
                Triple(name.removePrefix("sram_"), "SRAM", storage.sram(name.removePrefix("sram_")))
            name.startsWith("state_") ->
                Triple(name.removePrefix("state_"), "STATE", storage.state(name.removePrefix("state_")))
            else -> {
                store.clearDirty(name)
                return false
            }
        }

        // Local file vanished (uninstalled) — drop its sync state.
        if (!localFile.exists()) {
            store.clearDirty(name)
            store.removeMeta(name)
            return false
        }

        val local = SyncMeta(
            filePath = name,
            crc32 = ChecksumCalculator.crc32(localFile),
            lastModified = localFile.lastModified(),
            size = localFile.length(),
            driveFileId = null,
            driveModifiedTime = null
        )

        val remotePath = "saves/$hackId/$name"
        val cloudRaw = service.findSaveFile(hackId, name)
        val cloud: SyncMeta? = cloudRaw?.let {
            SyncMeta(
                filePath = name,
                crc32 = it.appProperties["crc32"] ?: "",
                lastModified = 0L,
                size = it.size,
                driveFileId = it.id,
                driveModifiedTime = it.modifiedTime
            )
        }

        return when (decideSync(local, cloud)) {
            SyncDecision.FIRST_UPLOAD -> {
                val id = service.uploadFile(localFile, remotePath, appPropertiesFor(localFile), null)
                store.putMeta(
                    local.copy(driveFileId = id, driveModifiedTime = formatNowRfc3339())
                )
                store.clearDirty(name)
                false
            }
            SyncDecision.EQUAL -> {
                if (cloud != null) {
                    store.putMeta(
                        local.copy(driveFileId = cloud.driveFileId, driveModifiedTime = cloud.driveModifiedTime)
                    )
                }
                store.clearDirty(name)
                false
            }
            SyncDecision.UPLOAD_LOCAL -> {
                val id = service.uploadFile(
                    localFile, remotePath, appPropertiesFor(localFile), cloud?.driveFileId
                )
                store.putMeta(
                    local.copy(driveFileId = id, driveModifiedTime = formatNowRfc3339())
                )
                store.clearDirty(name)
                false
            }
            SyncDecision.DOWNLOAD_CLOUD -> {
                service.downloadFile(cloud!!.driveFileId!!, localFile)
                store.putMeta(
                    local.copy(driveFileId = cloud.driveFileId, driveModifiedTime = cloud.driveModifiedTime)
                )
                store.clearDirty(name)
                false
            }
            SyncDecision.CONFLICT -> {
                val record = ConflictRecord(
                    id = "${name}_${System.currentTimeMillis()}",
                    hackId = hackId,
                    gameName = resolveName(context, hackId),
                    fileType = type,
                    fileName = name,
                    localMeta = local,
                    cloudMeta = cloud!!,
                    timestamp = System.currentTimeMillis()
                )
                val conflicts = ConflictStore(context)
                conflicts.add(record)
                conflicts.notifyConflict(context, record)
                store.clearDirty(name)
                true
            }
        }
    }

    /** Resolve a hack id to a display name for notifications / the resolver UI. */
    private fun resolveName(context: Context, hackId: String): String =
        runCatching {
            InstalledLibrary.entries(context).firstOrNull { it.romId == hackId }?.title
        }.getOrNull() ?: hackId
}
