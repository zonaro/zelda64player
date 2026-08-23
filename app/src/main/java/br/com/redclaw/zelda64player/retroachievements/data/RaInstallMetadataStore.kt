package br.com.redclaw.zelda64player.retroachievements.data

import android.content.Context
import br.com.redclaw.zelda64player.retroachievements.api.RaHttpClient
import br.com.redclaw.zelda64player.retroachievements.jni.RcheevosJni
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * RetroAchievements identity of one installed hack, resolved at install time.
 *
 * @param raHash The rhash-generated hash of the final patched ROM. This is the
 *   value the RA database indexes; it is computed ONLY from the patched ROM
 *   written to Storage.rom(hackId), never from a base ROM or intermediate.
 * @param gameId RA game id, or 0 when the hash is not yet known to the
 *   database (e.g. brand new hacks) — achievements stay unavailable but the
 *   entry is kept so later installs can retry resolution.
 * @param title Game title as reported by the RA database, or null when
 *   unresolved.
 */
data class RaGameIdentity(
    val raHash: String,
    val gameId: Long,
    val title: String?
) {
    val isResolved: Boolean get() = gameId != 0L

    fun toJson(): JSONObject = JSONObject()
        .put(KEY_HASH, raHash)
        .put(KEY_GAME_ID, gameId)
        .put(KEY_TITLE, title ?: JSONObject.NULL)

    companion object {
        const val KEY_HASH = "raHash"
        const val KEY_GAME_ID = "gameId"
        const val KEY_TITLE = "title"

        fun fromJson(json: JSONObject): RaGameIdentity = RaGameIdentity(
            raHash = json.optString(KEY_HASH),
            gameId = json.optLong(KEY_GAME_ID, 0L),
            title = if (json.isNull(KEY_TITLE)) null else json.optString(KEY_TITLE)
        )
    }
}

/**
 * Persists per-hack [RaGameIdentity] records as a single JSON document in the
 * durable files dir (`filesDir/ra_metadata.json`), keyed by hackId. Small,
 * read-mostly data; loaded lazily and rewritten atomically on change.
 *
 * @param context Application context used for the files dir.
 */
class RaInstallMetadataStore(private val context: Context) {

    private val file: File
        get() = File(context.applicationContext.filesDir, FILE_NAME)

    @Volatile
    private var cache: MutableMap<String, RaGameIdentity>? = null

    private fun load(): MutableMap<String, RaGameIdentity> {
        cache?.let { return it }
        synchronized(this) {
            cache?.let { return it }
            val map = mutableMapOf<String, RaGameIdentity>()
            try {
                if (file.exists()) {
                    val root = JSONObject(file.readText())
                    val keys = root.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        map[key] = RaGameIdentity.fromJson(root.getJSONObject(key))
                    }
                }
            } catch (_: Exception) {
                // Corrupt or unreadable metadata is treated as empty; entries
                // are recomputed on next install/launch.
            }
            cache = map
            return map
        }
    }

    private fun persist(map: Map<String, RaGameIdentity>) {
        try {
            val root = JSONObject()
            for ((key, value) in map) {
                root.put(key, value.toJson())
            }
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(root.toString())
            if (!tmp.renameTo(file)) {
                file.writeText(root.toString())
                tmp.delete()
            }
        } catch (_: Exception) {
            // Persistence failures degrade gracefully: identities are cached
            // in memory and recomputed on a future install.
        }
    }

    /** Returns the stored identity for [hackId], or null when never computed. */
    fun get(hackId: String): RaGameIdentity? = load()[hackId]

    /** Stores (or replaces) the identity for [hackId]. */
    fun put(hackId: String, identity: RaGameIdentity) {
        synchronized(this) {
            load()[hackId] = identity
            persist(load())
        }
    }

    /** Removes the identity for [hackId] (uninstall cleanup). */
    fun remove(hackId: String) {
        synchronized(this) {
            load().remove(hackId)
            persist(load())
        }
    }

    private companion object {
        const val FILE_NAME = "ra_metadata.json"
    }
}

/**
 * Computes and resolves the RetroAchievements identity of an installed ROM.
 *
 * Pipeline (all blocking work on Dispatchers.IO):
 * 1. Hash the final patched ROM with rhash ([RcheevosJni.nativeComputeRomHash]).
 * 2. Ask the RA database which game the hash belongs to, using the standalone
 *   resolve-hash rapi helpers (no credentials required, no live session).
 *
 * Called by the install pipeline right after the patched ROM is published and
 * opportunistically at launch time when a previous attempt failed.
 */
class RaHashService(
    private val http: RaHttpClient,
    private val metadataStore: RaInstallMetadataStore
) {

    /**
     * Computes the RA hash of [romFile], resolves its game id and persists the
     * result under [hackId]. Returns the stored identity; [RaGameIdentity.gameId]
     * is 0 when hashing failed or the server does not know the hash yet.
     */
    suspend fun computeAndResolve(hackId: String, romFile: File): RaGameIdentity =
        withContext(Dispatchers.IO) {
            val hash = RcheevosJni.nativeComputeRomHash(romFile.absolutePath)
            if (hash.isBlank()) {
                return@withContext RaGameIdentity(raHash = "", gameId = 0L, title = null)
                    .also { metadataStore.put(hackId, it) }
            }

            val gameId = resolveGameId(hash)
            val identity = RaGameIdentity(
                raHash = hash,
                gameId = gameId,
                title = null // Title arrives with session game info; not needed offline.
            )
            metadataStore.put(hackId, identity)
            identity
        }

    /**
     * Resolves [raHash] against the RA database. Returns the game id, or 0
     * when unmatched/unavailable.
     */
    suspend fun resolveGameId(raHash: String): Long {
        val requestParts = RcheevosJni.nativeBuildResolveHashRequest(raHash) ?: return 0L
        val url = requestParts.getOrNull(0) ?: return 0L
        val postData = requestParts.getOrNull(1)

        val response = http.execute(url, postData)
        if (!response.isSuccessful) return 0L
        val body = response.bodyAsString() ?: return 0L
        return RcheevosJni.nativeProcessResolveHashResponse(body)
    }
}
