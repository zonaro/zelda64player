package br.com.redclaw.zelda64player.utils

import android.content.Context

/**
 * Shared read/write access to the selected LibRetro core index.
 *
 * Used by both [LibraryActivity] (settings core dialog) and
 * [GameActivityViewModel] (to pick the core .so at launch) so the choice
 * lives in exactly one place.
 */
object CorePrefs {
    private const val PREFS_NAME = "ludere_prefs"
    private const val KEY = "selected_core_index"

    val options = arrayOf(
        "Mupen64Plus Next (GLES3)",
        "Mupen64Plus Next (GLES2)",
        "Parallel N64"
    )
    val libNames = arrayOf(
        "libcore_mupen_gles3.so",
        "libcore_mupen_gles2.so",
        "libcore_parallel.so"
    )

    fun getSelectedCoreIndex(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(KEY, 0)

    fun getSelectedCoreLib(context: Context): String {
        val index = getSelectedCoreIndex(context)
        return libNames.getOrElse(index) { libNames[0] }
    }

    fun setSelectedCoreIndex(context: Context, index: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY, index).apply()
    }
}
