package br.com.redclaw.zelda64player.input

import android.view.KeyEvent
import br.com.redclaw.zelda64player.gamepad.ButtonStickMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class N64ControllerMappingTest {
    private val standardProfile = N64ControllerMapping.Profile(
        buttonStickMode = ButtonStickMode.C_RIGHT,
        autoZEnabled = true
    )

    @Test
    fun `physical face and shoulder buttons resolve to the configured N64 controls`() {
        assertEquals(N64ControllerMapping.Control.A, control(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(N64ControllerMapping.Control.B, control(KeyEvent.KEYCODE_BUTTON_B))
        assertEquals(N64ControllerMapping.Control.C_DOWN, control(KeyEvent.KEYCODE_BUTTON_X))
        assertEquals(N64ControllerMapping.Control.C_RIGHT, control(KeyEvent.KEYCODE_BUTTON_Y))
        assertEquals(N64ControllerMapping.Control.C_UP, control(KeyEvent.KEYCODE_BUTTON_L1))
        assertEquals(N64ControllerMapping.Control.C_LEFT, control(KeyEvent.KEYCODE_BUTTON_R1))
        assertEquals(N64ControllerMapping.Control.Z, control(KeyEvent.KEYCODE_BUTTON_L2))
        assertEquals(N64ControllerMapping.Control.R, control(KeyEvent.KEYCODE_BUTTON_R2))
    }

    @Test
    fun `raw C remap remains shared with ControllerInput`() {
        assertEquals(KeyEvent.KEYCODE_BUTTON_Y, N64ControllerMapping.effectivePhysicalKeyCode(KeyEvent.KEYCODE_BUTTON_L1))
        assertEquals(KeyEvent.KEYCODE_BUTTON_R1, N64ControllerMapping.effectivePhysicalKeyCode(KeyEvent.KEYCODE_BUTTON_Y))
        assertEquals(KeyEvent.KEYCODE_BUTTON_L1, N64ControllerMapping.effectivePhysicalKeyCode(KeyEvent.KEYCODE_BUTTON_R1))
    }

    @Test
    fun `auto Z and button stick settings are reflected in physical mappings`() {
        val noAutoZ = standardProfile.copy(autoZEnabled = false)
        assertEquals(N64ControllerMapping.Control.Z, control(KeyEvent.KEYCODE_BUTTON_THUMBL))
        assertNull(control(KeyEvent.KEYCODE_BUTTON_THUMBL, noAutoZ))

        val off = standardProfile.copy(buttonStickMode = ButtonStickMode.OFF)
        assertNull(control(KeyEvent.KEYCODE_BUTTON_THUMBR, off))
        assertFalse(N64ControllerMapping.rightStickControlsN64Analog(off))

        val auto = standardProfile.copy(buttonStickMode = ButtonStickMode.AUTO)
        assertEquals(
            N64ControllerMapping.Control.C_LEFT,
            control(KeyEvent.KEYCODE_BUTTON_THUMBR, auto, N64ControllerMapping.Control.C_LEFT)
        )
        assertTrue(N64ControllerMapping.rightStickControlsN64Analog(auto))
    }

    private fun control(
        keyCode: Int,
        profile: N64ControllerMapping.Profile = standardProfile,
        autoTarget: N64ControllerMapping.Control = N64ControllerMapping.Control.C_RIGHT
    ) = N64ControllerMapping.n64ControlForPhysicalKey(keyCode, profile, autoTarget)
}
