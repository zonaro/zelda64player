package br.com.redclaw.zelda64player.gamepad

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.FrameLayout

/**
 * Wraps a single child (the analog stick) to detect double-taps without interfering with the
 * child's own touch handling. onInterceptTouchEvent only observes the stream and always returns
 * false, so dragging/analog motion on the wrapped view keeps working exactly as if this wrapper
 * weren't there.
 */
class DoubleTapContainer(context: Context, private val onDoubleTap: () -> Unit) : FrameLayout(context) {
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            onDoubleTap()
            return true
        }
    })

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        gestureDetector.onTouchEvent(ev)
        return false
    }
}
