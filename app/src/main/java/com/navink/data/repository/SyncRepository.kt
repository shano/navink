package com.navink.data.repository

import com.navink.data.local.dao.AlbumDao
import com.navink.data.local.dao.ArtistDao
import com.navink.data.local.dao.SongDao
import com.navink.data.local.entity.AlbumEntity
import com.navink.data.local.entity.ArtistEntity
import com.navink.data.local.entity.SongEntity
import com.navink.data.remote.SubsonicService
import com.navink.data.remote.dto.AlbumDto
import com.navink.data.remote.dto.ArtistDto
import com.navink.data.remote.dto.SongDto
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SyncRepository @Inject constructor(
    private val service: SubsonicService,
    private val artistDao: ArtistDao,
    private val albumDao: AlbumDao,
    private val songDao: SongDao,
) {
    suspend fun syncArtist(artistId: String) {
        val artistDetail = service.getArtist(artistId).response.artist ?: return
        albumDao.upsertAll(artistDetail.album.map { it.toEntity(artistId = artistId) })
        for (albumDto in artistDetail.album) {
            val albumDetail = service.getAlbum(albumDto.id).response.album ?: continue
            songDao.upsertAll(albumDetail.song.map { it.toEntity(albumId = albumDto.id, artistId = artistId) })
        }
    }

    suspend fun syncAll() {
        val artistsResponse = service.getArtists()
        val allArtistDtos = artistsResponse.response.artists?.index
            ?.flatMap { it.artist } ?: return

        artistDao.upsertAll(allArtistDtos.map { it.toEntity() })

        for (artistDto in allArtistDtos) {
            val artistDetail = service.getArtist(artistDto.id).response.artist ?: continue
            albumDao.upsertAll(artistDetail.album.map { it.toEntity(artistId = artistDto.id) })

            for (albumDto in artistDetail.album) {
                val albumDetail = service.getAlbum(albumDto.id).response.album ?: continue
                songDao.upsertAll(albumDetail.song.map { it.toEntity(albumId = albumDto.id, artistId = artistDto.id) })
            }
        }
    }

    private fun ArtistDto.toEntity() = ArtistEntity(
        id = id,
        name = name,
        albumCount = albumCount,
        isStarred = starred != null,
    )

    private fun AlbumDto.toEntity(artistId: String) = AlbumEntity(
        id = id,
        artistId = this.artistId.ifBlank { artistId },
        name = name,
        year = year,
        coverArtId = coverArt,
        songCount = songCount,
        isStarred = starred != null,
    )

    private fun SongDto.toEntity(albumId: String, artistId: String) = SongEntity(
        id = id,
        albumId = this.albumId.ifBlank { albumId },
        artistId = this.artistId.ifBlank { artistId },
        title = title,
        trackNumber = track,
        duration = duration,
        coverArtId = coverArt,
        isStarred = starred != null,
    )
}
