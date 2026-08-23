package br.com.redclaw.zelda64player.views

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.round

/**
 * ImageView that enforces a fixed aspect ratio (width to height) in measurement,
 * independent of the loaded drawable's or placeholder's intrinsic size.
 *
 * WHY THIS EXISTS
 *
 * The Library and Store grids display 10:7 LANDSCAPE covers. The placeholder
 * drawable (`placeholder_cover.png`) is a 3:4 PORTRAIT image. A plain
 * `ImageView` using `adjustViewBounds="true"` adopts the placeholder's portrait
 * ratio while the placeholder is visible, and the view bounds stay too tall
 * even after the landscape cover arrives. The result is a band of
 * `surfaceContainer` color above the artwork (the reported bug).
 *
 * By overriding [onMeasure] to derive the height purely from the measured width
 * and the project's cover ratio, the card height is determined solely by the
 * column width and the 10:7 convention. The placeholder can no longer drive the
 * geometry, so no empty space appears behind the art.
 *
 * @param ratio Aspect ratio expressed as WIDTH divided by HEIGHT. The default
 *   `10f / 7f` matches the native 10:7 landscape cover convention. A value of
 *   `2f` would mean "twice as wide as tall".
 */
class CoverImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    /** Aspect ratio as width / height. Must be positive. */
    var ratio: Float = 10f / 7f
        set(value) {
            require(value > 0f) { "ratio must be positive" }
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        if (width <= 0) {
            // No usable width yet (e.g. zero-width pass); fall back to default
            // measurement so we never produce a degenerate zero/negative height.
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        // Height is derived from width and the fixed ratio: height = width / ratio.
        val height = round(width / ratio).toInt()
        val fixedHeightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        super.onMeasure(widthMeasureSpec, fixedHeightSpec)
    }
}
