package br.com.redclaw.zelda64player.retroachievements.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import br.com.redclaw.zelda64player.Zelda64PlayerApp
import br.com.redclaw.zelda64player.retroachievements.data.RaUserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** State rendered by [RaProfileActivity]. */
sealed interface RaProfileUiState {
    data object Loading : RaProfileUiState
    data object SignedOut : RaProfileUiState
    data class Content(val profile: RaUserProfile) : RaProfileUiState
    data object Error : RaProfileUiState
}

/** Loads the local profile snapshot first, then refreshes it from RetroAchievements. */
class RaProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = Zelda64PlayerApp.raUserProfileRepository
    private val credentials = Zelda64PlayerApp.raCredentialStore

    private val _state = MutableStateFlow<RaProfileUiState>(RaProfileUiState.Loading)
    val state: StateFlow<RaProfileUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        if (!credentials.hasCredentials()) {
            _state.value = RaProfileUiState.SignedOut
            return
        }
        viewModelScope.launch {
            val cached = repository.getCachedProfile()
            if (cached != null) _state.value = RaProfileUiState.Content(cached)
            else _state.value = RaProfileUiState.Loading

            // Opening the profile should show the current server snapshot. A
            // cached profile is rendered first so the screen remains responsive
            // while this refresh is in flight.
            val result = repository.getProfile(forceRefresh = true)
            result.onSuccess { _state.value = RaProfileUiState.Content(it) }
                .onFailure {
                    if (cached == null) _state.value = RaProfileUiState.Error
                }
        }
    }
}
