package com.example.bitrix_app.domain.repository

import com.example.bitrix_app.domain.model.Task
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    fun observeTasks(userId: String): Flow<List<Task>>
    fun observeTasksByStatus(userId: String, status: String): Flow<List<Task>>
    suspend fun getTaskById(taskId: String): Task?
    suspend fun refreshTasks(userId: String, webhookUrl: String): Result<Unit>
    suspend fun updateTimeSpent(taskId: String, seconds: Int): Result<Unit>
    suspend fun updateTaskStatus(taskId: String, newStatus: String): Result<Unit>
    suspend fun saveTaskTime(taskId: String, userId: String, webhookUrl: String, seconds: Int, comment: String): Result<Unit>
    suspend fun addComment(taskId: String, userId: String, webhookUrl: String, comment: String): Result<Unit>
    suspend fun completeTask(taskId: String, userId: String, webhookUrl: String): Result<Unit>
    suspend fun deleteAllForUser(userId: String)
    suspend fun getPendingSyncCount(): Int
    suspend fun getSyncQueueStatistics(): List<com.example.bitrix_app.data.local.dao.SyncStatusStatistic>
    suspend fun syncPendingOperations(): Result<Unit>
    suspend fun createTask(title: String, userId: String, webhookUrl: String, estimateMinutes: Int, groupId: String, deadline: Long?): Result<Unit>
    suspend fun deleteTask(taskId: String, userId: String, webhookUrl: String): Result<Unit>
    suspend fun fetchChecklist(taskId: String, webhookUrl: String): Result<List<com.example.bitrix_app.domain.model.ChecklistItem>>
    suspend fun toggleChecklistItem(taskId: String, itemId: String, action: String, webhookUrl: String): Result<Unit>
    suspend fun getGroups(webhookUrl: String): Result<List<Pair<String, String>>>
    suspend fun clearAllSyncQueue()
}