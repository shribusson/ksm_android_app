package com.example.bitrix_app.data.local.dao

import androidx.room.*
import com.example.bitrix_app.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE userId = :userId ORDER BY isImportant DESC, deadline ASC")
    fun observeTasksByUser(userId: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE userId = :userId")
    suspend fun getTasksByUser(userId: String): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE userId = :userId AND status = :status")
    fun observeTasksByUserAndStatus(userId: String, status: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :taskId")
    fun getTaskById(taskId: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertAll(tasks: List<TaskEntity>)

    @Query("DELETE FROM tasks WHERE userId = :userId")
    fun deleteAllForUser(userId: String)

    @Query("UPDATE tasks SET timeSpent = :newTimeSpent WHERE id = :taskId")
    fun updateTimeSpent(taskId: String, newTimeSpent: Int)

    @Query("UPDATE tasks SET status = :status WHERE id = :taskId")
    fun updateStatus(taskId: String, status: String)

    @Query("UPDATE tasks SET syncStatus = :status, updatedAt = :timestamp WHERE id = :taskId")
    fun updateSyncStatus(taskId: String, status: String, timestamp: Long)
}