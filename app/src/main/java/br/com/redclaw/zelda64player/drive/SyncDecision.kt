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

import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Pure (Android-free) sync decision logic and metadata model for the automatic
 * cloud-sync feature.
 *
 * Kept free of Android dependencies so the conflict-resolution rules can be
 * unit-tested on the JVM without Robolectric. The worker ([CloudSyncWorker])
 * and the resolution UI ([ConflictResolveActivity]) build on top of these types.
 */

/**
 * Metadata describing one synchronizable file (an SRAM or save-state).
 *
 * @param filePath the local file name, used as the stable sync key
 *   (e.g. `sram_abc123`). Unique per hack.
 * @param crc32 CRC32 of the file content (8-digit lowercase hex), used to detect
 *   content equality without re-uploading.
 * @param lastModified local `file.lastModified()` epoch millis.
 * @param size file size in bytes.
 * @param driveFileId the Google Drive file id once a remote copy exists, or null
 *   for a purely local file that has never been uploaded.
 * @param driveModifiedTime the Drive `modifiedTime` (RFC3339) of the remote copy,
 *   or null when no remote copy exists.
 */
data class SyncMeta(
    val filePath: String,
    val crc32: String,
    val lastModified: Long,
    val size: Long,
    val driveFileId: String?,
    val driveModifiedTime: String?
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("filePath", filePath)
        put("crc32", crc32)
        put("lastModified", lastModified)
        put("size", size)
        putOpt("driveFileId", driveFileId)
        putOpt("driveModifiedTime", driveModifiedTime)
    }

    companion object {
        fun fromJson(o: JSONObject): SyncMeta = SyncMeta(
            filePath = o.optString("filePath"),
            crc32 = o.optString("crc32"),
            lastModified = o.optLong("lastModified"),
            size = o.optLong("size"),
            driveFileId = o.optString("driveFileId").takeIf { it.isNotEmpty() },
            driveModifiedTime = o.optString("driveModifiedTime").takeIf { it.isNotEmpty() }
        )
    }
}

/** Outcome of [decideSync] for a single dirty file. */
enum class SyncDecision {
    /** No remote copy exists yet — upload unconditionally. */
    FIRST_UPLOAD,
    /** Local and remote content match — nothing to transfer. */
    EQUAL,
    /** Local copy is newer — push it to the cloud. */
    UPLOAD_LOCAL,
    /** Remote copy is newer — pull it down and overwrite local. */
    DOWNLOAD_CLOUD,
    /** Cannot determine which side is newer — needs user resolution. */
    CONFLICT
}

/**
 * Decide what to do for a single dirty file given its local metadata and the
 * metadata of the remote (cloud) copy.
 *
 * The decision tree mirrors the product spec:
 * - no remote file (or no id) -> [FIRST_UPLOAD]
 * - identical CRC32 -> [EQUAL] (no transfer)
 * - local modified after cloud -> [UPLOAD_LOCAL]
 * - cloud modified after local -> [DOWNLOAD_CLOUD]
 * - otherwise (equal timestamps but different content, or missing metadata) ->
 *   [CONFLICT]
 *
 * @param local the local file metadata (always present for a dirty file).
 * @param cloud the remote metadata, or null when the file does not exist in the
 *   cloud yet. [SyncMeta.driveFileId] must be non-null for a real cloud copy.
 */
fun decideSync(local: SyncMeta, cloud: SyncMeta?): SyncDecision {
    if (cloud == null || cloud.driveFileId == null) return SyncDecision.FIRST_UPLOAD
    if (local.crc32.isNotEmpty() && local.crc32 == cloud.crc32) return SyncDecision.EQUAL

    val localTime = local.lastModified
    val cloudTime = parseRfc3339ToEpochMillis(cloud.driveModifiedTime ?: "")
    return when {
        localTime > cloudTime -> SyncDecision.UPLOAD_LOCAL
        cloudTime > localTime -> SyncDecision.DOWNLOAD_CLOUD
        else -> SyncDecision.CONFLICT
    }
}

/**
 * Build the backup file name used when the user chooses "keep both" on a
 * conflict: the local copy is renamed so the cloud copy can become the primary
 * save. e.g. `sram_abc123` -> `sram_abc123_conflict_1693000000000.bak`.
 */
fun conflictBackupName(filePath: String, timestampMillis: Long): String {
    val dot = filePath.lastIndexOf('.')
    val base = if (dot > 0) filePath.substring(0, dot) else filePath
    val ext = if (dot > 0) filePath.substring(dot) else ""
    return "${base}_conflict_${timestampMillis}${ext}.bak"
}

/** Parse a Drive RFC3339 timestamp (e.g. `2026-08-26T12:00:00.000Z`) to epoch millis. */
fun parseRfc3339ToEpochMillis(value: String): Long {
    if (value.isEmpty()) return 0L
    val normalized = if (value.endsWith("Z")) {
        value.substring(0, value.length - 1) + "+0000"
    } else value
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSZ",
        "yyyy-MM-dd'T'HH:mm:ssZ",
        "yyyy-MM-dd'T'HH:mm:ss.SSZ"
    )
    for (pattern in patterns) {
        runCatching {
            val sdf = SimpleDateFormat(pattern, Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            return sdf.parse(normalized)?.time ?: 0L
        }
    }
    return 0L
}

/** Format the current time as an RFC3339 timestamp (UTC, with millis). */
fun formatNowRfc3339(): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date())
