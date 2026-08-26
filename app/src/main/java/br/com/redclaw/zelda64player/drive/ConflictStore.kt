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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.utils.CorePrefs
import org.json.JSONArray
import org.json.JSONObject

/**
 * A save conflict the automatic sync worker could not resolve on its own.
 *
 * @param id stable unique id (file name + timestamp).
 * @param hackId the hack the conflicting save belongs to.
 * @param gameName display name for [hackId] (resolved at detection time so the
 *   notification can show a human-readable title even if the library changes).
 * @param fileType `SRAM` or `STATE` (save state).
 * @param fileName the local file name (`sram_<hackId>` / `state_<hackId>`).
 * @param localMeta metadata of the local copy at detection time.
 * @param cloudMeta metadata of the remote copy at detection time.
 * @param timestamp epoch millis when the conflict was detected.
 */
data class ConflictRecord(
    val id: String,
    val hackId: String,
    val gameName: String,
    val fileType: String,
    val fileName: String,
    val localMeta: SyncMeta,
    val cloudMeta: SyncMeta,
    val timestamp: Long
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("hackId", hackId)
        put("gameName", gameName)
        put("fileType", fileType)
        put("fileName", fileName)
        put("localMeta", localMeta.toJson())
        put("cloudMeta", cloudMeta.toJson())
        put("timestamp", timestamp)
    }

    companion object {
        fun fromJson(o: JSONObject): ConflictRecord = ConflictRecord(
            id = o.optString("id"),
            hackId = o.optString("hackId"),
            gameName = o.optString("gameName"),
            fileType = o.optString("fileType"),
            fileName = o.optString("fileName"),
            localMeta = SyncMeta.fromJson(o.getJSONObject("localMeta")),
            cloudMeta = SyncMeta.fromJson(o.getJSONObject("cloudMeta")),
            timestamp = o.optLong("timestamp")
        )
    }
}

/**
 * Persists pending [ConflictRecord]s and raises the user-facing notification
 * that opens [ConflictResolveActivity].
 *
 * The store is intentionally simple (a JSON list in SharedPreferences). Conflicts
 * are rare, so a full database would be overkill; the list is small and read
 * wholesale by the settings screen and the resolver activity.
 */
class ConflictStore(private val kv: KeyValueStore) {

    constructor(context: Context) : this(SharedPreferencesKeyValueStore(context, PREFS_NAME))

    /** Record a new conflict (replacing any prior record with the same id). */
    fun add(record: ConflictRecord) {
        val all = getAll().toMutableList()
        all.removeIf { it.id == record.id }
        all.add(record)
        save(all)
    }

    /** All pending conflicts, oldest first. */
    fun getAll(): List<ConflictRecord> {
        val raw = kv.getString(KEY_CONFLICTS) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                runCatching { ConflictRecord.fromJson(arr.getJSONObject(i)) }.getOrNull()
            }
        }.getOrDefault(emptyList())
    }

    /** Look up a single conflict by id. */
    fun get(id: String): ConflictRecord? = getAll().firstOrNull { it.id == id }

    /** Remove a resolved conflict. */
    fun remove(id: String) {
        save(getAll().filter { it.id != id })
    }

    /** Number of pending conflicts (drives the settings status line). */
    fun count(): Int = getAll().size

    /**
     * Raise a high-priority notification for [record], unless conflict
     * notifications are disabled in settings. Tapping it opens
     * [ConflictResolveActivity] for that conflict.
     */
    fun notifyConflict(context: Context, record: ConflictRecord) {
        if (!CorePrefs.getCloudSyncNotifications(context)) return
        ensureChannel(context)

        val intent = Intent(context, ConflictResolveActivity::class.java).apply {
            putExtra(ConflictResolveActivity.EXTRA_CONFLICT_ID, record.id)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            record.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_cloud_sync)
            .setContentTitle(context.getString(R.string.cloudsync_conflict_title))
            .setContentText(
                context.getString(R.string.cloudsync_conflict_notify, record.gameName)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ERROR)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(record.id.hashCode(), notification)
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.cloudsync_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                )
                channel.description = context.getString(R.string.cloudsync_channel_desc)
                manager.createNotificationChannel(channel)
            }
        }
    }

    private fun save(list: List<ConflictRecord>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        kv.putString(KEY_CONFLICTS, arr.toString())
    }

    companion object {
        private const val PREFS_NAME = "cloud_sync_conflicts"
        private const val KEY_CONFLICTS = "conflicts"
        const val CHANNEL_ID = "cloud_sync"
    }
}
