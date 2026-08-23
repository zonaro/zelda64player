package br.com.redclaw.zelda64player.store

import br.com.redclaw.zelda64player.data.local.InstalledHacksRepository
import br.com.redclaw.zelda64player.data.local.PatchRepository
import br.com.redclaw.zelda64player.data.model.HackEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Streaming download of a hack's patch (BPS, possibly inside a `.zip`) with
 * checksum validation. On success the patch is stored via [patchRepository]
 * under `<hackId>.bps` and the installed state is recorded in
 * [installedRepository] so the Library/Store can show install status.
 *
 * The network layer is thin and the [OkHttpClient] is injected for testability;
 * the validation/extraction logic lives in [PatchValidator] and [ZipExtractor].
 */
class DownloadManager(
    private val client: OkHttpClient,
    private val patchRepository: PatchRepository,
    private val installedRepository: InstalledHacksRepository
) {
    /**
     * Download and install [hack]. [onProgress] reports bytes downloaded and the
     * expected total (from [HackEntry.patch.size] or the HTTP Content-Length).
     * Returns the installed patch file on success.
     */
    suspend fun download(
        hack: HackEntry,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val patch = hack.patch
            val tempArchive = File(patchRepository.directory, "${hack.id}.tmp")
            val bpsTemp = File(patchRepository.directory, "${hack.id}.bps.tmp")
            try {
                val request = Request.Builder().url(patch.url).build()
                val response = client.newCall(request).execute()
                try {
                    if (!response.isSuccessful) {
                        throw StoreException.NetworkError("HTTP ${response.code}")
                    }
                    val total = response.body?.contentLength()?.takeIf { it > 0 } ?: patch.size
                    var downloaded = 0L
                    response.body!!.byteStream().use { input ->
                        tempArchive.outputStream().use { output ->
                            val buf = ByteArray(64 * 1024)
                            var read: Int
                            while (input.read(buf).also { read = it } != -1) {
                                output.write(buf, 0, read)
                                downloaded += read
                                onProgress(downloaded, total)
                            }
                        }
                    }

                    // Resolve the actual BPS bytes (extract from the zip if needed).
                    val bpsBytes = if (patch.url.endsWith(".zip", ignoreCase = true)) {
                        ZipExtractor.extractEntry(tempArchive, patch.filename)
                    } else {
                        tempArchive.readBytes()
                    }

                    // Validate before touching the installed store.
                    PatchValidator.validate(bpsBytes, patch.checksums)
                        .onFailure { throw it }

                    bpsTemp.writeBytes(bpsBytes)
                    val dest = patchRepository.copyPatch(bpsTemp, hack.id).getOrThrow()
                    installedRepository.markInstalled(hack.id, hack.version, patch.filename)
                    dest
                } finally {
                    response.close()
                }
            } finally {
                tempArchive.delete()
                bpsTemp.delete()
            }
        }
    }
}
