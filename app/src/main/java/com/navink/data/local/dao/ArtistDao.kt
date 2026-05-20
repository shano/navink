package com.navink.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.navink.data.local.entity.ArtistEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ArtistDao {
    @Query("SELECT * FROM ArtistEntity ORDER BY name ASC")
    fun allArtists(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM ArtistEntity WHERE id = :id")
    suspend fun artistById(id: String): ArtistEntity?

    @Upsert
    suspend fun upsertAll(artists: List<ArtistEntity>)

    @Query("UPDATE ArtistEntity SET isStarred = :starred WHERE id = :id")
    suspend fun setStarred(id: String, starred: Boolean)

    @Query("SELECT * FROM ArtistEntity WHERE isStarred = 1 ORDER BY name ASC")
    fun starredArtists(): Flow<List<ArtistEntity>>
}
