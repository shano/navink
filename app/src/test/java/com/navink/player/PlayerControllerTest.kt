package com.navink.player

import com.navink.data.local.entity.SongEntity
import org.junit.Test
import kotlin.test.assertEquals

class PlayerControllerTest {
    @Test
    fun `buildQueue returns songs from startIndex onwards`() {
        val songs = listOf(
            SongEntity(id = "s1", albumId = "a1", artistId = "ar1", title = "T1", duration = 100),
            SongEntity(id = "s2", albumId = "a1", artistId = "ar1", title = "T2", duration = 100),
            SongEntity(id = "s3", albumId = "a1", artistId = "ar1", title = "T3", duration = 100),
        )
        val queue = PlayerController.buildQueue(songs = songs, startSongId = "s2")
        assertEquals(2, queue.size)
        assertEquals("s2", queue[0].id)
        assertEquals("s3", queue[1].id)
    }

    @Test
    fun `buildQueue with unknown startSongId returns full list`() {
        val songs = listOf(
            SongEntity(id = "s1", albumId = "a1", artistId = "ar1", title = "T1", duration = 100),
        )
        val queue = PlayerController.buildQueue(songs = songs, startSongId = "unknown")
        assertEquals(1, queue.size)
    }
}
