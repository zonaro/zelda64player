package br.com.redclaw.zelda64player.gamepad

import android.view.KeyEvent

/**
 * Legacy mode enum kept for migration from old prefs. New code uses a simple boolean toggle. [OFF]
 * = disabled, any other value = enabled (migrated to true).
 */
enum class ButtonStickMode(val keyCode: Int?) {
    OFF(null),
    C_RIGHT(KeyEvent.KEYCODE_BUTTON_R1),
    C_LEFT(KeyEvent.KEYCODE_BUTTON_L1),
    C_DOWN(KeyEvent.KEYCODE_BUTTON_X),
    A(KeyEvent.KEYCODE_BUTTON_A),
    B(KeyEvent.KEYCODE_BUTTON_B),
    AUTO(null),
}
