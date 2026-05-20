package com.navink.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class AlbumEntity(
    @PrimaryKey val id: String,
    val artistId: String,
    val name: String,
    val year: Int? = null,
    val coverArtId: String? = null,
    val songCount: Int = 0,
    val isStarred: Boolean = false,
)
