package br.com.redclaw.zelda64player.gamepad

import org.junit.Assert.assertEquals
import org.junit.Test

class TouchControlLayoutTest {
    @Test
    fun `action controls form two evenly spaced diagonals`() {
        assertMidpoint(TouchControlLayout.cLeft, TouchControlLayout.cDown, TouchControlLayout.a)
        assertMidpoint(TouchControlLayout.r, TouchControlLayout.cRight, TouchControlLayout.b)
    }

    @Test
    fun `action cluster uses a common size and semi-transparent overlay`() {
        assertEquals(0.097f, TouchControlLayout.CLUSTER_BUTTON_SIZE_FRACTION)
        assertEquals(0.75f, TouchControlLayout.OVERLAY_OPACITY)
    }

    private fun assertMidpoint(
            first: NormalizedPoint,
            middle: NormalizedPoint,
            last: NormalizedPoint
    ) {
        assertEquals((first.x + last.x) / 2f, middle.x, 0.00001f)
        assertEquals((first.y + last.y) / 2f, middle.y, 0.00001f)
    }
}
