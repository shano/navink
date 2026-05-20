package com.navink.data.repository

import com.navink.data.local.dao.AlbumDao
import com.navink.data.local.dao.ArtistDao
import com.navink.data.local.dao.SongDao
import com.navink.data.local.entity.AlbumEntity
import com.navink.data.local.entity.ArtistEntity
import com.navink.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicRepository @Inject constructor(
    private val artistDao: ArtistDao,
    private val albumDao: AlbumDao,
    private val songDao: SongDao,
) {
    fun allArtists(): Flow<List<ArtistEntity>> = artistDao.allArtists()
    fun albumsForArtist(artistId: String): Flow<List<AlbumEntity>> = albumDao.albumsForArtist(artistId)
    fun songsForAlbum(albumId: String): Flow<List<SongEntity>> = songDao.songsForAlbum(albumId)
    fun starredSongs(): Flow<List<SongEntity>> = songDao.starredSongs()
    fun starredAlbums(): Flow<List<AlbumEntity>> = albumDao.starredAlbums()
    fun starredArtists(): Flow<List<ArtistEntity>> = artistDao.starredArtists()
    suspend fun songsForAlbumOnce(albumId: String): List<SongEntity> = songDao.songsForAlbumOnce(albumId)
    suspend fun songById(id: String): SongEntity? = songDao.songById(id)
}
