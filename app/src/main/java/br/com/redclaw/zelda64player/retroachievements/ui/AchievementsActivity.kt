package br.com.redclaw.zelda64player.retroachievements.ui

import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.Zelda64PlayerApp
import br.com.redclaw.zelda64player.databinding.ActivityAchievementsBinding
import br.com.redclaw.zelda64player.retroachievements.data.RaGameData
import br.com.redclaw.zelda64player.retroachievements.data.RaGameIdentity
import br.com.redclaw.zelda64player.ui.switchui.SwitchImmersive
import br.com.redclaw.zelda64player.ui.switchui.SwitchBackButton
import br.com.redclaw.zelda64player.views.InstalledLibrary
import coil.load
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * RetroAchievements screen, opened in one of two modes:
 *
 *  - Single game (EXTRA_HACK_ID present): shows only the achievements of that
 *    installed hack, identified the same way the core identifies a game at
 *    launch — by hashing the FINAL playable ROM (see RaHashService.ensureIdentity).
 *    The per-game header card is shown.
 *
 *  - All games (no extra): iterates every installed Library entry, resolves each
 *    identity lazily, de-duplicates by RA game id and renders one section per
 *    tracked game with its achievement rows beneath. The big per-game header
 *    card is hidden in this mode because each section carries its own header
 *    (cleaner than an aggregate total and zero extra layout work). Games that
 *    cannot be identified are silently skipped; a network failure on one game
 *    never aborts the others.
 *
 * Data comes from the standalone rapi endpoints (no live session required):
 * the identity supplies the game id, catalog definitions come from
 * fetch-game-data and the user's unlock set from fetch-user-unlocks (only when
 * credentials exist). All network/parse work runs on Dispatchers.IO.
 */
class AchievementsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAchievementsBinding
    private val adapter = RaAchievementAdapter()

    private val backHelper = SwitchBackButton()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAchievementsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SwitchImmersive.enterFullscreen(this)

        setSupportActionBar(binding.achievementsToolbar)
        backHelper.attach(this, binding.achievementsBack.root, onBack = { finish() })

        binding.achievementsList.layoutManager = LinearLayoutManager(this)
        binding.achievementsList.adapter = adapter

        val hackId = intent.getStringExtra(EXTRA_HACK_ID)
        if (hackId.isNullOrBlank()) {
            supportActionBar?.setTitle(R.string.achievements_title_all)
            loadAllGames()
        } else {
            supportActionBar?.setTitle(R.string.achievements_title)
            loadSingle(hackId)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) SwitchImmersive.enterFullscreen(this)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        backHelper.onTouch(ev)
        return super.dispatchTouchEvent(ev)
    }

    // --- Single game mode -------------------------------------------------

    private fun loadSingle(hackId: String) {
        val credentials = Zelda64PlayerApp.raCredentialStore
        val repository = Zelda64PlayerApp.raCatalogRepository

        lifecycleScope.launch {
            // Lazily (re)resolve identity from the final playable ROM, exactly as
            // the core does at launch. Falls back to stored metadata when no ROM
            // is present.
            val identity = withContext(Dispatchers.IO) {
                Zelda64PlayerApp.raHashService.ensureIdentity(this@AchievementsActivity, hackId)
            }
            if (identity == null || !identity.isResolved) {
                showMessage(R.string.ra_error_untracked)
                return@launch
            }

            showMessage(R.string.ra_loading)

            val gameData = withContext(Dispatchers.IO) {
                repository.fetchGameData(identity.gameId)
            }
            if (gameData == null) {
                showMessage(R.string.ra_error_network)
                return@launch
            }

            val unlockedIds = withContext(Dispatchers.IO) {
                val username = credentials.getUsername().orEmpty()
                val token = credentials.getToken().orEmpty()
                repository.fetchUserUnlocks(
                    gameId = identity.gameId,
                    username = username,
                    apiToken = token,
                    hardcore = false
                )
            }

            render(gameData, unlockedIds, hackTitle(hackId))
        }
    }

    /** Renders header + rows; falls back to the installed title when offline. */
    private fun render(gameData: RaGameData, unlockedIds: Set<Long>, fallbackTitle: String) {
        binding.achievementsMessage.visibility = View.GONE
        binding.achievementsHeader.visibility = View.VISIBLE

        val title = gameData.title.ifBlank { fallbackTitle }
        binding.achievementsGameTitle.text = title

        val unlockedCount = gameData.achievements.count { it.id in unlockedIds }
        val totalPoints = gameData.achievements.sumOf { it.points }
        val earnedPoints =
            gameData.achievements.filter { it.id in unlockedIds }.sumOf { it.points }
        binding.achievementsProgressSummary.text = getString(
            R.string.ra_progress_summary,
            unlockedCount,
            gameData.achievements.size,
            earnedPoints,
            totalPoints
        )

        if (gameData.imageUrl != null) {
            binding.achievementsGameBadge.load(gameData.imageUrl) { crossfade(true) }
        }

        // Unlocked first (rarest last), then locked alphabetically.
        val rows = gameData.achievements
            .map { RaAchievementRow(it, it.id in unlockedIds) }
            .sortedWith(
                compareByDescending<RaAchievementRow> { it.unlocked }
                    .thenBy { it.def.title.lowercase() }
            )
        adapter.submitList(rows)
        if (rows.isEmpty()) {
            showMessage(R.string.ra_empty)
        }
    }

    // --- All games mode ---------------------------------------------------

    private fun loadAllGames() {
        val credentials = Zelda64PlayerApp.raCredentialStore
        val repository = Zelda64PlayerApp.raCatalogRepository

        // The per-game header card is hidden; each section carries its own header.
        binding.achievementsHeader.visibility = View.GONE
        showMessage(R.string.ra_loading)

        lifecycleScope.launch {
            val entries = withContext(Dispatchers.IO) {
                InstalledLibrary.entries(this@AchievementsActivity)
            }

            // Lazily resolve every entry's identity (mupen-style: hash the final
            // playable ROM). Best-effort per entry; unresolved ones are skipped.
            val identities = withContext(Dispatchers.IO) {
                buildMap<String, RaGameIdentity> {
                    for (entry in entries) {
                        val identity = Zelda64PlayerApp.raHashService
                            .ensureIdentity(this@AchievementsActivity, entry.romId)
                        if (identity != null) put(entry.romId, identity)
                    }
                }
            }

            val resolvedByGame = collectResolvedGames(entries, identities)
            if (resolvedByGame.isEmpty()) {
                showMessage(R.string.ra_all_empty)
                return@launch
            }

            val username = credentials.getUsername().orEmpty()
            val token = credentials.getToken().orEmpty()

            val loadedGames = mutableListOf<GameAchievements>()
            for ((gameId, fallbackTitle) in resolvedByGame) {
                // A failure on one game must not abort the others.
                val gameData = runCatching {
                    withContext(Dispatchers.IO) { repository.fetchGameData(gameId) }
                }.getOrNull() ?: continue
                val unlockedIds = runCatching {
                    withContext(Dispatchers.IO) {
                        repository.fetchUserUnlocks(
                            gameId = gameId,
                            username = username,
                            apiToken = token,
                            hardcore = false
                        )
                    }
                }.getOrDefault(emptySet())
                loadedGames += GameAchievements(gameId, fallbackTitle, gameData, unlockedIds)
            }

            if (loadedGames.isEmpty()) {
                // At least one game was identified, but every catalog fetch failed.
                showMessage(R.string.ra_error_network)
                return@launch
            }

            val rows = buildSectionedRows(loadedGames)
            binding.achievementsMessage.visibility = View.GONE
            adapter.submitList(rows)
        }
    }

    /** Installed-hack display name for the header fallback. */
    private fun hackTitle(hackId: String): String =
        InstalledLibrary.entries(this).firstOrNull { it.id == hackId }?.title.orEmpty()

    private fun showMessage(resId: Int) {
        binding.achievementsHeader.visibility = View.GONE
        binding.achievementsMessage.setText(resId)
        binding.achievementsMessage.visibility = View.VISIBLE
    }

    companion object {
        /** Intent extra carrying the installed hack id to display. */
        const val EXTRA_HACK_ID = "hack_id"
    }
}
