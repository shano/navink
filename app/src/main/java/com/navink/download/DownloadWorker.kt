package com.navink.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.navink.data.local.dao.DownloadQueueDao
import com.navink.data.local.dao.SongDao
import com.navink.data.local.entity.DownloadQueueEntity
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
    private val queueDao: DownloadQueueDao,
    private val okHttpClient: OkHttpClient,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        queueDao.resetRunning()
        // Foreground keeps the drain alive past JobScheduler's ~10 min execution cap
        try { setForeground(foregroundInfo("Downloading…", 0)) } catch (_: Exception) {}
        while (true) {
            val item = queueDao.nextQueued() ?: break
            queueDao.markRunning(item.songId)
            var error: String? = null
            for (attempt in 1..MAX_ATTEMPTS) {
                error = try {
                    downloadOne(item)
                } catch (e: Exception) {
                    e.message ?: e.javaClass.simpleName
                }
                if (error == null) break
            }
            if (error == null) queueDao.delete(item.songId) else queueDao.markFailed(item.songId, error)
        }
        Result.success()
    }

    /** Returns null on success, or an error message. */
    private suspend fun downloadOne(item: DownloadQueueEntity): String? {
        val creds = settingsRepository.getCredentials()
        // Auth params added by SubsonicAuthInterceptor — don't duplicate them here
        val url = "${creds.serverUrl}/rest/download.view?id=${item.songId}"
        okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return "HTTP ${response.code}"
            val contentType = response.header("Content-Type").orEmpty()
            // Subsonic reports errors as HTTP 200 with a JSON/XML body
            if ("json" in contentType || "xml" in contentType) return "Server returned an error"
            val body = response.body ?: return "Empty response"
            val dir = resolveStorageDir() ?: return "Storage unavailable"
            dir.mkdirs()
            val part = File(dir, "${item.songId}.mp3.part")
            val total = body.contentLength()
            var lastPct = 0
            body.byteStream().use { input ->
                part.outputStream().use { output ->
                    val buf = ByteArray(64 * 1024)
                    var copied = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n == -1) break
                        output.write(buf, 0, n)
                        copied += n
                        if (total > 0) {
                            val pct = (copied * 100 / total).toInt()
                            if (pct >= lastPct + 5) {
                                lastPct = pct
                                queueDao.updateProgress(item.songId, pct)
                                notifyProgress(item.title, pct)
                            }
                        }
                    }
                }
            }
            val final = File(dir, "${item.songId}.mp3")
            if (final.exists()) final.delete()
            if (!part.renameTo(final)) return "Could not finalise file"
            songDao.setDownloaded(item.songId, final.absolutePath)
            return null
        }
    }

    private suspend fun resolveStorageDir(): File? {
        val location = settingsRepository.getStorageLocation()
        return if (location == "internal") {
            applicationContext.filesDir.resolve("music")
        } else {
            // "external": use last external dir — on devices with SD card this is the card
            val dirs = applicationContext.getExternalFilesDirs("music")
            dirs.filterNotNull().lastOrNull() ?: applicationContext.getExternalFilesDir("music")
        }
    }

    private fun foregroundInfo(text: String, progress: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Navink downloads")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, progress, progress == 0)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun notifyProgress(title: String, pct: Int) {
        try { setForegroundAsync(foregroundInfo("$title — $pct%", pct)) } catch (_: Exception) {}
    }

    companion object {
        const val MAX_ATTEMPTS = 3
        const val CHANNEL_ID = "downloads"
        const val NOTIFICATION_ID = 2001
        const val WORK_NAME = "navink-download-drain"
    }
}
