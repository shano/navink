package com.navink.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.navink.data.local.entity.AlbumEntity
import com.navink.data.local.entity.ArtistEntity
import com.navink.data.local.entity.DownloadQueueEntity
import com.navink.data.local.entity.SongEntity
import com.navink.data.repository.DownloadRepository
import com.navink.data.repository.MusicRepository
import com.navink.data.repository.SettingsRepository
import com.navink.data.repository.SyncRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BrowseUiState(
    val artists: List<ArtistEntity> = emptyList(),
    val albums: List<AlbumEntity> = emptyList(),
    val songs: List<SongEntity> = emptyList(),
    val downloadQueue: List<DownloadQueueEntity> = emptyList(),
    val downloadedCountByAlbum: Map<String, Int> = emptyMap(),
    val downloadedAlbumCountByArtist: Map<String, Int> = emptyMap(),
    val isSyncing: Boolean = false,
    val isLoadingAlbums: Boolean = false,
    val syncError: String? = null,
    val albumSyncError: String? = null,
    val downloadMessage: String? = null,
    val isOfflineMode: Boolean = false,
)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val musicRepository: MusicRepository,
    private val syncRepository: SyncRepository,
    private val settingsRepository: SettingsRepository,
    private val downloadRepository: DownloadRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BrowseUiState())
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = _state.value.copy(isOfflineMode = settingsRepository.getOfflineMode())
        }
        viewModelScope.launch {
            downloadRepository.queue().collect { q ->
                _state.value = _state.value.copy(downloadQueue = q)
            }
        }
        viewModelScope.launch {
            musicRepository.downloadedCountByAlbum().collect { counts ->
                _state.value = _state.value.copy(downloadedCountByAlbum = counts)
            }
        }
        viewModelScope.launch {
            musicRepository.downloadedAlbumCountByArtist().collect { counts ->
                _state.value = _state.value.copy(downloadedAlbumCountByArtist = counts)
            }
        }
    }

    fun toggleOfflineMode() {
        viewModelScope.launch {
            val next = !_state.value.isOfflineMode
            if (next) downloadRepository.verifyDownloads()
            settingsRepository.saveOfflineMode(next)
            _state.value = _state.value.copy(isOfflineMode = next)
            observeArtists()
        }
    }

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

    private var artistsJob: Job? = null
    fun observeArtists() {
        artistsJob?.cancel()
        artistsJob = viewModelScope.launch {
            val offline = settingsRepository.getOfflineMode()
            _state.value = _state.value.copy(isOfflineMode = offline)
            val flow = if (offline) {
                musicRepository.artistsWithDownloads()
            } else {
                musicRepository.allArtists()
            }
            flow.collect { list -> _state.value = _state.value.copy(artists = list) }
        }
    }

    private var albumsJob: Job? = null
    fun observeAlbums(artistId: String) {
        albumsJob?.cancel()
        _state.value = _state.value.copy(albums = emptyList())
        albumsJob = viewModelScope.launch {
            val offline = settingsRepository.getOfflineMode()
            _state.value = _state.value.copy(isOfflineMode = offline)
            val flow = if (offline) {
                musicRepository.albumsWithDownloadsForArtist(artistId)
            } else {
                musicRepository.albumsForArtist(artistId)
            }
            flow.collect { list -> _state.value = _state.value.copy(albums = list) }
        }
    }

    fun syncArtistAlbums(artistId: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoadingAlbums = true, albumSyncError = null)
            try {
                syncRepository.syncArtist(artistId)
            } catch (e: Exception) {
                _state.value = _state.value.copy(albumSyncError = e.message ?: e.javaClass.simpleName)
            } finally {
                _state.value = _state.value.copy(isLoadingAlbums = false)
            }
        }
    }

    private var songsJob: Job? = null
    fun observeSongs(albumId: String) {
        songsJob?.cancel()
        _state.value = _state.value.copy(songs = emptyList())
        songsJob = viewModelScope.launch {
            val offline = settingsRepository.getOfflineMode()
            _state.value = _state.value.copy(isOfflineMode = offline)
            val flow = if (offline) {
                musicRepository.downloadedSongsForAlbum(albumId)
            } else {
                musicRepository.songsForAlbum(albumId)
            }
            flow.collect { list -> _state.value = _state.value.copy(songs = list) }
        }
    }

    fun downloadAlbum(albumId: String) {
        viewModelScope.launch {
            try {
                syncRepository.syncAlbumSongs(albumId)
            } catch (_: Exception) {}
            val queued = downloadRepository.enqueueAlbum(albumId)
            _state.value = _state.value.copy(
                downloadMessage = if (queued == 0) "Already downloaded" else "Queued $queued tracks"
            )
        }
    }

    fun downloadArtist(artistId: String) {
        viewModelScope.launch {
            try {
                syncRepository.syncArtist(artistId)
            } catch (_: Exception) {}
            val queued = downloadRepository.enqueueArtist(artistId)
            _state.value = _state.value.copy(
                downloadMessage = if (queued == 0) "Already downloaded" else "Queued $queued tracks"
            )
        }
    }

    fun deleteAlbumDownloads(albumId: String) {
        viewModelScope.launch {
            downloadRepository.deleteAlbumDownloads(albumId)
            _state.value = _state.value.copy(downloadMessage = "Downloads removed")
        }
    }

    fun retryFailedDownloads() {
        viewModelScope.launch { downloadRepository.retryFailed() }
    }

    fun clearFailedDownloads() {
        viewModelScope.launch { downloadRepository.clearFailed() }
    }

    fun clearDownloadMessage() {
        _state.value = _state.value.copy(downloadMessage = null)
    }
}
