package br.com.redclaw.zelda64player.retroachievements.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import br.com.redclaw.zelda64player.R
import br.com.redclaw.zelda64player.databinding.ActivityRaProfileBinding
import br.com.redclaw.zelda64player.retroachievements.data.RaLastGame
import br.com.redclaw.zelda64player.retroachievements.data.RaRecentAchievement
import br.com.redclaw.zelda64player.retroachievements.data.RaRecentlyPlayed
import br.com.redclaw.zelda64player.retroachievements.data.RaUserProfile
import br.com.redclaw.zelda64player.ui.switchui.SwitchImmersive
import br.com.redclaw.zelda64player.ui.switchui.SwitchBackButton
import coil.load
import kotlinx.coroutines.launch

/**
 * Full RetroAchievements profile for the currently authenticated player.
 *
 * Mirrors the Vigia-ai dashboard: header (avatar, motto, status, member since,
 * rich presence), point/rank metrics, mastery/beaten awards, last game,
 * recently played games and recent achievements.
 */
class RaProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRaProfileBinding
    private val viewModel: RaProfileViewModel by viewModels()

    private val backHelper = SwitchBackButton()
    private var profileUsername: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRaProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SwitchImmersive.enterFullscreen(this)

        backHelper.attach(this, binding.raProfileBack.root, onBack = { finish() })
        binding.raProfileRetry.setOnClickListener { viewModel.retry() }
        binding.raProfileAvatar.outlineProvider = ViewOutlineProvider.BACKGROUND
        binding.raProfileAvatar.clipToOutline = true
        binding.raProfileOpenWeb.setOnClickListener {
            val username = profileUsername
            if (!username.isNullOrBlank()) {
                startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://retroachievements.org/user/$username"))
                )
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.state.collect(::render)
            }
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

    private fun render(state: RaProfileUiState) {
        binding.raProfileProgress.isVisible = state is RaProfileUiState.Loading
        binding.raProfileContent.isVisible = state is RaProfileUiState.Content
        binding.raProfileMessage.isVisible =
            state is RaProfileUiState.SignedOut || state is RaProfileUiState.NeedsApiKey || state is RaProfileUiState.Error
        binding.raProfileRetry.isVisible = state is RaProfileUiState.Error
        when (state) {
            is RaProfileUiState.Content -> bindProfile(state.profile)
            RaProfileUiState.SignedOut -> binding.raProfileMessage.setText(R.string.ra_profile_signed_out)
            RaProfileUiState.NeedsApiKey -> binding.raProfileMessage.setText(R.string.ra_profile_needs_api_key)
            RaProfileUiState.Error -> binding.raProfileMessage.setText(R.string.ra_profile_error)
            RaProfileUiState.Loading -> Unit
        }
    }

    private fun bindProfile(profile: RaUserProfile) = with(binding) {
        profileUsername = profile.username
        raProfileUsername.text = profile.username
        bindOptional(raProfileMotto, profile.motto)
        bindOptional(raProfileStatus, profile.status)
        bindOptional(raProfileMemberSince, profile.memberSince?.let { getString(R.string.ra_profile_member_since, it) })
        bindOptional(raProfilePresence, profile.richPresence)
        raProfileAvatar.load(profile.avatarUrl) {
            crossfade(true)
            placeholder(R.drawable.ic_trophy)
            error(R.drawable.ic_trophy)
        }

        bindMetrics(profile)
        bindLastGame(profile.lastGame)
        bindRecentlyPlayed(profile.recentlyPlayed)
        bindRecentAchievements(profile.recentAchievements)
    }

    private fun bindOptional(view: TextView, value: String?) {
        view.text = value.orEmpty()
        view.isVisible = !value.isNullOrBlank()
    }

    /* ── Metrics grid (2 columns) ──────────────────────────────────────── */

    private fun bindMetrics(profile: RaUserProfile) = with(binding) {
        raProfileMetrics.removeAllViews()

        val rankSub = if (profile.rank != null && profile.totalRanked != null) {
            getString(R.string.ra_profile_metric_rank_sub, formatNumber(profile.rank), formatNumber(profile.totalRanked))
        } else {
            null
        }
        addMetricRow(
            getString(R.string.ra_profile_metric_hardcore_points),
            formatNumber(profile.totalPoints),
            rankSub,
            getString(R.string.ra_profile_metric_true_points),
            formatNumber(profile.totalTruePoints),
            profile.totalSoftcorePoints?.let { getString(R.string.ra_profile_metric_softcore_sub, formatNumber(it)) }
        )

        val awards = profile.awards
        val mastery = awards?.masteryAwardsCount
        val completion = awards?.completionAwardsCount
        val beatenHardcore = awards?.beatenHardcoreAwardsCount
        val beatenSoftcore = awards?.beatenSoftcoreAwardsCount
        addMetricRow(
            getString(R.string.ra_profile_metric_mastery),
            formatNumber(mastery),
            completion?.let { getString(R.string.ra_profile_metric_completion_sub, formatNumber(it)) },
            getString(R.string.ra_profile_metric_beaten),
            formatNumber(beatenHardcore),
            beatenSoftcore?.let { getString(R.string.ra_profile_metric_beaten_softcore_sub, formatNumber(it)) }
        )
    }

    private fun addMetricRow(
        label1: String, value1: String, sub1: String?,
        label2: String, value2: String, sub2: String?
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
        row.addView(metricCard(label1, value1, sub1))
        row.addView(metricCard(label2, value2, sub2).apply {
            (layoutParams as LinearLayout.LayoutParams).marginStart = dp(10)
        })
        binding.raProfileMetrics.addView(row)
    }

    private fun metricCard(label: String, value: String, sub: String?): View {
        val card = layoutInflater.inflate(R.layout.item_ra_profile_metric, binding.raProfileMetrics, false)
        card.findViewById<TextView>(R.id.ra_profile_metric_label).text = label
        card.findViewById<TextView>(R.id.ra_profile_metric_value).text = value
        val subView = card.findViewById<TextView>(R.id.ra_profile_metric_sub)
        if (sub.isNullOrBlank()) {
            subView.isVisible = false
        } else {
            subView.text = sub
            subView.isVisible = true
        }
        return card
    }

    /* ── Last game ─────────────────────────────────────────────────────── */

    private fun bindLastGame(lastGame: RaLastGame?) = with(binding) {
        if (lastGame == null || lastGame.title.isNullOrBlank()) {
            raProfileLastGameTitle.isVisible = false
            raProfileLastGameCard.isVisible = false
            return@with
        }
        raProfileLastGameTitle.isVisible = true
        raProfileLastGameCard.isVisible = true
        raProfileLastGameName.text = lastGame.title
        bindOptional(raProfileLastGameConsole, lastGame.console)
        raProfileLastGameImage.load(lastGame.imageIcon) {
            crossfade(true)
            placeholder(R.drawable.ic_hack)
            error(R.drawable.ic_hack)
        }
    }

    /* ── Recently played ───────────────────────────────────────────────── */

    private fun bindRecentlyPlayed(games: List<RaRecentlyPlayed>) = with(binding) {
        raProfileRecentGames.removeAllViews()
        if (games.isEmpty()) {
            raProfileRecentGamesTitle.isVisible = false
            return@with
        }
        raProfileRecentGamesTitle.isVisible = true
        games.take(MAX_RECENT_GAMES).forEach { game ->
            raProfileRecentGames.addView(gameRow(game))
        }
    }

    private fun gameRow(game: RaRecentlyPlayed): View {
        val row = layoutInflater.inflate(R.layout.item_ra_profile_game, binding.raProfileRecentGames, false)
        row.findViewById<TextView>(R.id.ra_profile_game_title).text =
            game.title?.takeIf { it.isNotBlank() } ?: getString(R.string.ra_profile_unknown_game)
        bindOptional(row.findViewById(R.id.ra_profile_game_console), game.consoleName)
        val progress = buildString {
            if (game.numAchieved != null && game.achievementsTotal != null) {
                append(getString(R.string.ra_profile_game_progress, game.numAchieved, game.achievementsTotal))
            }
            if (game.scoreAchieved != null) {
                if (isNotEmpty()) append(" · ")
                append(getString(R.string.ra_profile_game_points, formatNumber(game.scoreAchieved)))
            }
        }
        bindOptional(row.findViewById(R.id.ra_profile_game_progress), progress.ifBlank { null })
        row.findViewById<android.widget.ImageView>(R.id.ra_profile_game_image).load(game.imageIcon) {
            crossfade(true)
            placeholder(R.drawable.ic_hack)
            error(R.drawable.ic_hack)
        }
        return row
    }

    /* ── Recent achievements ───────────────────────────────────────────── */

    private fun bindRecentAchievements(achievements: List<RaRecentAchievement>) = with(binding) {
        raProfileRecentAchievements.removeAllViews()
        if (achievements.isEmpty()) {
            raProfileRecentAchievementsTitle.isVisible = false
            return@with
        }
        raProfileRecentAchievementsTitle.isVisible = true
        achievements.take(MAX_RECENT_ACHIEVEMENTS).forEach { ach ->
            raProfileRecentAchievements.addView(achievementRow(ach))
        }
    }

    private fun achievementRow(ach: RaRecentAchievement): View {
        val row = layoutInflater.inflate(R.layout.item_ra_profile_achievement, binding.raProfileRecentAchievements, false)
        row.findViewById<TextView>(R.id.ra_profile_achievement_title).text =
            ach.title?.takeIf { it.isNotBlank() } ?: getString(R.string.ra_profile_unknown_achievement)
        bindOptional(row.findViewById(R.id.ra_profile_achievement_game), ach.gameTitle)
        val meta = buildString {
            if (ach.points != null) append(getString(R.string.ra_profile_achievement_points, ach.points))
            if (ach.hardcore != null) {
                if (isNotEmpty()) append(" · ")
                append(getString(if (ach.hardcore) R.string.ra_profile_achievement_hardcore else R.string.ra_profile_achievement_softcore))
            }
            if (!ach.dateAwarded.isNullOrBlank()) {
                if (isNotEmpty()) append(" · ")
                append(ach.dateAwarded)
            }
        }
        bindOptional(row.findViewById(R.id.ra_profile_achievement_meta), meta.ifBlank { null })
        row.findViewById<android.widget.ImageView>(R.id.ra_profile_achievement_badge).load(ach.badgeUrl) {
            crossfade(true)
            placeholder(R.drawable.ic_trophy)
            error(R.drawable.ic_trophy)
        }
        return row
    }

    /* ── Helpers ───────────────────────────────────────────────────────── */

    private fun formatNumber(value: Int?): String =
        if (value == null) getString(R.string.ra_profile_not_available)
        else "%,d".format(java.util.Locale.US, value)

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val MAX_RECENT_GAMES = 5
        private const val MAX_RECENT_ACHIEVEMENTS = 5
    }
}
