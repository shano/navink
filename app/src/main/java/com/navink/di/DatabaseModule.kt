package com.navink.di

import android.content.Context
import androidx.room.Room
import com.navink.data.local.NavinkDatabase
import com.navink.data.local.dao.AlbumDao
import com.navink.data.local.dao.ArtistDao
import com.navink.data.local.dao.SongDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NavinkDatabase =
        Room.databaseBuilder(context, NavinkDatabase::class.java, "navink.db").build()

    @Provides fun provideArtistDao(db: NavinkDatabase): ArtistDao = db.artistDao()
    @Provides fun provideAlbumDao(db: NavinkDatabase): AlbumDao = db.albumDao()
    @Provides fun provideSongDao(db: NavinkDatabase): SongDao = db.songDao()
}
