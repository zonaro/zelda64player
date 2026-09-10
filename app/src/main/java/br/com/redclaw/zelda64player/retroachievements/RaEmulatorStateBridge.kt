package br.com.redclaw.zelda64player.retroachievements

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.retroachievements.data.RaSaveState
import br.com.redclaw.zelda64player.retroachievements.data.RaSaveStateCodec
import br.com.redclaw.zelda64player.retroachievements.jni.RcheevosJni
import com.swordfish.libretrodroid.LibretroDroid
import org.json.JSONObject

/** Core-locked state and policy hooks shared by menus, hotkeys and direct native callers. */
internal class RaEmulatorStateBridge(context: Context) : LibretroDroid.StateCallback {
    private val context = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    override fun onSave(coreState: ByteArray): ByteArray = RaSaveStateCodec.encode(
        RaSaveState(coreState, RcheevosJni.nativeSerializeProgress(), currentHash())
    )

    override fun onDecode(savedState: ByteArray): ByteArray? {
        if (RcheevosJni.nativeIsHardcore()) {
            message(R.string.ra_hardcore_state_blocked)
            return null
        }
        return runCatching {
            val state = RaSaveStateCodec.decode(savedState)
            val hash = currentHash()
            require(state.hash.isBlank() || hash.isBlank() || state.hash == hash)
            state.core
        }.getOrNull()
    }

    override fun onLoaded(savedState: ByteArray) {
        val state = runCatching { RaSaveStateCodec.decode(savedState) }.getOrNull()
        val hash = currentHash()
        val progress = state?.progress?.takeIf { hash.isNotBlank() && state.hash == hash }
        RcheevosJni.nativeDeserializeProgress(progress)
    }

    override fun onReset() = RcheevosJni.nativeResetProgress()

    override fun allowCheat(): Boolean = !RcheevosJni.nativeIsHardcore()

    override fun onPause() {
        // Android lifecycle pauses cannot be refused; drop to casual rather than bypass the gate.
        if (!RcheevosJni.nativeCanPause()) {
            RcheevosJni.nativeSetHardcoreEnabled(false)
            message(R.string.ra_hardcore_pause_disabled)
        }
    }

    private fun currentHash(): String = runCatching {
        JSONObject(RcheevosJni.nativeGetGameInfoJson()).optString("hash")
    }.getOrDefault("")

    private fun message(resource: Int) {
        main.post { Toast.makeText(context, resource, Toast.LENGTH_LONG).show() }
    }
}
