package com.navink.data.repository

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.navink.data.local.NavinkDatabase
import com.navink.data.remote.SubsonicService
import com.navink.data.remote.dto.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class MusicRepositoryTest {
    private lateinit var db: NavinkDatabase
    private lateinit var service: SubsonicService
    private lateinit var syncRepo: SyncRepository
    private lateinit var musicRepo: MusicRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NavinkDatabase::class.java,
        ).allowMainThreadQueries().build()
        service = mockk()
        syncRepo = SyncRepository(service, db.artistDao(), db.albumDao(), db.songDao())
        musicRepo = MusicRepository(db.artistDao(), db.albumDao(), db.songDao())
    }

    @After
    fun tearDown() { db.close() }

    @Test
    fun `sync writes artists to Room and allArtists flow returns them`() = runTest {
        coEvery { service.getArtists() } returns SubsonicResponse(
            SubsonicResponseBody(
                status = "ok", version = "1.16.1",
                artists = ArtistsResult(index = listOf(
                    ArtistIndexDto("A", listOf(ArtistDto(id = "1", name = "ABBA", albumCount = 3)))
                ))
            )
        )
        coEvery { service.getArtist("1") } returns SubsonicResponse(
            SubsonicResponseBody(
                status = "ok", version = "1.16.1",
                artist = ArtistDetailDto(id = "1", name = "ABBA", album = listOf(
                    AlbumDto(id = "al1", artistId = "1", name = "Gold", year = 1992, songCount = 19)
                ))
            )
        )
        coEvery { service.getAlbum("al1") } returns SubsonicResponse(
            SubsonicResponseBody(
                status = "ok", version = "1.16.1",
                album = AlbumDetailDto(id = "al1", artistId = "1", name = "Gold",
                    song = listOf(SongDto(id = "s1", albumId = "al1", artistId = "1", title = "Dancing Queen", track = 1, duration = 230))
                )
            )
        )

        syncRepo.syncAll()

        val artists = musicRepo.allArtists().first()
        assertEquals(1, artists.size)
        assertEquals("ABBA", artists[0].name)
    }
}
