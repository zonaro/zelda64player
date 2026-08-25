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

package br.com.redclaw.zelda64player.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.repositories.Storage
import br.com.redclaw.zelda64player.views.LibraryActivity
import java.io.File

/**
 * Foreground service that records the screen via [MediaProjection] +
 * [MediaRecorder], writing an MP4 to [Storage.recordingFile].
 *
 * The MediaProjection consent is obtained by [GameActivityViewModel] (which
 * needs an Activity to launch the consent intent) and the resulting
 * `resultCode` + `data` are forwarded here through the start intent. The service
 * then drives a [VirtualDisplay] fed by the recorder's input surface.
 *
 * ### Overlay handling
 * When the recording is started with `includeOverlay == false`, the on-screen
 * control overlays (gamepad, Ocarina HUD, RA overlay) must be hidden so they do
 * not appear in the capture. The service broadcasts [ACTION_HIDE_OVERLAYS] on
 * start and [ACTION_SHOW_OVERLAYS] on stop; [br.com.redclaw.zelda64player.views.GameActivity]
 * listens and toggles those Views' visibility (INVISIBLE / VISIBLE). This is a
 * temporary visibility change only — it never alters the RadialGamePad layout,
 * modes or positioning, so Rule 14 (frozen gamepad) is preserved.
 */
class CaptureService : Service() {

    companion object {
        private const val TAG = "CaptureService"

        const val ACTION_HIDE_OVERLAYS =
            "br.com.redclaw.zelda64player.capture.ACTION_HIDE_OVERLAYS"
        const val ACTION_SHOW_OVERLAYS =
            "br.com.redclaw.zelda64player.capture.ACTION_SHOW_OVERLAYS"

        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_HACK_ID = "extra_hack_id"
        const val EXTRA_INCLUDE_OVERLAY = "extra_include_overlay"

        private const val CHANNEL_ID = "capture_channel"
        private const val NOTIFICATION_ID = 53001
        private const val VIRTUAL_DISPLAY_NAME = "Zelda64PlayerCapture"
        private const val FRAME_RATE = 30
    }

    private var mediaProjection: MediaProjection? = null
    private var mediaRecorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var outputFile: File? = null
    private var includeOverlay = true

    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.d(TAG, "MediaProjection stopped by user")
            stopRecording()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
        val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        val hackId = intent.getStringExtra(EXTRA_HACK_ID) ?: "unknown"
        includeOverlay = intent.getBooleanExtra(EXTRA_INCLUDE_OVERLAY, true)

        if (resultCode == -1 || resultData == null) {
            Log.w(TAG, "onStartCommand: missing MediaProjection consent")
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground()
        if (!prepareRecorder(hackId, resultCode, resultData)) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (!includeOverlay) {
            sendBroadcast(Intent(ACTION_HIDE_OVERLAYS))
        }
        return START_NOT_STICKY
    }

    private fun prepareRecorder(
        hackId: String,
        resultCode: Int,
        resultData: Intent
    ): Boolean {
        val projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData)
        mediaProjection?.registerCallback(mediaProjectionCallback, null)

        val metrics = DisplayMetrics()
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            wm.currentWindowMetrics.bounds.let { b ->
                metrics.widthPixels = b.width()
                metrics.heightPixels = b.height()
            }
        } else {
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getMetrics(metrics)
        }
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val densityDpi = resources.displayMetrics.densityDpi

        outputFile = Storage.getInstance(this).recordingFile(hackId, System.currentTimeMillis())
        val recorder = MediaRecorder()
        return try {
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setOutputFile(outputFile!!.absolutePath)
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            recorder.setVideoSize(width, height)
            recorder.setVideoFrameRate(FRAME_RATE)
            recorder.setVideoEncodingBitRate(8_000_000)
            recorder.prepare()

            val surface = recorder.surface
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                VIRTUAL_DISPLAY_NAME,
                width, height, densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                surface, null, null
            )
            recorder.start()
            mediaRecorder = recorder
            true
        } catch (e: Exception) {
            Log.e(TAG, "prepareRecorder failed", e)
            cleanup()
            false
        }
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "stopRecording: recorder stop failed", e)
        }
        cleanup()
        sendBroadcast(Intent(ACTION_SHOW_OVERLAYS))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun cleanup() {
        virtualDisplay?.release()
        virtualDisplay = null
        mediaRecorder?.release()
        mediaRecorder = null
        mediaProjection?.unregisterCallback(mediaProjectionCallback)
        mediaProjection?.stop()
        mediaProjection = null
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure overlays are always restored if the service dies unexpectedly.
        sendBroadcast(Intent(ACTION_SHOW_OVERLAYS))
        cleanup()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.settings_capture_category),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForeground() {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, LibraryActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.recording_started))
            .setContentText(getString(R.string.capture_overlay_hidden_hint))
            .setSmallIcon(R.drawable.ic_record)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }
}
