package br.com.redclaw.zelda64player.store

import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Extracts a single named entry from a ZIP archive. Used when a catalog patch is
 * distributed as a `.zip` containing the `.bps` file (the [PatchRef.filename]).
 *
 * Pure (no Android deps) so it can be unit-tested with an in-memory ZIP.
 */
object ZipExtractor {
    /** Extract [entryName] from an in-memory ZIP byte array. */
    fun extractEntry(zipBytes: ByteArray, entryName: String): ByteArray =
        extractBytes(ByteArrayInputStream(zipBytes), entryName)

    /** Extract [entryName] from a ZIP file on disk. */
    fun extractEntry(zipFile: File, entryName: String): ByteArray =
        extractBytes(zipFile.inputStream(), entryName)

    private fun extractBytes(stream: InputStream, entryName: String): ByteArray {
        ZipInputStream(stream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (entry.name == entryName && !entry.isDirectory) {
                    return zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        throw StoreException.GenericError("Entry '$entryName' not found in archive")
    }
}
