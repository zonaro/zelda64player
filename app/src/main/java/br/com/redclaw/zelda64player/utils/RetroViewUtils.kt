package br.com.redclaw.zelda64player.utils

import android.app.Activity
import android.content.Context
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.drive.SyncTrigger
import br.com.redclaw.zelda64player.repositories.Storage
import br.com.redclaw.zelda64player.retroview.RetroView

/**
 * Persists and restores emulator state (SRAM, save states, frame speed, audio)
 * for a single hack, keyed by [hackId].
 */
class RetroViewUtils(private val activity: Activity, private val hackId: String) {
    private val storage = Storage.getInstance(activity)
    private val sharedPreferences = activity.getPreferences(Context.MODE_PRIVATE)
    private val fastForwardSpeed = activity.resources.getInteger(R.integer.config_fast_forward_multiplier)

    fun restoreEmulatorState(retroView: RetroView) {
        retroView.view.frameSpeed = sharedPreferences.getInt(activity.getString(R.string.pref_frame_speed), 1)
        retroView.view.audioEnabled = sharedPreferences.getBoolean(activity.getString(R.string.pref_audio_enabled), true)
    }

    fun preserveEmulatorState(retroView: RetroView) {
        saveSRAM(retroView)

        with (sharedPreferences.edit()) {
            putInt(activity.getString(R.string.pref_frame_speed), retroView.view.frameSpeed)
            putBoolean(activity.getString(R.string.pref_audio_enabled), retroView.view.audioEnabled)
            apply()
        }
    }

    fun saveSRAM(retroView: RetroView) {
        storage.sram(hackId).outputStream().use {
            it.write(retroView.view.serializeSRAM())
        }
        // Schedule an incremental cloud sync of this SRAM (no-op when disabled).
        SyncTrigger.markDirtySram(activity, hackId)
    }

    fun loadState(retroView: RetroView) {
        val stateFile = storage.state(hackId)
        if (!stateFile.exists())
            return

        val stateBytes = stateFile.inputStream().use {
            it.readBytes()
        }

        if (stateBytes.isEmpty())
            return

        retroView.view.unserializeState(stateBytes)
    }

    fun saveState(retroView: RetroView) {
        storage.state(hackId).outputStream().use {
            it.write(retroView.view.serializeState())
        }
        // Schedule an incremental cloud sync of this save state (no-op when disabled).
        SyncTrigger.markDirtyState(activity, hackId)
    }

    fun fastForward(retroView: RetroView) {
        retroView.view.frameSpeed = if (retroView.view.frameSpeed == 1) fastForwardSpeed else 1
    }
}
