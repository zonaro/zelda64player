package br.com.redclaw.zelda64player.retroview

import android.content.Context
import android.util.Log
import android.view.Gravity
import android.widget.FrameLayout
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.repositories.Storage
import com.swordfish.libretrodroid.AspectRatioGLSurfaceView
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.Variable
import io.reactivex.disposables.CompositeDisposable
import java.io.File

/**
 * Wraps a [GLRetroView] configured for a single hack. The patched ROM is loaded
 * from the file cache at [Storage.rom] (populated by the patcher in later phases);
 * the core launches without a ROM when that cache file is not present yet, so the
 * app never crashes on a missing ROM.
 */
class RetroView(
    private val context: Context,
    private val compositeDisposable: CompositeDisposable,
    private val hackId: String,
    coreLibName: String = "libcore_mupen_gles3.so"
) {
    private val resources = context.resources
    private val storage = Storage.getInstance(context)

    private val _frameRendered = MutableLiveData(false)
    val frameRendered: LiveData<Boolean> = _frameRendered

    /**
     * Copy the selected core .so from nativeLibraryDir to internal storage
     * so GLRetroView can load it by absolute path
     */
    private fun prepareCoreFile(coreLibName: String): String {
        val coreDir = File(context.filesDir, "cores")
        coreDir.mkdirs()
        val destFile = File(coreDir, "libretrocore.so")

        val nativeDir = File(context.applicationInfo.nativeLibraryDir)
        val srcFile = File(nativeDir, coreLibName)

        if (srcFile.exists()) {
            srcFile.inputStream().use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        return destFile.absolutePath
    }

    private val retroViewData = GLRetroViewData(context).apply {
        coreFilePath = prepareCoreFile(coreLibName)

        /* Load the patched ROM from the file cache (Storage.rom). In Phase 0 the patched
           ROM cache may not exist yet (the patcher lands in a later phase), so we launch the
           core without a ROM instead of throwing -- the user just sees an empty emulator. */
        val romFile = storage.rom(hackId)
        if (romFile.exists()) {
            gameFilePath = romFile.absolutePath
        } else {
            Log.w("RetroView", "Patched ROM cache not found for hack: $hackId")
        }

        shader = GLRetroView.SHADER_SHARP
        variables = getCoreVariables()

        val sramFile = storage.sram(hackId)
        if (sramFile.exists()) {
            sramFile.inputStream().use {
                saveRAMState = it.readBytes()
            }
        }
    }

    /**
     * GLRetroView instance itself
     */
    val view = GLRetroView(context, retroViewData)

    init {
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
        params.gravity = Gravity.CENTER
        view.layoutParams = params
        /* Keep the native N64 aspect ratio letterboxed by default. Per-hack stretch-to-fill
           (e.g. OoT DX) will be driven from the catalog in a later phase. */
        view.setResizeMode(AspectRatioGLSurfaceView.RESIZE_MODE_FIT)
    }

    /**
     * Register listener for when first frame is rendered
     */
    fun registerFrameRenderedListener() {
        val renderDisposable = view
            .getGLRetroEvents()
            .takeUntil { _frameRendered.value == true }
            .subscribe {
                if (it == GLRetroView.GLRetroEvents.FrameRendered && _frameRendered.value == false) {
                    _frameRendered.postValue(true)
                }
            }
        compositeDisposable.add(renderDisposable)
    }

    /**
     * Parse core variables from config
     */
    private fun getCoreVariables(): Array<Variable> {
        val variables = arrayListOf<Variable>()
        val rawVariablesString = context.getString(R.string.config_variables)
        val rawVariables = rawVariablesString.split(",")

        for (rawVariable in rawVariables) {
            val rawVariableSplit = rawVariable.split("=")
            if (rawVariableSplit.size != 2)
                continue

            variables.add(Variable(rawVariableSplit[0], rawVariableSplit[1]))
        }

        return variables.toTypedArray()
    }
}
