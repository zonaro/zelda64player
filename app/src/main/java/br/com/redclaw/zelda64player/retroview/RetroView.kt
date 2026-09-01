package br.com.redclaw.zelda64player.retroview

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import android.view.Gravity
import android.widget.FrameLayout
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.repositories.GameRomResolver
import br.com.redclaw.zelda64player.repositories.Storage
import com.swordfish.libretrodroid.GLRetroView
import com.swordfish.libretrodroid.GLRetroViewData
import com.swordfish.libretrodroid.ShaderConfig
import com.swordfish.libretrodroid.Variable
import io.reactivex.disposables.CompositeDisposable
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Wraps a [GLRetroView] configured for a single hack. The patched ROM is loaded
 * from the durable file store at [Storage.rom]; it is produced at install time
 * by [br.com.redclaw.zelda64player.store.DownloadManager] (download + apply BPS
 * against the imported base ROM). The core launches without a ROM when that file
 * is not present (hack not installed), so the app never crashes on a missing ROM.
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

    /* Core error codes reported by LibretroDroid via GLRetroView.getGLRetroErrors()
       (e.g. ERROR_LOAD_GAME, ERROR_GL_NOT_COMPATIBLE). Non-null once the core
       reports a fatal error. Drives the ViewModel's coreFailed state so the
       exit/recreate guards know the core aborted -- in that case LibretroDroid
       sets an internal aborted flag and SKIPS destroy(), making teardown safe
       even if it happens mid-load (no SIGSEGV in retro_deinit). */
    private val _coreError = MutableLiveData<Int?>(null)
    val coreError: LiveData<Int?> = _coreError

    /* Collects the GLRetroView event / error Flows (see registerFrameRenderedListener
       and registerCoreErrorListener). */
    private val glEventScope = CoroutineScope(Dispatchers.Main)

    /**
     * Resolve the core library path for GLRetroView.
     *
     * The .so is loaded directly from the read-only native library directory:
     * since Android 10 the W^X policy forbids executing files from writable
     * app storage, so copying the core into filesDir makes dlopen fail with
     * "Cannot dlopen library" on modern devices.
     */
    private fun prepareCoreFile(coreLibName: String): String {
        val srcFile = File(context.applicationInfo.nativeLibraryDir, coreLibName)
        if (!srcFile.exists()) {
            Log.e("RetroView", "Core library missing from nativeLibraryDir: ${srcFile.absolutePath}")
        }
        return srcFile.absolutePath
    }

    private val retroViewData = GLRetroViewData(context).apply {
        coreFilePath = prepareCoreFile(coreLibName)

        /* Resolve the playable ROM via the single resolver (vanilla base ROMs map to
            the imported file; store hacks / seeds map to the patched ROM at Storage.rom).
            If no ROM exists we launch the core without a ROM instead of throwing -- the
            user just sees an empty emulator. */
        val romFile = GameRomResolver.resolveRomFile(context, hackId)
        if (romFile != null && romFile.exists()) {
            gameFilePath = romFile.absolutePath
        } else {
            Log.w("RetroView", "Playable ROM not found for hack: $hackId")
        }

        shader = ShaderConfig.Sharp
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

    /** Record only the emulator's GL framebuffer; no system screen capture is used. */
    fun startVideoRecording(
        outputFile: File,
        includeMicrophone: Boolean,
        onResult: (Boolean) -> Unit
    ) {
        view.startVideoRecording(outputFile, includeMicrophone, onResult)
    }

    fun stopVideoRecording(onStopped: (() -> Unit)? = null) {
        view.stopVideoRecording(onStopped)
    }

    /** Capture only the emulator framebuffer, excluding all Android overlay Views. */
    fun captureScreenshot(onResult: (Bitmap?) -> Unit) {
        view.captureScreenshot(onResult)
    }

    init {
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        )
        params.gravity = Gravity.CENTER
        view.layoutParams = params
        /* Aspect-ratio handling is now performed internally by LibretroDroid 0.13.x.
           The legacy AspectRatioGLSurfaceView / GLRetroView.setResizeMode(RESIZE_MODE_FIT)
           API and LibretroDroid.getAspectRatio() were removed in this version; the renderer
           applies aspect correction itself, so the default full-surface viewport preserves
           the previous letterboxed (non-stretched) output. No call site action required. */
    }

    /**
     * Register listener for when first frame is rendered.
     *
     * getGLRetroEvents() returns a Kotlin Flow since LibretroDroid 0.13.x (it was an
     * RxJava Observable before). We collect it on the main dispatcher and stop after the
     * first rendered frame, mirroring the old takeUntil(_frameRendered == true) behaviour.
     * The collection Job is wrapped in a RxJava Disposable so it is cancelled by the
     * ViewModel's compositeDisposable on teardown -- no change to the cleanup path.
     */
    fun registerFrameRenderedListener() {
        val job = glEventScope.launch {
            view.getGLRetroEvents().collect { event ->
                if (event is GLRetroView.GLRetroEvents.FrameRendered && _frameRendered.value == false) {
                    _frameRendered.postValue(true)
                    this.coroutineContext[Job]?.cancel()
                }
            }
        }
        compositeDisposable.add(object : io.reactivex.disposables.Disposable {
            private val disposed = AtomicBoolean(false)
            override fun isDisposed(): Boolean = disposed.get()
            override fun dispose() {
                if (disposed.compareAndSet(false, true)) {
                    job.cancel()
                }
            }
        })
    }

    /**
     * Register listener for fatal core errors reported by LibretroDroid.
     *
     * getGLRetroErrors() returns a Kotlin Flow of Int error codes since LibretroDroid
     * 0.13.x. We collect it on the main dispatcher and post every code to [_coreError].
     * The collection Job is wrapped in a RxJava Disposable (same pattern as
     * [registerFrameRenderedListener]) so it is cancelled by the ViewModel's
     * compositeDisposable on teardown -- no change to the cleanup path.
     *
     * A reported error means the core aborted; LibretroDroid then sets an internal
     * aborted flag and SKIPS destroy(), so finishing the Activity afterwards is safe.
     * This is the signal the ViewModel's exit/recreate guards rely on to allow teardown
     * even while the core would otherwise still be loading.
     */
    fun registerCoreErrorListener() {
        val job = glEventScope.launch {
            view.getGLRetroErrors().collect { error ->
                _coreError.postValue(error)
            }
        }
        compositeDisposable.add(object : io.reactivex.disposables.Disposable {
            private val disposed = AtomicBoolean(false)
            override fun isDisposed(): Boolean = disposed.get()
            override fun dispose() {
                if (disposed.compareAndSet(false, true)) {
                    job.cancel()
                }
            }
        })
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
