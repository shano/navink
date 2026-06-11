package com.navink.player

import com.navink.data.local.entity.SongEntity
import org.junit.Test
import kotlin.test.assertEquals

class PlayerControllerTest {
    private val songs = listOf(
        SongEntity(id = "s1", albumId = "a1", artistId = "ar1", title = "T1", duration = 100),
        SongEntity(id = "s2", albumId = "a1", artistId = "ar1", title = "T2", duration = 100),
        SongEntity(id = "s3", albumId = "a1", artistId = "ar1", title = "T3", duration = 100),
    )

    @Test
    fun `startIndex returns position of the chosen song`() {
        assertEquals(1, PlayerController.startIndex(songs, "s2"))
    }

    @Test
    fun `startIndex defaults to 0 for unknown song`() {
        assertEquals(0, PlayerController.startIndex(songs, "unknown"))
    }
}
