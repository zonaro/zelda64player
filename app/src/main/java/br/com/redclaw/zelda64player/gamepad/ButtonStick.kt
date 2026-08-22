package br.com.redclaw.zelda64player.gamepad

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import br.com.redclaw.zelda64player.input.InputMapper
import com.swordfish.libretrodroid.GLRetroView
import kotlin.math.hypot
import kotlin.math.min

/**
 * What ButtonStick acts as. [OFF] hides it entirely; [AUTO] follows whichever of C-Right/
 * C-Left/C-Down was pressed most recently elsewhere on screen.
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

/**
 * [targetKeyCode] is held for the entire touch, from finger down to finger up. Dragging
 * additionally drives MOTION_SOURCE_ANALOG_LEFT -- the same channel the main stick on the left
 * side of the screen drives, scaled by [sensitivity] -- proportionally to the offset from the
 * initial touch point, recentering on release.
 */
class ButtonStick(context: Context) : View(context) {
    private data class Theme(val normal: Int, val pressed: Int, val text: Int)

    companion object {
        private const val DRAG_THRESHOLD_DP = 12f

        private val YELLOW_THEME = Theme(0xFFFFEB3B.toInt(), 0xFFF9A825.toInt(), Color.DKGRAY)
        private val BLUE_THEME = Theme(0xFF2196F3.toInt(), 0xFF1565C0.toInt(), Color.WHITE)
        private val GREEN_THEME = Theme(0xFF4CAF50.toInt(), 0xFF2E7D32.toInt(), Color.WHITE)

        private val LABELS = mapOf(
            KeyEvent.KEYCODE_BUTTON_R1 to "C▶",
            KeyEvent.KEYCODE_BUTTON_L1 to "C◀",
            KeyEvent.KEYCODE_BUTTON_X to "C▼",
            KeyEvent.KEYCODE_BUTTON_A to "A",
            KeyEvent.KEYCODE_BUTTON_B to "B",
        )

        /** Matches each target button's own color on screen (A blue, B green, C-buttons yellow). */
        private val THEMES = mapOf(
            KeyEvent.KEYCODE_BUTTON_A to BLUE_THEME,
            KeyEvent.KEYCODE_BUTTON_B to GREEN_THEME,
        )
    }

    var retroView: GLRetroView? = null

    /** 0f..1f multiplier applied to the drag's analog magnitude; live-adjustable from Settings. */
    var sensitivity: Float = 0.5f

    /** Which button this acts as; update live (e.g. from the Button Stick menu, or Auto mode). */
    var targetKeyCode: Int = KeyEvent.KEYCODE_BUTTON_R1
        set(value) {
            field = value
            invalidate()
        }

    private val dragThresholdPx = DRAG_THRESHOLD_DP * resources.displayMetrics.density

    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var pressed = false
    private var thumbOffsetX = 0f
    private var thumbOffsetY = 0f

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f
        val theme = THEMES[targetKeyCode] ?: YELLOW_THEME

        backgroundPaint.color = if (pressed && !dragging) theme.pressed else theme.normal
        canvas.drawCircle(cx, cy, radius, backgroundPaint)

        if (dragging) {
            thumbPaint.color = theme.pressed
            canvas.drawCircle(cx + thumbOffsetX, cy + thumbOffsetY, radius * 0.4f, thumbPaint)
        } else {
            textPaint.color = theme.text
            textPaint.textSize = radius * 0.6f
            canvas.drawText(LABELS[targetKeyCode] ?: "C▶", cx, cy - (textPaint.ascent() + textPaint.descent()) / 2, textPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val maxRadius = min(width, height) / 2f

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                dragging = false
                pressed = true
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                retroView?.sendKeyEvent(KeyEvent.ACTION_DOWN, InputMapper.mapKeyCode(targetKeyCode))
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                val dist = hypot(dx, dy)

                /* Purely cosmetic -- the target button stays held regardless of this */
                if (!dragging && dist > dragThresholdPx)
                    dragging = true

                val clampedDist = min(dist, maxRadius)
                val nx = if (dist > 0) dx / dist else 0f
                val ny = if (dist > 0) dy / dist else 0f
                thumbOffsetX = nx * clampedDist
                thumbOffsetY = ny * clampedDist

                val magnitude = clampedDist / maxRadius * sensitivity
                retroView?.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, nx * magnitude, -ny * magnitude)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                retroView?.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, 0f, 0f)
                retroView?.sendKeyEvent(KeyEvent.ACTION_UP, InputMapper.mapKeyCode(targetKeyCode))
                dragging = false
                pressed = false
                thumbOffsetX = 0f
                thumbOffsetY = 0f
                invalidate()
            }
        }
        return true
    }
}
