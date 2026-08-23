package br.com.redclaw.zelda64player.shortcuts

import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.views.GameActivity
import br.com.redclaw.zelda64player.views.HackLibraryEntry
import coil.Coil
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Owns the Android App Shortcuts lifecycle for installed games. Its
 * responsibilities (and only these) are:
 *
 *  - sync dynamic shortcuts to the currently installed games, ranked by
 *    most-recently-played and truncated to the launcher's max shortcut count;
 *  - publish/update a single game's shortcut right after it is installed;
 *  - request pinning a game's shortcut to the home screen (API 26+);
 *  - disable pinned shortcuts whose game is no longer installed (and re-enable
 *    them if the game returns), so a stale pin never launches a missing ROM.
 *
 * Built on [ShortcutManagerCompat] so everything below API 25 is a safe no-op:
 * dynamic shortcuts simply do not appear and pinning reports as unsupported.
 *
 * All shortcut work is performed asynchronously on a shared, application-lifetime
 * background [scope]. Callers may invoke [sync], [publishOrUpdate] and
 * [requestPin] freely from the main thread: none of them block the calling
 * thread. The shared [syncMutex] serializes the two full-sync paths
 * ([sync] and the internal reconcile) so a cold-start [sync] and an
 * Activity.onResume [sync] cannot interleave and corrupt the shortcut set.
 *
 * @param context any context; stored as [Context.getApplicationContext] so the
 *   manager never leaks an Activity.
 * @param playHistory recency store used to rank dynamic shortcuts.
 */
