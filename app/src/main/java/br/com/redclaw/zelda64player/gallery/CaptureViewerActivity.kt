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

package br.com.redclaw.zelda64player.gallery

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.Zelda64PlayerApp
import br.com.redclaw.zelda64player.databinding.ActivityCaptureViewerBinding
import br.com.redclaw.zelda64player.ui.switchui.SwitchBackButton
import br.com.redclaw.zelda64player.ui.switchui.SwitchImmersive
import coil.load
import java.io.File

/**
 * Fullscreen, internal viewer for one gallery capture.
 *
 * Images are shown fit-to-screen. Recordings use the platform [android.widget.VideoView]
 * with Switch-style play/pause controls so neither media type delegates viewing to an
 * external app. The activity accepts only files named like a gallery capture and checks
 * that the supplied type matches the extension before rendering it.
 */
class CaptureViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCaptureViewerBinding
    private val backHelper = SwitchBackButton()
    private var mediaType: MediaType? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCaptureViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SwitchImmersive.enterFullscreen(this)

        backHelper.attach(this, binding.captureViewerBack.root, onBack = { finish() })
        binding.captureViewerPlayback.setOnClickListener { togglePlayback() }

        val file = intent.getStringExtra(EXTRA_CAPTURE_PATH)?.let(::File)
        val type = intent.getStringExtra(EXTRA_MEDIA_TYPE)
            ?.let { runCatching { MediaType.valueOf(it) }.getOrNull() }
        if (!isValidCapture(file, type)) {
            showError()
            return
        }

        mediaType = type
        if (type == MediaType.IMAGE) showImage(file!!) else showVideo(file!!)
    }

    override fun onPause() {
        if (binding.captureViewerVideo.isPlaying) {
            binding.captureViewerVideo.pause()
            updatePlaybackButton(isPlaying = false)
        }
        super.onPause()
    }

    override fun onDestroy() {
        binding.captureViewerVideo.stopPlayback()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) SwitchImmersive.enterFullscreen(this)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        backHelper.onTouch(ev)
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_BUTTON_B) {
            Zelda64PlayerApp.sfxManager.back()
            finish()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun showImage(file: File) = with(binding) {
        captureViewerTitle.setText(R.string.gallery_image_viewer_title)
        captureViewerLoading.visibility = View.GONE
        captureViewerImage.visibility = View.VISIBLE
        captureViewerImage.load(file) {
            crossfade(true)
            placeholder(R.drawable.ic_screenshot)
            error(R.drawable.ic_screenshot)
        }
    }

    private fun showVideo(file: File) = with(binding) {
        captureViewerTitle.setText(R.string.gallery_video_player_title)
        captureViewerVideo.visibility = View.VISIBLE
        captureViewerPlayback.visibility = View.VISIBLE
        captureViewerVideo.setVideoPath(file.absolutePath)
        captureViewerVideo.setOnPreparedListener { player -> onVideoPrepared(player) }
        captureViewerVideo.setOnCompletionListener {
            updatePlaybackButton(isPlaying = false)
            captureViewerVideo.seekTo(0)
        }
        captureViewerVideo.setOnErrorListener { _, _, _ ->
            showError()
            true
        }
        captureViewerVideo.requestFocus()
    }

    private fun onVideoPrepared(player: MediaPlayer) {
        binding.captureViewerLoading.visibility = View.GONE
        binding.captureViewerPlayback.isEnabled = true
        binding.captureViewerPlayback.requestFocus()
        updatePlaybackButton(isPlaying = false)
        player.isLooping = false
    }

    private fun togglePlayback() {
        if (mediaType != MediaType.VIDEO) return
        with(binding.captureViewerVideo) {
            if (isPlaying) {
                pause()
                updatePlaybackButton(isPlaying = false)
            } else {
                start()
                updatePlaybackButton(isPlaying = true)
            }
        }
    }

    private fun updatePlaybackButton(isPlaying: Boolean) = with(binding.captureViewerPlayback) {
        setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
        contentDescription = getString(
            if (isPlaying) R.string.gallery_pause_video else R.string.gallery_play_video
        )
    }

    private fun showError() = with(binding) {
        captureViewerLoading.visibility = View.GONE
        captureViewerVideo.visibility = View.GONE
        captureViewerPlayback.visibility = View.GONE
        captureViewerError.visibility = View.VISIBLE
    }

    private fun isValidCapture(file: File?, type: MediaType?): Boolean {
        if (file == null || type == null || !file.isFile) return false
        return when (type) {
            MediaType.IMAGE -> file.name.startsWith("screenshot_") && file.extension.equals("png", true)
            MediaType.VIDEO -> file.name.startsWith("recording_") && file.extension.equals("mp4", true)
        }
    }

    companion object {
        private const val EXTRA_CAPTURE_PATH = "capture_path"
        private const val EXTRA_MEDIA_TYPE = "media_type"

        /** Creates an explicit intent for the in-app viewer of [item]. */
        fun intent(context: Context, item: GalleryItem): Intent =
            Intent(context, CaptureViewerActivity::class.java).apply {
                putExtra(EXTRA_CAPTURE_PATH, item.path.absolutePath)
                putExtra(EXTRA_MEDIA_TYPE, item.type.name)
            }
    }
}
