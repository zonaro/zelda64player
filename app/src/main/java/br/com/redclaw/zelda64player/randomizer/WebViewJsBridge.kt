package br.com.redclaw.zelda64player.randomizer

import android.util.Base64
import android.webkit.JavascriptInterface
import java.io.File
import java.io.FileOutputStream

/**
 * Fallback capture path used when the localhost [LocalRomServer] is unreachable.
 *
 * The injected JS ([RandomizerJs.hookDownload]) streams the patched ROM as
 * base64 chunks; each chunk is decoded and appended to a temp file so the whole
 * ROM is never buffered in memory (important for 32+ MB N64 ROMs). When the
 * final chunk arrives, [onCaptured] is invoked with the assembled file.
 *
 * Registered with the WebView as `@JavascriptInterface` name `AndroidRandomizer`.
 */
class WebViewJsBridge(
    private val tempDir: File,
    private val onCaptured: (File, String?) -> Unit
) {
    @Volatile private var tempFile: File? = null

    @JavascriptInterface
    fun appendChunk(base64: String) {
        val file = tempFile ?: run {
            val f = File(tempDir, "randomizer_capture_${System.currentTimeMillis()}.z64")
            tempFile = f
            f
        }
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        FileOutputStream(file, true).use { it.write(bytes) }
    }

    @JavascriptInterface
    fun endCapture(fileName: String?) {
        val file = tempFile ?: return
        tempFile = null
        onCaptured(file, fileName?.takeIf { it.isNotBlank() })
    }
}
