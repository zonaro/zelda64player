package br.com.redclaw.zelda64player.gamepad

import android.content.Context
import android.content.res.Resources
import android.graphics.Color
import android.view.KeyEvent
import br.com.redclaw.zelda64player.R
import com.swordfish.radialgamepad.library.config.*
import com.swordfish.radialgamepad.library.haptics.HapticConfig

/**
 * Where a single [RadialGamePadConfig] should sit within the full-screen gamepad overlay.
 *
 * [gravityX]/[gravityY] and [sizeFraction] are all fractions (0f..1f) measured directly off the
 * reference layout (ajustes-layout-controles/referencia.png, 1684x774) -- center position and
 * diameter as a fraction of the image's width/height respectively -- so the on-screen result stays
 * proportionally correct regardless of the device's actual resolution or density.
 */
data class PadPlacement(
    val config: RadialGamePadConfig,
    val gravityX: Float,
    val gravityY: Float,
    val sizeFraction: Float,
    /** Set only for C-Left/C-Right/C-Down/Z, so their presses can be tracked elsewhere
     *  (Button Stick's Auto mode, and canceling a double-tap-held Z). */
    val buttonKeyCode: Int? = null
)

class GamePadConfig(
    context: Context,
    private val resources: Resources
) {
    companion object {
        private val themeBlue = RadialGamePadTheme(
            primaryDialBackground = Color.TRANSPARENT,
            textColor = Color.WHITE,
            normalColor = 0xFF2196F3.toInt(),
            pressedColor = 0xFF1565C0.toInt()
        )

        private val themeGreen = RadialGamePadTheme(
            primaryDialBackground = Color.TRANSPARENT,
            textColor = Color.WHITE,
            normalColor = 0xFF4CAF50.toInt(),
            pressedColor = 0xFF2E7D32.toInt()
        )

        private val themeYellow = RadialGamePadTheme(
            primaryDialBackground = Color.TRANSPARENT,
            textColor = Color.DKGRAY,
            normalColor = 0xFFFFEB3B.toInt(),
            pressedColor = 0xFFF9A825.toInt()
        )

        private val themeRed = RadialGamePadTheme(
            primaryDialBackground = Color.TRANSPARENT,
            textColor = Color.WHITE,
            normalColor = 0xFFF44336.toInt(),
            pressedColor = 0xFFC62828.toInt()
        )

        val BUTTON_START = ButtonConfig(
            id = KeyEvent.KEYCODE_BUTTON_START,
            label = "S",
            theme = themeRed
        )

        val BUTTON_SELECT = ButtonConfig(
            id = KeyEvent.KEYCODE_BUTTON_SELECT,
            label = "L"
        )

        val BUTTON_L1 = ButtonConfig(
            id = KeyEvent.KEYCODE_BUTTON_L1,
            label = "C◀",
            theme = themeYellow
        )

        val BUTTON_R1 = ButtonConfig(
            id = KeyEvent.KEYCODE_BUTTON_R1,
            label = "C▶",
            theme = themeYellow
        )

        val BUTTON_L2 = ButtonConfig(
            id = KeyEvent.KEYCODE_BUTTON_L2,
            label = "Z"
        )

        val BUTTON_R2 = ButtonConfig(
            id = KeyEvent.KEYCODE_BUTTON_R2,
            label = "R"
        )

        val BUTTON_A = ButtonConfig(
            id = KeyEvent.KEYCODE_BUTTON_A,
            label = "A",
            theme = themeBlue
        )

        val BUTTON_B = ButtonConfig(
            id = KeyEvent.KEYCODE_BUTTON_B,
            label = "B",
            theme = themeGreen
        )

        val BUTTON_X = ButtonConfig(
            id = KeyEvent.KEYCODE_BUTTON_X,
            label = "C▼",
            theme = themeYellow
        )

        val BUTTON_Y = ButtonConfig(
            id = KeyEvent.KEYCODE_BUTTON_Y,
            label = "C▲",
            theme = themeYellow
        )

        /* The reference layout's own buttons read as too small as real touch targets */
        private const val SIZE_SCALE = 1.7f

        /* Placement for the standalone ButtonStick: empty area below the C-right/C-down
           diagonal, left of Z and right of A -- a bit bigger than the regular C-buttons
           since it doubles as an analog nub. */
        const val BUTTON_STICK_GRAVITY_X = 0.735f
        const val BUTTON_STICK_GRAVITY_Y = 0.900f
        const val BUTTON_STICK_SIZE_FRACTION = 0.15f

        /* FloatingJoystick capture region: the empty lower-left area of the screen (fractions of
           the overlay's width/height). Real buttons (Select, D-pad) are added on top of it later
           and naturally claim their own touches first via view z-order, so the region can be
           generous without stealing input from them. The hint circle marks the analog stick's old
           fixed spot so it still visually reads as a stick when idle. */
        const val FLOATING_JOYSTICK_REGION_RIGHT_FRACTION = 0.60f
        const val FLOATING_JOYSTICK_HINT_GRAVITY_X = 0.212f
        const val FLOATING_JOYSTICK_HINT_GRAVITY_Y = 0.787f
        const val FLOATING_JOYSTICK_HINT_SIZE_FRACTION = 0.164f * SIZE_SCALE
        const val FLOATING_JOYSTICK_MAX_REACH_FRACTION = 0.075f
    }

    private val radialGamePadTheme = RadialGamePadTheme(
        primaryDialBackground = Color.TRANSPARENT,
        textColor = Color.WHITE,
        normalColor = 0x44FFFFFF.toInt(),
        pressedColor = 0x88FFFFFF.toInt()
    )

    /**
     * Wrap a single primary dial (no secondaries) in its own config, so it can be placed
     * independently anywhere on screen via [PadPlacement.gravityX]/[PadPlacement.gravityY].
     */
    private fun single(primary: PrimaryDialConfig) = RadialGamePadConfig(
        haptic = if (resources.getBoolean(R.bool.config_gamepad_haptic)) HapticConfig.PRESS else HapticConfig.OFF,
        theme = radialGamePadTheme,
        sockets = 1,
        primaryDial = primary,
        secondaryDials = emptyList()
    )

    private fun singleButton(button: ButtonConfig) =
        single(PrimaryDialConfig.PrimaryButtons(dials = emptyList(), center = button))

    /**
     * Every on-screen control, positioned to match the button centers measured from
     * referencia.png (fractions of the full-screen overlay's width/height). Sizes are the
     * measured reference diameter scaled up by [SIZE_SCALE] -- the reference's own buttons
     * read as too small as real touch targets.
     */
    val placements: List<PadPlacement> = listOfNotNull(
        /* When the left analog stick is enabled, it's rendered as a standalone FloatingJoystick
           (see GameActivityViewModel.setupGamePads) instead of a RadialGamePad placement here. */
        if (!resources.getBoolean(R.bool.config_left_analog)) {
            PadPlacement(single(PrimaryDialConfig.Cross(CrossConfig(0))), 0.212f, 0.787f, 0.164f * SIZE_SCALE)
        } else null,
        PadPlacement(single(PrimaryDialConfig.Cross(CrossConfig(0))), 0.067f, 0.558f, 0.121f * SIZE_SCALE)
            .takeIf { resources.getBoolean(R.bool.config_left_analog) },
        PadPlacement(singleButton(BUTTON_SELECT), 0.017f, 0.408f, 0.093f * SIZE_SCALE)
            .takeIf { resources.getBoolean(R.bool.config_gamepad_select) },

        PadPlacement(singleButton(BUTTON_A), 0.865f, 0.800f, 0.097f * SIZE_SCALE)
            .takeIf { resources.getBoolean(R.bool.config_gamepad_a) },
        PadPlacement(singleButton(BUTTON_B), 0.904f, 0.717f, 0.097f * SIZE_SCALE)
            .takeIf { resources.getBoolean(R.bool.config_gamepad_b) },
        PadPlacement(singleButton(BUTTON_R2), 0.822f, 0.540f, 0.093f * SIZE_SCALE)
            .takeIf { resources.getBoolean(R.bool.config_gamepad_r2) },
        PadPlacement(singleButton(BUTTON_L2), 0.956f, 0.902f, 0.093f * SIZE_SCALE, KeyEvent.KEYCODE_BUTTON_L2)
            .takeIf { resources.getBoolean(R.bool.config_gamepad_l2) },
        PadPlacement(singleButton(BUTTON_Y), 0.956f, 0.481f, 0.052f * SIZE_SCALE)
            .takeIf { resources.getBoolean(R.bool.config_gamepad_y) },
        /* Exact midpoint of R2 (R) and B, so R -> C-right -> B forms one straight,
           evenly-spaced diagonal */
        PadPlacement(singleButton(BUTTON_R1), 0.863f, 0.6285f, 0.097f * SIZE_SCALE, KeyEvent.KEYCODE_BUTTON_R1)
            .takeIf { resources.getBoolean(R.bool.config_gamepad_r1) },
        /* Exact midpoint of L1 (C-left) and A, so C-left -> C-down -> A forms the
           other straight, evenly-spaced diagonal */
        PadPlacement(singleButton(BUTTON_X), 0.823f, 0.720f, 0.097f * SIZE_SCALE, KeyEvent.KEYCODE_BUTTON_X)
            .takeIf { resources.getBoolean(R.bool.config_gamepad_x) },
        PadPlacement(singleButton(BUTTON_L1), 0.781f, 0.640f, 0.092f * SIZE_SCALE, KeyEvent.KEYCODE_BUTTON_L1)
            .takeIf { resources.getBoolean(R.bool.config_gamepad_l1) },

        PadPlacement(singleButton(BUTTON_START), 0.475f, 0.907f, 0.092f * SIZE_SCALE)
            .takeIf { resources.getBoolean(R.bool.config_gamepad_start) },
    )
}
