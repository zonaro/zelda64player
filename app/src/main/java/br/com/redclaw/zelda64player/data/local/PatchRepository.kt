package br.com.redclaw.zelda64player.data.local

import java.io.File

/**
 * Interim local patch store (Phase 1). Patches are placed manually in
 * `<externalFilesDir>/patches/<hackId>.bps`; this repository lists them and
 * provides copy/delete helpers. Phase 2 will add catalog-driven download and
 * replace the storage location with `cacheDir/patches/`.
 *
 * Takes an explicit directory so it is unit-testable on the JVM.
 */
class PatchRepository(private val patchesDir: File) {
    init {
        patchesDir.mkdirs()
    }

    /** The directory where patch files are stored (exposed for temp files). */
    val directory: File get() = patchesDir

    /** All hack ids available locally, derived from `<hackId>.bps` filenames. */
    fun listHackIds(): List<String> =
        patchesDir.listFiles { f -> f.isFile && f.extension.equals("bps", ignoreCase = true) }
            ?.map { it.nameWithoutExtension }
            ?.sorted()
            ?: emptyList()

    /** The local patch file for [hackId], or null if not present. */
    fun getPatchFile(hackId: String): File? {
        val file = File(patchesDir, "$hackId.bps")
        return if (file.exists()) file else null
    }

    /** Delete the local patch for [hackId]. Returns true if something was removed. */
    fun delete(hackId: String): Boolean = getPatchFile(hackId)?.delete() ?: false

    /** Copy an external patch file into the local store under [hackId]. */
    fun copyPatch(src: File, hackId: String): Result<File> = runCatching {
        val dest = File(patchesDir, "$hackId.bps")
        src.inputStream().use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        dest
    }
}
