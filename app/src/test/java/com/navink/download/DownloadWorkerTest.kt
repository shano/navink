package com.navink.download

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.navink.data.local.NavinkDatabase
import com.navink.data.local.entity.DownloadQueueEntity
import com.navink.data.local.entity.SongEntity
import com.navink.data.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
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
class DownloadWorkerTest {
    private lateinit var db: NavinkDatabase
    private lateinit var server: MockWebServer
    private lateinit var context: Context
    private val settingsRepository: SettingsRepository = mockk()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, NavinkDatabase::class.java)
            .allowMainThreadQueries().build()
        server = MockWebServer()
        server.start()
        coEvery { settingsRepository.getCredentials() } returns
            SettingsRepository.Credentials(server.url("/").toString().trimEnd('/'), "u", "p")
        coEvery { settingsRepository.getStorageLocation() } returns "internal"
    }

    @After
    fun tearDown() {
        db.close()
        server.shutdown()
        File(context.filesDir, "music").deleteRecursively()
    }

    private fun buildWorker(): DownloadWorker =
        TestListenableWorkerBuilder<DownloadWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(
                    appContext: Context,
                    workerClassName: String,
                    workerParameters: WorkerParameters,
                ): ListenableWorker = DownloadWorker(
                    appContext, workerParameters, settingsRepository,
                    db.songDao(), db.downloadQueueDao(), OkHttpClient(),
                )
            })
            .build() as DownloadWorker

    private suspend fun seedSong(id: String) {
        db.songDao().upsertAll(listOf(
            SongEntity(id = id, albumId = "a1", artistId = "ar1", title = "T$id", duration = 1)
        ))
        db.downloadQueueDao().insertAll(listOf(
            DownloadQueueEntity(songId = id, title = "T$id", status = DownloadQueueEntity.STATUS_QUEUED, enqueuedAt = System.currentTimeMillis())
        ))
    }

    @Test
    fun `drains queue sequentially and marks songs downloaded`() = runTest {
        seedSong("s1"); seedSong("s2")
        server.enqueue(MockResponse().setHeader("Content-Type", "audio/mpeg").setBody("AUDIO1"))
        server.enqueue(MockResponse().setHeader("Content-Type", "audio/mpeg").setBody("AUDIO2"))

        val result = buildWorker().doWork()

        assertTrue(result is ListenableWorker.Result.Success)
        val s1 = db.songDao().songById("s1")!!
        assertTrue(s1.isDownloaded)
        assertEquals("AUDIO1", File(s1.localPath!!).readText())
        assertTrue(db.songDao().songById("s2")!!.isDownloaded)
        assertTrue(db.downloadQueueDao().queue().first().isEmpty())
    }

    @Test
    fun `json error body marks item failed without writing audio`() = runTest {
        seedSong("s1")
        repeat(DownloadWorker.MAX_ATTEMPTS) {
            server.enqueue(
                MockResponse().setHeader("Content-Type", "application/json")
                    .setBody("""{"subsonic-response":{"status":"failed"}}""")
            )
        }

        buildWorker().doWork()

        val rows = db.downloadQueueDao().queue().first()
        assertEquals(DownloadQueueEntity.STATUS_FAILED, rows[0].status)
        assertTrue(!db.songDao().songById("s1")!!.isDownloaded)
        assertTrue(!File(context.filesDir, "music/s1.mp3").exists())
    }

    @Test
    fun `failed item does not block later items`() = runTest {
        seedSong("s1"); seedSong("s2")
        repeat(DownloadWorker.MAX_ATTEMPTS) { server.enqueue(MockResponse().setResponseCode(500)) }
        server.enqueue(MockResponse().setHeader("Content-Type", "audio/mpeg").setBody("AUDIO2"))

        buildWorker().doWork()

        val rows = db.downloadQueueDao().queue().first()
        assertEquals(listOf("s1"), rows.map { it.songId })
        assertEquals(DownloadQueueEntity.STATUS_FAILED, rows[0].status)
        assertTrue(db.songDao().songById("s2")!!.isDownloaded)
    }
}
