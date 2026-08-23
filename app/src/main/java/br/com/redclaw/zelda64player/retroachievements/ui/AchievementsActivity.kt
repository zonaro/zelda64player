package br.com.redclaw.zelda64player.retroachievements.ui

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.Zelda64PlayerApp
import br.com.redclaw.zelda64player.databinding.ActivityAchievementsBinding
import br.com.redclaw.zelda64player.retroachievements.data.RaGameData
import br.com.redclaw.zelda64player.views.InstalledLibrary
import coil.load
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * RetroAchievements screen for one installed hack: game badge, unlock
 * progress and the full achievement list (unlocked first).
 *
 * Data comes from the standalone rapi endpoints (no live session required):
 * the install-time identity supplies the game id, catalog definitions come
 * from fetch-game-data and the user's unlock set from fetch-user-unlocks
 * (only when credentials exist). All network/parse work runs on Dispatchers.IO.
 */
class AchievementsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAchievementsBinding
    private val adapter = RaAchievementAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAchievementsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.achievementsToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.achievementsToolbar.setNavigationOnClickListener { finish() }

        binding.achievementsList.layoutManager = LinearLayoutManager(this)
        binding.achievementsList.adapter = adapter

        val hackId = intent.getStringExtra(EXTRA_HACK_ID)
        if (hackId.isNullOrBlank()) {
            showMessage(R.string.ra_error_untracked)
            return
        }
        load(hackId)
    }

    private fun load(hackId: String) {
        val metadata = Zelda64PlayerApp.raInstallMetadataStore
        val credentials = Zelda64PlayerApp.raCredentialStore
        val repository = Zelda64PlayerApp.raCatalogRepository

        lifecycleScope.launch {
            val identity = withContext(Dispatchers.IO) { metadata.get(hackId) }
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

    /** Installed-hack display name for the header fallback. */
    private fun hackTitle(hackId: String): String =
        InstalledLibrary.entries(this).firstOrNull { it.id == hackId }?.title.orEmpty()

    private fun showMessage(resId: Int) {
        binding.achievementsHeader.visibility = View.GONE
        binding.achievementsMessage.setText(resId)
        binding.achievementsMessage.visibility = View.VISIBLE
    }

    private companion object {
        const val EXTRA_HACK_ID = "hack_id"
    }
}
