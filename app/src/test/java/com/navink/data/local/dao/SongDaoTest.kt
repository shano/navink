package com.navink.data.local.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.navink.data.local.NavinkDatabase
import com.navink.data.local.entity.SongEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import android.app.Application
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SongDaoTest {
    private lateinit var db: NavinkDatabase
    private lateinit var dao: SongDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NavinkDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.songDao()
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `upsert and query songs for album`() = runTest {
        val songs = listOf(
            SongEntity(id = "s1", albumId = "a1", artistId = "ar1", title = "Track 1", trackNumber = 1, duration = 240),
            SongEntity(id = "s2", albumId = "a1", artistId = "ar1", title = "Track 2", trackNumber = 2, duration = 180),
        )
        dao.upsertAll(songs)
        val result = dao.songsForAlbum("a1").first()
        assertEquals(2, result.size)
        assertEquals("Track 1", result[0].title)
    }

    @Test
    fun `setDownloaded updates localPath`() = runTest {
        dao.upsertAll(listOf(
            SongEntity(id = "s1", albumId = "a1", artistId = "ar1", title = "Track 1", duration = 240)
        ))
        dao.setDownloaded("s1", "/data/music/track1.mp3")
        val song = dao.songById("s1")
        assertTrue(song!!.isDownloaded)
        assertEquals("/data/music/track1.mp3", song.localPath)
    }

    @Test
    fun `setStarred updates isStarred flag`() = runTest {
        dao.upsertAll(listOf(
            SongEntity(id = "s1", albumId = "a1", artistId = "ar1", title = "Track 1", duration = 240)
        ))
        dao.setStarred("s1", true)
        val starred = dao.starredSongs().first()
        assertEquals(1, starred.size)
        assertEquals("s1", starred[0].id)
    }
}
