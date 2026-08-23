package br.com.redclaw.zelda64player.data.local

import br.com.redclaw.zelda64player.patcher.n64.ChecksumCalculator
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Local (on-device) backup and restore of per-hack save data (SRAM and
 * save-states). Exports a single ZIP containing a manifest.json plus, for each
 * installed hack that has save data, its sram/state files. Restore validates
 * each entry's CRC32 against the manifest before overwriting, so a corrupted or
 * tampered archive is rejected file-by-file rather than clobbering good saves.
 *
 * All logic here is Android-free and operates on plain [InputStream]/[OutputStream]
 * (the Activity layer adapts [android.net.Uri] content streams to these
 * methods), so it is fully unit-testable on the JVM.
 */
object SaveBackupManager {

    private const val MANIFEST_ENTRY = "manifest.json"

    /** Summary of a backup or restore operation. */
    data class BackupSummary(
        val hacks: Int,
        val files: Int,
        val skipped: Int,
        val errors: List<String>
    ) {
        val ok: Boolean get() = errors.isEmpty() && skipped == 0
    }

    /** Per-file manifest entry. */
    data class FileEntry(val name: String, val size: Long, val crc32: String)

    /** Per-hack manifest entry. */
    data class HackEntry(val hackId: String, val files: List<FileEntry>)

    /** Top-level manifest written into the ZIP. */
    data class Manifest(
        val appVersion: String,
        val exportDate: String,
        val hacks: List<HackEntry>
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("appVersion", appVersion)
            put("exportDate", exportDate)
            put("hacks", JSONArray().also { arr ->
                hacks.forEach { h ->
                    arr.put(JSONObject().apply {
                        put("hackId", h.hackId)
                        put("files", JSONArray().also { fa ->
                            h.files.forEach { f ->
                                fa.put(JSONObject().apply {
                                    put("name", f.name)
                                    put("size", f.size)
                                    put("crc32", f.crc32)
                                })
                            }
                        })
                    })
                }
            })
        }

        companion object {
            fun fromJson(o: JSONObject): Manifest = Manifest(
                appVersion = o.optString("appVersion", ""),
                exportDate = o.optString("exportDate", ""),
                hacks = (0 until o.getJSONArray("hacks").length()).map { i ->
                    val h = o.getJSONArray("hacks").getJSONObject(i)
                    HackEntry(
                        hackId = h.getString("hackId"),
                        files = (0 until h.getJSONArray("files").length()).map { j ->
                            val f = h.getJSONArray("files").getJSONObject(j)
                            FileEntry(f.getString("name"), f.getLong("size"), f.getString("crc32"))
                        }
                    )
                }
            )
        }
    }

    /**
     * Export the given save files into [output] as a ZIP with a manifest.
     *
     * @param saves map of hackId -> list of save files (typically from
     *   [br.com.redclaw.zelda64player.repositories.Storage.saveFiles]).
     * @param appVersion version string recorded in the manifest for diagnostics.
     */
    fun export(output: OutputStream, saves: Map<String, List<File>>, appVersion: String): BackupSummary {
        val hacks = mutableListOf<HackEntry>()
        val errors = mutableListOf<String>()
        ZipOutputStream(output).use { zip ->
            for ((hackId, files) in saves) {
                val entries = mutableListOf<FileEntry>()
                for (saveFile in files) {
                    if (!saveFile.exists()) continue
                    try {
                        val bytes = saveFile.readBytes()
                        val entryName = "$hackId/${saveFile.name}"
                        zip.putNextEntry(ZipEntry(entryName))
                        zip.write(bytes)
                        zip.closeEntry()
                        entries.add(FileEntry(entryName, bytes.size.toLong(), ChecksumCalculator.crc32(bytes)))
                    } catch (e: Exception) {
                        errors.add("${saveFile.name}: ${e.message}")
                    }
                }
                if (entries.isNotEmpty()) hacks.add(HackEntry(hackId, entries))
            }
            val manifest = Manifest(appVersion, isoNow(), hacks)
            zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
            zip.write(manifest.toJson().toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return BackupSummary(hacks.size, hacks.sumOf { it.files.size }, 0, errors)
    }

    /**
     * Restore saves from [input] (a ZIP produced by [export]), validating each
     * entry's CRC32 against the manifest before writing.
     *
     * @param targetResolver maps a (hackId, fileName) pair to the destination
     *   [File]. Returning null skips that entry. The Activity supplies this from
     *   [br.com.redclaw.zelda64player.repositories.Storage].
     * @return a summary reporting how many hacks/files were restored and how many
     *   were skipped (missing, CRC mismatch, or without a target path).
     */
    fun restore(
        input: InputStream,
        targetResolver: (hackId: String, fileName: String) -> File?
    ): BackupSummary {
        val errors = mutableListOf<String>()
        var restoredHacks = 0
        var restoredFiles = 0
        var skipped = 0
        val fileBytes = mutableMapOf<String, ByteArray>()
        var manifestJson: String? = null

        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == MANIFEST_ENTRY) {
                    manifestJson = zip.bufferedReader(Charsets.UTF_8).readText()
                } else {
                    fileBytes[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        if (manifestJson == null) {
            return BackupSummary(0, 0, 0, listOf("Manifest missing from backup archive"))
        }
        val manifest = try {
            Manifest.fromJson(JSONObject(manifestJson))
        } catch (e: Exception) {
            return BackupSummary(0, 0, 0, listOf("Manifest unreadable: ${e.message}"))
        }

        for (hack in manifest.hacks) {
            var hackRestored = false
            for (fe in hack.files) {
                val bytes = fileBytes[fe.name]
                if (bytes == null) {
                    skipped++
                    errors.add("${fe.name}: missing from archive")
                    continue
                }
                val actualCrc = ChecksumCalculator.crc32(bytes)
                if (actualCrc != fe.crc32) {
                    skipped++
                    errors.add("${fe.name}: CRC32 mismatch (expected ${fe.crc32}, got $actualCrc)")
                    continue
                }
                val (hackId, fileName) = parseEntryName(fe.name)
                val target = targetResolver(hackId, fileName)
                if (target == null) {
                    skipped++
                    errors.add("${fe.name}: no target path")
                    continue
                }
                try {
                    target.parentFile?.mkdirs()
                    target.writeBytes(bytes)
                    restoredFiles++
                    hackRestored = true
                } catch (e: Exception) {
                    errors.add("${fe.name}: ${e.message}")
                }
            }
            if (hackRestored) restoredHacks++
        }
        return BackupSummary(restoredHacks, restoredFiles, skipped, errors)
    }

    /** Split an entry name "<hackId>/<fileName>" into its parts. */
    private fun parseEntryName(entryName: String): Pair<String, String> {
        val idx = entryName.indexOf('/')
        return if (idx < 0) entryName to entryName else entryName.substring(0, idx) to entryName.substring(idx + 1)
    }

    /** Current time as a UTC ISO-8601 string (API 24 compatible). */
    private fun isoNow(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }
}
