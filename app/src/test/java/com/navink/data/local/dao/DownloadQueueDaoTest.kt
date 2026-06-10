package com.navink.data.local.dao

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.navink.data.local.NavinkDatabase
import com.navink.data.local.entity.DownloadQueueEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DownloadQueueDaoTest {
    private lateinit var db: NavinkDatabase
    private lateinit var dao: DownloadQueueDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NavinkDatabase::class.java,
        ).allowMainThreadQueries().build()
        dao = db.downloadQueueDao()
    }

    @After
    fun tearDown() { db.close() }

    private fun item(id: String, at: Long, status: String = DownloadQueueEntity.STATUS_QUEUED) =
        DownloadQueueEntity(songId = id, title = "T$id", status = status, enqueuedAt = at)

    @Test
    fun `nextQueued returns oldest queued item`() = runTest {
        dao.insertAll(listOf(item("s2", at = 2), item("s1", at = 1)))
        assertEquals("s1", dao.nextQueued()!!.songId)
    }

    @Test
    fun `insert ignores duplicate songId`() = runTest {
        dao.insertAll(listOf(item("s1", at = 1)))
        dao.markRunning("s1")
        dao.insertAll(listOf(item("s1", at = 99)))
        val rows = dao.queue().first()
        assertEquals(1, rows.size)
        assertEquals(DownloadQueueEntity.STATUS_RUNNING, rows[0].status)
    }

    @Test
    fun `markFailed stores error and is skipped by nextQueued`() = runTest {
        dao.insertAll(listOf(item("s1", at = 1)))
        dao.markFailed("s1", "HTTP 500")
        assertNull(dao.nextQueued())
        assertEquals("HTTP 500", dao.queue().first()[0].errorMessage)
    }

    @Test
    fun `requeueFailed resets failed rows to queued`() = runTest {
        dao.insertAll(listOf(item("s1", at = 1)))
        dao.markFailed("s1", "boom")
        dao.requeueFailed()
        assertEquals("s1", dao.nextQueued()!!.songId)
        assertNull(dao.queue().first()[0].errorMessage)
    }

    @Test
    fun `resetRunning returns orphaned running rows to queued`() = runTest {
        dao.insertAll(listOf(item("s1", at = 1)))
        dao.markRunning("s1")
        dao.resetRunning()
        assertEquals("s1", dao.nextQueued()!!.songId)
    }

    @Test
    fun `updateProgress stores percent on running row`() = runTest {
        dao.insertAll(listOf(item("s1", at = 1)))
        dao.markRunning("s1")
        dao.updateProgress("s1", 42)
        assertEquals(42, dao.queue().first()[0].progressPercent)
    }

    @Test
    fun `clearFailed removes only failed rows`() = runTest {
        dao.insertAll(listOf(item("s1", at = 1), item("s2", at = 2)))
        dao.markFailed("s1", "boom")
        dao.clearFailed()
        val rows = dao.queue().first()
        assertEquals(1, rows.size)
        assertEquals("s2", rows[0].songId)
    }
}
