package br.com.redclaw.zelda64player.input

import android.view.KeyEvent

/**
 * Translates gamepad keycodes into the RetroPad layout expected by the
 * mupen64plus_next core running with the "mupen64plus-alt-map" option enabled.
 *
 * With the core's alternate mapping the RetroPad buttons are assigned as:
 *   RetroPad B     -> N64 A
 *   B     -> N64 B
 *   R     -> N64 C-Right
 *   L     -> N64 C-Left
 *   A     -> N64 C-Down
 *   X     -> N64 C-Up
 *   L2    -> N64 Z-Trigger
 *   R2    -> N64 R
 *   SELECT-> N64 L
 *
 * To present the user with an Xbox-style layout (A=A, B=B, Y=C-Up, X=C-Down),
 * the face buttons must be shifted: A -> B, B -> Y, Y -> X, X -> A.
 * Every other button (shoulders, triggers, DPAD, START, SELECT) passes through.
 */
object InputMapper {
    private val FACE_BUTTON_MAP = mapOf(
        KeyEvent.KEYCODE_BUTTON_A to KeyEvent.KEYCODE_BUTTON_B,
        KeyEvent.KEYCODE_BUTTON_B to KeyEvent.KEYCODE_BUTTON_Y,
        KeyEvent.KEYCODE_BUTTON_Y to KeyEvent.KEYCODE_BUTTON_X,
        KeyEvent.KEYCODE_BUTTON_X to KeyEvent.KEYCODE_BUTTON_A
    )

    fun mapKeyCode(keyCode: Int): Int = FACE_BUTTON_MAP[keyCode] ?: keyCode
}
