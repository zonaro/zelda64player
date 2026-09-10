package br.com.redclaw.zelda64player.retroachievements.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * FrameLayout that always measures itself as a square (width == height).
 *
 * Used by the achievements grid cells so badge thumbnails render as perfect squares regardless of
 * the column width chosen by the GridLayoutManager. Mirrors the aspect-ratio enforcement pattern of
 * SwitchGameCard (DRY).
 */
class SquareFrameLayout @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
        FrameLayout(context, attrs) {

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        if (MeasureSpec.getMode(widthSpec) != MeasureSpec.UNSPECIFIED) {
            val size = MeasureSpec.getSize(widthSpec)
            super.onMeasure(
                    MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY)
            )
        } else {
            super.onMeasure(widthSpec, heightSpec)
        }
    }
}
