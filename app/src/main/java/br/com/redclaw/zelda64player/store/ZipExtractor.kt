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

    /**
     * Extract the first entry whose name matches [regex] (case-insensitive).
     * Used when a catalog only declares a `.zip` archive without the inner patch
     * filename: we pick the first `*.bps`/`*.ips`/`*.xdelta` inside it.
     */
    fun extractFirstMatching(zipBytes: ByteArray, regex: String): ByteArray =
        extractFirstMatching(ByteArrayInputStream(zipBytes), regex)

    fun extractFirstMatching(zipFile: File, regex: String): ByteArray =
        extractFirstMatching(zipFile.inputStream(), regex)

    private fun extractFirstMatching(stream: InputStream, regex: String): ByteArray {
        val pattern = Regex(regex, RegexOption.IGNORE_CASE)
        ZipInputStream(stream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && pattern.matches(entry.name)) {
                    return zis.readBytes()
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        throw StoreException.GenericError("No entry matching '$regex' found in archive")
    }
}
