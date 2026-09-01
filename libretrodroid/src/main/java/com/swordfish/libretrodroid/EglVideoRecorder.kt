/*
 * Copyright (C) 2026 RedClaw
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3.
 */
package com.swordfish.libretrodroid

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLExt
import android.opengl.GLES30
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import java.io.File
import kotlin.concurrent.thread

/**
 * Encodes this [GLRetroView]'s framebuffer and the PCM emitted by its libretro core.
 *
 * The core PCM is read through a bounded native tap and AAC-muxed with AVC video. This does not
 * use MediaProjection: recordings contain the emulator only, never audio from other applications.
 */
internal class EglVideoRecorder {
    companion object {
        private const val TAG = "EglVideoRecorder"
        private const val FRAME_RATE = 30
        private const val VIDEO_BIT_RATE = 8_000_000
        private const val AUDIO_BIT_RATE = 128_000
        private const val AUDIO_CHANNELS = 2
        private const val AUDIO_BUFFER_FRAMES = 2_048
        private const val DEQUEUE_TIMEOUT_US = 10_000L
    }

    private enum class Track { VIDEO, AUDIO }
    private data class EncodedSample(val track: Track, val bytes: ByteArray, val info: MediaCodec.BufferInfo)

    private var videoCodec: MediaCodec? = null
    private var audioCodec: MediaCodec? = null
    private var muxer: MediaMuxer? = null
    private var videoInputSurface: Surface? = null
    private var encoderSurface: android.opengl.EGLSurface? = null
    private var display: android.opengl.EGLDisplay? = null
    private var eglContext: android.opengl.EGLContext? = null
    private var outputFile: File? = null
    private var frameCount = 0
    private var recordingStartNanos = 0L
    private var audioSampleRate = 44_100
    private var audioFramesWritten = 0L
    @Volatile private var audioWorkerRunning = false
    private var audioWorker: Thread? = null
    private var includeMicrophone = false

    private val muxerLock = Any()
    private var videoTrack = -1
    private var audioTrack = -1
    private var muxerStarted = false
    private val pendingSamples = mutableListOf<EncodedSample>()

