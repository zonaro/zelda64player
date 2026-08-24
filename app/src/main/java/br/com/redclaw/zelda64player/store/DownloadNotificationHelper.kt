package br.com.redclaw.zelda64player.store

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import br.com.redclaw.zelda64player.R

/**
 * Posts system notifications reflecting download + patch progress for the Hack
 * Store queue. Each hack gets a stable notification id (so updates replace
 * rather than stack) and an ongoing progress notification with a cancel action
 * wired to [DownloadCancelReceiver].
 *
 * Fail-safe: every public method is a no-op when notifications cannot be posted
 * (missing POST_NOTIFICATIONS permission on API 33+, or notifications disabled
 * in system settings). No preferences gate this channel — progress visibility is
 * expected behavior once the user has granted the permission.
 */
object DownloadNotificationHelper {

    const val CHANNEL_ID = "store_downloads"
    private const val BASE_NOTIFICATION_ID = 0x44000

    private val notificationIds = mutableMapOf<String, Int>()
    private var nextId = BASE_NOTIFICATION_ID + 1

    /** Ensures the download progress channel exists. Idempotent. */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.download_channel),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.download_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    /** True when a notification may be posted right now (permission + enabled). */
    fun canPost(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun idFor(hackId: String): Int = synchronized(notificationIds) {
        notificationIds.getOrPut(hackId) { nextId++ }
    }

    /** Progress notification for an active (DOWNLOADING/PATCHING) item. */
    fun updateProgress(context: Context, ui: QueueItemUi) {
        if (!canPost(context)) return
        ensureChannel(context)

        val (text, indeterminate, percent) = when (ui.phase) {
            DownloadPhase.DOWNLOADING ->
                Triple(context.getString(R.string.download_notif_downloading, ui.progressPercent), false, ui.progressPercent)
            DownloadPhase.PATCHING ->
                Triple(context.getString(R.string.download_notif_patching), true, 0)
            else -> return
        }

        val cancelIntent = android.content.Intent(context, DownloadCancelReceiver::class.java)
            .putExtra(DownloadCancelReceiver.EXTRA_HACK_ID, ui.hackId)
        val cancelPending = android.app.PendingIntent.getBroadcast(
            context,
            ui.hackId.hashCode(),
            cancelIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_download)
            .setContentTitle(ui.name)
            .setContentText(text)
            .setProgress(100, percent, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(R.drawable.ic_close, context.getString(R.string.download_cancel), cancelPending)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(idFor(ui.hackId), notification)
        }
    }

    /** Replaces the progress notification with a completed (auto-cancelling) one. */
    fun notifyCompleted(context: Context, ui: QueueItemUi) {
        if (!canPost(context)) return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_check)
            .setContentTitle(ui.name)
            .setContentText(context.getString(R.string.download_notif_completed, ui.name))
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(idFor(ui.hackId), notification)
        }
    }

    /** Replaces the progress notification with an error (auto-cancelling) one. */
    fun notifyError(context: Context, ui: QueueItemUi) {
        if (!canPost(context)) return
        ensureChannel(context)
        val message = ui.error ?: context.getString(R.string.download_notif_error, "")
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_close)
            .setContentTitle(ui.name)
            .setContentText(context.getString(R.string.download_notif_error, message))
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(idFor(ui.hackId), notification)
        }
    }

    /** Replaces the progress notification with a cancelled (auto-cancelling) one. */
    fun notifyCancelled(context: Context, ui: QueueItemUi) {
        if (!canPost(context)) return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_close)
            .setContentTitle(ui.name)
            .setContentText(context.getString(R.string.download_notif_cancelled, ui.name))
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(idFor(ui.hackId), notification)
        }
    }
}
