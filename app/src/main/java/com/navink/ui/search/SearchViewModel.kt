package com.navink.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navink.data.remote.SubsonicService
import com.navink.data.remote.dto.AlbumDto
import com.navink.data.remote.dto.ArtistDto
import com.navink.data.remote.dto.SongDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SearchUiState(
    val query: String = "",
    val artists: List<ArtistDto> = emptyList(),
    val albums: List<AlbumDto> = emptyList(),
    val songs: List<SongDto> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val service: SubsonicService,
) : ViewModel() {
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    fun onQueryChange(q: String) { _state.value = _state.value.copy(query = q) }

    fun search() {
        val q = _state.value.query.trim()
        if (q.isBlank()) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val response = service.search3(q)
                val result = response.response.searchResult3
                _state.value = _state.value.copy(
                    artists = result?.artist ?: emptyList(),
                    albums = result?.album ?: emptyList(),
                    songs = result?.song ?: emptyList(),
                    isLoading = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }
}
