package com.navink.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navink.data.local.entity.AlbumEntity
import com.navink.data.local.entity.ArtistEntity
import com.navink.data.local.entity.SongEntity
import com.navink.data.repository.MusicRepository
import com.navink.data.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BrowseUiState(
    val artists: List<ArtistEntity> = emptyList(),
    val albums: List<AlbumEntity> = emptyList(),
    val songs: List<SongEntity> = emptyList(),
    val isSyncing: Boolean = false,
    val syncError: String? = null,
)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val syncRepository: SyncRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BrowseUiState())
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    init { syncOnLaunch() }

    fun syncOnLaunch() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSyncing = true, syncError = null)
            try {
                syncRepository.syncAll()
            } catch (e: Exception) {
                _state.value = _state.value.copy(syncError = e.message)
            } finally {
                _state.value = _state.value.copy(isSyncing = false)
            }
        }
    }

    fun observeArtists() {
        viewModelScope.launch {
            musicRepository.allArtists().collect { list ->
                _state.value = _state.value.copy(artists = list)
            }
        }
    }

    fun observeAlbums(artistId: String) {
        viewModelScope.launch {
            musicRepository.albumsForArtist(artistId).collect { list ->
                _state.value = _state.value.copy(albums = list)
            }
        }
    }

    fun observeSongs(albumId: String) {
        viewModelScope.launch {
            musicRepository.songsForAlbum(albumId).collect { list ->
                _state.value = _state.value.copy(songs = list)
            }
        }
    }
}
