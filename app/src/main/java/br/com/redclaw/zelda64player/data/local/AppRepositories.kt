package br.com.redclaw.zelda64player.data.local

import android.content.Context
import br.com.redclaw.zelda64player.repositories.Storage
import java.io.File

/**
 * Single construction point for the app-wide repositories so every ViewModel
 * shares identical paths and migration behavior (DRY).
 *
 * Base ROMs are stored under filesDir (persistent internal storage) — never
 * under a cache dir, which the OS may wipe at any time. [BaseRomRepository]
 * transparently migrates files left behind by older builds that used the
 * cache directory.
 */
object AppRepositories {

    /** Persistent storage for normalized base ROMs. */
    fun baseRomStorageDir(context: Context): File = File(context.filesDir, "base_roms")

    /** Legacy cache location used by earlier builds; migrated on startup. */
    private fun legacyBaseRomStorageDir(context: Context): File =
        File(context.externalCacheDir ?: context.cacheDir, "base_roms")

    fun baseRomRepository(context: Context): BaseRomRepository {
        val external = context.getExternalFilesDir(null) ?: context.filesDir
        return BaseRomRepository(
            importDir = File(external, "base_roms"),
            storageDir = baseRomStorageDir(context),
            registryFile = File(context.filesDir, "base_roms.json"),
            legacyStorageDirs = listOf(legacyBaseRomStorageDir(context))
        )
    }

    fun patchRepository(context: Context): PatchRepository {
        val external = context.getExternalFilesDir(null) ?: context.filesDir
        return PatchRepository(File(external, "patches"))
    }

    /**
     * Persistent store for user-imported BPS/IPS hacks (imported from the Store
     * screen). Kept separate from the remote merged catalog so a catalog refresh
     * never drops a user's own hacks.
     */
    fun userHacksRepository(context: Context): UserHacksRepository =
        UserHacksRepository(File(context.filesDir, "user_hacks.json"))

    fun userHackCoverRepository(context: Context): UserHackCoverRepository =
        UserHackCoverRepository(File(context.filesDir, "user_hack_covers"))
}
