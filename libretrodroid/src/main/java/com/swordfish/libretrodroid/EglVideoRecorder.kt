/*
 *     Copyright (C) 2026 RedClaw
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package com.swordfish.libretrodroid

import android.media.MediaRecorder
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLExt
import android.opengl.GLES30
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import java.io.File

/**
 * Encodes the current [GLRetroView] framebuffer without using MediaProjection.
 *
 * Every method is called on GLSurfaceView's GL thread. The encoder owns a
 * second EGL window surface backed by [MediaRecorder]'s input surface. Each
 * frame is blitted from the emulator's display surface to that encoder surface,
 * so Android UI overlays and content from other apps can never be recorded.
 */
internal class EglVideoRecorder {

    companion object {
        private const val TAG = "EglVideoRecorder"
        private const val FRAME_RATE = 30
        private const val BIT_RATE = 8_000_000
    }

    private var mediaRecorder: MediaRecorder? = null
    private var inputSurface: Surface? = null
    private var encoderSurface: android.opengl.EGLSurface? = null
    private var display: android.opengl.EGLDisplay? = null
    private var eglContext: android.opengl.EGLContext? = null
    private var outputFile: File? = null
    private var frameCount = 0

    fun start(file: File, requestedWidth: Int, requestedHeight: Int): Boolean {
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
            val recorder = MediaRecorder()
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setOutputFile(file.absolutePath)
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            recorder.setVideoSize(width, height)
            recorder.setVideoFrameRate(FRAME_RATE)
            recorder.setVideoEncodingBitRate(BIT_RATE)
            recorder.prepare()

            val surface = recorder.surface
            mediaRecorder = recorder
            inputSurface = surface
            display = currentDisplay
            eglContext = currentContext
            outputFile = file
            val config = findCurrentConfig(currentDisplay, currentContext)
                ?: error("Could not resolve the GLSurfaceView EGL config")
            val eglSurface = EGL14.eglCreateWindowSurface(
                currentDisplay,
                config,
                surface,
                intArrayOf(EGL14.EGL_NONE),
                0
            )
            check(eglSurface != EGL14.EGL_NO_SURFACE) {
                "Could not create the encoder EGL surface: ${EGL14.eglGetError()}"
            }
            encoderSurface = eglSurface

            recorder.start()
            frameCount = 0
            true
        } catch (error: Exception) {
            Log.e(TAG, "Unable to start emulator recording", error)
            release(deleteEmptyOutput = true)
            false
        }
    }

    /** Copy the emulator's current display framebuffer into the H.264 encoder. */
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
                "Could not make encoder EGL surface current: ${EGL14.eglGetError()}"
            }
            val hasEncodedWidth = EGL14.eglQuerySurface(
                currentDisplay, destination, EGL14.EGL_WIDTH, IntArray(1), 0
            )
            val encodedWidth = IntArray(1)
            val encodedHeight = IntArray(1)
            EGL14.eglQuerySurface(currentDisplay, destination, EGL14.EGL_WIDTH, encodedWidth, 0)
            EGL14.eglQuerySurface(currentDisplay, destination, EGL14.EGL_HEIGHT, encodedHeight, 0)
            if (!hasEncodedWidth || encodedWidth[0] <= 0 || encodedHeight[0] <= 0) return

            GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, 0)
            GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, 0)
            GLES30.glBlitFramebuffer(
                0, 0, sourceWidth, sourceHeight,
                0, 0, encodedWidth[0], encodedHeight[0],
                GLES30.GL_COLOR_BUFFER_BIT,
                GLES30.GL_NEAREST
            )
            EGLExt.eglPresentationTimeANDROID(
                currentDisplay,
                destination,
                SystemClock.elapsedRealtimeNanos()
            )
            check(EGL14.eglSwapBuffers(currentDisplay, destination)) {
                "Could not submit encoder frame: ${EGL14.eglGetError()}"
            }
            frameCount++
        } catch (error: Exception) {
            Log.e(TAG, "Unable to encode emulator frame", error)
            shouldRelease = true
        } finally {
            EGL14.eglMakeCurrent(currentDisplay, sourceDraw, sourceRead, currentContext)
            if (shouldRelease) release(deleteEmptyOutput = frameCount == 0)
        }
    }

    fun stop() {
        release(deleteEmptyOutput = frameCount == 0)
    }

    private fun findCurrentConfig(
        currentDisplay: android.opengl.EGLDisplay,
        currentContext: android.opengl.EGLContext
    ): EGLConfig? {
        val currentConfigId = IntArray(1)
        if (!EGL14.eglQueryContext(
                currentDisplay, currentContext, EGL14.EGL_CONFIG_ID, currentConfigId, 0
            )
        ) return null
        val count = IntArray(1)
        if (!EGL14.eglGetConfigs(currentDisplay, null, 0, 0, count, 0)) return null
        val configs = arrayOfNulls<EGLConfig>(count[0])
        if (!EGL14.eglGetConfigs(currentDisplay, configs, 0, configs.size, count, 0)) return null
        return configs.firstOrNull { config ->
            config != null && IntArray(1).let { configId ->
                EGL14.eglGetConfigAttrib(
                    currentDisplay, config, EGL14.EGL_CONFIG_ID, configId, 0
                ) && configId[0] == currentConfigId[0]
            }
        }
    }

    private fun release(deleteEmptyOutput: Boolean) {
        val currentDisplay = display
        encoderSurface?.let { surface ->
            if (currentDisplay != null) EGL14.eglDestroySurface(currentDisplay, surface)
        }
        encoderSurface = null
        try {
            mediaRecorder?.stop()
        } catch (error: RuntimeException) {
            Log.w(TAG, "Recorder stopped before a complete frame was written", error)
        }
        mediaRecorder?.release()
        mediaRecorder = null
        inputSurface?.release()
        inputSurface = null
        if (deleteEmptyOutput) outputFile?.delete()
        outputFile = null
        display = null
        eglContext = null
        frameCount = 0
    }
}
