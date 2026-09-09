package br.com.redclaw.zelda64player.retroachievements.api

import android.content.Context
import android.os.Build

/**
 * Builds the User-Agent header required by the RetroAchievements API.
 *
 * Format contract (rcheevos): `<product>/<semver> (<system-info>) <extensions>`. Hardcore mode
 * stays disabled until RAdmin validates this UA string for the app; changing the format requires
 * re-validation, so treat it as frozen.
 */
object RaUserAgent {

    private const val PRODUCT = "Zelda64Player"
    private const val VERSION = "1.0.0"

    fun build(@Suppress("unused") context: Context): String {
        val androidVersion = Build.VERSION.RELEASE ?: "unknown"
        val device = Build.MODEL?.takeIf { it.isNotBlank() } ?: Build.DEVICE ?: "android"
        val base = "$PRODUCT/$VERSION (Android $androidVersion; $device)"
        val clause =
                runCatching {
                    // rcheevos clause is "rcheevos/12.4.0" — required for Hardcore validation by
                    // RAdmin.
                    // Falls back to hardcoded version when native lib not yet loaded (e.g. early
                    // startup).
                    br.com.redclaw.zelda64player.retroachievements.jni.RcheevosJni
                            .getUserAgentClause()
                }
                        .getOrNull()
                        ?.takeIf { it.isNotBlank() }
                        ?: "rcheevos/12.4.0"
        return "$base $clause"
    }
}
