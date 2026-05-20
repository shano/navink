package com.navink.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class SongEntity(
    @PrimaryKey val id: String,
    val albumId: String,
    val artistId: String,
    val title: String,
    val trackNumber: Int? = null,
    val duration: Int = 0,
    val coverArtId: String? = null,
    val isStarred: Boolean = false,
    val isDownloaded: Boolean = false,
    val localPath: String? = null,
)
