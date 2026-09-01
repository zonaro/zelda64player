package br.com.redclaw.zelda64player.input

import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import br.com.redclaw.zelda64player.retroview.RetroView
import com.swordfish.libretrodroid.GLRetroView

class ControllerInput {
    companion object {
        /** Combination to open the menu */
        val KEYCOMBO_MENU = setOf(KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT)

        /** Any of these keys will not be piped to the RetroView */
        val EXCLUDED_KEYS =
                setOf(
                        KeyEvent.KEYCODE_VOLUME_DOWN,
                        KeyEvent.KEYCODE_VOLUME_UP,
                        KeyEvent.KEYCODE_BACK,
                        KeyEvent.KEYCODE_POWER
                )

        /**
         * Raw (pre-InputMapper) keycodes for C-Left/C-Right/C-Down/Z -- pressing any of these, from
         * any input source, is tracked via [onCButtonDown] so Button Stick's Auto mode can follow
         * it.
         */
        val TRACKED_C_BUTTONS =
                setOf(
                        KeyEvent.KEYCODE_BUTTON_R1,
                        KeyEvent.KEYCODE_BUTTON_L1,
                        KeyEvent.KEYCODE_BUTTON_X,
                        KeyEvent.KEYCODE_BUTTON_L2
                )
    }
    /** Set of keys currently being held by the user */
    private val keyLog = mutableSetOf<Int>()

    /** The callback for when the user inputs the menu key-combination */
    var menuCallback: () -> Unit = {}

    /**
     * Whether ButtonStick toggle is ON. When ON, the physical right stick drives the N64 analog
     * (MOTION_SOURCE_ANALOG_LEFT) combined with the left stick; when OFF it drives
     * MOTION_SOURCE_ANALOG_RIGHT (core alt-map for C-buttons). Mirrors the on-screen toggle.
     */
    var isButtonStickEnabled: (() -> Boolean)? = null

    /** @deprecated Use [isButtonStickEnabled] — kept for migration. */
    var buttonStickTargetKeyCode: (() -> Int?)? = null
        get() = field
        set(value) {
            field = value
            // Bridge old API to new: non-null target means enabled
            if (value != null && isButtonStickEnabled == null) {
                isButtonStickEnabled = { value.invoke() != null }
            }
        }

    /** Mirrors the touch StickButtons' sensitivity so both feel the same. */
    var buttonStickSensitivity: Float = 0.5f

    var autoZEnabled: Boolean = true

    /**
     * Fires on ACTION_DOWN for any of [TRACKED_C_BUTTONS], mirroring the touch Button Stick's own
     * press tracking so Auto mode also follows presses made from the physical controller.
     */
    var onCButtonDown: ((Int) -> Unit)? = null

    /**
     * Which button R3 is currently holding down, so the matching key-up goes out on release even if
     * Auto mode's target changed while it was held.
     */
    private var r3HeldKeyCode: Int? = null

    /** Controller numbers are [1, inf), we need [0, inf) */
    private fun getPort(event: InputEvent): Int =
            ((event.device?.controllerNumber ?: 1) - 1).coerceAtLeast(0)

    /** Check if we should be showing the user the menu */
    private fun checkMenuKeyCombo() {
        if (keyLog == KEYCOMBO_MENU) menuCallback()
    }

    fun processKeyEvent(keyCode: Int, event: KeyEvent, retroView: RetroView): Boolean? {
        /* Block these keys! */
        if (EXCLUDED_KEYS.contains(keyCode)) return null

        /* We're not ready yet! */
        if (retroView.frameRendered.value == false) return true

        /* Guide/Xbox button opens the emulator menu instead of being piped to the core */
        if (keyCode == KeyEvent.KEYCODE_BUTTON_MODE) {
            if (event.action == KeyEvent.ACTION_DOWN) menuCallback()
            return true
        }

        /* L3 stands in for a physical Z button while Auto-Z is enabled -- held only while the
        stick itself is held, unlike the double-tap-to-toggle behavior on the touch stick. */
        if (keyCode == KeyEvent.KEYCODE_BUTTON_THUMBL) {
            if (!autoZEnabled) return true

            val port = getPort(event)
            retroView.view.sendKeyEvent(
                    event.action,
                    InputMapper.mapKeyCode(KeyEvent.KEYCODE_BUTTON_L2),
                    port
            )
            return true
        }

        /* R3 is no longer mapped to ButtonStick (toggle mode removed per-button target).
        When ButtonStick is ON, right stick already drives analog; R3 is free. */
        if (keyCode == KeyEvent.KEYCODE_BUTTON_THUMBR) {
            return true
        }

        val effectiveKeyCode = N64ControllerMapping.effectivePhysicalKeyCode(keyCode)
        val port = getPort(event)
        retroView.view.sendKeyEvent(event.action, InputMapper.mapKeyCode(effectiveKeyCode), port)

        if (event.action == KeyEvent.ACTION_DOWN &&
                        (effectiveKeyCode in TRACKED_C_BUTTONS ||
                                effectiveKeyCode == KeyEvent.KEYCODE_BUTTON_START)
        )
                onCButtonDown?.invoke(effectiveKeyCode)

        /* Keep track of user input events */
        when (event.action) {
            KeyEvent.ACTION_DOWN -> keyLog.add(keyCode)
            KeyEvent.ACTION_UP -> keyLog.remove(keyCode)
        }

        checkMenuKeyCombo()

        return true
    }

    fun processMotionEvent(event: MotionEvent, retroView: RetroView): Boolean? {
        /* We're not ready yet! */
        if (retroView.frameRendered.value == false) return null

        val port = getPort(event)
        val view = retroView.view

        view.sendMotionEvent(
                GLRetroView.MOTION_SOURCE_DPAD,
                event.getAxisValue(MotionEvent.AXIS_HAT_X),
                event.getAxisValue(MotionEvent.AXIS_HAT_Y),
                port
        )

        val leftX = event.getAxisValue(MotionEvent.AXIS_X)
        val leftY = event.getAxisValue(MotionEvent.AXIS_Y)

        val stickOn = isButtonStickEnabled?.invoke() ?: (buttonStickTargetKeyCode?.invoke() != null)
        if (!stickOn) {
            view.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, leftX, leftY, port)
            view.sendMotionEvent(
                    GLRetroView.MOTION_SOURCE_ANALOG_RIGHT,
                    event.getAxisValue(MotionEvent.AXIS_Z),
                    event.getAxisValue(MotionEvent.AXIS_RZ),
                    port
            )
        } else {
            val rightX = event.getAxisValue(MotionEvent.AXIS_Z) * buttonStickSensitivity
            val rightY = -event.getAxisValue(MotionEvent.AXIS_RZ) * buttonStickSensitivity
            view.sendMotionEvent(
                    GLRetroView.MOTION_SOURCE_ANALOG_LEFT,
                    (leftX + rightX).coerceIn(-1f, 1f),
                    (leftY + rightY).coerceIn(-1f, 1f),
                    port
            )
        }

        return true
    }
}
