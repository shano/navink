package com.navink.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.navink.data.local.entity.DownloadQueueEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadQueueDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<DownloadQueueEntity>)

    @Query("SELECT * FROM DownloadQueueEntity ORDER BY enqueuedAt ASC")
    fun queue(): Flow<List<DownloadQueueEntity>>

    @Query("SELECT * FROM DownloadQueueEntity WHERE status = 'QUEUED' ORDER BY enqueuedAt ASC LIMIT 1")
    suspend fun nextQueued(): DownloadQueueEntity?

    @Query("UPDATE DownloadQueueEntity SET status = 'RUNNING', progressPercent = 0 WHERE songId = :songId")
    suspend fun markRunning(songId: String)

    @Query("UPDATE DownloadQueueEntity SET progressPercent = :pct WHERE songId = :songId")
    suspend fun updateProgress(songId: String, pct: Int)

    @Query("UPDATE DownloadQueueEntity SET status = 'FAILED', errorMessage = :error WHERE songId = :songId")
    suspend fun markFailed(songId: String, error: String)

    @Query("DELETE FROM DownloadQueueEntity WHERE songId = :songId")
    suspend fun delete(songId: String)

    @Query("DELETE FROM DownloadQueueEntity WHERE status = 'FAILED'")
    suspend fun clearFailed()

    @Query("UPDATE DownloadQueueEntity SET status = 'QUEUED', errorMessage = NULL, progressPercent = 0 WHERE status = 'FAILED'")
    suspend fun requeueFailed()

    @Query("UPDATE DownloadQueueEntity SET status = 'QUEUED', errorMessage = NULL, progressPercent = 0 WHERE songId IN (:songIds) AND status = 'FAILED'")
    suspend fun requeueFailedByIds(songIds: List<String>)

    @Query("UPDATE DownloadQueueEntity SET status = 'QUEUED', progressPercent = 0 WHERE status = 'RUNNING'")
    suspend fun resetRunning()
}
