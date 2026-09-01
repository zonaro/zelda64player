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
 * Legacy mode enum kept for migration from old prefs. New code uses a simple boolean toggle.
 * [OFF] = disabled, any other value = enabled (migrated to true).
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
 * Toggle simples ON/OFF para o modo ButtonStick.
 * Quando ON, os botões C-Left/C-Down/C-Right/A/B operam como StickButton individuais
 * (toque = botão, arraste = botão + analógico). Quando OFF, são botões puros.
 */
class ButtonStick(context: Context) : View(context) {
    private data class Theme(val normal: Int, val pressed: Int, val text: Int)

    companion object {
        private val ON_THEME = Theme(0xFFFFEB3B.toInt(), 0xFFF9A825.toInt(), Color.DKGRAY)
        private val OFF_THEME = Theme(0xFF616161.toInt(), 0xFF424242.toInt(), Color.WHITE)
    }

    var isStickEnabled: Boolean = true
        set(value) {
            field = value
            invalidate()
        }

    /** Chamado quando o usuário toca para alternar. */
    var onToggle: ((Boolean) -> Unit)? = null

    private var pressed = false

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) / 2f
        val theme = if (isStickEnabled) ON_THEME else OFF_THEME

        backgroundPaint.color = if (pressed) theme.pressed else theme.normal
        canvas.drawCircle(cx, cy, radius, backgroundPaint)

        // Label principal
        textPaint.color = theme.text
        textPaint.textSize = radius * 0.45f
        val label = if (isStickEnabled) "STICK" else "STICK"
        canvas.drawText(label, cx, cy - radius * 0.05f, textPaint)

        // Sub-label ON/OFF
        subTextPaint.color = theme.text
        subTextPaint.textSize = radius * 0.32f
        subTextPaint.alpha = 200
        val sub = if (isStickEnabled) "ON" else "OFF"
        canvas.drawText(sub, cx, cy + radius * 0.35f, subTextPaint)

        // Borda quando ON para destacar
        if (isStickEnabled) {
            val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = radius * 0.08f
                color = 0xFFFFFFFF.toInt()
                alpha = 180
            }
            canvas.drawCircle(cx, cy, radius - borderPaint.strokeWidth / 2, borderPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = true
                invalidate()
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            }
            MotionEvent.ACTION_UP -> {
                pressed = false
                // Toggle
                isStickEnabled = !isStickEnabled
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onToggle?.invoke(isStickEnabled)
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                pressed = false
                invalidate()
            }
        }
        return true
    }
}
