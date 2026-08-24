package br.com.redclaw.zelda64player.retroachievements.data

import android.content.Context
import br.com.redclaw.zelda64player.retroachievements.api.RaHttpClient
import br.com.redclaw.zelda64player.retroachievements.jni.RcheevosJni
import br.com.redclaw.zelda64player.repositories.GameRomResolver
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
     *
     * A previously stored game id (e.g. seeded from the catalog) is preserved
     * when the fresh resolution comes back empty, so a transient network
     * failure never downgrades a known identity.
     */
    suspend fun computeAndResolve(hackId: String, romFile: File): RaGameIdentity =
        withContext(Dispatchers.IO) {
            val previous = metadataStore.get(hackId)
            val hash = RcheevosJni.nativeComputeRomHash(romFile.absolutePath)
            if (hash.isBlank()) {
                // Keep any previously known identity; only refresh the hash
                // when we actually produced one.
                val identity = previous?.copy(raHash = "")
                    ?: RaGameIdentity(raHash = "", gameId = 0L, title = null)
                metadataStore.put(hackId, identity)
                return@withContext identity
            }

            val resolvedGameId = resolveGameId(hash)
            val identity = RaGameIdentity(
                raHash = hash,
                gameId = if (resolvedGameId != 0L) resolvedGameId else (previous?.gameId ?: 0L),
                title = previous?.title?.takeIf { resolvedGameId == 0L }
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

    /**
     * Lazily ensures a usable RetroAchievements identity for [hackId], the same
     * way the core identifies a game at launch: by hashing the FINAL playable
     * ROM file (via [GameRomResolver], which resolves vanilla base ROMs and
     * patched hacks identically to [br.com.redclaw.zelda64player.retroview.RetroView]).
     *
     * Pipeline (all blocking work on [Dispatchers.IO]):
     * 1. Read any stored identity for [hackId].
     * 2. Resolve the playable ROM file. When none exists we can only return the
     *    stored identity (which may itself be unresolved) — no hashing possible.
     * 3. Compute the rhash from the ROM file only when we do not already have a
     *    stored hash (avoids re-hashing a 32-64 MB file on every open).
     * 4. Resolve the game id from the hash when still unknown.
     * 5. Persist any improvement, never downgrading a previously known title or
     *    game id (a transient network failure must not erase a good identity).
     *
     * Returns the best identity we could determine, or null when nothing was
     * ever stored and no ROM is available to identify from.
     */
    suspend fun ensureIdentity(context: Context, hackId: String): RaGameIdentity? =
        withContext(Dispatchers.IO) {
            val stored = metadataStore.get(hackId)
            val romFile = GameRomResolver.resolveRomFile(context, hackId)

            // Without a playable ROM we cannot improve on what we already know.
            if (romFile == null) return@withContext stored

            var identity = stored ?: RaGameIdentity(raHash = "", gameId = 0L, title = null)

            // Hash the final ROM only when we lack a stored hash.
            if (identity.raHash.isBlank()) {
                val hash = RcheevosJni.nativeComputeRomHash(romFile.absolutePath)
                if (hash.isBlank()) {
                    // Hashing failed (missing/corrupt ROM): keep prior knowledge.
                    return@withContext stored
                }
                identity = identity.copy(raHash = hash)
            }

            // Resolve the game id when still unknown but we have a hash.
            if (identity.gameId == 0L && identity.raHash.isNotBlank()) {
                val resolved = resolveGameId(identity.raHash)
                if (resolved != 0L) identity = identity.copy(gameId = resolved)
            }

            // Persist only when something improved, never downgrading.
            if (identity != stored) {
                val merged = mergeIdentity(stored, identity)
                metadataStore.put(hackId, merged)
                return@withContext merged
            }
            stored
        }

    /**
     * Merges a freshly computed [computed] identity with the [stored] one,
     * keeping the best value of each field so a transient failure during
     * re-resolution never downgrades a known title or game id.
     */
    private fun mergeIdentity(stored: RaGameIdentity?, computed: RaGameIdentity): RaGameIdentity {
        if (stored == null) return computed
        return RaGameIdentity(
            raHash = computed.raHash.ifBlank { stored.raHash },
            gameId = if (computed.gameId != 0L) computed.gameId else stored.gameId,
            title = computed.title ?: stored.title
        )
    }
}
