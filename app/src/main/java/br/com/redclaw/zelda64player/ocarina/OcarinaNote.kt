package br.com.redclaw.zelda64player.ocarina

import android.view.KeyEvent

/**
 * A single Ocarina note, mapped to the raw Android keycode the on-screen touch
 * pad would send for that N64 button.
 *
 * The touch pad ([br.com.redclaw.zelda64player.gamepad.GamePadConfig]) maps each
 * N64 face button to a raw keycode before it reaches
 * [br.com.redclaw.zelda64player.input.InputMapper]:
 *   N64 A       -> KEYCODE_BUTTON_A
 *   N64 C-Up    -> KEYCODE_BUTTON_Y
 *   N64 C-Down  -> KEYCODE_BUTTON_X
 *   N64 C-Left  -> KEYCODE_BUTTON_L1
 *   N64 C-Right -> KEYCODE_BUTTON_R1
 *
 * The macro player forwards these raw keycodes straight to
 * [com.swordfish.libretrodroid.GLRetroView.sendKeyEvent] (via InputMapper, exactly
 * like a human tapping the pad), bypassing
 * [br.com.redclaw.zelda64player.input.ControllerInput] so no side effects
 * (ButtonStick AUTO target tracking, key logging) occur.
 *
 * [glyph] and [chipColor] drive the on-screen HUD; the colors mirror the
 * GamePadConfig theme (blue for A, yellow for the C buttons).
 */
enum class OcarinaNote(
    /** Raw Android keycode the touch pad uses for this note (pre-InputMapper). */
    val touchKeyCode: Int,
    /** Glyph rendered on the HUD chip. */
    val glyph: String,
    /** HUD chip fill color (mirrors GamePadConfig theme colors). */
    val chipColor: Int
) {
    A(KeyEvent.KEYCODE_BUTTON_A, "A", 0xFF2196F3.toInt()),
    C_UP(KeyEvent.KEYCODE_BUTTON_Y, "▲", 0xFFFFEB3B.toInt()),
    C_DOWN(KeyEvent.KEYCODE_BUTTON_X, "▼", 0xFFFFEB3B.toInt()),
    C_LEFT(KeyEvent.KEYCODE_BUTTON_L1, "◀", 0xFFFFEB3B.toInt()),
    C_RIGHT(KeyEvent.KEYCODE_BUTTON_R1, "▶", 0xFFFFEB3B.toInt());

    companion object {
        /** Parse a catalog note code ("A", "C_UP", ...) into an [OcarinaNote]. */
        fun fromCode(code: String): OcarinaNote? = when (code) {
            "A" -> A
            "C_UP" -> C_UP
            "C_DOWN" -> C_DOWN
            "C_LEFT" -> C_LEFT
            "C_RIGHT" -> C_RIGHT
            else -> null
        }
    }
}
