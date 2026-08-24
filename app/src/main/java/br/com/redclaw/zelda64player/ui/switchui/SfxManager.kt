/*
 * Zelda 64 Player - native Android N64 emulator frontend for Zelda ROM hacks.
 * Copyright (C) 2026 RedClaw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package br.com.redclaw.zelda64player.ui.switchui

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.utils.CorePrefs

/**
 * Low-latency [SoundPool] wrapper for the Nintendo Switch-style UI sound
 * effects: focus move, select, back, panel open and panel close.
 *
 * All five clips are synthesized originals (see `tools/gen_switch_sfx.py`), so
 * there are no licensing concerns. The pool is created once and the clips are
 * preloaded; playback respects the system media volume through
 * [AudioAttributes.USAGE_MEDIA].
 *
 * The enabled state is persisted via [CorePrefs] so the user's mute preference
 * survives restarts. Call [release] when the owning scope (the application) is
 * torn down to free the native audio resources.
 *
 * @param context any context; it is immediately reduced to the application
 *   context so the manager does not leak an Activity.
 */
class SfxManager(context: Context) {

    private val appContext = context.applicationContext
    private val soundPool: SoundPool
    private val soundIds = mutableMapOf<Int, Int>() // raw resId -> loaded soundId

    private var enabled: Boolean = CorePrefs.getSwitchSfxEnabled(appContext)

    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(MAX_STREAMS)
            .setAudioAttributes(audioAttributes)
            .build()
        preload(R.raw.sfx_focus_move)
        preload(R.raw.sfx_select)
        preload(R.raw.sfx_back)
        preload(R.raw.sfx_panel_open)
        preload(R.raw.sfx_panel_close)
    }

    private fun preload(resId: Int) {
        soundIds[resId] = soundPool.load(appContext, resId, PRIORITY)
    }

    /** Plays the focus-move "toc" tick. */
    fun focusMove() = play(R.raw.sfx_focus_move)

    /** Plays the select (blip up) sound. */
    fun select() = play(R.raw.sfx_select)

    /** Plays the back (blip down) sound. */
    fun back() = play(R.raw.sfx_back)

    /** Plays the panel-open (swoosh up) sound. */
    fun panelOpen() = play(R.raw.sfx_panel_open)

    /** Plays the panel-close (swoosh down) sound. */
    fun panelClose() = play(R.raw.sfx_panel_close)

    private fun play(resId: Int) {
        if (!enabled) return
        val soundId = soundIds[resId] ?: return
        soundPool.play(soundId, VOLUME, VOLUME, PRIORITY, NO_LOOP, RATE)
    }

    /**
     * Enables or mutes all UI sound effects, persisting the choice so it is
     * restored on the next launch.
     */
    fun setEnabled(enabled: Boolean) {
        this.enabled = enabled
        CorePrefs.setSwitchSfxEnabled(appContext, enabled)
    }

    /** Releases the underlying [SoundPool]. Call on application teardown. */
    fun release() {
        soundPool.release()
        soundIds.clear()
    }

    private companion object {
        const val MAX_STREAMS = 2
        const val VOLUME = 1.0f
        const val PRIORITY = 1
        const val NO_LOOP = 0
        const val RATE = 1.0f
    }
}
