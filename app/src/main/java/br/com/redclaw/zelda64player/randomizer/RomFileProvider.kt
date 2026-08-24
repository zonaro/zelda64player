package br.com.redclaw.zelda64player.randomizer

import androidx.core.content.FileProvider

/**
 * ContentProvider that exposes ONLY the temporary vanilla OoT ROM file (served
 * from the app's `cache/randomizer_rom` directory) to the in-app WebView, so it
 * can be supplied to the ootrandomizer.com ROM file input via
 * [android.webkit.WebChromeClient.onShowFileChooser].
 *
 * No other app file is reachable through this authority. The file paths are
 * declared in `res/xml/randomizer_file_paths.xml`.
 */
class RomFileProvider : FileProvider()
