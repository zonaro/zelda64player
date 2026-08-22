package br.com.redclaw.zelda64player.gamepad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import com.swordfish.libretrodroid.GLRetroView
import kotlin.math.hypot
import kotlin.math.min

/**
 * A "floating"/relative analog stick spanning the whole empty left side of the screen: touching
 * down anywhere within its bounds starts a virtual stick centered on that exact point, instead of
 * requiring the touch to land on a fixed graphic -- matching how most mobile games implement
 * movement sticks. Real buttons layered on top (L, D-pad) claim their own touches first via
 * normal view z-order, so this view only ever sees touches that land on genuinely empty space.
 * A static hint circle at [hintX]/[hintY] marks the stick's usual resting spot when idle, purely
 * cosmetic.
 */
class FloatingJoystick(context: Context) : View(context) {
    companion object {
        private const val BASE_COLOR = 0x33FFFFFF
        private const val KNOB_COLOR = 0x88FFFFFF.toInt()
        private const val HINT_COLOR = 0x44FFFFFF
    }

    var retroView: GLRetroView? = null

    /** 0f..1f multiplier applied to the drag's analog magnitude; live-adjustable from Settings. */
    var sensitivity: Float = 1f

    var hintX: Float = 0f
    var hintY: Float = 0f
    var hintRadius: Float = 0f

    /** How far (px) the finger can drag from the touch-down point before magnitude maxes out. */
    var maxReachPx: Float = 0f

    private var active = false
    private var centerX = 0f
    private var centerY = 0f
    private var knobOffsetX = 0f
    private var knobOffsetY = 0f

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = BASE_COLOR }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = KNOB_COLOR }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = HINT_COLOR }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (active) {
            canvas.drawCircle(centerX, centerY, maxReachPx, basePaint)
            canvas.drawCircle(centerX + knobOffsetX, centerY + knobOffsetY, maxReachPx * 0.4f, knobPaint)
        } else {
            canvas.drawCircle(hintX, hintY, hintRadius, hintPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                active = true
                centerX = event.x
                centerY = event.y
                knobOffsetX = 0f
                knobOffsetY = 0f
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - centerX
                val dy = event.y - centerY
                val dist = hypot(dx, dy)
                val clampedDist = min(dist, maxReachPx)
                val nx = if (dist > 0) dx / dist else 0f
                val ny = if (dist > 0) dy / dist else 0f
                knobOffsetX = nx * clampedDist
                knobOffsetY = ny * clampedDist

                val magnitude = if (maxReachPx > 0) clampedDist / maxReachPx * sensitivity else 0f
                retroView?.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, nx * magnitude, ny * magnitude)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                active = false
                knobOffsetX = 0f
                knobOffsetY = 0f
                retroView?.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, 0f, 0f)
                invalidate()
            }
        }
        return true
    }
}
