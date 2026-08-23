package br.com.redclaw.zelda64player.ocarina

import android.view.KeyEvent
import br.com.redclaw.zelda64player.input.InputMapper
import com.swordfish.libretrodroid.GLRetroView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Coroutine-driven sequencer that "plays" an [OcarinaSong] by sending the
 * equivalent button presses directly to the mupen64plus_next core.
 *
 * Timing is fixed and reliable (see constants): each note is held for
 * [PRESS_MS], released, then a [GAP_MS] gap follows, for a steady [NOTE_MS]
 * cadence. An initial [INITIAL_DELAY_MS] lets the game regain focus after the
 * menu that launched the song is dismissed.
 *
 * Input is sent through [GLRetroView.sendKeyEvent] with
 * [InputMapper.mapKeyCode] (the same transform the on-screen touch pad applies),
 * bypassing [br.com.redclaw.zelda64player.input.ControllerInput] so no side
 * effects (ButtonStick AUTO tracking, key logging) occur.
 */
class OcarinaMacroPlayer(
    private val retroView: GLRetroView,
    private val scope: CoroutineScope
) {
    companion object {
        /** Delay after the launching menu is dismissed, before the first note. */
        const val INITIAL_DELAY_MS = 600L
        /** How long a note button is held down. */
        const val PRESS_MS = 120L
        /** Gap between releasing one note and pressing the next. */
        const val GAP_MS = 210L
        /** Total per-note cadence (PRESS_MS + GAP_MS). */
        const val NOTE_MS = PRESS_MS + GAP_MS
    }

    private var job: Job? = null
    private var heldKeyCode: Int? = null
    private var callbacks: OcarinaMacroCallbacks? = null

    /** True while a song is actively being played. */
    val isRunning: Boolean get() = job?.isActive == true

    /**
     * Play [song]. Any previously running playback is cancelled first. The
     * [callbacks] receive progress updates for the HUD.
     */
    fun play(song: OcarinaSong, callbacks: OcarinaMacroCallbacks) {
        cancel()
        this.callbacks = callbacks
        job = scope.launch {
            try {
                delay(INITIAL_DELAY_MS)
                callbacks.onStarted(song)
                song.notes.forEachIndexed { index, note ->
                    val mapped = InputMapper.mapKeyCode(note.touchKeyCode)
                    heldKeyCode = mapped
                    callbacks.onNoteStart(index)
                    retroView.sendKeyEvent(KeyEvent.ACTION_DOWN, mapped)
                    delay(PRESS_MS)
                    retroView.sendKeyEvent(KeyEvent.ACTION_UP, mapped)
                    heldKeyCode = null
                    callbacks.onNoteComplete(index)
                    delay(GAP_MS)
                }
                callbacks.onFinished()
            } finally {
                release()
            }
        }
    }

    /** Cancel playback immediately, releasing any held button. */
    fun cancel() {
        job?.cancel()
        job = null
        callbacks = null
        release()
    }

    /** Release any button currently held down (idempotent). */
    private fun release() {
        heldKeyCode?.let { retroView.sendKeyEvent(KeyEvent.ACTION_UP, it) }
        heldKeyCode = null
    }
}

/** Progress callbacks for [OcarinaMacroPlayer], used to drive the HUD. */
interface OcarinaMacroCallbacks {
    /** Called once after [OcarinaMacroPlayer.INITIAL_DELAY_MS], just before the first note. */
    fun onStarted(song: OcarinaSong)

    /** Called when the note at [index] is pressed (ACTION_DOWN sent to the core). */
    fun onNoteStart(index: Int)

    /** Called when the note at [index] is released (ACTION_UP sent to the core). */
    fun onNoteComplete(index: Int)

    /** Called after the final note completes (playback finished naturally, not cancelled). */
    fun onFinished()
}
