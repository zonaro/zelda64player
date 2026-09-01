package br.com.redclaw.zelda64player.store.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.data.local.AppRepositories
import br.com.redclaw.zelda64player.data.local.InstalledHacksRepository
import br.com.redclaw.zelda64player.data.model.HackEntry
import br.com.redclaw.zelda64player.databinding.ActivityWebviewDownloadBinding
import br.com.redclaw.zelda64player.patcher.PatcherFacade
import br.com.redclaw.zelda64player.repositories.Storage
import br.com.redclaw.zelda64player.store.ImportPatchInvalid
import br.com.redclaw.zelda64player.store.ImportPatchNoCompatibleRom
import br.com.redclaw.zelda64player.store.ImportPatchSuccess
import br.com.redclaw.zelda64player.store.ImportPatchUnsupported
import br.com.redclaw.zelda64player.store.ImportRomDuplicate
import br.com.redclaw.zelda64player.store.ImportRomInvalid
import br.com.redclaw.zelda64player.store.ImportRomSuccess
import br.com.redclaw.zelda64player.store.ImportedPatchInstaller
import br.com.redclaw.zelda64player.store.ImportedRomInstaller
import br.com.redclaw.zelda64player.ui.switchui.SwitchImmersive
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/**
 * Embedded WebView for hacks whose catalog entry has no direct patch URL (
 * [br.com.redclaw.zelda64player.store.DownloadTarget.ExternalLink]).
 *
 * Instead of opening the system browser, this Activity loads the source page inside the app and
 * intercepts any download whose URL or Content-Disposition filename ends with a supported
 * extension:
 *
 * - Patch files: `.bps`, `.ips`, `.xdelta` (also inside `.zip`)
 * - Direct ROMs: `.n64`, `.z64`, `.v64`
 *
 * When such a file is detected the WebView navigation is cancelled, the file is downloaded with
 * OkHttp (reusing cookies from the WebView), and then installed through the same pipeline used by
 * the manual import flow:
 *
 * - Patches → [ImportedPatchInstaller] (applies BPS/IPS/XDELTA against the user's imported base ROM
 * and writes `rom_<hackId>`).
 * - ROMs → [ImportedRomInstaller] (normalizes and registers as a base ROM).
 *
 * Archives (`.zip`, `.7z`, `.rar`) are handled when they contain a supported inner file: `.zip` is
 * extracted via [br.com.redclaw.zelda64player.store.ZipExtractor], `.7z`/`.rar` are rejected with a
 * user-facing message (the app cannot extract them without native tooling).
 *
 * The Activity is launched from [HackDetailDialog] with the hack JSON and the initial URL. On
 * success it finishes and the Library will show the new entry.
 */
class WebViewDownloadActivity : AppCompatActivity() {

    private lateinit var binding: ActivityWebviewDownloadBinding
    private lateinit var hack: HackEntry
    private var isHandlingDownload = false

