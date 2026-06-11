package com.navink.data.repository

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.Configuration
import androidx.work.testing.WorkManagerTestInitHelper
import com.navink.data.local.NavinkDatabase
import com.navink.data.local.entity.AlbumEntity
import com.navink.data.local.entity.DownloadQueueEntity
import com.navink.data.local.entity.SongEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DownloadRepositoryTest {
    private lateinit var db: NavinkDatabase
    private lateinit var repo: DownloadRepository
    private lateinit var context: Context
    private lateinit var musicDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            context, Configuration.Builder().build()
        )
        db = Room.inMemoryDatabaseBuilder(context, NavinkDatabase::class.java)
            .allowMainThreadQueries().build()
        repo = DownloadRepository(context, db.songDao(), db.albumDao(), db.downloadQueueDao())
        musicDir = File(context.filesDir, "music").apply { mkdirs() }
    }

    @After
    fun tearDown() {
        db.close()
        musicDir.deleteRecursively()
    }

    private suspend fun seed(id: String, downloaded: Boolean = false): File {
        val f = File(musicDir, "$id.mp3")
        if (downloaded) f.writeText("AUDIO-$id")
        db.songDao().upsertAll(listOf(
            SongEntity(
                id = id, albumId = "a1", artistId = "ar1", title = "T$id", duration = 1,
                isDownloaded = downloaded, localPath = if (downloaded) f.absolutePath else null,
            )
        ))
        return f
    }

    @Test
    fun `enqueueAlbum queues only non-downloaded songs`() = runTest {
        db.albumDao().upsertAll(listOf(AlbumEntity(id = "a1", artistId = "ar1", name = "A")))
        seed("s1", downloaded = true)
        seed("s2")
        val queued = repo.enqueueAlbum("a1")
        assertEquals(1, queued)
        assertEquals(listOf("s2"), db.downloadQueueDao().queue().first().map { it.songId })
    }

    @Test
    fun `verifyDownloads clears flags for missing files`() = runTest {
        seed("s1", downloaded = true)
        val gone = seed("s2", downloaded = true)
        gone.delete()
        val repaired = repo.verifyDownloads()
        assertEquals(1, repaired)
        assertTrue(db.songDao().songById("s1")!!.isDownloaded)
        assertTrue(!db.songDao().songById("s2")!!.isDownloaded)
    }

    @Test
    fun `deleteAlbumDownloads removes files and clears flags`() = runTest {
        val f1 = seed("s1", downloaded = true)
        seed("s2")
        repo.deleteAlbumDownloads("a1")
        assertTrue(!f1.exists())
        assertTrue(!db.songDao().songById("s1")!!.isDownloaded)
    }

    @Test
    fun `enqueueAlbum requeues failed song for retry`() = runTest {
        db.albumDao().upsertAll(listOf(AlbumEntity(id = "a1", artistId = "ar1", name = "A")))
        seed("s1")
        db.downloadQueueDao().insertAll(listOf(
            DownloadQueueEntity(songId = "s1", title = "Ts1", status = DownloadQueueEntity.STATUS_QUEUED, enqueuedAt = 1L)
        ))
        db.downloadQueueDao().markFailed("s1", "network error")

        repo.enqueueAlbum("a1")

        val row = db.downloadQueueDao().queue().first().single { it.songId == "s1" }
        assertEquals(DownloadQueueEntity.STATUS_QUEUED, row.status)
        assertEquals(null, row.errorMessage)
    }

    @Test
    fun `deleteSongDownload removes queue row`() = runTest {
        seed("s1", downloaded = true)
        db.downloadQueueDao().insertAll(listOf(
            DownloadQueueEntity(songId = "s1", title = "Ts1", status = DownloadQueueEntity.STATUS_QUEUED, enqueuedAt = 1L)
        ))

        repo.deleteSongDownload("s1")

        assertTrue(db.downloadQueueDao().queue().first().isEmpty())
    }

    @Test
    fun `storageUsedBytes sums file sizes`() = runTest {
        seed("s1", downloaded = true)  // 8 bytes: "AUDIO-s1"
        seed("s2", downloaded = true)
        assertEquals(16, repo.storageUsedBytes())
    }
}
