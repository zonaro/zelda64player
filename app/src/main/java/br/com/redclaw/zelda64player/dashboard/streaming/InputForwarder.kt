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
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.swordfish.libretrodroid.GLRetroView

/**
 * Forwards input events received from the browser (via WebRTC DataChannel) to the emulator's
 * GLRetroView.
 *
 * The browser sends input events as binary messages over the DataChannel:
 *
 * Key Events (4 bytes): [0] = 1 (key event type) [1] = keyCode (Android KeyEvent code) [2] = action
 * (0 = up, 1 = down) [3] = device id (0 for browser input)
 *
 * Motion Events (12 bytes): [0] = 2 (motion event type) [1] = source (0 = joystick, 1 =
 * mouse/touch) [2-3] = x axis (float as short, -32768 to 32767 mapped to -1.0 to 1.0) [4-5] = y
 * axis [6-7] = z axis (trigger) [8-9] = rx axis [10-11] = ry axis
 *
 * N64 Controller Mapping (browser → Android): A button → KeyEvent.KEYCODE_BUTTON_A B button →
 * KeyEvent.KEYCODE_BUTTON_B Z button → KeyEvent.KEYCODE_BUTTON_THUMBL Start →
 * KeyEvent.KEYCODE_BUTTON_START D-Pad → KeyEvent.KEYCODE_DPAD_UP/DOWN/LEFT/RIGHT C buttons →
 * KeyEvent.KEYCODE_BUTTON_X/Y/A/B (remapped) Analog stick → MotionEvent with SOURCE_JOYSTICK
 */
class InputForwarder(private val glRetroView: GLRetroView) {

    companion object {
        private const val TAG = "InputForwarder"
        private const val EVENT_TYPE_KEY = 1
        private const val EVENT_TYPE_MOTION = 2
        private const val AXIS_RANGE = 32767f
    }

    /**
     * Process a raw input event from the browser's DataChannel.
     *
     * @param data Raw binary data from DataChannel
     */
    fun processInputEvent(data: ByteArray) {
        if (data.isEmpty()) return

        when (data[0].toInt()) {
            EVENT_TYPE_KEY -> processKeyEvent(data)
            EVENT_TYPE_MOTION -> processMotionEvent(data)
            else -> Log.w(TAG, "Unknown input event type: ${data[0]}")
        }
    }

    /** Process a key event from the browser. */
    private fun processKeyEvent(data: ByteArray) {
        if (data.size < 4) return

        val keyCode = data[1].toInt() and 0xFF
        val action = data[2].toInt()
        val deviceId = data[3].toInt() and 0xFF

        val eventAction =
                when (action) {
                    0 -> KeyEvent.ACTION_UP
                    1 -> KeyEvent.ACTION_DOWN
                    else -> return
                }

        val event =
                KeyEvent(
                        0, // eventTime
                        0, // downTime
                        eventAction,
                        keyCode,
                        0, // repeat
                        0, // metaState
                        deviceId, // deviceId
                        0, // scanCode
                        0, // flags
                        0 // source
                )

        // Dispatch to the GLRetroView.
        glRetroView.dispatchKeyEvent(event)
    }

    /** Process a motion event from the browser (analog stick, triggers). */
    private fun processMotionEvent(data: ByteArray) {
        if (data.size < 12) return

        val source = data[1].toInt()
        val xAxis = readShort(data, 2) / AXIS_RANGE
        val yAxis = readShort(data, 4) / AXIS_RANGE
        val zAxis = readShort(data, 6) / AXIS_RANGE
        val rxAxis = readShort(data, 8) / AXIS_RANGE
        val ryAxis = readShort(data, 10) / AXIS_RANGE

        val eventSource =
                when (source) {
                    0 -> InputDevice.SOURCE_JOYSTICK
                    1 -> InputDevice.SOURCE_TOUCHSCREEN
                    else -> InputDevice.SOURCE_JOYSTICK
                }

        val pointerProperties =
                arrayOf(
                        MotionEvent.PointerProperties().apply {
                            id = 0
                            toolType = MotionEvent.TOOL_TYPE_UNKNOWN
                        }
                )
        val pointerCoords =
                arrayOf(
                        MotionEvent.PointerCoords().apply {
                            x = xAxis
                            y = yAxis
                            setAxisValue(MotionEvent.AXIS_Z, zAxis)
                            setAxisValue(MotionEvent.AXIS_RX, rxAxis)
                            setAxisValue(MotionEvent.AXIS_RY, ryAxis)
                        }
                )

        val event =
                MotionEvent.obtain(
                        0, // downTime
                        0, // eventTime
                        MotionEvent.ACTION_MOVE,
                        1, // pointerCount
                        pointerProperties,
                        pointerCoords,
                        0, // metaState
                        0, // buttonState
                        0.0f, // xPrecision
                        0.0f, // yPrecision
                        0, // deviceId (0 for browser)
                        0, // edgeFlags
                        eventSource, // source
                        0 // flags
                )

        glRetroView.dispatchGenericMotionEvent(event)
    }

    /** Read a signed short from a byte array at the given offset. */
    private fun readShort(data: ByteArray, offset: Int): Short {
        return ((data[offset].toInt() shl 8) or (data[offset + 1].toInt() and 0xFF)).toShort()
    }
}
