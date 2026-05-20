package com.navink.data.repository

import com.navink.data.local.dao.AlbumDao
import com.navink.data.local.dao.ArtistDao
import com.navink.data.local.dao.SongDao
import com.navink.data.local.entity.AlbumEntity
import com.navink.data.local.entity.ArtistEntity
import com.navink.data.local.entity.SongEntity
import com.navink.data.remote.SubsonicService
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FavouritesRepository @Inject constructor(
    private val service: SubsonicService,
    private val artistDao: ArtistDao,
    private val albumDao: AlbumDao,
    private val songDao: SongDao,
) {
    fun starredArtists(): Flow<List<ArtistEntity>> = artistDao.starredArtists()
    fun starredAlbums(): Flow<List<AlbumEntity>> = albumDao.starredAlbums()
    fun starredSongs(): Flow<List<SongEntity>> = songDao.starredSongs()

    suspend fun syncStarred() {
        val starred2 = service.getStarred2().response.starred2 ?: return
        starred2.artist.forEach { artistDao.setStarred(it.id, true) }
        starred2.album.forEach { albumDao.setStarred(it.id, true) }
        starred2.song.forEach { songDao.setStarred(it.id, true) }
    }

    suspend fun starSong(id: String) {
        service.star(songId = id)
        songDao.setStarred(id, true)
    }

    suspend fun unstarSong(id: String) {
        service.unstar(songId = id)
        songDao.setStarred(id, false)
    }

    suspend fun starAlbum(id: String) {
        service.star(albumId = id)
        albumDao.setStarred(id, true)
    }

    suspend fun unstarAlbum(id: String) {
        service.unstar(albumId = id)
        albumDao.setStarred(id, false)
    }

    suspend fun starArtist(id: String) {
        service.star(artistId = id)
        artistDao.setStarred(id, true)
    }

    suspend fun unstarArtist(id: String) {
        service.unstar(artistId = id)
        artistDao.setStarred(id, false)
    }
}
