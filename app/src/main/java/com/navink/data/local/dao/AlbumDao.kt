package com.navink.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.navink.data.local.entity.AlbumEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Query("SELECT * FROM AlbumEntity WHERE artistId = :artistId ORDER BY year ASC, name ASC")
    fun albumsForArtist(artistId: String): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM AlbumEntity WHERE id = :id")
    suspend fun albumById(id: String): AlbumEntity?

    @Upsert
    suspend fun upsertAll(albums: List<AlbumEntity>)

    @Query("UPDATE AlbumEntity SET isStarred = :starred WHERE id = :id")
    suspend fun setStarred(id: String, starred: Boolean)

    @Query("SELECT * FROM AlbumEntity WHERE isStarred = 1 ORDER BY name ASC")
    fun starredAlbums(): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM AlbumEntity WHERE artistId = :artistId ORDER BY year ASC, name ASC")
    suspend fun albumsForArtistOnce(artistId: String): List<AlbumEntity>
}
