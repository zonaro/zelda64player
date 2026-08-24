package br.com.redclaw.zelda64player.randomizer

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Extracts a Nintendo 64 ROM (.z64/.n64) from a user-supplied ZIP archive
 * without loading the whole archive or the ROM into the heap: the matching
 * entry is streamed entry-by-entry and copied in small chunks.
 *
 * This is used when the user imports a `.zip` that contains their vanilla OoT
 * ROM; the extracted file is then served to the ootrandomizer.com WebView via
 * [RomFileProvider] so it can be supplied to the site's ROM file input.
 */
object RomZipExtractor {

    private val ROM_EXTENSIONS = setOf("z64", "n64")

    /**
     * Find the first entry whose name ends with `.z64` or `.n64`
     * (case-insensitive) and stream it into [outDir].
     *
     * @return the extracted [File], or `null` when no ROM entry was found.
     */
    fun extractZ64(zipFile: File, outDir: File): File? {
        if (!zipFile.exists()) return null
        outDir.mkdirs()
        ZipInputStream(FileInputStream(zipFile)).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val name = entry.name
                val lower = name.lowercase()
                val isRom = !entry.isDirectory &&
                    ROM_EXTENSIONS.any { lower.endsWith(".$it") }
                if (isRom) {
                    val outFile = File(outDir, sanitize(name))
                    FileOutputStream(outFile).use { out -> zis.copyTo(out) }
                    return outFile
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return null
    }

    /** Keep only the file name (strip path separators) to avoid zip-slip. */
    private fun sanitize(name: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\')
        return if (base.isBlank()) "rom.z64" else base
    }
}
