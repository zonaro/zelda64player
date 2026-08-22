package br.com.redclaw.zelda64player.repositories

import android.content.Context
import java.io.File

/**
 * Singleton for globally accessible ROM metadata.
 *
 * Paths are keyed by hack id so each title keeps its own ROM cache, SRAM and
 * save-state files instead of a single shared set.
 */
class Storage(context: Context) {
    companion object {
        @Volatile private var instance: Storage? = null
        fun getInstance(context: Context): Storage = instance ?: synchronized(this) {
            instance ?: Storage(context).also { instance = it }
        }
    }

    val storagePath: String = (context.getExternalFilesDir(null) ?: context.filesDir).path
    val cachePath: String = (context.externalCacheDir ?: context.cacheDir).path

    fun rom(hackId: String) = File("$cachePath/rom_$hackId")
    fun sram(hackId: String) = File("$storagePath/sram_$hackId")
    fun state(hackId: String) = File("$storagePath/state_$hackId")
}