class GameShortcutsManager(
    context: Context,
    private val playHistory: GamePlayHistoryStore
) {
    private val context = context.applicationContext

    /**
     * Full sync of dynamic shortcuts from the given installed [entries].
     * Ranks by recency (most recent first), truncates to the launcher limit,
     * and disables/enables pinned shortcuts to match the install state.
     *
     * This call is fire-and-forget: the ranking, icon loading and shortcut
     * reconciliation happen asynchronously on a background dispatcher and never
     * block the calling (e.g. main) thread. Safe to call from
     * [android.app.Application.onCreate] or an Activity lifecycle callback.
     */
    fun sync(entries: List<HackLibraryEntry>) {
        scope.launch {
            syncMutex.withLock { syncInternal(entries) }
        }
    }

    /**
     * Publish or update the dynamic shortcut for a single [entry] (e.g. right
     * after a successful install). The launcher automatically drops the
     * lowest-ranked dynamic shortcut if the limit would be exceeded.
     *
     * Fire-and-forget: the shortcut build and reconciliation run asynchronously
     * on a background dispatcher and never block the calling thread.
     */
    fun publishOrUpdate(entry: HackLibraryEntry) {
        scope.launch { publishOrUpdateInternal(entry) }
    }

    /** Record that [hackId] was just played; used to re-rank shortcuts. */
    fun markPlayed(hackId: String) {
        playHistory.markPlayed(hackId)
    }

    /**
     * Request the launcher to pin [entry] to the home screen. Returns false
     * (and the caller should show a localized explanation) when the device or
     * launcher does not support pinning (below API 26 or an unsupported
     * launcher). When pinning is supported this method returns true
     * immediately; the actual shortcut build and pin request are performed
     * asynchronously on a background dispatcher and therefore do NOT block the
     * calling thread. The caller's toast/feedback should treat a `true` result
     * as "pinning was offered to the launcher", not as a guarantee the user
     * accepted it.
     */
    fun requestPin(entry: HackLibraryEntry): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        scope.launch {
            val info = buildShortcut(entry, rank = 0)
            runCatching {
                ShortcutManagerCompat.requestPinShortcut(context, info, null)
            }
        }
        return true
    }

    /**
     * Disable pinned shortcuts whose game is no longer in [entries], and
     * re-enable those that are. Defensive: there is no uninstall flow yet, but
     * this keeps pins correct the moment one exists.
     */
    private suspend fun reconcilePinned(entries: List<HackLibraryEntry>) {
        val installedIds = entries.map { it.id }.toSet()
        val pinned = runCatching {
            ShortcutManagerCompat.getShortcuts(context, ShortcutManagerCompat.FLAG_MATCH_PINNED)
        }.getOrDefault(emptyList())
        if (pinned.isEmpty()) return

        val toDisable = mutableListOf<String>()
        val toEnable = mutableListOf<ShortcutInfoCompat>()
        for (info in pinned) {
            val hackId = info.id.removePrefix(SHORTCUT_ID_PREFIX)
            if (hackId == info.id) continue // not one of ours
            if (installedIds.contains(hackId)) toEnable.add(info)
            else toDisable.add(info.id)
        }
        if (toDisable.isNotEmpty()) {
            runCatching {
                ShortcutManagerCompat.disableShortcuts(
                    context,
                    toDisable,
                    context.getString(R.string.shortcut_disabled_not_installed)
                )
            }
        }
        if (toEnable.isNotEmpty()) {
            runCatching { ShortcutManagerCompat.enableShortcuts(context, toEnable) }
        }
    }

    /** Build a [ShortcutInfoCompat] for [entry] with the given [rank]. */
    private suspend fun buildShortcut(entry: HackLibraryEntry, rank: Int): ShortcutInfoCompat {
        val uri = Uri.parse("$SHORTCUT_SCHEME${entry.id}")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setClass(context, GameActivity::class.java)
            putExtra(EXTRA_HACK_ID, entry.id)
        }
        val longLabel = context.getString(R.string.shortcut_long_label_play, entry.title)
        return ShortcutInfoCompat.Builder(context, "$SHORTCUT_ID_PREFIX${entry.id}")
            .setShortLabel(entry.title)
            .setLongLabel(longLabel)
            .setIcon(buildIcon(entry))
            .setIntent(intent)
            .setRank(rank)
            .build()
    }

    /**
     * Icon strategy: prefer the game's cover art, but ONLY from Coil's local
     * disk/memory cache (network disabled) so we never block on a download.
     * Coil's [coil.ImageLoader.execute] is a suspending, main-safe call, so it
     * is invoked directly from this suspend function (which always runs on a
     * background dispatcher via the shared [scope]). Cover images are small
     * (a few KB) and cache hits decode quickly; if the cover is not cached or
     * fails to load, fall back to the app's adaptive launcher icon.
     */
    private suspend fun buildIcon(entry: HackLibraryEntry): IconCompat {
        val cover = entry.coverUrl
        if (cover != null) {
            runCatching {
                val request = ImageRequest.Builder(context)
                    .data(cover)
                    .networkCachePolicy(CachePolicy.DISABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .size(Size(256, 256))
                    .build()
                val result = Coil.imageLoader(context).execute(request)
                val drawable = (result as? SuccessResult)?.drawable as? BitmapDrawable
                if (drawable != null) return IconCompat.createWithBitmap(drawable.bitmap)
            }
        }
        return IconCompat.createWithResource(context, R.mipmap.ic_launcher)
    }

    private suspend fun syncInternal(entries: List<HackLibraryEntry>) {
        val rankedIds = playHistory.recencyRanked(entries.map { it.id })
        val byId = entries.associateBy { it.id }
        val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).coerceAtLeast(0)
        val shortcuts = rankedIds.mapNotNull { byId[it] }
            .take(max)
            .mapIndexed { index, entry -> buildShortcut(entry, rank = index) }
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
        reconcilePinned(entries)
    }

    private suspend fun publishOrUpdateInternal(entry: HackLibraryEntry) {
        runCatching {
            ShortcutManagerCompat.pushDynamicShortcut(context, buildShortcut(entry, rank = 0))
        }
        reconcilePinned(listOf(entry))
    }

    companion object {
        /**
         * Application-lifetime scope for all shortcut work. Lives on the
         * companion so it survives the throwaway [GameShortcutsManager]
         * instances that callers construct on every invocation (e.g. each
         * Activity.onResume). A [SupervisorJob] ensures a failure in one
         * shortcut operation does not cancel the others.
         */
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        /**
         * Serializes the full-sync path so concurrent [sync] calls (cold start
         * vs. Activity.onResume) cannot interleave and corrupt the dynamic
         * shortcut set.
         */
        private val syncMutex = Mutex()

        /** Prefix for all game shortcut ids, keeping them namespaced/unique. */
        const val SHORTCUT_ID_PREFIX = "game_"

        /** URI scheme+authority giving each shortcut a unique data URI. */
        const val SHORTCUT_SCHEME = "zelda64player://game/"

        /** Extra mirroring GameActivity's existing launch contract. */
        const val EXTRA_HACK_ID = "hack_id"
    }
}
