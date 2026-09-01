package br.com.redclaw.zelda64player.gamepad

/**
 * Normalized geometry for the six-button action cluster in the touch overlay.
 *
 * Coordinates are expressed as fractions of the full overlay. Keeping the three controls in
 * each group on a shared line makes the layout independent of screen size and overlay scaling.
 */
data class NormalizedPoint(val x: Float, val y: Float)

object TouchControlLayout {
    /** All touch controls, including the floating-stick variants, render at 75% opacity. */
    const val OVERLAY_OPACITY = 0.75f

    /** Base diameter before [GamePadConfig.SIZE_SCALE] is applied. */
    const val CLUSTER_BUTTON_SIZE_FRACTION = 0.097f

    // Diagonal 1: C-left -> C-down -> A.
    val cLeft = NormalizedPoint(x = 0.744f, y = 0.625f)
    val cDown = NormalizedPoint(x = 0.803f, y = 0.7505f)
    val a = NormalizedPoint(x = 0.862f, y = 0.876f)

    // Diagonal 2: R -> C-right -> B.
    val r = NormalizedPoint(x = 0.806f, y = 0.494f)
    val cRight = NormalizedPoint(x = 0.866f, y = 0.618f)
    val b = NormalizedPoint(x = 0.926f, y = 0.742f)
}
