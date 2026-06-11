package com.navink.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.navink.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow

data class AlbumDownloadCount(val albumId: String, val cnt: Int)

data class ArtistAlbumDownloadCount(val artistId: String, val cnt: Int)

@Dao
interface SongDao {
    @Query("SELECT * FROM SongEntity WHERE albumId = :albumId ORDER BY trackNumber ASC, title ASC")
    fun songsForAlbum(albumId: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM SongEntity WHERE id = :id")
    suspend fun songById(id: String): SongEntity?

    @Upsert
    suspend fun upsertAll(songs: List<SongEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(songs: List<SongEntity>): List<Long>

    @Query(
        """UPDATE SongEntity SET albumId = :albumId, artistId = :artistId, title = :title,
           trackNumber = :trackNumber, duration = :duration, coverArtId = :coverArtId,
           isStarred = :isStarred WHERE id = :id"""
    )
    suspend fun updateMetadata(
        id: String,
        albumId: String,
        artistId: String,
        title: String,
        trackNumber: Int?,
        duration: Int,
        coverArtId: String?,
        isStarred: Boolean,
    )

    /** Insert new songs, update metadata of existing ones; never touches isDownloaded/localPath. */
    @Transaction
    suspend fun upsertPreservingDownloads(songs: List<SongEntity>) {
        val inserted = insertIgnore(songs)
        songs.forEachIndexed { i, s ->
            if (inserted[i] == -1L) {
                updateMetadata(s.id, s.albumId, s.artistId, s.title, s.trackNumber, s.duration, s.coverArtId, s.isStarred)
            }
        }
    }

    @Query("UPDATE SongEntity SET isStarred = :starred WHERE id = :id")
    suspend fun setStarred(id: String, starred: Boolean)

    @Query("UPDATE SongEntity SET isDownloaded = 1, localPath = :path WHERE id = :id")
    suspend fun setDownloaded(id: String, path: String)

    @Query("UPDATE SongEntity SET isDownloaded = 0, localPath = NULL WHERE id = :id")
    suspend fun clearDownloaded(id: String)

    @Query("SELECT * FROM SongEntity WHERE isStarred = 1 ORDER BY title ASC")
    fun starredSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM SongEntity WHERE albumId = :albumId ORDER BY trackNumber ASC, title ASC")
    suspend fun songsForAlbumOnce(albumId: String): List<SongEntity>

    @Query("SELECT * FROM SongEntity WHERE isDownloaded = 1 ORDER BY title ASC")
    fun downloadedSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM SongEntity WHERE isDownloaded = 1")
    suspend fun downloadedSongsOnce(): List<SongEntity>

    @Query("SELECT * FROM SongEntity WHERE albumId = :albumId AND isDownloaded = 1 ORDER BY trackNumber ASC, title ASC")
    fun downloadedSongsForAlbum(albumId: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM SongEntity WHERE albumId = :albumId AND isDownloaded = 1 ORDER BY trackNumber ASC, title ASC")
    suspend fun downloadedSongsForAlbumOnce(albumId: String): List<SongEntity>

    @Query("SELECT albumId, COUNT(*) AS cnt FROM SongEntity WHERE isDownloaded = 1 GROUP BY albumId")
    fun downloadedCountByAlbum(): Flow<List<AlbumDownloadCount>>

    @Query("SELECT artistId, COUNT(DISTINCT albumId) AS cnt FROM SongEntity WHERE isDownloaded = 1 GROUP BY artistId")
    fun downloadedAlbumCountByArtist(): Flow<List<ArtistAlbumDownloadCount>>
}
