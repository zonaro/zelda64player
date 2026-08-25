package br.com.redclaw.zelda64player.repositories

import android.content.Context
import java.io.File

/**
 * Singleton for globally accessible ROM metadata.
 *
 * Paths are keyed by hack id so each title keeps its own ROM cache, SRAM and
 * save-state files instead of a single shared set.
 *
 * The patched ROM ([rom]) lives under [storagePath] (durable external files
 * dir) so it survives cache eviction and process death. Earlier builds stored
 * it under the cache dir; [migrateLegacyRoms] relocates any leftover files on
 * startup.
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

    /**
     * Absolute path of the patched, ready-to-play ROM for [hackId]. Stored under
     * the durable external files dir (not the cache) so it is not wiped by the OS.
     */
    fun rom(hackId: String) = File("$storagePath/rom_$hackId")

    fun sram(hackId: String) = File("$storagePath/sram_$hackId")
    fun state(hackId: String) = File("$storagePath/state_$hackId")

    /**
     * Directory holding all captured screenshots and screen recordings. Lives
     * under [storagePath] (durable external files dir, not the cache) so captures
     * survive cache eviction and process death. Created on first access.
     */
    fun galleryDir(): File = File(storagePath, "gallery").apply { mkdirs() }

    /**
     * Path of a screenshot PNG for [hackId] taken at [timestamp]. [withOverlay]
     * selects the "overlay" (controls drawn) or "clean" (game only) variant; the
     * two are always produced together by [br.com.redclaw.zelda64player.capture.CaptureManager].
     */
    fun screenshotFile(hackId: String, timestamp: Long, withOverlay: Boolean): File =
        File(
            galleryDir(),
            "screenshot_${hackId}_${timestamp}_${if (withOverlay) "overlay" else "clean"}.png"
        )

    /** Path of a screen-recording MP4 for [hackId] taken at [timestamp]. */
    fun recordingFile(hackId: String, timestamp: Long): File =
        File(galleryDir(), "recording_${hackId}_${timestamp}.mp4")

    /**
     * One-time relocation of patched ROMs left in the legacy cache directory by
     * earlier builds. For every `rom_<id>` file found in [cachePath] that is not
     * already present under [storagePath], it is moved (or copied+deleted as a
     * fallback). Idempotent and side-effect safe: safe to call on every startup.
     */
    fun migrateLegacyRoms() {
        migrateLegacyRomFiles(File(cachePath), File(storagePath))
    }

    /** All per-hack save files (SRAM and save-state), in a stable order. */
    fun saveFiles(hackId: String): List<File> = listOf(sram(hackId), state(hackId))
}

/**
 * Pure relocation logic for [Storage.migrateLegacyRoms], extracted so it can be
 * unit-tested on the JVM with temporary folders (no Android dependencies).
 *
 * Moves every file named `rom_*` from [cacheDir] into [storageDir]. If the
 * target already exists the legacy copy is discarded; on rename failure it
 * falls back to a streamed copy. Never throws.
 */
fun migrateLegacyRomFiles(cacheDir: File, storageDir: File) {
    val legacy = cacheDir.listFiles { f -> f.isFile && f.name.startsWith("rom_") } ?: return
    for (file in legacy) {
        val dest = File(storageDir, file.name)
        if (dest.exists()) {
            file.delete()
            continue
        }
        try {
            if (file.renameTo(dest)) continue
            file.inputStream().use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
            file.delete()
        } catch (_: Exception) {
            // Leave the legacy file in place if relocation fails; a later
            // startup will retry.
        }
    }
}

/**
 * Pure deletion logic for per-hack files, extracted so it can be unit-tested on
 * the JVM with temporary folders (no Android dependencies).
 *
 * Deletes `rom_<hackId>`, `sram_<hackId>` and `state_<hackId>` under
 * [storageDir]. Missing files are treated as already-removed (no error). Returns
 * true when every present file was deleted successfully (or none existed),
 * false if any deletion attempt failed. The caller is responsible for also
 * unmarking the install registry and removing the play-history entry.
 */
fun uninstallHackFiles(storageDir: File, hackId: String): Boolean {
    val names = listOf("rom_$hackId", "sram_$hackId", "state_$hackId")
    var ok = true
    for (name in names) {
        val file = File(storageDir, name)
        if (file.exists()) {
            ok = file.delete() && ok
        }
    }
    return ok
}
