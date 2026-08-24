package br.com.redclaw.zelda64player.retroachievements

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
import br.com.redclaw.zelda64player.utils.CorePrefs
import org.json.JSONObject

/**
 * Posts system notifications for achievement unlocks.
 *
 * Opt-in by default (pref_ra_system_notifications = true) but gated behind
 * the POST_NOTIFICATIONS runtime permission on API 33+; callers must request
 * the permission first (the first unlock triggers the request from
 * GameActivity). Never posts when the channel is disabled or permission is
 * missing — every call is fail-safe.
 */
object RaNotificationHelper {

    const val CHANNEL_ID = "ra_achievements"
    private const val BASE_NOTIFICATION_ID = 0x5A000

    /** Ensures the unlock notification channel exists. Idempotent. */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.ra_notification_channel),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.ra_notification_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    /** True when a notification may be posted right now (prefs + permission). */
    fun canPost(context: Context): Boolean {
        if (!CorePrefs.getRaSystemNotifications(context)) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * Posts an unlock notification for the achievement in [payloadJson].
     * Silent no-op when not permitted. The id is derived from the achievement
     * id so re-unlocks replace rather than stack.
     */
    fun postUnlock(context: Context, payloadJson: String, gameTitle: String) {
        if (!canPost(context)) return
        val payload = runCatching { JSONObject(payloadJson) }.getOrNull() ?: return
        ensureChannel(context)

        val achievementId = payload.optLong("id", 0L)
        val title = payload.optString("title").ifBlank {
            context.getString(R.string.ra_notification_title)
        }
        val text = if (gameTitle.isBlank()) {
            context.getString(R.string.ra_notification_text)
        } else {
            context.getString(R.string.ra_notification_text_game, gameTitle)
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_trophy)
            .setContentTitle(title)
            .setContentText(text)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context)
                .notify((BASE_NOTIFICATION_ID + achievementId).toInt(), notification)
        }
    }
}
