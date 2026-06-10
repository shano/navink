package com.navink.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.navink.data.local.NavinkDatabase
import com.navink.data.local.dao.AlbumDao
import com.navink.data.local.dao.ArtistDao
import com.navink.data.local.dao.DownloadQueueDao
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
    private val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `DownloadQueueEntity` (
                    `songId` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `status` TEXT NOT NULL,
                    `progressPercent` INTEGER NOT NULL,
                    `errorMessage` TEXT,
                    `enqueuedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`songId`)
                )
                """.trimIndent()
            )
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): NavinkDatabase =
        Room.databaseBuilder(context, NavinkDatabase::class.java, "navink.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides fun provideArtistDao(db: NavinkDatabase): ArtistDao = db.artistDao()
    @Provides fun provideAlbumDao(db: NavinkDatabase): AlbumDao = db.albumDao()
    @Provides fun provideSongDao(db: NavinkDatabase): SongDao = db.songDao()
    @Provides fun provideDownloadQueueDao(db: NavinkDatabase): DownloadQueueDao = db.downloadQueueDao()
}
