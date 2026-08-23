package br.com.redclaw.zelda64player.repositories

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Fixed ZIP entry names for the two per-game save files. */
const val SAVE_ENTRY_SRAM = "sram.bin"

/** Fixed ZIP entry names for the two per-game save files. */
const val SAVE_ENTRY_STATE = "state.bin"

/**
 * Pure, JVM-testable helpers for exporting and importing a single game's save
 * files (SRAM and save-state) as a ZIP archive.
 *
 * Only the two well-known entry names ([SAVE_ENTRY_SRAM], [SAVE_ENTRY_STATE])
 * are ever read or written, so a malicious or malformed archive cannot cause
 * path traversal or write outside the caller-provided target files: the import
 * maps each known name onto the current hack's [Storage] paths and ignores
 * every other entry.
 *
 * No Android dependencies: every function takes explicit [File] / stream
 * parameters so it can be exercised directly on the JVM in unit tests.
 */
object SaveBackupManager {

    /**
     * Write [sram] and [state] into [out] as a ZIP. Entries whose source file
     * does not exist are skipped, so exporting a game that only has one save
     * type still produces a valid archive. Never throws for missing inputs.
     */
    fun exportToStream(out: OutputStream, sram: File, state: File) {
        ZipOutputStream(out).use { zip ->
            if (sram.exists()) writeEntry(zip, SAVE_ENTRY_SRAM, sram)
            if (state.exists()) writeEntry(zip, SAVE_ENTRY_STATE, state)
        }
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, file: File) {
        zip.putNextEntry(ZipEntry(name))
        file.inputStream().use { it.copyTo(zip) }
        zip.closeEntry()
    }

    /**
     * Result of [importFromStream].
     *
     * @param ok true when at least one known save entry was imported.
     * @param files number of save files written.
     */
    data class ImportResult(val ok: Boolean, val files: Int)

    /**
     * Read [input] as a ZIP and restore [SAVE_ENTRY_SRAM] / [SAVE_ENTRY_STATE]
     * into [sramTarget] / [stateTarget]. Entries with any other name are
     * ignored (no traversal possible). If the archive contains neither known
     * entry, [ImportResult.ok] is false and nothing is written. The caller
     * passes the resolved target files (e.g. [Storage.sram]/[Storage.state] for
     * the current hack id).
     */
    fun importFromStream(input: InputStream, sramTarget: File, stateTarget: File): ImportResult {
        var files = 0
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when (entry.name) {
                    SAVE_ENTRY_SRAM -> {
                        sramTarget.outputStream().use { zip.copyTo(it) }
                        files++
                    }
                    SAVE_ENTRY_STATE -> {
                        stateTarget.outputStream().use { zip.copyTo(it) }
                        files++
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        return ImportResult(ok = files > 0, files = files)
    }
}
