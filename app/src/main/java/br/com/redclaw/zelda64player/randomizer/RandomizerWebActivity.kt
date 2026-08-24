package br.com.redclaw.zelda64player.randomizer

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.databinding.ActivityRandomizerWebBinding
import br.com.redclaw.zelda64player.randomizer.repository.RandomizedSeedEntry
import br.com.redclaw.zelda64player.settings.ui.SettingsActivity
import br.com.redclaw.zelda64player.ui.switchui.SwitchDialog
import br.com.redclaw.zelda64player.ui.switchui.SwitchImmersive
import br.com.redclaw.zelda64player.views.LibraryActivity
import java.io.File

/**
 * OoT Randomizer generator, implemented as a native WebView that embeds the
 * official ootrandomizer.com generator. The app pre-fills the ROM file input
 * with the user's imported vanilla OoT ROM and intercepts the patched ROM
 * download (generated client-side via WASM) so it can be registered locally in
 * the Library "Randomizadores" section.
 *
 * Capture flow (see plan "Feature: Gerador de Randomizador OoT via WebView"):
 *  - Challenge A (ROM pre-fill): on the seed page we inject JS that clicks the
 *    ROM file input; [WebChromeClient.onShowFileChooser] then immediately
 *    supplies the vanilla ROM [Uri] (no system chooser).
 *  - Challenge B (patch capture): injected JS hooks the blob download and POSTs
 *    the bytes to [LocalRomServer] (localhost); the fallback
 *    [WebViewJsBridge] streams base64 chunks if the server is unreachable.
 *  - Challenge C (ZIP): [RomZipExtractor] streams a `.z64` out of a `.zip`.
 *
 * Chrome follows the Nintendo Switch UI design tokens (see AGENTS.md).
 */
class RandomizerWebActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRandomizerWebBinding
    private val viewModel: RandomizerWebViewModel by viewModels()
    private lateinit var server: LocalRomServer
    private lateinit var bridge: WebViewJsBridge

    companion object {
        private const val GENERATOR_URL = "https://ootrandomizer.com/generator"
        private const val SEED_PATH = "/seed/get"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRandomizerWebBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SwitchImmersive.enterFullscreen(this)

        binding.randomizerBack.setOnClickListener { finish() }

        setupWebView()
        setupServer()

        viewModel.loadOotRoms()
        when (viewModel.ootRoms.value.size) {
            0 -> showNoVanilla()
            1 -> loadGenerator()
            else -> showRomPicker { loadGenerator() }
        }
    }

    private fun setupWebView() {
        binding.randomizerWebview.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = true
            // The patched ROM is POSTed to http://127.0.0.1 (a secure context),
            // so mixed-content from the HTTPS page is permitted for localhost.
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        bridge = WebViewJsBridge(cacheDir) { file, name -> onPatchCaptured(file, name) }
        binding.randomizerWebview.addJavascriptInterface(bridge, "AndroidRandomizer")

        binding.randomizerWebview.webChromeClient =
            object : WebChromeClient() {
                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    val romUri = viewModel.romUri.value
                    val accept = fileChooserParams?.acceptTypes
                        ?.joinToString(",")?.lowercase() ?: ""
                    val isRomRequest = accept.contains("z64") ||
                        accept.contains("n64") ||
                        accept.contains("rom") ||
                        accept.isBlank()
                    if (romUri != null && isRomRequest) {
                        // Auto-supply the vanilla ROM without a system chooser.
                        filePathCallback.onReceiveValue(arrayOf(romUri))
                        return true
                    }
                    // Non-ROM request (e.g. plandomizer json): cancel so the
                    // page does not hang waiting for a callback.
                    filePathCallback.onReceiveValue(null)
                    return true
                }
            }

        binding.randomizerWebview.webViewClient =
            object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val url = request?.url?.toString() ?: return false
                    // Restrict navigation to ootrandomizer.com (and subdomains).
                    val allowed = url.startsWith("https://ootrandomizer.com") ||
                        url.startsWith("http://ootrandomizer.com")
                    return !allowed
                }

                override fun onPageStarted(
                    view: WebView?,
                    url: String?,
                    favicon: Bitmap?
                ) {
                    super.onPageStarted(view, url, favicon)
                    binding.randomizerProgress.visibility = View.VISIBLE
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    binding.randomizerProgress.visibility = View.GONE
                    url ?: return
                    val seedId = parseSeedId(url)
                    if (seedId != null) {
                        viewModel.setSeedId(seedId)
                        // Inject the ROM autofill (clicks the file input ->
                        // onShowFileChooser). Delay to let the DOM settle.
                        view?.postDelayed({
                            view.evaluateJavascript(
                                RandomizerJs.INJECT_ROM_AUTOFILL,
                                null
                            )
                        }, 1200)
                    } else {
                        viewModel.setSeedId(null)
                    }
                    // Install the download hook on every page so it is ready
                    // whenever the user clicks "Patch ROM!".
                    val port = server.port
                    if (port > 0) {
                        view?.evaluateJavascript(
                            RandomizerJs.hookDownload(port),
                            null
                        )
                    }
                }
            }
    }

    private fun setupServer() {
        server = LocalRomServer(cacheDir) { file, name -> onPatchCaptured(file, name) }
        server.start()
    }

    private fun parseSeedId(url: String): String? {
        if (!url.contains(SEED_PATH)) return null
        val id = Uri.parse(url).getQueryParameter("id")
        return id?.takeIf { it.isNotBlank() }
    }

    private fun loadGenerator() {
        binding.randomizerWebview.loadUrl(GENERATOR_URL)
    }

    /** Called from the server (background thread) or the JS bridge thread. */
    private fun onPatchCaptured(file: File, fileName: String?) {
        runOnUiThread {
            val entry = viewModel.consumeCapture(file, fileName)
            if (entry != null) {
                showCaptureSuccess(entry)
            } else {
                showCaptureError()
            }
        }
    }

    private fun showCaptureSuccess(entry: RandomizedSeedEntry) {
        SwitchDialog(this)
            .title(getString(R.string.randomizer_web_patch_captured))
            .message(entry.name)
            .positiveButton(getString(R.string.randomizer_web_open_library)) {
                startActivity(Intent(this, LibraryActivity::class.java))
                finish()
            }
            .negativeButton(getString(android.R.string.ok)) {
                // Stay on the generator page for another seed.
                viewModel.resetCapture()
            }
            .show()
    }

    private fun showCaptureError() {
        SwitchDialog(this)
            .title(getString(R.string.randomizer_web_title))
            .message(getString(R.string.randomizer_web_capture_failed))
            .positiveButton(getString(android.R.string.ok)) {}
            .show()
    }

    private fun showNoVanilla() {
        SwitchDialog(this)
            .title(getString(R.string.randomizer_web_title))
            .message(getString(R.string.randomizer_web_no_vanilla))
            .positiveButton(getString(R.string.randomizer_web_open_settings)) {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
            .negativeButton(getString(android.R.string.cancel)) { finish() }
            .show()
    }

    private fun showRomPicker(onSelected: () -> Unit) {
        val roms = viewModel.ootRoms.value
        if (roms.isEmpty()) {
            showNoVanilla()
            return
        }
        val labels = roms.map { it.displayName }
        SwitchDialog(this)
            .title(getString(R.string.randomizer_web_rom_picker_title))
            .singleChoice(labels, 0) { which ->
                viewModel.selectRom(roms[which])
                onSelected()
            }
            .negativeButton(getString(android.R.string.cancel)) { finish() }
            .show()
    }

    override fun onDestroy() {
        server.stop()
        binding.randomizerWebview.destroy()
        super.onDestroy()
    }
}
