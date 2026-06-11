package com.navink.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.navink.data.local.NavinkDatabase
import com.navink.data.local.entity.AlbumEntity
import com.navink.data.local.entity.SongEntity
import com.navink.data.remote.SubsonicService
import com.navink.data.remote.dto.AlbumDetailDto
import com.navink.data.remote.dto.SongDto
import com.navink.data.remote.dto.SubsonicResponse
import com.navink.data.remote.dto.SubsonicResponseBody
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
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
class SyncRepositoryTest {
    private lateinit var db: NavinkDatabase
    private lateinit var repo: SyncRepository
    private val service: SubsonicService = mockk()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NavinkDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = SyncRepository(service, db.artistDao(), db.albumDao(), db.songDao())
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `syncAlbumSongs updates metadata but preserves download flags`() = runTest {
        db.albumDao().upsertAll(listOf(AlbumEntity(id = "a1", artistId = "ar1", name = "Album")))
        db.songDao().upsertAll(listOf(
            SongEntity(id = "s1", albumId = "a1", artistId = "ar1", title = "Old Title", duration = 100)
        ))
        db.songDao().setDownloaded("s1", "/m/s1.mp3")

        coEvery { service.getAlbum("a1") } returns SubsonicResponse(
            SubsonicResponseBody(
                status = "ok",
                version = "1.16.1",
                album = AlbumDetailDto(
                    id = "a1",
                    name = "",
                    song = listOf(SongDto(id = "s1", title = "New Title", track = 1, duration = 120)),
                ),
            )
        )

        repo.syncAlbumSongs("a1")

        val s1 = db.songDao().songById("s1")!!
        assertEquals("New Title", s1.title)
        assertTrue(s1.isDownloaded)
        assertEquals("/m/s1.mp3", s1.localPath)
    }
}
