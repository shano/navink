package com.navink.download

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.navink.data.local.dao.SongDao
import com.navink.data.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val settingsRepository: SettingsRepository,
    private val songDao: SongDao,
    private val okHttpClient: OkHttpClient,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val songId = inputData.getString(KEY_SONG_ID) ?: return@withContext Result.failure()
        val creds = settingsRepository.getCredentials()

        // Auth params added by SubsonicAuthInterceptor — don't duplicate them here
        val url = "${creds.serverUrl}/rest/download.view?id=$songId"

        try {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()

            if (!response.isSuccessful) return@withContext Result.retry()

            val dir = resolveStorageDir()
                ?: return@withContext Result.failure()
            dir.mkdirs()
            val file = File(dir, "$songId.mp3")

            response.body?.byteStream()?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }

            songDao.setDownloaded(songId, file.absolutePath)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun resolveStorageDir(): File? {
        val location = settingsRepository.getStorageLocation()
        return if (location == "internal") {
            applicationContext.filesDir.resolve("music")
        } else {
            // "external": use last external dir — on devices with SD card this is the card
            val dirs = applicationContext.getExternalFilesDirs("music")
            dirs.lastOrNull() ?: applicationContext.getExternalFilesDir("music")
        }
    }

    companion object {
        const val KEY_SONG_ID = "song_id"
    }
}
