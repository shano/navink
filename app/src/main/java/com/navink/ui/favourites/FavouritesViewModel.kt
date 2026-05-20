package com.navink.ui.favourites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navink.data.local.entity.SongEntity
import com.navink.data.repository.FavouritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class FavouritesUiState(
    val songs: List<SongEntity> = emptyList(),
    val isSyncing: Boolean = false,
)

@HiltViewModel
class FavouritesViewModel @Inject constructor(
    private val favouritesRepository: FavouritesRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(FavouritesUiState())
    val state: StateFlow<FavouritesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            favouritesRepository.starredSongs().collect { list ->
                _state.value = _state.value.copy(songs = list)
            }
        }
        syncStarred()
    }

    private fun syncStarred() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSyncing = true)
            try { favouritesRepository.syncStarred() } catch (_: Exception) {}
            _state.value = _state.value.copy(isSyncing = false)
        }
    }

    fun toggleStar(songId: String, isCurrentlyStarred: Boolean) {
        viewModelScope.launch {
            if (isCurrentlyStarred) favouritesRepository.unstarSong(songId)
            else favouritesRepository.starSong(songId)
        }
    }
}
