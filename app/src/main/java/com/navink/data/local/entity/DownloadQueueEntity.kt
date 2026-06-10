package com.navink.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity
data class DownloadQueueEntity(
    @PrimaryKey val songId: String,
    val title: String,
    val status: String,
    val progressPercent: Int = 0,
    val errorMessage: String? = null,
    val enqueuedAt: Long,
) {
    companion object {
        const val STATUS_QUEUED = "QUEUED"
        const val STATUS_RUNNING = "RUNNING"
        const val STATUS_FAILED = "FAILED"
    }
}
