package com.example.bitrix_app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_queue")
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val operationType: String,
    val taskId: String,
    val userId: String,
    val payload: String,
    val status: String,
    val retryCount: Int,
    val maxRetries: Int,
    val lastAttemptAt: Long?,
    val nextRetryAt: Long,
    val errorMessage: String?,
    val createdAt: Long,
    val updatedAt: Long
)

object SyncStatus {
    const val PENDING = "PENDING"
    const val COMPLETED = "COMPLETED"
    const val FAILED = "FAILED"
    const val SYNCED = "SYNCED"
}