    /** Starts an emulator-only MP4 recording; [includeMic] mixes microphone input when permitted. */
    fun start(file: File, requestedWidth: Int, requestedHeight: Int, includeMic: Boolean): Boolean {
        val width = requestedWidth and 1.inv()
        val height = requestedHeight and 1.inv()
        if (width <= 0 || height <= 0) return false
        val currentDisplay = EGL14.eglGetCurrentDisplay()
        val currentContext = EGL14.eglGetCurrentContext()
        if (currentDisplay == EGL14.EGL_NO_DISPLAY || currentContext == EGL14.EGL_NO_CONTEXT) {
            Log.w(TAG, "Recording requested before the GL surface was ready")
            return false
        }
        return try {
            audioSampleRate = LibretroDroid.getRecordingAudioSampleRate().coerceAtLeast(8_000)
            this.includeMicrophone = includeMic
            outputFile = file
            display = currentDisplay
            eglContext = currentContext
            recordingStartNanos = SystemClock.elapsedRealtimeNanos()
            audioFramesWritten = 0L
            frameCount = 0
            muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val video = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, VIDEO_BIT_RATE)
                setInteger(MediaFormat.KEY_FRAME_RATE, FRAME_RATE)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                video.configure(this, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }
            videoInputSurface = video.createInputSurface()
            video.start()
            videoCodec = video

            val audio = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
            MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, audioSampleRate, AUDIO_CHANNELS).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, AUDIO_BIT_RATE)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, AUDIO_BUFFER_FRAMES * AUDIO_CHANNELS * 2)
                audio.configure(this, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }
            audio.start()
            audioCodec = audio

            val config = findCurrentConfig(currentDisplay, currentContext)
                ?: error("Could not resolve the GLSurfaceView EGL config")
            val surface = EGL14.eglCreateWindowSurface(
                currentDisplay, config, videoInputSurface, intArrayOf(EGL14.EGL_NONE), 0
            )
            check(surface != EGL14.EGL_NO_SURFACE) {
                "Could not create the encoder EGL surface: \${EGL14.eglGetError()}"
            }
            encoderSurface = surface
            LibretroDroid.startRecordingAudioCapture()
            audioWorkerRunning = true
            audioWorker = thread(name = "EmulatorRecordingAudio") { encodeCoreAudio() }
            true
        } catch (error: Exception) {
            Log.e(TAG, "Unable to start emulator recording", error)
            release(deleteEmptyOutput = true)
            false
        }
    }

    /** Copies the current emulator framebuffer into the AVC encoder. Called on the GL thread. */
    fun recordFrame(sourceWidth: Int, sourceHeight: Int) {
        val currentDisplay = display ?: return
        val currentContext = eglContext ?: return
        val destination = encoderSurface ?: return
        val sourceDraw = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW)
        val sourceRead = EGL14.eglGetCurrentSurface(EGL14.EGL_READ)
        if (sourceDraw == EGL14.EGL_NO_SURFACE || sourceRead == EGL14.EGL_NO_SURFACE) return
        var shouldRelease = false
        try {
            check(EGL14.eglMakeCurrent(currentDisplay, destination, sourceRead, currentContext)) {
                "Could not make encoder EGL surface current: \${EGL14.eglGetError()}"
            }
            val encodedWidth = IntArray(1)
            val encodedHeight = IntArray(1)
            EGL14.eglQuerySurface(currentDisplay, destination, EGL14.EGL_WIDTH, encodedWidth, 0)
            EGL14.eglQuerySurface(currentDisplay, destination, EGL14.EGL_HEIGHT, encodedHeight, 0)
            if (encodedWidth[0] <= 0 || encodedHeight[0] <= 0) return
            GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, 0)
            GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, 0)
            GLES30.glBlitFramebuffer(
                0, 0, sourceWidth, sourceHeight, 0, 0, encodedWidth[0], encodedHeight[0],
                GLES30.GL_COLOR_BUFFER_BIT, GLES30.GL_NEAREST
            )
            EGLExt.eglPresentationTimeANDROID(
                currentDisplay, destination, SystemClock.elapsedRealtimeNanos() - recordingStartNanos
            )
            check(EGL14.eglSwapBuffers(currentDisplay, destination)) {
                "Could not submit encoder frame: \${EGL14.eglGetError()}"
            }
            frameCount++
            drainEncoder(videoCodec, Track.VIDEO, false)
        } catch (error: Exception) {
            Log.e(TAG, "Unable to encode emulator frame", error)
            shouldRelease = true
        } finally {
            EGL14.eglMakeCurrent(currentDisplay, sourceDraw, sourceRead, currentContext)
            if (shouldRelease) release(deleteEmptyOutput = frameCount == 0)
        }
    }

    fun stop() = release(deleteEmptyOutput = frameCount == 0)

    private fun encodeCoreAudio() {
        val coreSamples = ShortArray(AUDIO_BUFFER_FRAMES * AUDIO_CHANNELS)
        val microphone = if (includeMicrophone) createMicrophone() else null
        val micSamples = ShortArray(AUDIO_BUFFER_FRAMES)
        try {
            while (audioWorkerRunning) {
                var sampleCount = LibretroDroid.readRecordingAudio(coreSamples)
                sampleCount -= sampleCount % AUDIO_CHANNELS
                if (sampleCount == 0) {
                    Thread.sleep(4)
                    continue
                }
                if (microphone != null) {
                    val frames = sampleCount / AUDIO_CHANNELS
                    val micCount = microphone.read(micSamples, 0, frames, AudioRecord.READ_BLOCKING)
                    if (micCount > 0) mixMicrophone(coreSamples, micSamples, frames, micCount)
                }
                queueAudio(coreSamples, sampleCount)
            }
        } catch (error: Exception) {
            Log.w(TAG, "Emulator audio encoder stopped unexpectedly", error)
        } finally {
            runCatching { microphone?.stop() }
            microphone?.release()
        }
    }

    private fun createMicrophone(): AudioRecord? = runCatching {
        val minBuffer = AudioRecord.getMinBufferSize(
            audioSampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return null
        AudioRecord(
            MediaRecorder.AudioSource.MIC, audioSampleRate, AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, AUDIO_BUFFER_FRAMES * 2)
        ).also {
            if (it.state != AudioRecord.STATE_INITIALIZED) {
                it.release()
                return null
            }
            it.startRecording()
        }
    }.getOrNull()

    private fun mixMicrophone(core: ShortArray, microphone: ShortArray, frames: Int, micCount: Int) {
        for (frame in 0 until frames) {
            val mic = if (frame < micCount) microphone[frame].toInt() else 0
            core[frame * 2] = (core[frame * 2].toInt() + mic)
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            core[frame * 2 + 1] = (core[frame * 2 + 1].toInt() + mic)
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }

    private fun queueAudio(samples: ShortArray, sampleCount: Int) {
        val codec = audioCodec ?: return
        var inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
        while (inputIndex < 0 && audioWorkerRunning) {
            drainEncoder(codec, Track.AUDIO, false)
            inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
        }
        if (inputIndex < 0) return
        val input = codec.getInputBuffer(inputIndex) ?: return
        input.clear()
        input.asShortBuffer().put(samples, 0, sampleCount)
        val pts = audioFramesWritten * 1_000_000L / audioSampleRate
        audioFramesWritten += sampleCount / AUDIO_CHANNELS
        codec.queueInputBuffer(inputIndex, 0, sampleCount * 2, pts, 0)
        drainEncoder(codec, Track.AUDIO, false)
    }

    private fun drainEncoder(codec: MediaCodec?, track: Track, endOfStream: Boolean) {
        codec ?: return
        val info = MediaCodec.BufferInfo()
        var attempts = 0
        while (attempts++ < if (endOfStream) 50 else 1) {
            when (val outputIndex = codec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)) {
                MediaCodec.INFO_TRY_AGAIN_LATER -> if (!endOfStream) return else continue
                MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> addTrack(track, codec.outputFormat)
                MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED -> Unit
                else -> if (outputIndex >= 0) {
                    val output = codec.getOutputBuffer(outputIndex)
                    if (output != null && info.size > 0 &&
                        info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                    ) {
                        output.position(info.offset)
                        output.limit(info.offset + info.size)
                        val bytes = ByteArray(info.size)
                        output.get(bytes)
                        writeSample(EncodedSample(track, bytes, MediaCodec.BufferInfo().apply {
                            set(0, bytes.size, info.presentationTimeUs, info.flags)
                        }))
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    private fun addTrack(track: Track, format: MediaFormat) = synchronized(muxerLock) {
        val currentMuxer = muxer ?: return@synchronized
        when (track) {
            Track.VIDEO -> if (videoTrack < 0) videoTrack = currentMuxer.addTrack(format)
            Track.AUDIO -> if (audioTrack < 0) audioTrack = currentMuxer.addTrack(format)
        }
        if (!muxerStarted && videoTrack >= 0 && audioTrack >= 0) {
            currentMuxer.start()
            muxerStarted = true
            pendingSamples.forEach(::writeSampleLocked)
            pendingSamples.clear()
        }
    }

    private fun writeSample(sample: EncodedSample) = synchronized(muxerLock) {
        if (muxerStarted) writeSampleLocked(sample) else pendingSamples += sample
    }

    private fun writeSampleLocked(sample: EncodedSample) {
        val trackIndex = if (sample.track == Track.VIDEO) videoTrack else audioTrack
        if (trackIndex >= 0) muxer?.writeSampleData(trackIndex, java.nio.ByteBuffer.wrap(sample.bytes), sample.info)
    }

    private fun release(deleteEmptyOutput: Boolean) {
        audioWorkerRunning = false
        audioWorker?.takeIf { it !== Thread.currentThread() }?.join(1_000)
        audioWorker = null
        LibretroDroid.stopRecordingAudioCapture()
        audioCodec?.let { codec ->
            runCatching {
                val input = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                if (input >= 0) codec.queueInputBuffer(
                    input, 0, 0, audioFramesWritten * 1_000_000L / audioSampleRate,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
                drainEncoder(codec, Track.AUDIO, true)
            }
            runCatching { codec.stop() }
            codec.release()
        }
        audioCodec = null
        videoCodec?.let { codec ->
            runCatching { codec.signalEndOfInputStream() }
            drainEncoder(codec, Track.VIDEO, true)
            runCatching { codec.stop() }
            codec.release()
        }
        videoCodec = null
        encoderSurface?.let { surface -> display?.let { EGL14.eglDestroySurface(it, surface) } }
        encoderSurface = null
        videoInputSurface?.release()
        videoInputSurface = null
        synchronized(muxerLock) {
            runCatching { if (muxerStarted) muxer?.stop() }
            muxer?.release()
            muxer = null
            muxerStarted = false
            videoTrack = -1
            audioTrack = -1
            pendingSamples.clear()
        }
        if (deleteEmptyOutput) outputFile?.delete()
        outputFile = null
        display = null
        eglContext = null
        frameCount = 0
    }

    private fun findCurrentConfig(
        currentDisplay: android.opengl.EGLDisplay, currentContext: android.opengl.EGLContext
    ): EGLConfig? {
        val currentConfigId = IntArray(1)
        if (!EGL14.eglQueryContext(currentDisplay, currentContext, EGL14.EGL_CONFIG_ID, currentConfigId, 0)) return null
        val count = IntArray(1)
        if (!EGL14.eglGetConfigs(currentDisplay, null, 0, 0, count, 0)) return null
        val configs = arrayOfNulls<EGLConfig>(count[0])
        if (!EGL14.eglGetConfigs(currentDisplay, configs, 0, configs.size, count, 0)) return null
        return configs.firstOrNull { config ->
            config != null && IntArray(1).let { configId ->
                EGL14.eglGetConfigAttrib(currentDisplay, config, EGL14.EGL_CONFIG_ID, configId, 0) &&
                    configId[0] == currentConfigId[0]
            }
        }
    }
}
