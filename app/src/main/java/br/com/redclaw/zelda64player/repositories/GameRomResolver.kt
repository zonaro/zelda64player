package br.com.redclaw.zelda64player.repositories

import android.content.Context
import br.com.redclaw.zelda64player.data.local.AppRepositories
import br.com.redclaw.zelda64player.data.model.BaseRom
import java.io.File

/**
 * Single resolution point for the playable ROM file of a Library entry.
 *
 * Project Rule 10 keeps [br.com.redclaw.zelda64player.retroview.RetroView] the
 * ONLY place ROM bytes reach the core; this resolver only decides WHICH file path
 * is used. Vanilla base-ROM entries (id prefixed with [VANILLA_PREFIX]) resolve to
 * the user's imported, normalized base ROM file, while store hacks and randomizer
 * seeds resolve to the patched ROM at [Storage.rom]. No ROM file is ever copied or
 * duplicated (base ROMs are 32-64 MB; Rule 9 performance).
 *
 * The core function [resolveRomFile] is pure and JVM-testable: it takes the
 * [hackId], a [baseRomLookup] that returns the [BaseRom] for a crc32 id (or null),
 * and a [storageRom] factory that produces the patched-ROM [File] for a non-vanilla
 * id. The Android [Context] wrapper wires those two callbacks to the real
 * [AppRepositories.baseRomRepository] and [Storage] singletons.
 *
 * Returns null when no playable ROM exists so callers keep their existing
 * missing-ROM error paths (e.g. RetroView launches without a ROM, launchHack shows
 * an i18n'd error). For non-vanilla ids the patched-ROM File is always returned
 * (possibly non-existent), exactly mirroring the previous `Storage.rom(hackId)`
 * behaviour that callers already guard with `.exists()`.
 */
object GameRomResolver {

    /** Prefix applied to every vanilla base-ROM entry id. */
    const val VANILLA_PREFIX = "vanilla_"

    /**
     * Pure resolution core.
     *
     * @param hackId the Library entry id (may be vanilla-prefixed)
     * @param baseRomLookup returns the [BaseRom] for a crc32 id, or null if unknown
     * @param storageRom returns the patched-ROM [File] for a non-vanilla id
     * @return the [File] to load, or null when no playable ROM exists
     */
    fun resolveRomFile(
        hackId: String,
        baseRomLookup: (String) -> BaseRom?,
        storageRom: (String) -> File
    ): File? {
        if (hackId.startsWith(VANILLA_PREFIX)) {
            val baseId = hackId.removePrefix(VANILLA_PREFIX)
            val rom = baseRomLookup(baseId) ?: return null
            val file = File(rom.path)
            return if (file.exists()) file else null
        }
        return storageRom(hackId)
    }

    /**
     * Android [Context] wrapper used by call sites that already hold a Context.
     * Resolves vanilla ids against the real [AppRepositories.baseRomRepository] and
     * everything else against [Storage.rom].
     */
    fun resolveRomFile(context: Context, hackId: String): File? {
        val repository = AppRepositories.baseRomRepository(context)
        return resolveRomFile(
            hackId = hackId,
            baseRomLookup = { repository.getById(it) },
            storageRom = { Storage.getInstance(context).rom(it) }
        )
    }
}
