package br.com.redclaw.zelda64player.views

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import coil.drawable.CrossfadeDrawable
import kotlin.math.round

/**
 * ImageView whose measured height follows the aspect ratio of the cover image
 * that is currently displayed, instead of a single hard-coded ratio.
 *
 * WHY THIS EXISTS
 *
 * The Library and Store grids show covers whose native proportions vary:
 * Libretro boxarts are roughly 512x357 (10:7 landscape), catalog covers may be
 * 16:9, and the bundled `placeholder_cover.png` is a 3:4 PORTRAIT image. A plain
 * `ImageView` with `adjustViewBounds="true"` would adopt whatever drawable is
 * visible at measure time, while a fixed 10:7 card would force `centerCrop` to
 * slice off the top/bottom of any non-10:7 artwork.
 *
 * The user requirement is explicit: each card must have the SAME proportion as
 * its cover and be fully filled by it, with NOTHING cropped. To guarantee that,
 * this view derives its ratio from the loaded drawable's intrinsic dimensions
 * and requests a re-measure whenever that drawable changes. Because the card
 * ratio then matches the artwork exactly, `scaleType="centerCrop"` fills the
 * whole card with zero cropping (it only guards sub-pixel rounding gaps).
 *
 * ADAPTIVE RATIO RESOLUTION
 *
 * The ratio is taken from the *target* (final) drawable, never from a transient
 * placeholder or crossfade wrapper:
 *
 *  - Coil's `crossfade(true)` delivers a `coil.drawable.CrossfadeDrawable`
 *    wrapping `start` (placeholder) and `end` (the loaded cover). Its intrinsic
 *    size is `max(start, end)` per dimension, which would yield a wrong,
 *    too-tall ratio during and after the transition. We therefore unwrap it and
 *    prefer its `end` drawable — the actual cover.
 *  - A `TransitionDrawable` (a `LayerDrawable` of placeholder + target) is
 *    handled the same way by preferring its LAST (top) layer, i.e. the target.
 *  - Any other drawable (plain `BitmapDrawable`, the placeholder set directly
 *    via `setImageResource`, etc.) is used as-is.
 *
 * FALLBACK
 *
 * If no usable intrinsic size can be resolved (drawable is null, a solid
 * `ColorDrawable`, or otherwise reports -1 dimensions), the view falls back to
 * the default [ratio] (10:7 landscape). This keeps a sane geometry for the
 * degenerate case and — importantly — means a not-yet-loaded tile that still
 * shows the PORTRAIT placeholder will adopt the placeholder's portrait ratio
 * (taller than the eventual cover). That transient height difference is an
 * accepted trade-off: it is better than cropping artwork, and the tile snaps to
 * the cover's true ratio as soon as the image loads.
 *
 * @param ratio Default aspect ratio as WIDTH / HEIGHT, used only when no
 *   drawable with a known intrinsic size is present. `10f / 7f` matches the
 *   common 10:7 landscape cover convention. A value of `2f` would mean "twice
 *   as wide as tall".
 */
class CoverImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    /** Default aspect ratio as width / height. Must be positive. */
    var ratio: Float = 10f / 7f
        set(value) {
            require(value > 0f) { "ratio must be positive" }
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    /**
     * Resolved aspect ratio (width / height) derived from the current drawable's
     * target intrinsic dimensions. `0f` means "not resolved yet" and signals
     * that the default [ratio] should be used instead.
     */
    private var adaptiveRatio: Float = 0f

    override fun setImageDrawable(drawable: Drawable?) {
        super.setImageDrawable(drawable)
        updateAdaptiveRatio(drawable)
    }

    private fun updateAdaptiveRatio(drawable: Drawable?) {
        val resolved = resolveRatio(drawable)
        val next = resolved ?: 0f
        if (next != adaptiveRatio) {
            adaptiveRatio = next
            requestLayout()
        }
    }

    /**
     * Unwraps crossfade/transition wrappers to the *target* drawable and returns
     * its aspect ratio (width / height), or null when no usable intrinsic size
     * exists.
     */
    private fun resolveRatio(drawable: Drawable?): Float? {
        val source = resolveSource(drawable) ?: return null
        val w = source.intrinsicWidth
        val h = source.intrinsicHeight
        if (w > 0 && h > 0) {
            return w.toFloat() / h.toFloat()
        }
        // Last resort: the wrapper's own intrinsic size (e.g. a plain drawable
        // that resolveSource could not unwrap but still reports dimensions).
        val dw = drawable?.intrinsicWidth ?: -1
        val dh = drawable?.intrinsicHeight ?: -1
        if (dw > 0 && dh > 0) {
            return dw.toFloat() / dh.toFloat()
        }
        return null
    }

    /**
     * Recursively descends through crossfade/transition wrappers to the
     * drawable that actually represents the final artwork:
     *
     *  - [CrossfadeDrawable] (Coil `crossfade(true)`): use its `end` drawable.
     *  - [LayerDrawable] (e.g. [android.graphics.drawable.TransitionDrawable]):
     *    use its last (top) layer, which is the target.
     *  - anything else: return it unchanged.
     */
    private fun resolveSource(drawable: Drawable?): Drawable? {
        if (drawable == null) return null
        if (drawable is CrossfadeDrawable) {
            return resolveSource(drawable.end) ?: drawable
        }
        if (drawable is LayerDrawable) {
            val last = drawable.getDrawable(drawable.numberOfLayers - 1)
            return resolveSource(last) ?: drawable
        }
        return drawable
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        if (width <= 0) {
            // No usable width yet (e.g. zero-width pass); fall back to default
            // measurement so we never produce a degenerate zero/negative height.
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }
        // Prefer the ratio derived from the current cover; fall back to the
        // default ratio when none has been resolved yet.
        val effectiveRatio = if (adaptiveRatio > 0f) adaptiveRatio else ratio
        // Height is derived from width and the ratio: height = width / ratio.
        val height = round(width / effectiveRatio).toInt()
        val fixedHeightSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        super.onMeasure(widthMeasureSpec, fixedHeightSpec)
    }
}
