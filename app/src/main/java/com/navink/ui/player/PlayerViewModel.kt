package com.navink.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navink.data.repository.DownloadRepository
import com.navink.data.repository.MusicRepository
import com.navink.data.repository.SettingsRepository
import com.navink.player.PlayerController
import com.navink.player.PlayerState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    private val _isCurrentSongDownloaded = MutableStateFlow(false)
    val isCurrentSongDownloaded: StateFlow<Boolean> = _isCurrentSongDownloaded.asStateFlow()

    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    init {
        viewModelScope.launch {
            var prevSongId: String? = null
            playerController.state.collect { s ->
                if (s.currentSongId != prevSongId) {
                    _isDownloadingCurrentSong.value = false
                    prevSongId = s.currentSongId
                    _durationMs.value = playerController.durationMs()
                }
            }
        }
        viewModelScope.launch {
            combine(playerController.state, musicRepository.downloadedSongs()) { playerState, downloaded ->
                downloaded.any { it.id == playerState.currentSongId }
            }.collect { _isCurrentSongDownloaded.value = it }
        }
        viewModelScope.launch {
            while (true) {
                if (state.value.isPlaying) {
                    _positionMs.value = playerController.currentPositionMs()
                    _durationMs.value = playerController.durationMs()
                }
                delay(500)
            }
        }
    }

    fun playSongFromAlbum(songId: String, albumId: String) {
        viewModelScope.launch {
            val offline = settingsRepository.getOfflineMode()
            val songs = if (offline) {
                musicRepository.downloadedSongsForAlbumOnce(albumId)
            } else {
                musicRepository.songsForAlbumOnce(albumId)
            }
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
        viewModelScope.launch { downloadRepository.enqueueSong(songId) }
    }

    fun deleteCurrentSongDownload() {
        val songId = state.value.currentSongId ?: return
        viewModelScope.launch { downloadRepository.deleteSongDownload(songId) }
    }

    fun playPause() = playerController.playPause()
    fun next() = playerController.next()
    fun previous() = playerController.previous()

    fun seekTo(fraction: Float) {
        val target = seekTargetMs(fraction, _durationMs.value)
        playerController.seekTo(target)
        _positionMs.value = target
    }

    companion object {
        fun seekTargetMs(fraction: Float, durationMs: Long): Long =
            (fraction.coerceIn(0f, 1f) * durationMs).toLong().coerceIn(0L, durationMs)
    }
}
