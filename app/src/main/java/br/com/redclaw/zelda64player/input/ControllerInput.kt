package br.com.redclaw.zelda64player.input

import android.view.InputEvent
import android.view.KeyEvent
import android.view.MotionEvent
import br.com.redclaw.zelda64player.retroview.RetroView
import com.swordfish.libretrodroid.GLRetroView

class ControllerInput {
    companion object {
        /**
         * Combination to open the menu
         */
        val KEYCOMBO_MENU = setOf(
            KeyEvent.KEYCODE_BUTTON_START,
            KeyEvent.KEYCODE_BUTTON_SELECT
        )

        /**
         * Any of these keys will not be piped to the RetroView
         */
        val EXCLUDED_KEYS = setOf(
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_BACK,
            KeyEvent.KEYCODE_POWER
        )

        /** Raw (pre-InputMapper) keycodes for C-Left/C-Right/C-Down/Z -- pressing any of these,
         *  from any input source, is tracked via [onCButtonDown] so Button Stick's Auto mode can
         *  follow it. */
        val TRACKED_C_BUTTONS = setOf(
            KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_L1,
            KeyEvent.KEYCODE_BUTTON_X,
            KeyEvent.KEYCODE_BUTTON_L2
        )

    }
    /**
     * Set of keys currently being held by the user
     */
    private val keyLog = mutableSetOf<Int>()

    /**
     * The callback for when the user inputs the menu key-combination
     */
    var menuCallback: () -> Unit = {}

    /**
     * Resolves the physical right stick's current Button Stick target, mirroring the on-screen
     * Button Stick's own mode -- null means Off, so the right stick keeps its default behavior
     * (raw axis piped to MOTION_SOURCE_ANALOG_RIGHT, which the core's alt-map turns into digital
     * C-button presses). Non-null means R3 presses that button (see [processKeyEvent]) and the
     * stick itself always drives the N64's own analog stick (MOTION_SOURCE_ANALOG_LEFT) instead.
     */
    var buttonStickTargetKeyCode: (() -> Int?)? = null

    /** Mirrors the touch Button Stick's own sensitivity so both feel the same. */
    var buttonStickSensitivity: Float = 0.5f

    var autoZEnabled: Boolean = true

    /** Fires on ACTION_DOWN for any of [TRACKED_C_BUTTONS], mirroring the touch Button Stick's
     *  own press tracking so Auto mode also follows presses made from the physical controller. */
    var onCButtonDown: ((Int) -> Unit)? = null

    /** Which button R3 is currently holding down, so the matching key-up goes out on release
     *  even if Auto mode's target changed while it was held. */
    private var r3HeldKeyCode: Int? = null

    /**
     *  Controller numbers are [1, inf), we need [0, inf)
     */
    private fun getPort(event: InputEvent): Int =
        ((event.device?.controllerNumber ?: 1) - 1).coerceAtLeast(0)

    /**
     * Check if we should be showing the user the menu
     */
    private fun checkMenuKeyCombo() {
        if (keyLog == KEYCOMBO_MENU)
            menuCallback()
    }

    fun processKeyEvent(keyCode: Int, event: KeyEvent, retroView: RetroView): Boolean? {
        /* Block these keys! */
        if (EXCLUDED_KEYS.contains(keyCode))
            return null

        /* We're not ready yet! */
        if (retroView.frameRendered.value == false)
            return true

        /* Guide/Xbox button opens the emulator menu instead of being piped to the core */
        if (keyCode == KeyEvent.KEYCODE_BUTTON_MODE) {
            if (event.action == KeyEvent.ACTION_DOWN)
                menuCallback()
            return true
        }

        /* L3 stands in for a physical Z button while Auto-Z is enabled -- held only while the
           stick itself is held, unlike the double-tap-to-toggle behavior on the touch stick. */
        if (keyCode == KeyEvent.KEYCODE_BUTTON_THUMBL) {
            if (!autoZEnabled)
                return true

            val port = getPort(event)
            retroView.view.sendKeyEvent(event.action, InputMapper.mapKeyCode(KeyEvent.KEYCODE_BUTTON_L2), port)
            return true
        }

        /* R3 presses/holds the right stick's current Button Stick target, mirroring touching
           down on the touch Button Stick -- the stick's own motion always drives the N64 analog
           stick while a target is set (see processMotionEvent). Off leaves R3 unmapped. */
        if (keyCode == KeyEvent.KEYCODE_BUTTON_THUMBR) {
            val port = getPort(event)
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    val targetKeyCode = buttonStickTargetKeyCode?.invoke() ?: return true
                    r3HeldKeyCode = targetKeyCode
                    retroView.view.sendKeyEvent(KeyEvent.ACTION_DOWN, InputMapper.mapKeyCode(targetKeyCode), port)
                }
                KeyEvent.ACTION_UP -> {
                    r3HeldKeyCode?.let {
                        r3HeldKeyCode = null
                        retroView.view.sendKeyEvent(KeyEvent.ACTION_UP, InputMapper.mapKeyCode(it), port)
                    }
                }
            }
            return true
        }

        val effectiveKeyCode = N64ControllerMapping.effectivePhysicalKeyCode(keyCode)
        val port = getPort(event)
        retroView.view.sendKeyEvent(event.action, InputMapper.mapKeyCode(effectiveKeyCode), port)

        if (event.action == KeyEvent.ACTION_DOWN && effectiveKeyCode in TRACKED_C_BUTTONS)
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
        if (retroView.frameRendered.value == false)
            return null

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

        val targetKeyCode = buttonStickTargetKeyCode?.invoke()
        if (targetKeyCode == null) {
            /* Off -- default behavior: left stick drives the N64 stick as usual, right stick's
               raw axis is piped to the core's own alt-map for digital C-buttons */
            view.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, leftX, leftY, port)
            view.sendMotionEvent(
                GLRetroView.MOTION_SOURCE_ANALOG_RIGHT,
                event.getAxisValue(MotionEvent.AXIS_Z),
                event.getAxisValue(MotionEvent.AXIS_RZ),
                port
            )
        } else {
            /* Acts like the touch Button Stick: the stick always drives the N64's own analog
               stick (holding the target button is R3's job, see processKeyEvent above), scaled
               by the shared sensitivity and combined with the left stick -- both sticks feed the
               same MOTION_SOURCE_ANALOG_LEFT channel, so they must be summed into one call
               instead of each overwriting the other's value. */
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
