package com.example.bitrix_app.data.local.dao

import androidx.room.*
import com.example.bitrix_app.data.local.entity.SyncQueueEntity

data class SyncStatusStatistic(
    val status: String,
    val count: Int
)

@Dao
interface SyncQueueDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entity: SyncQueueEntity)

    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' AND nextRetryAt <= :currentTime ORDER BY createdAt ASC")
    fun getPendingOperationsReadyForRetry(currentTime: Long = System.currentTimeMillis()): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING'")
    fun getPendingCount(): Int

    @Query("SELECT status, COUNT(*) as count FROM sync_queue GROUP BY status")
    fun getStatusStatistics(): List<SyncStatusStatistic>

    @Query("UPDATE sync_queue SET status = :status WHERE id = :id")
    fun updateStatus(id: Long, status: String)

    @Query("UPDATE sync_queue SET status = :status, errorMessage = :error WHERE id = :id")
    fun updateStatusWithError(id: Long, status: String, error: String?)

    @Query("UPDATE sync_queue SET retryCount = :retryCount, lastAttemptAt = :lastAttempt, nextRetryAt = :nextRetry, errorMessage = :error WHERE id = :id")
    fun updateForRetry(id: Long, retryCount: Int, lastAttempt: Long, nextRetry: Long, error: String?)

    @Query("DELETE FROM sync_queue WHERE id = :id")
    fun deleteById(id: Long)

    @Query("DELETE FROM sync_queue WHERE status = 'COMPLETED'")
    fun deleteCompleted(): Int

    @Query("DELETE FROM sync_queue WHERE status = 'FAILED' AND retryCount >= maxRetries")
    fun deleteFailedExhausted(): Int

    @Query("DELETE FROM sync_queue")
    fun deleteAll(): Int
}