    companion object {
        const val EXTRA_HACK_JSON = "extra_hack_json"
        const val EXTRA_URL = "extra_url"

        private const val TAG = "WebViewDownload"

        /** Extensions that trigger interception (lowercase, with dot). */
        private val PATCH_EXTS = setOf(".bps", ".ips", ".xdelta")
        private val ROM_EXTS = setOf(".n64", ".z64", ".v64")
        private val ARCHIVE_EXTS = setOf(".zip", ".7z", ".rar")
        private val ALL_INTERCEPT_EXTS = PATCH_EXTS + ROM_EXTS + ARCHIVE_EXTS

        /** Also intercept when Content-Disposition suggests a patch/ROM filename. */
        private fun isInterceptableUrl(url: String): Boolean {
            val lower = url.lowercase().substringBefore('?').substringBefore('#')
            return ALL_INTERCEPT_EXTS.any { lower.endsWith(it) }
        }

        private fun isInterceptableFilename(name: String): Boolean {
            val lower = name.lowercase()
            return ALL_INTERCEPT_EXTS.any { lower.endsWith(it) }
        }

        private fun isPatchFile(name: String): Boolean {
            val lower = name.lowercase()
            return PATCH_EXTS.any { lower.endsWith(it) }
        }

        private fun isRomFile(name: String): Boolean {
            val lower = name.lowercase()
            return ROM_EXTS.any { lower.endsWith(it) }
        }

        private fun isArchiveFile(name: String): Boolean {
            val lower = name.lowercase()
            return ARCHIVE_EXTS.any { lower.endsWith(it) }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebviewDownloadBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SwitchImmersive.enterFullscreen(this)

        val hackJson = intent.getStringExtra(EXTRA_HACK_JSON)
        val initialUrl = intent.getStringExtra(EXTRA_URL)
        if (hackJson.isNullOrBlank() || initialUrl.isNullOrBlank()) {
            finish()
            return
        }
        hack = HackEntry.fromJson(JSONObject(hackJson))

        binding.webviewToolbarTitle.text = hack.name
        binding.webviewBack.setOnClickListener { handleBack() }
        binding.webviewClose.setOnClickListener { finish() }

        val webView = binding.webview
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.allowFileAccess = false
        webView.settings.allowContentAccess = false
        CookieManager.getInstance().setAcceptCookie(true)

        webView.webViewClient =
                object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false
                        Log.d(TAG, "shouldOverrideUrlLoading: $url")
                        if (isInterceptableUrl(url)) {
                            val filename = URLUtil.guessFileName(url, null, null)
                            if (isInterceptableFilename(filename) || isInterceptableUrl(url)) {
                                handleDownload(url, filename, null, null)
                                return true
                            }
                        }
                        return false
                    }

                    override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                    ): WebResourceResponse? {
                        // Let the WebView handle normal page loads; download interception
                        // is done via shouldOverrideUrlLoading + DownloadListener.
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        binding.webviewProgress.visibility = View.GONE
                        binding.webviewProgressText.visibility = View.GONE
                    }
                }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            val filename = URLUtil.guessFileName(url, contentDisposition, mimeType)
            Log.d(TAG, "DownloadListener: url=$url filename=$filename mime=$mimeType")
            if (isInterceptableFilename(filename) || isInterceptableUrl(url)) {
                handleDownload(url, filename, contentDisposition, mimeType)
            } else {
                // Not a patch/ROM — let the system handle it or ignore.
                Log.d(TAG, "Ignoring non-patch download: $filename")
            }
        }

        webView.loadUrl(initialUrl)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) SwitchImmersive.enterFullscreen(this)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            handleBack()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun handleBack() {
        if (binding.webview.canGoBack()) {
            binding.webview.goBack()
        } else {
            finish()
        }
    }

    private fun handleDownload(
            url: String,
            filename: String,
            contentDisposition: String?,
            mimeType: String?
    ) {
        if (isHandlingDownload) {
            Log.d(TAG, "Already handling a download, ignoring: $url")
            return
        }

        // Reject unsupported archives early with a clear message.
        val lowerName = filename.lowercase()
        if (lowerName.endsWith(".7z") || lowerName.endsWith(".rar")) {
            AlertDialog.Builder(this)
                    .setTitle(R.string.webview_unsupported_archive_title)
                    .setMessage(getString(R.string.webview_unsupported_archive_message, filename))
                    .setPositiveButton(R.string.dialog_ok, null)
                    .show()
            return
        }

        isHandlingDownload = true
        binding.webviewProgress.visibility = View.VISIBLE
        binding.webviewProgress.isIndeterminate = true
        binding.webviewProgressText.visibility = View.VISIBLE
        binding.webviewProgressText.text = getString(R.string.webview_downloading, filename)

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { downloadAndInstall(url, filename) }
            isHandlingDownload = false
            binding.webviewProgress.visibility = View.GONE
            binding.webviewProgressText.visibility = View.GONE

            when (result) {
                is WebViewInstallResult.Success -> {
                    Toast.makeText(
                                    this@WebViewDownloadActivity,
                                    getString(R.string.webview_install_success, result.title),
                                    Toast.LENGTH_LONG
                            )
                            .show()
                    setResult(RESULT_OK)
                    finish()
                }
                is WebViewInstallResult.Error -> {
                    AlertDialog.Builder(this@WebViewDownloadActivity)
                            .setTitle(R.string.webview_install_error_title)
                            .setMessage(result.message)
                            .setPositiveButton(R.string.dialog_ok, null)
                            .show()
                }
                is WebViewInstallResult.UnsupportedArchive -> {
                    AlertDialog.Builder(this@WebViewDownloadActivity)
                            .setTitle(R.string.webview_unsupported_archive_title)
                            .setMessage(
                                    getString(
                                            R.string.webview_unsupported_archive_message,
                                            filename
                                    )
                            )
                            .setPositiveButton(R.string.dialog_ok, null)
                            .show()
                }
            }
        }
    }

    /**
     * Download [url] to a temp file, then install it as a patch or ROM. Runs on [Dispatchers.IO].
     */
    private suspend fun downloadAndInstall(url: String, filename: String): WebViewInstallResult =
            withContext(Dispatchers.IO) {
                val cookie = CookieManager.getInstance().getCookie(url)
                val client =
                        OkHttpClient.Builder()
                                .connectTimeout(30, TimeUnit.SECONDS)
                                .readTimeout(60, TimeUnit.SECONDS)
                                .followRedirects(true)
                                .followSslRedirects(true)
                                .build()

                val requestBuilder = Request.Builder().url(url)
                if (!cookie.isNullOrBlank()) {
                    requestBuilder.header("Cookie", cookie)
                }
                // Some hosts require a Referer or User-Agent to serve downloads.
                requestBuilder.header("User-Agent", binding.webview.settings.userAgentString)

                val tempFile: File
                try {
                    val response = client.newCall(requestBuilder.build()).execute()
                    if (!response.isSuccessful) {
                        return@withContext WebViewInstallResult.Error(
                                getString(R.string.webview_download_failed, "HTTP ${response.code}")
                        )
                    }
                    val body =
                            response.body
                                    ?: return@withContext WebViewInstallResult.Error(
                                            getString(
                                                    R.string.webview_download_failed,
                                                    "empty body"
                                            )
                                    )

                    // Use the filename from Content-Disposition if available, otherwise the guessed
                    // one.
                    val contentDisp = response.header("Content-Disposition")
                    val finalName =
                            when {
                                contentDisp != null -> URLUtil.guessFileName(url, contentDisp, null)
                                else -> filename
                            }

                    // If the response is HTML (e.g. a landing page), don't treat it as a patch.
                    val contentType = response.header("Content-Type")?.lowercase() ?: ""
                    if (contentType.contains("text/html") && !isInterceptableFilename(finalName)) {
                        return@withContext WebViewInstallResult.Error(
                                getString(
                                        R.string.webview_download_failed,
                                        "unexpected HTML response"
                                )
                        )
                    }

                    tempFile = File(cacheDir, "webview_${System.currentTimeMillis()}_${finalName}")
                    body.byteStream().use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    Log.d(
                            TAG,
                            "Downloaded ${tempFile.length()} bytes to ${tempFile.absolutePath} (finalName=$finalName)"
                    )

                    // Handle ZIP archives: extract the first patch/ROM inside.
                    val lowerFinal = finalName.lowercase()
                    val fileToInstall: File
                    val displayName: String
                    if (lowerFinal.endsWith(".zip")) {
                        val extracted = tryExtractFromZip(tempFile, finalName)
                        if (extracted == null) {
                            tempFile.delete()
                            return@withContext WebViewInstallResult.Error(
                                    getString(R.string.webview_no_patch_in_zip, finalName)
                            )
                        }
                        // extracted is a temp file with the inner patch/ROM bytes.
                        tempFile.delete()
                        fileToInstall = extracted
                        displayName = extracted.name
                    } else {
                        fileToInstall = tempFile
                        displayName = finalName
                    }

                    // Dispatch to the correct installer based on file type.
                    val lowerDisplay = displayName.lowercase()
                    val result: WebViewInstallResult =
                            when {
                                PATCH_EXTS.any { lowerDisplay.endsWith(it) } -> {
                                    installPatch(fileToInstall, displayName)
                                }
                                ROM_EXTS.any { lowerDisplay.endsWith(it) } -> {
                                    installRom(fileToInstall, displayName)
                                }
                                else -> {
                                    // Try to detect by content (e.g. BPS magic) even if extension
                                    // is odd.
                                    val format = PatcherFacade.detectPatchFormat(fileToInstall)
                                    if (format != PatcherFacade.PatchFormat.UNKNOWN) {
                                        installPatch(fileToInstall, displayName)
                                    } else {
                                        WebViewInstallResult.Error(
                                                getString(
                                                        R.string.webview_unsupported_file,
                                                        displayName
                                                )
                                        )
                                    }
                                }
                            }

                    // Clean up temp file if not already deleted.
                    if (fileToInstall.exists() && fileToInstall != tempFile) {
                        fileToInstall.delete()
                    }
                    if (tempFile.exists()) tempFile.delete()

                    result
                } catch (e: Exception) {
                    Log.e(TAG, "Download/install failed for $url", e)
                    WebViewInstallResult.Error(
                            getString(
                                    R.string.webview_download_failed,
                                    e.message ?: "unknown error"
                            )
                    )
                }
            }

    /**
     * Extract the first patch/ROM entry from a ZIP file. Returns a temp file with the extracted
     * bytes, or null if no suitable entry was found.
     */
    private fun tryExtractFromZip(zipFile: File, zipName: String): File? {
        return try {
            val bytes =
                    try {
                        // Try to find a patch file first, then a ROM.
                        br.com.redclaw.zelda64player.store.ZipExtractor.extractFirstMatching(
                                zipFile,
                                ".*\\.(bps|ips|xdelta)$"
                        )
                    } catch (_: Exception) {
                        try {
                            br.com.redclaw.zelda64player.store.ZipExtractor.extractFirstMatching(
                                    zipFile,
                                    ".*\\.(n64|z64|v64)$"
                            )
                        } catch (_: Exception) {
                            return null
                        }
                    }
            // Write extracted bytes to a temp file with the correct extension.
            // We need to know the inner filename to preserve the extension.
            val innerName =
                    findFirstMatchingName(zipFile, ".*\\.(bps|ips|xdelta|n64|z64|v64)$")
                            ?: "extracted_patch.bps"
            val out =
                    File(
                            cacheDir,
                            "webview_extracted_${System.currentTimeMillis()}_${innerName.substringAfterLast('/')}"
                    )
            out.writeBytes(bytes)
            out
        } catch (e: Exception) {
            Log.e(TAG, "Failed to extract from ZIP: $zipName", e)
            null
        }
    }

    private fun findFirstMatchingName(zipFile: File, regex: String): String? {
        val pattern = Regex(regex, RegexOption.IGNORE_CASE)
        try {
            java.util.zip.ZipInputStream(zipFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && pattern.matches(entry.name)) {
                        return entry.name
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        } catch (_: Exception) {}
        return null
    }

    private suspend fun installPatch(patchFile: File, displayName: String): WebViewInstallResult =
            withContext(Dispatchers.IO) {
                // For patches, we install as a hack tied to the catalog entry's id.
                // Use the hack's id so the Library shows the correct title/cover.
                val baseRomRepo = AppRepositories.baseRomRepository(this@WebViewDownloadActivity)
                val installedRepo = InstalledHacksRepository(File(filesDir, "installed_hacks.json"))
                val userHacksRepo =
                        AppRepositories.userHacksRepository(this@WebViewDownloadActivity)
                val storage = Storage.getInstance(this@WebViewDownloadActivity)

                // Detect format and handle ZIP-wrapped patches that slipped through.
                val format = PatcherFacade.detectPatchFormat(patchFile)
                if (format == PatcherFacade.PatchFormat.UNKNOWN) {
                    // Maybe it's a ZIP that wasn't caught earlier (e.g. .bps.zip double extension).
                    if (displayName.lowercase().endsWith(".zip")) {
                        val extracted = tryExtractFromZip(patchFile, displayName)
                        if (extracted != null) {
                            val result = installPatch(extracted, extracted.name)
                            extracted.delete()
                            return@withContext result
                        }
                    }
                    return@withContext WebViewInstallResult.Error(
                            getString(R.string.webview_unsupported_file, displayName)
                    )
                }

                // Install via ImportedPatchInstaller but force the hack id to match
                // the catalog entry so the Store/Library dedup works.
                val installer =
                        ImportedPatchInstaller(
                                this@WebViewDownloadActivity,
                                baseRomRepo,
                                installedRepo,
                                userHacksRepo,
                                storage
                        )

                // We need to install with the catalog hack's id, not a generated one.
                // ImportedPatchInstaller generates an id from the filename, so we
                // install normally and then rename the installed entry if needed.
                val result = installer.install(patchFile, hack.name)

                when (result) {
                    is ImportPatchSuccess -> {
                        // If the generated id differs from the catalog id, migrate
                        // the installed ROM and repository entries to the catalog id.
                        if (result.hackId != hack.id) {
                            migrateToCatalogId(
                                    result.hackId,
                                    hack.id,
                                    storage,
                                    installedRepo,
                                    userHacksRepo
                            )
                        }
                        WebViewInstallResult.Success(result.title)
                    }
                    is ImportPatchNoCompatibleRom ->
                            WebViewInstallResult.Error(
                                    getString(
                                            R.string.webview_no_base_rom,
                                            result.expectedCrc32,
                                            result.targetDescription
                                                    ?: getString(R.string.game_unknown)
                                    )
                            )
                    is ImportPatchInvalid -> WebViewInstallResult.Error(result.message)
                    is ImportPatchUnsupported -> WebViewInstallResult.Error(result.message)
                    else -> WebViewInstallResult.Error(getString(R.string.webview_install_failed))
                }
            }

    private suspend fun installRom(romFile: File, displayName: String): WebViewInstallResult =
            withContext(Dispatchers.IO) {
                val baseRomRepo = AppRepositories.baseRomRepository(this@WebViewDownloadActivity)
                val installer = ImportedRomInstaller(this@WebViewDownloadActivity, baseRomRepo)
                val result = installer.install(romFile, displayName)
                when (result) {
                    is ImportRomSuccess -> WebViewInstallResult.Success(result.title)
                    is ImportRomDuplicate -> WebViewInstallResult.Success(result.title)
                    is ImportRomInvalid -> WebViewInstallResult.Error(result.message)
                    else -> WebViewInstallResult.Error(getString(R.string.webview_install_failed))
                }
            }

    /**
     * Migrate an imported hack from [generatedId] to [catalogId] so the Library and Store show it
     * under the catalog entry's identity.
     */
    private fun migrateToCatalogId(
            generatedId: String,
            catalogId: String,
            storage: Storage,
            installedRepo: InstalledHacksRepository,
            userHacksRepo: br.com.redclaw.zelda64player.data.local.UserHacksRepository
    ) {
        try {
            val srcRom = storage.rom(generatedId)
            val dstRom = storage.rom(catalogId)
            if (srcRom.exists() && !dstRom.exists()) {
                srcRom.renameTo(dstRom) ||
                        run {
                            srcRom.copyTo(dstRom, overwrite = true)
                            srcRom.delete()
                        }
            }
            // Migrate installed record.
            val installed = installedRepo.load()[generatedId]
            if (installed != null) {
                installedRepo.unmarkInstalled(generatedId)
                installedRepo.markInstalled(
                        catalogId,
                        installed.version,
                        installed.fileName,
                        installed.canonicalId,
                        installed.patchChecksums
                )
            }
            // Migrate user hack entry.
            val userHacks = AppRepositories.userHacksRepository(this)
            val entry = userHacks.getById(generatedId)
            if (entry != null) {
                userHacks.remove(generatedId)
                userHacks.add(entry.copy(id = catalogId))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to migrate $generatedId -> $catalogId", e)
        }
    }

    private sealed class WebViewInstallResult {
        data class Success(val title: String) : WebViewInstallResult()
        data class Error(val message: String) : WebViewInstallResult()
        data object UnsupportedArchive : WebViewInstallResult()
    }
}
