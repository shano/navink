package com.navink.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.navink.data.local.dao.AlbumDao
import com.navink.data.local.dao.DownloadQueueDao
import com.navink.data.local.dao.SongDao
import com.navink.data.local.entity.DownloadQueueEntity
import com.navink.data.local.entity.SongEntity
import com.navink.download.DownloadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val songDao: SongDao,
    private val albumDao: AlbumDao,
    private val queueDao: DownloadQueueDao,
) {
    fun queue(): Flow<List<DownloadQueueEntity>> = queueDao.queue()

    suspend fun enqueueSong(songId: String): Int {
        val song = songDao.songById(songId) ?: return 0
        return enqueueSongs(listOf(song))
    }

    suspend fun enqueueAlbum(albumId: String): Int =
        enqueueSongs(songDao.songsForAlbumOnce(albumId))

    suspend fun enqueueArtist(artistId: String): Int {
        val albums = albumDao.albumsForArtistOnce(artistId)
        return albums.sumOf { enqueueAlbum(it.id) }
    }

    private suspend fun enqueueSongs(songs: List<SongEntity>): Int {
        val pending = songs.filter { !it.isDownloaded }
        if (pending.isEmpty()) return 0
        val now = System.currentTimeMillis()
        queueDao.insertAll(pending.mapIndexed { i, s ->
            DownloadQueueEntity(
                songId = s.id,
                title = s.title,
                status = DownloadQueueEntity.STATUS_QUEUED,
                enqueuedAt = now + i,
            )
        })
        queueDao.requeueFailedByIds(pending.map { it.id })
        kickWorker()
        return pending.size
    }

    suspend fun deleteSongDownload(songId: String) {
        val song = songDao.songById(songId) ?: return
        song.localPath?.let { File(it).delete() }
        songDao.clearDownloaded(songId)
        queueDao.delete(songId)
    }

    suspend fun deleteAlbumDownloads(albumId: String) {
        songDao.downloadedSongsForAlbumOnce(albumId).forEach { deleteSongDownload(it.id) }
    }

    /** Clears download flags whose files are missing. Returns number repaired. */
    suspend fun verifyDownloads(): Int {
        var repaired = 0
        songDao.downloadedSongsOnce().forEach { s ->
            if (s.localPath == null || !File(s.localPath).exists()) {
                songDao.clearDownloaded(s.id)
                repaired++
            }
        }
        return repaired
    }

    suspend fun storageUsedBytes(): Long =
        songDao.downloadedSongsOnce().sumOf { s -> s.localPath?.let { File(it).length() } ?: 0L }

    suspend fun clearFailed() = queueDao.clearFailed()

    suspend fun retryFailed() {
        queueDao.requeueFailed()
        kickWorker()
    }

    /** Called on app start: requeue items orphaned in RUNNING by a process kill, resume draining. */
    suspend fun recoverQueue() {
        queueDao.resetRunning()
        if (queueDao.nextQueued() != null) kickWorker()
    }

    private fun kickWorker() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            DownloadWorker.WORK_NAME,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            OneTimeWorkRequestBuilder<DownloadWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build(),
        )
    }
}
