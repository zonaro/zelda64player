/*
 *     Copyright (C) 2026 RedClaw
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package br.com.redclaw.zelda64player.dashboard.streaming

import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Captures audio samples from the emulator's audio output and packages them for WebRTC
 * transmission.
 *
 * The audio pipeline:
 * 1. The emulator core produces PCM audio via the Oboe callback
 * 2. This capturer reads from a shared FIFO buffer (same one Oboe reads from)
 * 3. Audio samples are packaged into frames suitable for WebRTC AudioTrack
 *
 * Note: The actual Opus encoding is handled by the WebRTC native layer. This class provides raw PCM
 * data to the WebRTC AudioSource.
 */
class AudioCapturer(private val sampleRate: Int = 44100, private val channels: Int = 2) {
    companion object {
        private const val TAG = "AudioCapturer"
        private const val FRAME_SIZE_MS = 20 // 20ms audio frames (Opus standard)
    }

    /** Number of samples per frame (per channel). */
    private val samplesPerFrame = sampleRate * FRAME_SIZE_MS / 1000

    /** Bytes per frame (all channels). */
    private val bytesPerFrame = samplesPerFrame * channels * 2 // 16-bit PCM

    private val isCapturing = AtomicBoolean(false)
    private val audioQueue = LinkedBlockingQueue<ByteBuffer>(10)

    /** Callback invoked with raw PCM audio frames. */
    var onAudioFrame: ((ByteBuffer, Int) -> Unit)? = null

    /** Start capturing audio. Call this when streaming begins. */
    fun start() {
        isCapturing.set(true)
        Log.d(
                TAG,
                "Audio capture started: ${sampleRate}Hz, ${channels}ch, ${FRAME_SIZE_MS}ms frames"
        )
    }

    /** Stop capturing audio. Call this when streaming ends. */
    fun stop() {
        isCapturing.set(false)
        audioQueue.clear()
        Log.d(TAG, "Audio capture stopped")
    }

    /**
     * Feed audio data from the emulator's audio callback.
     *
     * This is called from the Oboe audio thread whenever new samples are available. The data is
     * queued for packaging into WebRTC frames.
     *
     * @param data Raw PCM samples (I16, interleaved stereo)
     * @param numFrames Number of sample frames (not bytes)
     */
    fun onAudioData(data: ShortArray, numFrames: Int) {
        if (!isCapturing.get()) return

        val buffer =
                ByteBuffer.allocateDirect(numFrames * channels * 2).apply {
                    order(ByteOrder.nativeOrder())
                    asShortBuffer().put(data, 0, numFrames * channels)
                }

        if (!audioQueue.offer(buffer)) {
            Log.w(TAG, "Audio queue full, dropping frame")
        }

        // Process queued audio into WebRTC-compatible frames.
        processAudioQueue()
    }

    /** Process queued audio data into fixed-size frames for WebRTC. */
    private fun processAudioQueue() {
        val combinedBuffer =
                ByteBuffer.allocateDirect(bytesPerFrame).apply { order(ByteOrder.nativeOrder()) }

        var bytesFilled = 0
        while (bytesFilled < bytesPerFrame) {
            val buffer = audioQueue.poll() ?: break
            val remaining = bytesPerFrame - bytesFilled
            val toCopy = minOf(buffer.remaining(), remaining)

            val temp = ByteArray(toCopy)
            buffer.get(temp)
            combinedBuffer.put(temp, 0, toCopy)
            bytesFilled += toCopy

            if (buffer.hasRemaining()) {
                audioQueue.offer(buffer) // Put back remaining data
            }
        }

        if (bytesFilled >= bytesPerFrame) {
            combinedBuffer.flip()
            onAudioFrame?.invoke(combinedBuffer, samplesPerFrame)
        }
    }

    /** Get the audio format description for WebRTC negotiation. */
    fun getAudioFormat(): AudioFormat {
        return AudioFormat(
                sampleRate = sampleRate,
                channels = channels,
                bitsPerSample = 16,
                frameSizeMs = FRAME_SIZE_MS
        )
    }
}

data class AudioFormat(
        val sampleRate: Int,
        val channels: Int,
        val bitsPerSample: Int,
        val frameSizeMs: Int
)
