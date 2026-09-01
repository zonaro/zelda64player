package br.com.redclaw.zelda64player.retroachievements.ui

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
import br.com.redclaw.zelda64player.retroachievements.data.RaUserProfile
import br.com.redclaw.zelda64player.retroachievements.data.RaUserProfileField
import br.com.redclaw.zelda64player.ui.switchui.SwitchImmersive
import br.com.redclaw.zelda64player.ui.switchui.SwitchBackButton
import coil.load
import kotlinx.coroutines.launch

/** Full RetroAchievements profile for the currently authenticated player. */
class RaProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRaProfileBinding
    private val viewModel: RaProfileViewModel by viewModels()

    private val backHelper = SwitchBackButton()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRaProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)
        SwitchImmersive.enterFullscreen(this)

        backHelper.attach(this, binding.raProfileBack.root, onBack = { finish() })
        binding.raProfileRetry.setOnClickListener { viewModel.retry() }
        binding.raProfileAvatar.outlineProvider = ViewOutlineProvider.BACKGROUND
        binding.raProfileAvatar.clipToOutline = true

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
        binding.raProfileMessage.isVisible = state is RaProfileUiState.SignedOut || state is RaProfileUiState.Error
        binding.raProfileRetry.isVisible = state is RaProfileUiState.Error
        when (state) {
            is RaProfileUiState.Content -> bindProfile(state.profile)
            RaProfileUiState.SignedOut -> binding.raProfileMessage.setText(R.string.ra_profile_signed_out)
            RaProfileUiState.Error -> binding.raProfileMessage.setText(R.string.ra_profile_error)
            RaProfileUiState.Loading -> Unit
        }
    }

    private fun bindProfile(profile: RaUserProfile) = with(binding) {
        raProfileUsername.text = profile.username
        bindOptional(raProfileMotto, profile.motto)
        bindOptional(raProfilePresence, profile.richPresence)
        raProfileAvatar.load(profile.avatarUrl) {
            crossfade(true)
            placeholder(R.drawable.ic_trophy)
            error(R.drawable.ic_trophy)
        }

        raProfileDetails.removeAllViews()
        profile.fields.forEach { field -> raProfileDetails.addView(detailView(field)) }
    }

    private fun bindOptional(view: TextView, value: String?) {
        view.text = value.orEmpty()
        view.isVisible = !value.isNullOrBlank()
    }

    private fun detailView(field: RaUserProfileField): View {
        val row = layoutInflater.inflate(R.layout.item_ra_profile_field, binding.raProfileDetails, false)
        row.findViewById<TextView>(R.id.ra_profile_field_label).text = labelFor(field.key)
        row.findViewById<TextView>(R.id.ra_profile_field_value).text =
            field.value.ifBlank { getString(R.string.ra_profile_not_available) }
        return row
    }

    private fun labelFor(key: String): String = when (key.lowercase()) {
        "user", "username" -> getString(R.string.ra_profile_field_user)
        "displayname", "display_name" -> getString(R.string.ra_profile_field_display_name)
        "id" -> getString(R.string.ra_profile_field_id)
        "ulid" -> getString(R.string.ra_profile_field_ulid)
        "userpic", "avatarurl", "avatar", "imageicon" -> getString(R.string.ra_profile_field_avatar)
        "motto" -> getString(R.string.ra_profile_field_motto)
        "richpresencemsg", "richpresence" -> getString(R.string.ra_profile_field_rich_presence)
        "lastgameid" -> getString(R.string.ra_profile_field_last_game_id)
        "contribcount" -> getString(R.string.ra_profile_field_contribution_count)
        "contribyield" -> getString(R.string.ra_profile_field_contribution_yield)
        "totalpoints" -> getString(R.string.ra_profile_field_total_points)
        "totalsoftcorepoints" -> getString(R.string.ra_profile_field_softcore_points)
        "totaltruepoints" -> getString(R.string.ra_profile_field_true_points)
        "permissions" -> getString(R.string.ra_profile_field_permissions)
        "untracked" -> getString(R.string.ra_profile_field_untracked)
        "userwallactive" -> getString(R.string.ra_profile_field_user_wall_active)
        else -> key
    }
}
