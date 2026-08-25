package br.com.redclaw.zelda64player.data.local

import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.InputStream

/**
 * Owns cover files selected for manually imported hacks. Files are copied into
 * app storage instead of retaining a SAF URI permission, so a cover survives
 * provider changes and process restarts. Store catalog artwork never uses this
 * repository.
 */
class UserHackCoverRepository(private val directory: File) {
    init {
        directory.mkdirs()
    }

    fun replace(hackId: String, input: InputStream): Result<String> = runCatching {
        val target = File(directory, "$hackId.cover")
        val temp = File(directory, "$hackId.cover.tmp")
        try {
            input.use { source ->
                temp.outputStream().use { destination ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var copied = 0L
                    while (true) {
                        val count = source.read(buffer)
                        if (count < 0) break
                        copied += count
                        require(copied <= MAX_COVER_BYTES) { "Cover image is too large" }
                        destination.write(buffer, 0, count)
                    }
                }
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temp.absolutePath, bounds)
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid cover image" }
            if (!temp.renameTo(target)) {
                temp.inputStream().use { source -> target.outputStream().use(source::copyTo) }
                temp.delete()
            }
            Uri.fromFile(target).toString()
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    fun remove(hackId: String) {
        File(directory, "$hackId.cover").delete()
    }

    companion object {
        private const val BUFFER_SIZE = 32 * 1024
        private const val MAX_COVER_BYTES = 10L * 1024L * 1024L
    }
}
