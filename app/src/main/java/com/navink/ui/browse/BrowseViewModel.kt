package com.navink.ui.browse

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.navink.data.local.entity.AlbumEntity
import com.navink.data.local.entity.ArtistEntity
import com.navink.data.local.entity.SongEntity
import com.navink.data.repository.MusicRepository
import com.navink.data.repository.SettingsRepository
import com.navink.data.repository.SyncRepository
import com.navink.download.DownloadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BrowseUiState(
    val artists: List<ArtistEntity> = emptyList(),
    val albums: List<AlbumEntity> = emptyList(),
    val songs: List<SongEntity> = emptyList(),
    val downloadedSongs: List<SongEntity> = emptyList(),
    val isSyncing: Boolean = false,
    val syncError: String? = null,
    val downloadMessage: String? = null,
    val storageLocation: String = "external",
)

@HiltViewModel
class BrowseViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val musicRepository: MusicRepository,
    private val syncRepository: SyncRepository,
    private val settingsRepository: SettingsRepository,
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

    fun observeDownloadedSongs() {
        viewModelScope.launch {
            musicRepository.downloadedSongs().collect { list ->
                _state.value = _state.value.copy(downloadedSongs = list)
            }
        }
    }

    fun downloadAlbum(albumId: String) {
        viewModelScope.launch {
            val songs = musicRepository.songsForAlbumOnce(albumId)
            val wm = WorkManager.getInstance(context)
            songs.forEach { song ->
                val data = workDataOf(DownloadWorker.KEY_SONG_ID to song.id)
                wm.enqueue(OneTimeWorkRequestBuilder<DownloadWorker>().setInputData(data).build())
            }
            _state.value = _state.value.copy(downloadMessage = "Queued ${songs.size} tracks")
        }
    }

    fun downloadArtist(artistId: String) {
        viewModelScope.launch {
            val albums = musicRepository.albumsForArtistOnce(artistId)
            val wm = WorkManager.getInstance(context)
            var count = 0
            albums.forEach { album ->
                musicRepository.songsForAlbumOnce(album.id).forEach { song ->
                    val data = workDataOf(DownloadWorker.KEY_SONG_ID to song.id)
                    wm.enqueue(OneTimeWorkRequestBuilder<DownloadWorker>().setInputData(data).build())
                    count++
                }
            }
            _state.value = _state.value.copy(downloadMessage = "Queued $count tracks")
        }
    }

    fun loadStorageLocation() {
        viewModelScope.launch {
            _state.value = _state.value.copy(storageLocation = settingsRepository.getStorageLocation())
        }
    }

    fun setStorageLocation(location: String) {
        viewModelScope.launch {
            settingsRepository.saveStorageLocation(location)
            _state.value = _state.value.copy(storageLocation = location)
        }
    }
}
