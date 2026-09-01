/*
 *     Copyright (C) 2026 RedClaw
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

package br.com.redclaw.zelda64player.dashboard.streaming

import android.opengl.GLES30
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Captures the emulator's GL framebuffer asynchronously using PBO (Pixel Buffer Objects) to avoid
 * stalling the GL pipeline.
 *
 * The capture flow:
 * 1. Allocate two PBOs for double-buffering
 * 2. On capture request, bind PBO-A and issue async glReadPixels
 * 3. On next capture, bind PBO-B for new read while reading PBO-A's data
 * 4. Map PBO-A data and pass it to the encoder
 *
 * This approach avoids the GL pipeline stall that synchronous glReadPixels would cause, maintaining
 * smooth emulation while streaming.
 */
class FrameCapturer(private val width: Int, private val height: Int) {
    companion object {
        private const val TAG = "FrameCapturer"
        private const val NUM_PBOS = 2
    }

    private var pboIds: IntArray? = null
    private var currentIndex = 0
    private val isInitialized = AtomicBoolean(false)

    /** Initialize the PBO resources. Must be called on the GL thread. */
    fun initialize() {
        if (isInitialized.get()) return

        pboIds = IntArray(NUM_PBOS)
        GLES30.glGenBuffers(NUM_PBOS, pboIds, 0)

        val bufferSize = width * height * 4 // RGBA

        for (i in 0 until NUM_PBOS) {
            GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, pboIds!![i])
            GLES30.glBufferData(
                    GLES30.GL_PIXEL_PACK_BUFFER,
                    bufferSize,
                    null,
                    GLES30.GL_STREAM_READ
            )
        }

        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0)
        isInitialized.set(true)
        Log.d(TAG, "FrameCapturer initialized: ${width}x${height}, ${NUM_PBOS} PBOs")
    }

    /**
     * Capture the current framebuffer into the next PBO. Must be called on the GL thread after
     * rendering is complete.
     *
     * @return ByteBuffer with RGBA pixel data, or null if capture is not ready yet.
     * ```
     *         The buffer is valid until the next capture call.
     * ```
     */
    fun capture(): ByteBuffer? {
        if (!isInitialized.get()) return null

        val pboIds = pboIds ?: return null
        val captureIndex = currentIndex

        // Start async read of current frame into PBO[captureIndex].
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, pboIds[captureIndex])
        GLES30.glReadPixels(0, 0, width, height, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, 0)
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0)

        // Read the PREVIOUS frame from PBO[1 - captureIndex] (double-buffer).
        val readIndex = 1 - captureIndex
        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, pboIds[readIndex])

        val bufferSize = width * height * 4
        val buffer = ByteBuffer.allocateDirect(bufferSize).apply { order(ByteOrder.nativeOrder()) }

        val mappedBuffer =
                GLES30.glMapBufferRange(
                        GLES30.GL_PIXEL_PACK_BUFFER,
                        0,
                        bufferSize,
                        GLES30.GL_MAP_READ_BIT
                )

        if (mappedBuffer != null) {
            buffer.put(mappedBuffer as ByteBuffer)
            GLES30.glUnmapBuffer(GLES30.GL_PIXEL_PACK_BUFFER)
            buffer.flip()
        }

        GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0)

        // Swap to next PBO for next capture.
        currentIndex = 1 - currentIndex

        return if (mappedBuffer != null) buffer else null
    }

    /** Release PBO resources. Must be called on the GL thread. */
    fun release() {
        if (!isInitialized.getAndSet(false)) return
        pboIds?.let { GLES30.glDeleteBuffers(NUM_PBOS, it, 0) }
        pboIds = null
    }
}
