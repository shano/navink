package com.navink.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.navink.data.local.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM SongEntity WHERE albumId = :albumId ORDER BY trackNumber ASC, title ASC")
    fun songsForAlbum(albumId: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM SongEntity WHERE id = :id")
    suspend fun songById(id: String): SongEntity?

    @Upsert
    suspend fun upsertAll(songs: List<SongEntity>)

    @Query("UPDATE SongEntity SET isStarred = :starred WHERE id = :id")
    suspend fun setStarred(id: String, starred: Boolean)

    @Query("UPDATE SongEntity SET isDownloaded = 1, localPath = :path WHERE id = :id")
    suspend fun setDownloaded(id: String, path: String)

    @Query("SELECT * FROM SongEntity WHERE isStarred = 1 ORDER BY title ASC")
    fun starredSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM SongEntity WHERE albumId = :albumId ORDER BY trackNumber ASC, title ASC")
    suspend fun songsForAlbumOnce(albumId: String): List<SongEntity>
}
