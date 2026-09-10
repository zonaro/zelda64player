/*
 * Zelda 64 Player - native Android N64 emulator frontend for Zelda ROM hacks.
 * Copyright (C) 2026 RedClaw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package br.com.redclaw.zelda64player.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.input.N64ControllerMapping
import br.com.redclaw.zelda64player.ui.switchui.AccentManager
import kotlin.math.abs
import kotlin.math.min

/**
 * Live, non-interactive controller visualizer used by [GamepadTesterActivity].
 * It deliberately observes Android events only: no event is forwarded to a
 * core, and it never changes the frozen touch-control layout.
 */
class GamepadTesterView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    enum class Mode { PHYSICAL, N64 }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val pressedKeys = mutableSetOf<Int>()
    private val availableKeys = mutableSetOf<Int>()
    private var hasConnectedDevice = false

    private val panelColor = ContextCompat.getColor(context, R.color.switch_panel)
    private val primaryText = ContextCompat.getColor(context, R.color.switch_text_primary)
    private val secondaryText = ContextCompat.getColor(context, R.color.switch_text_secondary)
    private val focusColor = ContextCompat.getColor(context, R.color.switch_accent_focus)
    private val amberColor = ContextCompat.getColor(context, R.color.switch_accent_amber)
    private val analogColor = AccentManager.getAccentColor(context)

    private var mode = Mode.PHYSICAL
    private var profile = N64ControllerMapping.currentProfile(context)
    private var autoTarget = N64ControllerMapping.Control.C_RIGHT
    private var leftX = 0f
    private var leftY = 0f
    private var rightX = 0f
    private var rightY = 0f
    private var hatX = 0f
    private var hatY = 0f

    private val knownKeys = intArrayOf(
        KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B,
        KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y,
        KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
        KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2,
        KeyEvent.KEYCODE_BUTTON_THUMBL, KeyEvent.KEYCODE_BUTTON_THUMBR,
        KeyEvent.KEYCODE_BUTTON_SELECT, KeyEvent.KEYCODE_BUTTON_START,
        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT
    )

    fun setMode(newMode: Mode) {
        mode = newMode
        invalidate()
    }

    fun refreshProfile() {
        profile = N64ControllerMapping.currentProfile(context)
        invalidate()
    }

    fun setInputDevice(device: InputDevice?) {
        availableKeys.clear()
        hasConnectedDevice = device != null
        if (device != null) {
            device.hasKeys(*knownKeys).forEachIndexed { index, hasKey ->
                if (hasKey) availableKeys += knownKeys[index]
            }
        }
        pressedKeys.clear()
        resetAxes()
        invalidate()
    }

    fun handleKeyEvent(event: KeyEvent) {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                pressedKeys += event.keyCode
                N64ControllerMapping.n64ControlForPhysicalKey(
                    event.keyCode,
                    profile,
                    autoTarget
                )?.let { control ->
                    if (control in AUTO_FOLLOWED_CONTROLS) autoTarget = control
                }
            }
            KeyEvent.ACTION_UP -> pressedKeys -= event.keyCode
        }
        invalidate()
    }

    fun handleMotionEvent(event: MotionEvent) {
        leftX = event.getAxisValue(MotionEvent.AXIS_X)
        leftY = event.getAxisValue(MotionEvent.AXIS_Y)
        rightX = event.getAxisValue(MotionEvent.AXIS_Z)
        rightY = event.getAxisValue(MotionEvent.AXIS_RZ)
        hatX = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        hatY = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(panelColor)
        val inset = width * 0.035f
        if (mode == Mode.PHYSICAL) drawPhysicalLayout(canvas, inset) else drawN64Layout(canvas, inset)
    }

    private fun drawPhysicalLayout(canvas: Canvas, inset: Float) {
        val w = width - inset * 2f
        val h = height - inset * 2f
        val x = inset
        val y = inset

        // Ombros maximizados — pills grandes, sem encostar no SELECT
        drawControl(canvas, RectF(x + w * .02f, y + h * .01f, x + w * .20f, y + h * .24f), label(R.string.tester_lb), KeyEvent.KEYCODE_BUTTON_L1)
        drawControl(canvas, RectF(x + w * .22f, y + h * .01f, x + w * .40f, y + h * .24f), label(R.string.tester_lt), KeyEvent.KEYCODE_BUTTON_L2)
        drawControl(canvas, RectF(x + w * .60f, y + h * .01f, x + w * .78f, y + h * .24f), label(R.string.tester_rt), KeyEvent.KEYCODE_BUTTON_R2)
        drawControl(canvas, RectF(x + w * .80f, y + h * .01f, x + w * .98f, y + h * .24f), label(R.string.tester_rb), KeyEvent.KEYCODE_BUTTON_R1)

        // SELECT/START logo abaixo dos ombros, com folga
        drawControl(canvas, RectF(x + w * .43f, y + h * .28f, x + w * .52f, y + h * .42f), label(R.string.tester_select), KeyEvent.KEYCODE_BUTTON_SELECT)
        drawControl(canvas, RectF(x + w * .56f, y + h * .28f, x + w * .65f, y + h * .42f), label(R.string.tester_start), KeyEvent.KEYCODE_BUTTON_START)

        // D-pad e analógicos maximizados — sem sobrepor SELECT nem entre si
        drawDpad(canvas, x + w * .18f, y + h * .60f, min(w, h) * .145f, physical = true)
        drawStick(canvas, x + w * .39f, y + h * .74f, min(w, h) * .18f, leftX, leftY, label(R.string.tester_l3), KeyEvent.KEYCODE_BUTTON_THUMBL)
        drawStick(canvas, x + w * .63f, y + h * .74f, min(w, h) * .18f, rightX, rightY, label(R.string.tester_r3), KeyEvent.KEYCODE_BUTTON_THUMBR)

        val faceRadius = min(w, h) * .105f
        drawControl(canvas, circle(x + w * .86f, y + h * .42f, faceRadius), label(R.string.tester_y), KeyEvent.KEYCODE_BUTTON_Y)
        drawControl(canvas, circle(x + w * .80f, y + h * .58f, faceRadius), label(R.string.tester_x), KeyEvent.KEYCODE_BUTTON_X)
        drawControl(canvas, circle(x + w * .92f, y + h * .58f, faceRadius), label(R.string.tester_b), KeyEvent.KEYCODE_BUTTON_B)
        drawControl(canvas, circle(x + w * .86f, y + h * .74f, faceRadius), label(R.string.tester_a), KeyEvent.KEYCODE_BUTTON_A)
    }

    private fun drawN64Layout(canvas: Canvas, inset: Float) {
        val w = width - inset * 2f
        val h = height - inset * 2f
        val x = inset
        val y = inset
        val n64 = ::isN64Pressed

        drawN64Control(canvas, RectF(x + w * .02f, y + h * .01f, x + w * .20f, y + h * .24f), label(R.string.tester_n64_l), N64ControllerMapping.Control.L, n64)
        drawN64Control(canvas, RectF(x + w * .80f, y + h * .01f, x + w * .98f, y + h * .24f), label(R.string.tester_n64_r), N64ControllerMapping.Control.R, n64)
        drawN64Control(canvas, RectF(x + w * .05f, y + h * .28f, x + w * .18f, y + h * .46f), label(R.string.tester_n64_z), N64ControllerMapping.Control.Z, n64)

        drawDpad(canvas, x + w * .28f, y + h * .62f, min(w, h) * .135f, physical = false)
        drawStick(canvas, x + w * .48f, y + h * .68f, min(w, h) * .19f, leftX, leftY, label(R.string.tester_n64_stick), null)
        drawN64Control(canvas, RectF(x + w * .46f, y + h * .88f, x + w * .56f, y + h * .99f), label(R.string.tester_n64_start), N64ControllerMapping.Control.START, n64)

        val cRadius = min(w, h) * .088f
        drawN64Control(canvas, circle(x + w * .70f, y + h * .40f, cRadius), label(R.string.tester_n64_c_up), N64ControllerMapping.Control.C_UP, n64)
        drawN64Control(canvas, circle(x + w * .64f, y + h * .54f, cRadius), label(R.string.tester_n64_c_left), N64ControllerMapping.Control.C_LEFT, n64)
        drawN64Control(canvas, circle(x + w * .76f, y + h * .54f, cRadius), label(R.string.tester_n64_c_right), N64ControllerMapping.Control.C_RIGHT, n64)
        drawN64Control(canvas, circle(x + w * .70f, y + h * .68f, cRadius), label(R.string.tester_n64_c_down), N64ControllerMapping.Control.C_DOWN, n64)
        drawN64Control(canvas, circle(x + w * .88f, y + h * .52f, cRadius * 1.25f), label(R.string.tester_n64_b), N64ControllerMapping.Control.B, n64)
        drawN64Control(canvas, circle(x + w * .93f, y + h * .72f, cRadius * 1.25f), label(R.string.tester_n64_a), N64ControllerMapping.Control.A, n64)
    }

    private fun drawDpad(canvas: Canvas, cx: Float, cy: Float, radius: Float, physical: Boolean) {
        val positions = arrayOf(
            RectF(cx - radius * .45f, cy - radius * 1.5f, cx + radius * .45f, cy - radius * .55f),
            RectF(cx - radius * .45f, cy + radius * .55f, cx + radius * .45f, cy + radius * 1.5f),
            RectF(cx - radius * 1.5f, cy - radius * .45f, cx - radius * .55f, cy + radius * .45f),
            RectF(cx + radius * .55f, cy - radius * .45f, cx + radius * 1.5f, cy + radius * .45f)
        )
        if (physical) {
            drawControl(canvas, positions[0], label(R.string.tester_dpad_up), KeyEvent.KEYCODE_DPAD_UP, hatY < -AXIS_THRESHOLD)
            drawControl(canvas, positions[1], label(R.string.tester_dpad_down), KeyEvent.KEYCODE_DPAD_DOWN, hatY > AXIS_THRESHOLD)
            drawControl(canvas, positions[2], label(R.string.tester_dpad_left), KeyEvent.KEYCODE_DPAD_LEFT, hatX < -AXIS_THRESHOLD)
            drawControl(canvas, positions[3], label(R.string.tester_dpad_right), KeyEvent.KEYCODE_DPAD_RIGHT, hatX > AXIS_THRESHOLD)
        } else {
            drawN64Control(canvas, positions[0], label(R.string.tester_dpad_up), N64ControllerMapping.Control.DPAD_UP, ::isN64Pressed, hatY < -AXIS_THRESHOLD)
            drawN64Control(canvas, positions[1], label(R.string.tester_dpad_down), N64ControllerMapping.Control.DPAD_DOWN, ::isN64Pressed, hatY > AXIS_THRESHOLD)
            drawN64Control(canvas, positions[2], label(R.string.tester_dpad_left), N64ControllerMapping.Control.DPAD_LEFT, ::isN64Pressed, hatX < -AXIS_THRESHOLD)
            drawN64Control(canvas, positions[3], label(R.string.tester_dpad_right), N64ControllerMapping.Control.DPAD_RIGHT, ::isN64Pressed, hatX > AXIS_THRESHOLD)
        }
    }

    private fun drawStick(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        axisX: Float,
        axisY: Float,
        stickLabel: String,
        keyCode: Int?
    ) {
        backgroundPaint.color = panelColor
        outlinePaint.color = if (keyCode != null && isKeyPressed(keyCode)) focusColor else secondaryText
        outlinePaint.strokeWidth = radius * .07f
        canvas.drawCircle(cx, cy, radius, backgroundPaint)
        canvas.drawCircle(cx, cy, radius, outlinePaint)
        backgroundPaint.color = analogColor
        canvas.drawCircle(cx + axisX.coerceIn(-1f, 1f) * radius * .55f, cy + axisY.coerceIn(-1f, 1f) * radius * .55f, radius * .31f, backgroundPaint)
        drawText(canvas, stickLabel, cx, cy + radius * 1.36f, radius * .30f, primaryText)
    }

    private fun drawControl(
        canvas: Canvas,
        bounds: RectF,
        controlLabel: String,
        keyCode: Int,
        extraPressed: Boolean = false
    ) {
        val available = hasConnectedDevice && keyCode in availableKeys
        drawButton(canvas, bounds, controlLabel, isKeyPressed(keyCode) || extraPressed, available, amber = false)
    }

    private fun drawN64Control(
        canvas: Canvas,
        bounds: RectF,
        controlLabel: String,
        control: N64ControllerMapping.Control,
        pressedResolver: (N64ControllerMapping.Control) -> Boolean,
        extraPressed: Boolean = false
    ) = drawButton(canvas, bounds, controlLabel, pressedResolver(control) || extraPressed, true, control in C_CONTROLS)

    private fun drawButton(
        canvas: Canvas,
        bounds: RectF,
        controlLabel: String,
        pressed: Boolean,
        available: Boolean,
        amber: Boolean
    ) {
        val labelSize = min(bounds.width(), bounds.height()) * .30f
        backgroundPaint.color = when {
            pressed -> focusColor
            available -> panelColor
            else -> secondaryText
        }
        outlinePaint.color = if (amber) amberColor else focusColor
        outlinePaint.strokeWidth = min(bounds.width(), bounds.height()) * .055f
        val isCircle = kotlin.math.abs(bounds.width() - bounds.height()) < 1f
        if (isCircle) {
            val radius = bounds.width() / 2f
            canvas.drawCircle(bounds.centerX(), bounds.centerY(), radius, backgroundPaint)
            canvas.drawCircle(bounds.centerX(), bounds.centerY(), radius - outlinePaint.strokeWidth / 2f, outlinePaint)
        } else {
            val radius = bounds.height() / 2f
            canvas.drawRoundRect(bounds, radius, radius, backgroundPaint)
            canvas.drawRoundRect(bounds, radius, radius, outlinePaint)
        }
        textPaint.textSize = labelSize
        drawText(
            canvas,
            controlLabel,
            bounds.centerX(),
            bounds.centerY() - (textPaint.descent() + textPaint.ascent()) / 2f,
            labelSize,
            if (available || pressed) primaryText else panelColor
        )
    }

    private fun drawN64Control(
        canvas: Canvas,
        bounds: RectF,
        controlLabel: String,
        control: N64ControllerMapping.Control,
        pressedResolver: (N64ControllerMapping.Control) -> Boolean
    ) = drawN64Control(canvas, bounds, controlLabel, control, pressedResolver, false)

    private fun isKeyPressed(keyCode: Int): Boolean = keyCode in pressedKeys

    private fun isN64Pressed(control: N64ControllerMapping.Control): Boolean {
        if (pressedKeys.any { N64ControllerMapping.n64ControlForPhysicalKey(it, profile, autoTarget) == control }) return true
        return when (control) {
            N64ControllerMapping.Control.DPAD_UP -> hatY < -AXIS_THRESHOLD
            N64ControllerMapping.Control.DPAD_DOWN -> hatY > AXIS_THRESHOLD
            N64ControllerMapping.Control.DPAD_LEFT -> hatX < -AXIS_THRESHOLD
            N64ControllerMapping.Control.DPAD_RIGHT -> hatX > AXIS_THRESHOLD
            N64ControllerMapping.Control.C_UP -> !N64ControllerMapping.rightStickControlsN64Analog(profile) && rightY < -AXIS_THRESHOLD
            N64ControllerMapping.Control.C_DOWN -> !N64ControllerMapping.rightStickControlsN64Analog(profile) && rightY > AXIS_THRESHOLD
            N64ControllerMapping.Control.C_LEFT -> !N64ControllerMapping.rightStickControlsN64Analog(profile) && rightX < -AXIS_THRESHOLD
            N64ControllerMapping.Control.C_RIGHT -> !N64ControllerMapping.rightStickControlsN64Analog(profile) && rightX > AXIS_THRESHOLD
            N64ControllerMapping.Control.ANALOG_STICK -> abs(leftX) > AXIS_THRESHOLD || abs(leftY) > AXIS_THRESHOLD ||
                (N64ControllerMapping.rightStickControlsN64Analog(profile) && (abs(rightX) > AXIS_THRESHOLD || abs(rightY) > AXIS_THRESHOLD))
            else -> false
        }
    }

    private fun resetAxes() {
        leftX = 0f
        leftY = 0f
        rightX = 0f
        rightY = 0f
        hatX = 0f
        hatY = 0f
    }

    private fun label(resId: Int): String = resources.getString(resId)

    private fun circle(cx: Float, cy: Float, radius: Float) = RectF(cx - radius, cy - radius, cx + radius, cy + radius)

    private fun drawText(canvas: Canvas, text: String, x: Float, y: Float, size: Float, color: Int) {
        textPaint.textSize = size
        textPaint.color = color
        canvas.drawText(text, x, y, textPaint)
    }

    private companion object {
        const val AXIS_THRESHOLD = .45f
        val C_CONTROLS = setOf(
            N64ControllerMapping.Control.C_UP,
            N64ControllerMapping.Control.C_DOWN,
            N64ControllerMapping.Control.C_LEFT,
            N64ControllerMapping.Control.C_RIGHT
        )
        val AUTO_FOLLOWED_CONTROLS = setOf(
            N64ControllerMapping.Control.C_RIGHT,
            N64ControllerMapping.Control.C_LEFT,
            N64ControllerMapping.Control.C_DOWN
        )
    }
}
