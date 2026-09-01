/*
 *     Copyright (C) 2026 RedClaw
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package br.com.redclaw.zelda64player.dashboard.streaming

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import java.nio.ByteBuffer

/**
 * Hardware-accelerated H.264 video encoder using Android's MediaCodec API.
 *
 * Encodes raw RGBA frames (from [FrameCapturer]) into H.264 NAL units suitable for WebRTC video
 * tracks. The encoder uses surface input mode for zero-copy when the source is a GLSurfaceView, or
 * buffer input mode for raw pixel data.
 *
 * Output format: H.264 Baseline Profile, 30fps, configurable bitrate.
 */
class VideoEncoder(
        private val width: Int,
        private val height: Int,
        private val bitrate: Int = DEFAULT_BITRATE,
        private val fps: Int = DEFAULT_FPS
) {
    companion object {
        private const val TAG = "VideoEncoder"
        private const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC
        private const val DEFAULT_BITRATE = 4_000_000 // 4 Mbps
        private const val DEFAULT_FPS = 30
        private const val I_FRAME_INTERVAL = 1 // Keyframe every 1 second
        private const val TIMEOUT_US = 10_000L // 10ms
    }

    private var encoder: MediaCodec? = null
    private var inputSurface: Surface? = null

    /** Callback invoked with encoded H.264 data. */
    var onEncodedData: ((ByteBuffer, MediaCodec.BufferInfo) -> Unit)? = null

    /** Callback invoked when the output format changes (SPS/PPS headers). */
    var onFormatChanged: ((MediaFormat) -> Unit)? = null

    /** Start the encoder. Must be called before feeding frames. */
    fun start() {
        val format =
                MediaFormat.createVideoFormat(MIME_TYPE, width, height).apply {
                    setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                    setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                    setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, I_FRAME_INTERVAL)
                    setInteger(
                            MediaFormat.KEY_COLOR_FORMAT,
                            MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                    )
                    // Baseline profile for maximum compatibility.
                    setInteger(
                            MediaFormat.KEY_PROFILE,
                            MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline
                    )
                    setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31)
                }

        encoder =
                MediaCodec.createEncoderByType(MIME_TYPE).apply {
                    configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                    inputSurface = createInputSurface()
                    start()
                }

        // Start output thread to drain encoded buffers.
        startOutputThread()

        Log.d(TAG, "Encoder started: ${width}x${height} @ ${bitrate / 1_000_000}Mbps, ${fps}fps")
    }

    /**
     * Encode a single RGBA frame from a ByteBuffer.
     *
     * This method copies the pixel data into the encoder's input buffer. For better performance,
     * prefer [getInputSurface] + GL blit.
     *
     * @param rgbaBuffer RGBA pixel data (width * height * 4 bytes)
     * @param presentationTimeUs Timestamp in microseconds
     */
    fun encodeFrame(rgbaBuffer: ByteBuffer, presentationTimeUs: Long) {
        val codec = encoder ?: return

        val inputIndex = codec.dequeueInputBuffer(TIMEOUT_US)
        if (inputIndex >= 0) {
            val inputBuffer = codec.getInputBuffer(inputIndex)
            if (inputBuffer != null) {
                inputBuffer.clear()
                rgbaBuffer.rewind()
                inputBuffer.put(rgbaBuffer)
                codec.queueInputBuffer(inputIndex, 0, rgbaBuffer.remaining(), presentationTimeUs, 0)
            }
        }
    }

    /**
     * Get the input Surface for zero-copy encoding.
     *
     * When using surface input, the GL thread can render directly to this surface via
     * eglSwapBuffers, avoiding the pixel copy overhead.
     */
    fun getInputSurface(): Surface? = inputSurface

    /** Signal the end of the stream. */
    fun signalEndOfStream() {
        encoder?.signalEndOfInputStream()
    }

    /** Stop and release the encoder. */
    fun stop() {
        try {
            encoder?.signalEndOfInputStream()
            encoder?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping encoder", e)
        }
        outputThread?.quitSafely()
        try {
            outputThread?.join(1000)
        } catch (e: InterruptedException) {
            // Ignore
        }
        encoder?.release()
        inputSurface?.release()
        encoder = null
        inputSurface = null
    }

    // ---- Output draining thread ----

    private var outputThread: HandlerThread? = null

    private fun startOutputThread() {
        outputThread = HandlerThread("VideoEncoderOutput").apply { start() }
        val handler = Handler(outputThread!!.looper)

        handler.post { drainEncoder() }
    }

    private fun drainEncoder() {
        val codec = encoder ?: return
        val bufferInfo = MediaCodec.BufferInfo()

        while (true) {
            val outputIndex = codec.dequeueOutputBuffer(bufferInfo, TIMEOUT_US)
            when {
                outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    // No output available yet, check again later.
                    outputThread?.let { Handler(it.looper).postDelayed({ drainEncoder() }, 1) }
                    return
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val newFormat = codec.outputFormat
                    Log.d(TAG, "Output format changed: $newFormat")
                    onFormatChanged?.invoke(newFormat)
                }
                outputIndex >= 0 -> {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        outputBuffer.position(bufferInfo.offset)
                        outputBuffer.limit(bufferInfo.offset + bufferInfo.size)
                        onEncodedData?.invoke(outputBuffer, bufferInfo)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        Log.d(TAG, "End of stream reached")
                        return
                    }

                    // Continue draining.
                    outputThread?.let { Handler(it.looper).post { drainEncoder() } }
                    return
                }
            }
        }
    }
}
