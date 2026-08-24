package br.com.redclaw.zelda64player.store

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives the cancel action tapped from a download progress notification and
 * forwards it to [DownloadQueueManager]. Registered (exported = false) in the
 * manifest so only the app itself can deliver the broadcast.
 */
class DownloadCancelReceiver : BroadcastReceiver() {
    companion object {
        const val EXTRA_HACK_ID = "hack_id"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val hackId = intent?.getStringExtra(EXTRA_HACK_ID) ?: return
        DownloadQueueManager.cancel(hackId)
    }
}
