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
 * Botão que opera como ButtonStick individual quando [stickEnabled] está ON.
 *
 * - Toque simples: pressiona/solta [targetKeyCode] (como botão normal).
 * - Toque + arraste: mantém [targetKeyCode] pressionado e move o analógico N64
 * (MOTION_SOURCE_ANALOG_LEFT) proporcional ao deslocamento, escalado por [sensitivity].
 *
 * Quando [stickEnabled] está OFF, comporta-se como botão puro (sem analógico).
 */
class StickButton(
        context: Context,
        val targetKeyCode: Int,
        private val label: String,
        private val theme: Theme,
        private val supportsAnalogDrag: Boolean = true,
        private val hapticEnabled: Boolean = true
) : View(context) {

    data class Theme(val normal: Int, val pressed: Int, val text: Int)

    companion object {
        private const val DRAG_THRESHOLD_DP = 12f

        val YELLOW_THEME = Theme(0xFFFFEB3B.toInt(), 0xFFF9A825.toInt(), Color.DKGRAY)
        val BLUE_THEME = Theme(0xFF2196F3.toInt(), 0xFF1565C0.toInt(), Color.WHITE)
        val GREEN_THEME = Theme(0xFF4CAF50.toInt(), 0xFF2E7D32.toInt(), Color.WHITE)
        val NEUTRAL_THEME = Theme(0x44FFFFFF.toInt(), 0x88FFFFFF.toInt(), Color.WHITE)
    }

    var retroView: GLRetroView? = null
    var sensitivity: Float = 0.5f
    var stickEnabled: Boolean = true

    private val dragThresholdPx = DRAG_THRESHOLD_DP * resources.displayMetrics.density

    private var downX = 0f
    private var downY = 0f
    private var dragging = false
    private var pressed = false
    private var thumbOffsetX = 0f
    private var thumbOffsetY = 0f

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f

        backgroundPaint.color = if (pressed && !dragging) theme.pressed else theme.normal
        canvas.drawCircle(cx, cy, radius, backgroundPaint)

        if (dragging && stickEnabled) {
            thumbPaint.color = theme.pressed
            canvas.drawCircle(cx + thumbOffsetX, cy + thumbOffsetY, radius * 0.4f, thumbPaint)
        } else {
            textPaint.color = theme.text
            textPaint.textSize = radius * 0.6f
            canvas.drawText(
                    label,
                    cx,
                    cy - (textPaint.ascent() + textPaint.descent()) / 2,
                    textPaint
            )
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
                if (hapticEnabled) performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                retroView?.sendKeyEvent(KeyEvent.ACTION_DOWN, InputMapper.mapKeyCode(targetKeyCode))
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (!stickEnabled || !supportsAnalogDrag) return true
                val dx = event.x - downX
                val dy = event.y - downY
                val dist = hypot(dx, dy)
                if (!dragging && dist > dragThresholdPx) dragging = true
                val clampedDist = min(dist, maxRadius)
                val nx = if (dist > 0) dx / dist else 0f
                val ny = if (dist > 0) dy / dist else 0f
                thumbOffsetX = nx * clampedDist
                thumbOffsetY = ny * clampedDist
                val magnitude = clampedDist / maxRadius * sensitivity
                retroView?.sendMotionEvent(
                        GLRetroView.MOTION_SOURCE_ANALOG_LEFT,
                        nx * magnitude,
                        -ny * magnitude
                )
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging && stickEnabled && supportsAnalogDrag) {
                    retroView?.sendMotionEvent(GLRetroView.MOTION_SOURCE_ANALOG_LEFT, 0f, 0f)
                }
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
