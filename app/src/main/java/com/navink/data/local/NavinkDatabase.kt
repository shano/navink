package com.navink.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.navink.data.local.dao.AlbumDao
import com.navink.data.local.dao.ArtistDao
import com.navink.data.local.dao.SongDao
import com.navink.data.local.entity.AlbumEntity
import com.navink.data.local.entity.ArtistEntity
import com.navink.data.local.entity.SongEntity

@Database(
    entities = [ArtistEntity::class, AlbumEntity::class, SongEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class NavinkDatabase : RoomDatabase() {
    abstract fun artistDao(): ArtistDao
    abstract fun albumDao(): AlbumDao
    abstract fun songDao(): SongDao
}
