package com.navink.ui.player

import org.junit.Test
import kotlin.test.assertEquals

class PlayerViewModelTest {
    @Test
    fun `seekTargetMs converts fraction of duration to milliseconds`() {
        assertEquals(50_000L, PlayerViewModel.seekTargetMs(0.5f, 100_000L))
    }

    @Test
    fun `seekTargetMs clamps fraction below zero to start`() {
        assertEquals(0L, PlayerViewModel.seekTargetMs(-1f, 100_000L))
    }

    @Test
    fun `seekTargetMs clamps fraction above one to end`() {
        assertEquals(100_000L, PlayerViewModel.seekTargetMs(2f, 100_000L))
    }

    @Test
    fun `seekTargetMs returns zero when duration unknown`() {
        assertEquals(0L, PlayerViewModel.seekTargetMs(0.5f, 0L))
    }
}
