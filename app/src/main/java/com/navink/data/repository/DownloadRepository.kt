package com.navink.data.repository

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.navink.data.local.dao.SongDao
import com.navink.download.DownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao,
) {
    fun downloadSong(songId: String) {
        val data = Data.Builder().putString(DownloadWorker.KEY_SONG_ID, songId).build()
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(data)
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    suspend fun downloadAlbum(albumId: String) {
        val songs = songDao.songsForAlbumOnce(albumId)
        songs.filter { !it.isDownloaded }.forEach { downloadSong(it.id) }
    }
}
