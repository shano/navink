package com.navink.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.navink.data.local.entity.SongEntity
import com.navink.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    private var controller: MediaController? = null

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    suspend fun connect() {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controller = MediaController.Builder(context, token).buildAsync().await()
        controller?.addListener(object : Player.Listener {
            override fun onMediaMetadataChanged(metadata: MediaMetadata) {
                _state.value = _state.value.copy(
                    currentTitle = metadata.title?.toString() ?: "",
                    currentArtist = metadata.artist?.toString() ?: "",
                    currentAlbum = metadata.albumTitle?.toString() ?: "",
                )
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
            }

            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                _state.value = _state.value.copy(
                    currentSongId = item?.mediaId,
                    hasQueue = (controller?.mediaItemCount ?: 0) > 0,
                )
            }
        })
    }

    fun playAlbum(songs: List<SongEntity>, startSongId: String) {
        val creds = runBlocking { settingsRepository.getCredentials() }
        val items = songs.map { song ->
            val uri = song.localPath?.let { "file://$it" }
                ?: "${creds.serverUrl}/rest/stream.view?id=${song.id}&u=${creds.username}&p=${creds.password}&v=1.16.1&c=navink"
            MediaItem.Builder()
                .setMediaId(song.id)
                .setUri(uri)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .build()
                )
                .build()
        }
        val start = startIndex(songs, startSongId)
        controller?.apply {
            setMediaItems(items, start, 0L)
            prepare()
            play()
        }
        val current = songs.getOrNull(start)
        _state.value = _state.value.copy(
            currentSongId = current?.id,
            currentCoverArtId = current?.coverArtId,
            hasQueue = items.isNotEmpty(),
        )
    }

    fun playPause() {
        controller?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPreviousMediaItem() }

    fun disconnect() { controller?.release() }

    companion object {
        fun startIndex(songs: List<SongEntity>, startSongId: String): Int =
            songs.indexOfFirst { it.id == startSongId }.coerceAtLeast(0)
    }
}
