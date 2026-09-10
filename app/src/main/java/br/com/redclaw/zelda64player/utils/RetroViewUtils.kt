package br.com.redclaw.zelda64player.utils

import android.app.Activity
import android.content.Context
import android.widget.Toast
import android.util.AtomicFile
import br.com.redclaw.zelda64player.retroachievements.jni.RcheevosJni
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.drive.SyncTrigger
import br.com.redclaw.zelda64player.repositories.Storage
import br.com.redclaw.zelda64player.retroview.RetroView

/**
 * Persists and restores emulator state (SRAM, save states, frame speed, audio) for a single hack,
 * keyed by [hackId].
 */
class RetroViewUtils(private val activity: Activity, private val hackId: String) {
    private val storage = Storage.getInstance(activity)
    private val sharedPreferences = activity.getPreferences(Context.MODE_PRIVATE)
    private val fastForwardSpeed =
            activity.resources.getInteger(R.integer.config_fast_forward_multiplier)

    fun restoreEmulatorState(retroView: RetroView) {
        retroView.view.frameSpeed =
                sharedPreferences.getInt(activity.getString(R.string.pref_frame_speed), 1).coerceIn(1, 16)
        retroView.view.audioEnabled =
                sharedPreferences.getBoolean(activity.getString(R.string.pref_audio_enabled), true)
    }

    fun preserveEmulatorState(retroView: RetroView) {
        saveSRAM(retroView)

        with(sharedPreferences.edit()) {
            putInt(activity.getString(R.string.pref_frame_speed), retroView.view.frameSpeed)
            putBoolean(activity.getString(R.string.pref_audio_enabled), retroView.view.audioEnabled)
            apply()
        }
    }

    fun saveSRAM(retroView: RetroView) {
        storage.sram(hackId).outputStream().use { it.write(retroView.view.serializeSRAM()) }
        // Schedule an incremental cloud sync of this SRAM (no-op when disabled).
        SyncTrigger.markDirtySram(activity, hackId)
    }

    fun loadState(retroView: RetroView) {
        if (RcheevosJni.nativeIsHardcore()) {
            Toast.makeText(activity, R.string.ra_hardcore_state_blocked, Toast.LENGTH_SHORT).show()
            return
        }
        val stateBytes = runCatching {
            AtomicFile(storage.state(hackId)).openRead().use { it.readBytes() }
        }.getOrNull()
        if (stateBytes == null) {
            Toast.makeText(activity, R.string.toast_state_none, Toast.LENGTH_SHORT).show()
            return
        }

        if (stateBytes.isEmpty()) {
            Toast.makeText(activity, R.string.toast_state_none, Toast.LENGTH_SHORT).show()
            return
        }

        val loaded = runCatching { retroView.view.unserializeState(stateBytes) }.getOrDefault(false)
        Toast.makeText(activity, if (loaded) R.string.toast_state_loaded else R.string.toast_state_load_failed, Toast.LENGTH_SHORT).show()
    }

    fun saveState(retroView: RetroView) {
        val saved = runCatching {
            val bytes = retroView.view.serializeState()
            require(bytes.isNotEmpty())
            val file = AtomicFile(storage.state(hackId))
            val output = file.startWrite()
            try {
                output.write(bytes)
                file.finishWrite(output)
            } catch (error: Throwable) {
                file.failWrite(output)
                throw error
            }
        }.isSuccess
        if (!saved) {
            Toast.makeText(activity, R.string.toast_state_save_failed, Toast.LENGTH_SHORT).show()
            return
        }
        // Schedule an incremental cloud sync of this save state (no-op when disabled).
        SyncTrigger.markDirtyState(activity, hackId)
        Toast.makeText(activity, R.string.toast_state_saved, Toast.LENGTH_SHORT).show()
    }

    fun fastForward(retroView: RetroView) {
        retroView.view.frameSpeed = if (retroView.view.frameSpeed == 1) fastForwardSpeed else 1
    }
}
