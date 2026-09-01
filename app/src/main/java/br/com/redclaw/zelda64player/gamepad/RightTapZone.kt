package br.com.redclaw.zelda64player.gamepad

import android.content.Context
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import br.com.redclaw.zelda64player.input.InputMapper
import com.swordfish.libretrodroid.GLRetroView

/**
 * Área invisível no lado direito da tela: tocar equivale a pressionar [targetKeyCode]. Quando
 * [targetKeyCode] é null (Off), a view é transparente e não consome toque.
 */
class RightTapZone(context: Context) : View(context) {
    var retroView: GLRetroView? = null
    var targetKeyCode: Int? = null

    private var isPressed = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val keyCode = targetKeyCode ?: return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isPressed = true
                retroView?.sendKeyEvent(KeyEvent.ACTION_DOWN, InputMapper.mapKeyCode(keyCode))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isPressed) {
                    retroView?.sendKeyEvent(KeyEvent.ACTION_UP, InputMapper.mapKeyCode(keyCode))
                    isPressed = false
                }
            }
        }
        return targetKeyCode != null
    }
}
