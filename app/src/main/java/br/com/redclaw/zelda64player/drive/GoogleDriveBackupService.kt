/*
 * Zelda 64 Player - native Android N64 emulator frontend for Zelda ROM hacks.
 * Copyright (C) 2026 RedClaw
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package br.com.redclaw.zelda64player.drive

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Thin REST client for the Google Drive v3 API, used by the backup feature.
 *
 * The app requests only the `drive.file` scope, so it can only see files it
 * created. All calls go through [OkHttpClient] (already used elsewhere in the
 * app) and are suspended on the IO dispatcher. The class is Android-light: it
 * needs no [android.content.Context] — the auth token is supplied through
 * [tokenProvider] so the network logic stays testable in isolation.
 *
 * @param tokenProvider suspends to return a valid Bearer token (cached by the
 *   caller). Invoked before every request so a refreshed token is always used.
 * @param onAuthFailure invoked when the server rejects the token (HTTP 401);
 *   the caller should invalidate the cached token here so the next
 *   [tokenProvider] call returns a fresh one.
 */
class GoogleDriveBackupService(
    private val tokenProvider: suspend () -> String,
    private val onAuthFailure: () -> Unit = {}
) {
    private val client = OkHttpClient()

    /** Thrown when the Drive API rejects the auth token so callers can retry. */
    class DriveAuthException(message: String) : IOException(message)

    /** Metadata of a remote Drive file (subset we care about). */
    data class DriveFileMeta(
        val id: String,
        val name: String,
        val createdTime: String,
        val modifiedTime: String,
        val mimeType: String,
        val size: Long,
        val appProperties: Map<String, String>
    )

    /** Summary of a backup run. */
    data class BackupSummary(
        val uploaded: Int,
        val deleted: Int,
        val errors: List<String>
    ) {
        val ok: Boolean get() = errors.isEmpty()
    }

    // ---- Folder resolution ----

    /**
     * Return the id of the app backup folder ("Zelda64Player"), creating it on
     * first use. The resolved id is reported through [onFolderResolved] so the
     * caller can persist it (e.g. to open the folder later without a lookup).
     */
    suspend fun ensureAppFolder(onFolderResolved: (String) -> Unit = {}): String {
        val existing = findFolder(APP_FOLDER_NAME, "root")
        if (existing != null) {
            onFolderResolved(existing)
            return existing
        }
        val id = createFolder(APP_FOLDER_NAME, "root")
        onFolderResolved(id)
        return id
    }

    private suspend fun ensureCategoryFolder(parentId: String, name: String): String {
        val existing = findFolder(name, parentId)
        if (existing != null) return existing
        return createFolder(name, parentId)
    }

    private suspend fun findFolder(name: String, parent: String): String? =
        withContext(Dispatchers.IO) {
            val q = "name='${name.replace("'", "\\'")}' and mimeType='$FOLDER_MIME' and trashed=false and '$parent' in parents"
            val url = "$API/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", q)
                .addQueryParameter("spaces", "drive")
                .addQueryParameter("fields", "files(id)")
                .addQueryParameter("pageSize", "1")
                .build()
            val resp = get(url).requireOk()
            val files = JSONObject(resp.body!!.string()).optJSONArray("files") ?: JSONArray()
            if (files.length() == 0) null else files.getJSONObject(0).getString("id")
        }

    private suspend fun createFolder(name: String, parent: String): String =
        withContext(Dispatchers.IO) {
            val meta = JSONObject().apply {
                put("name", name)
                put("mimeType", FOLDER_MIME)
                put("parents", JSONArray().put(parent))
            }
            val req = Request.Builder()
                .url("$API/files")
                .addHeader("Authorization", authHeader(token()))
                .post(meta.toString().toRequestBody(JSON))
                .build()
            val resp = client.newCall(req).execute().requireOk()
            JSONObject(resp.body!!.string()).getString("id")
        }

    // ---- Upload ----

    /**
     * Upload [localFile] to [remotePath] (e.g. `saves/hackId/sram_hackId`).
     * Creates the intermediate category folder as needed. [onProgress] reports
     * bytes written / total. Returns the uploaded file's Drive id.
     *
     * @param appProperties optional custom metadata persisted on the Drive file
     *   (invisible to the user, does not count against quota). Used by the cloud
     *   sync feature to store the content CRC32 + size for conflict detection.
     * @param existingFileId when non-null, the existing Drive file is updated via
     *   PATCH (content + metadata) instead of creating a duplicate. This is how
     *   the sync worker keeps one remote copy per local file.
     */
    suspend fun uploadFile(
        localFile: File,
        remotePath: String,
        appProperties: Map<String, String>? = null,
        existingFileId: String? = null,
        onProgress: (written: Long, total: Long) -> Unit = { _, _ -> }
    ): String = withContext(Dispatchers.IO) {
        val fileName = remotePath.substringAfterLast("/")
        val meta = JSONObject().apply {
            put("name", fileName)
            if (existingFileId == null) {
                val appFolder = ensureAppFolder()
                val parts = remotePath.split("/")
                val category = parts[0]
                val categoryFolder = ensureCategoryFolder(appFolder, category)
                put("parents", JSONArray().put(categoryFolder))
            }
            appProperties?.let { props ->
                val ap = JSONObject()
                props.forEach { (k, v) -> ap.put(k, v) }
                put("appProperties", ap)
            }
        }
        val mediaType = when {
            fileName.endsWith(".png") -> "image/png"
            fileName.endsWith(".mp4") -> "video/mp4"
            else -> "application/octet-stream"
        }.toMediaType()
        val mediaBody = ProgressRequestBody(localFile, mediaType, onProgress)
        val body = MultipartBody.Builder().setType("multipart/related".toMediaType())
            .addPart(MultipartBody.Part.create(meta.toString().toRequestBody(JSON)))
            .addPart(MultipartBody.Part.create(mediaBody))
            .build()
        val url = if (existingFileId == null) {
            "$UPLOAD/files?uploadType=multipart"
        } else {
            "$API/files/$existingFileId?uploadType=multipart"
        }
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", authHeader(token()))
            .method(if (existingFileId == null) "POST" else "PATCH", body)
            .build()
        val resp = client.newCall(req).execute().requireOk()
        JSONObject(resp.body!!.string()).getString("id")
    }

    // ---- List / Download / Delete ----

    /** List every file directly under [folderId] (newest first). */
    suspend fun listFiles(folderId: String): List<DriveFileMeta> = withContext(Dispatchers.IO) {
        val q = "trashed=false and '$folderId' in parents"
        val url = "$API/files".toHttpUrl().newBuilder()
            .addQueryParameter("q", q)
            .addQueryParameter("fields", "files(id,name,createdTime,modifiedTime,mimeType,size,appProperties)")
            .addQueryParameter("orderBy", "createdTime desc")
            .build()
        val resp = get(url).requireOk()
        val arr = JSONObject(resp.body!!.string()).optJSONArray("files") ?: JSONArray()
        (0 until arr.length()).mapNotNull { i ->
            runCatching { parseDriveFileMeta(arr.getJSONObject(i)) }.getOrNull()
        }
    }

    /**
     * Find a single file by [name] directly under [folderId], or null when it
     * does not exist (or is trashed). Returns the full [DriveFileMeta] including
     * `modifiedTime` and `appProperties` so the sync worker can run its conflict
     * decision without a second request.
     */
    suspend fun findFileByName(folderId: String, name: String): DriveFileMeta? =
        withContext(Dispatchers.IO) {
            val q = "name='${name.replace("'", "\\'")}' and trashed=false and '$folderId' in parents"
            val url = "$API/files".toHttpUrl().newBuilder()
                .addQueryParameter("q", q)
                .addQueryParameter("spaces", "drive")
                .addQueryParameter(
                    "fields",
                    "files(id,name,createdTime,modifiedTime,mimeType,size,appProperties)"
                )
                .addQueryParameter("pageSize", "1")
                .build()
            val resp = get(url).requireOk()
            val arr = JSONObject(resp.body!!.string()).optJSONArray("files") ?: JSONArray()
            if (arr.length() == 0) null else runCatching {
                parseDriveFileMeta(arr.getJSONObject(0))
            }.getOrNull()
        }

    /**
     * Resolve the remote copy of a save file for [hackId] (path
     * `saves/<hackId>/<fileName>`), or null when no remote copy exists yet.
     * Uses [findFolder] (no-create) so it never creates empty folders just to
     * check existence.
     */
    suspend fun findSaveFile(hackId: String, fileName: String): DriveFileMeta? =
        withContext(Dispatchers.IO) {
            val appFolder = findFolder(APP_FOLDER_NAME, "root") ?: return@withContext null
            val savesFolder = findFolder("saves", appFolder) ?: return@withContext null
            val hackFolder = findFolder(hackId, savesFolder) ?: return@withContext null
            findFileByName(hackFolder, fileName)
        }

    /** Parse a Drive files resource object into [DriveFileMeta]. */
    private fun parseDriveFileMeta(o: JSONObject): DriveFileMeta {
        val appProps = mutableMapOf<String, String>()
        o.optJSONObject("appProperties")?.let { ap ->
            ap.keys().forEach { key -> appProps[key] = ap.optString(key) }
        }
        return DriveFileMeta(
            id = o.getString("id"),
            name = o.optString("name"),
            createdTime = o.optString("createdTime"),
            modifiedTime = o.optString("modifiedTime"),
            mimeType = o.optString("mimeType"),
            size = o.optLong("size"),
            appProperties = appProps
        )
    }

    /** Download [fileId] to [dest] (overwrites). */
    suspend fun downloadFile(fileId: String, dest: File): Unit = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$API/files/$fileId?alt=media")
            .addHeader("Authorization", authHeader(token()))
            .get()
            .build()
        val resp = client.newCall(req).execute().requireOk()
        dest.outputStream().use { out -> resp.body!!.byteStream().use { it.copyTo(out) } }
    }

    /** Delete [fileId]. */
    suspend fun deleteFile(fileId: String): Unit = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$API/files/$fileId")
            .addHeader("Authorization", authHeader(token()))
            .delete()
            .build()
        client.newCall(req).execute().requireOk()
    }

    /** Web link to open [folderId] in the Drive app / browser. */
    fun folderLink(folderId: String): String =
        "https://drive.google.com/drive/folders/$folderId?usp=drive_link"

    // ---- High-level backup ----

    /**
     * Upload every [items], then prune each category folder to the most recent
     * [keepPerCategory] files. [onItemProgress] reports (done, total) so the UI
     * can show determinate progress.
     */
    suspend fun backup(
        items: List<BackupItem>,
        keepPerCategory: Int = DEFAULT_KEEP,
        onItemProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ): BackupSummary {
        val errors = mutableListOf<String>()
        var uploaded = 0
        items.forEachIndexed { index, item ->
            runCatching {
                uploadFile(item.localFile, item.remotePath)
                uploaded++
            }.onFailure { e -> errors.add("${item.remotePath}: ${e.message}") }
            onItemProgress(index + 1, items.size)
        }
        var deleted = 0
        if (keepPerCategory > 0) {
            runCatching { deleted = prune(keepPerCategory) }
                .onFailure { e -> errors.add("prune: ${e.message}") }
        }
        return BackupSummary(uploaded, deleted, errors)
    }

    /** Keep only the [keepPerCategory] most recent files in each category folder. */
    private suspend fun prune(keepPerCategory: Int): Int {
        val appFolder = ensureAppFolder()
        var removed = 0
        for (category in listOf("saves", "images", "videos")) {
            val folder = findFolder(category, appFolder) ?: continue
            val files = listFiles(folder)
            if (files.size <= keepPerCategory) continue
            files.sortedByDescending { it.createdTime }
                .drop(keepPerCategory)
                .forEach { f ->
                    runCatching { deleteFile(f.id) }.onSuccess { removed++ }
                }
        }
        return removed
    }

    private suspend fun get(url: okhttp3.HttpUrl): Response {
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", authHeader(token()))
            .get()
            .build()
        return client.newCall(req).execute()
    }

    private suspend fun token(): String = tokenProvider()

    private fun authHeader(token: String) = "Bearer $token"

    /**
     * Validate a response: on HTTP 401 notify [onAuthFailure] and throw
     * [DriveAuthException] (so the caller can invalidate + retry); on any other
     * non-2xx throw [IOException] with the body; otherwise return the response.
     */
    private fun Response.requireOk(): Response {
        if (code == 401) {
            onAuthFailure()
            throw DriveAuthException("auth rejected (401)")
        }
        if (!isSuccessful) {
            val detail = runCatching { body?.string() }.getOrNull().orEmpty()
            throw IOException("Drive API error $code: $detail")
        }
        return this
    }

    /**
     * [RequestBody] that reports upload progress via [onProgress]. Wraps the
     * file in a streaming copy so large saves/recordings never sit fully in
     * memory.
     */
    private class ProgressRequestBody(
        private val file: File,
        private val mediaType: okhttp3.MediaType,
        private val onProgress: (written: Long, total: Long) -> Unit
    ) : RequestBody() {
        override fun contentType(): okhttp3.MediaType? = mediaType
        override fun contentLength(): Long = file.length()

        override fun writeTo(sink: BufferedSink) {
            file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                var written = 0L
                var read: Int
                while (input.read(buffer).also { read = it } != -1) {
                    sink.write(buffer, 0, read)
                    written += read
                    onProgress(written, file.length())
                }
            }
        }
    }

    companion object {
        private const val API = "https://www.googleapis.com/drive/v3"
        private const val UPLOAD = "https://www.googleapis.com/upload/drive/v3"
        private const val FOLDER_MIME = "application/vnd.google-apps.folder"
        private const val APP_FOLDER_NAME = "Zelda64Player"
        private val JSON = "application/json; charset=UTF-8".toMediaType()
        const val DEFAULT_KEEP = 10
    }
}
