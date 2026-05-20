package com.navink.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navink.data.repository.DownloadRepository
import com.navink.data.repository.MusicRepository
import com.navink.data.repository.SettingsRepository
import com.navink.player.PlayerController
import com.navink.player.PlayerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val playerController: PlayerController,
    private val musicRepository: MusicRepository,
    private val settingsRepository: SettingsRepository,
    private val downloadRepository: DownloadRepository,
) : ViewModel() {
    val state: StateFlow<PlayerState> = playerController.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, playerController.state.value)

    private val _isDownloadingCurrentSong = MutableStateFlow(false)
    val isDownloadingCurrentSong: StateFlow<Boolean> = _isDownloadingCurrentSong.asStateFlow()

    init {
        viewModelScope.launch {
            var prevSongId: String? = null
            playerController.state.collect { s ->
                if (s.currentSongId != prevSongId) {
                    _isDownloadingCurrentSong.value = false
                    prevSongId = s.currentSongId
                }
            }
        }
    }

    fun playSongFromAlbum(songId: String, albumId: String) {
        viewModelScope.launch {
            val songs = musicRepository.songsForAlbumOnce(albumId)
            playerController.playAlbum(songs, songId)
        }
    }

    fun coverArtUrl(coverArtId: String?): String? {
        if (coverArtId == null) return null
        val creds = runBlocking { settingsRepository.getCredentials() }
        if (creds.serverUrl.isBlank()) return null
        return "${creds.serverUrl}/rest/getCoverArt.view?id=$coverArtId&u=${creds.username}&p=${creds.password}&v=1.16.1&c=navink"
    }

    fun downloadCurrentSong() {
        val songId = state.value.currentSongId ?: return
        _isDownloadingCurrentSong.value = true
        downloadRepository.downloadSong(songId)
    }

    fun playPause() = playerController.playPause()
    fun next() = playerController.next()
    fun previous() = playerController.previous()
}
