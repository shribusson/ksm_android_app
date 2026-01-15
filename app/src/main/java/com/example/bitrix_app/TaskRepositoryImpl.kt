package com.example.bitrix_app.data.repository

import com.example.bitrix_app.domain.model.Task
import com.example.bitrix_app.data.local.BitrixDatabase
import com.example.bitrix_app.data.local.dao.SyncQueueDao
import com.example.bitrix_app.data.local.dao.TaskDao
import com.example.bitrix_app.data.local.entity.SyncQueueEntity
import com.example.bitrix_app.data.local.entity.SyncStatus
import com.example.bitrix_app.data.local.entity.TaskEntity
import com.example.bitrix_app.data.local.mapper.toDomain
import com.example.bitrix_app.data.local.mapper.toEntity
import com.example.bitrix_app.domain.repository.TaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException

/**
 * Implementation TaskRepository с offline-first архитектурой.
 *
 * Ключевые принципы:
 * 1. Room DB - единственный источник правды (Single Source of Truth)
 * 2. Optimistic updates - UI обновляется моментально, sync в фоне
 * 3. Graceful degradation - offline режим прозрачен для пользователя
 * 4. Sync queue с retry - все failed операции сохраняются и retry автоматически
 */
class TaskRepositoryImpl(
    private val taskDao: TaskDao,
    private val syncQueueDao: SyncQueueDao,
    private val httpClient: OkHttpClient
) : TaskRepository {

    private val mutex = Mutex()  // Thread-safe operations
    private val json = Json { ignoreUnknownKeys = true }

    override fun observeTasks(userId: String): Flow<List<Task>> {
        return taskDao.observeTasksByUser(userId)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override fun observeTasksByStatus(userId: String, status: String): Flow<List<Task>> {
        return taskDao.observeTasksByUserAndStatus(userId, status)
            .map { entities -> entities.map { it.toDomain() } }
    }

    override suspend fun getTaskById(taskId: String): Task? = withContext(Dispatchers.IO) {
        taskDao.getTaskById(taskId)?.toDomain()
    }

    override suspend fun refreshTasks(userId: String, webhookUrl: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                try {
                    Timber.d("Refreshing tasks for user $userId from server")

                    // 1. Fetch from API
                    val remoteTasks = fetchTasksFromApi(webhookUrl, userId)

                    // 2. Update DB (single source of truth)
                    taskDao.deleteAllForUser(userId)
                    taskDao.insertAll(remoteTasks.map { it.toEntity() })

                    Timber.i("Successfully refreshed ${remoteTasks.size} tasks for user $userId")
                    Result.success(Unit)
                } catch (e: IOException) {
                    Timber.e(e, "Network error refreshing tasks, using cached data")
                    // Don't fail - we have cached data in DB
                    Result.failure(e)
                } catch (e: Exception) {
                    Timber.e(e, "Error refreshing tasks")
                    Result.failure(e)
                }
            }
        }

    override suspend fun updateTimeSpent(taskId: String, seconds: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                // Optimistic update to DB
                val currentTask = taskDao.getTaskById(taskId)
                if (currentTask != null) {
                    val newTimeSpent = currentTask.timeSpent + seconds
                    taskDao.updateTimeSpent(taskId, newTimeSpent)
                    Timber.d("Updated time spent for task $taskId: $newTimeSpent seconds")
                }
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to update time spent")
                Result.failure(e)
            }
        }

    override suspend fun updateTaskStatus(taskId: String, newStatus: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
                taskDao.updateStatus(taskId, newStatus)
                Timber.d("Updated status for task $taskId: $newStatus")
                Result.success(Unit)
            } catch (e: Exception) {
                Timber.e(e, "Failed to update task status")
                Result.failure(e)
            }
        }

    override suspend fun saveTaskTime(
        taskId: String,
        userId: String,
        webhookUrl: String,
        seconds: Int,
        comment: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Saving task time: taskId=$taskId, seconds=$seconds")

            // 1. Optimistic update to DB
            updateTimeSpent(taskId, seconds)

            // 2. Try immediate sync
            val syncResult = trySyncTaskTime(webhookUrl, taskId, seconds, comment, userId)

            if (syncResult.isSuccess) {
                Timber.i("Task time saved successfully to server")
                taskDao.updateSyncStatus(taskId, "SYNCED", System.currentTimeMillis())
                return@withContext Result.success(Unit)
            }

            // 3. Queue for later if sync failed
            Timber.w("Sync failed, queueing operation for retry")
            enqueueSyncOperation(
                operationType = "TIME_SAVE",
                taskId = taskId,
                userId = userId,
                payload = json.encodeToString(TimeSavePayload(webhookUrl, seconds, comment))
            )

            taskDao.updateSyncStatus(taskId, "PENDING", System.currentTimeMillis())
            Result.success(Unit)  // Success from user perspective
        } catch (e: Exception) {
            Timber.e(e, "Failed to save task time")

            // Queue for later
            enqueueSyncOperation(
                operationType = "TIME_SAVE",
                taskId = taskId,
                userId = userId,
                payload = json.encodeToString(TimeSavePayload(webhookUrl, seconds, comment))
            )

            Result.success(Unit)  // Don't fail UI
        }
    }

    override suspend fun addComment(
        taskId: String,
        userId: String,
        webhookUrl: String,
        comment: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Adding comment to task $taskId")

            // Try immediate sync
            val syncResult = trySyncComment(webhookUrl, taskId, comment, userId)

            if (syncResult.isSuccess) {
                Timber.i("Comment added successfully to server")
                return@withContext Result.success(Unit)
            }

            // Queue for later if sync failed
            Timber.w("Sync failed, queueing comment for retry")
            enqueueSyncOperation(
                operationType = "COMMENT_ADD",
                taskId = taskId,
                userId = userId,
                payload = json.encodeToString(CommentPayload(webhookUrl, comment))
            )

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to add comment")

            enqueueSyncOperation(
                operationType = "COMMENT_ADD",
                taskId = taskId,
                userId = userId,
                payload = json.encodeToString(CommentPayload(webhookUrl, comment))
            )

            Result.success(Unit)
        }
    }

    override suspend fun completeTask(
        taskId: String,
        userId: String,
        webhookUrl: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Completing task $taskId")

            // 1. Optimistic update to DB
            taskDao.updateStatus(taskId, "Завершена")

            // 2. Try immediate sync
            val syncResult = trySyncCompleteTask(webhookUrl, taskId)

            if (syncResult.isSuccess) {
                Timber.i("Task completed successfully on server")
                taskDao.updateSyncStatus(taskId, "SYNCED", System.currentTimeMillis())
                return@withContext Result.success(Unit)
            }

            // 3. Queue for later if sync failed
            Timber.w("Sync failed, queueing task completion for retry")
            enqueueSyncOperation(
                operationType = "TASK_COMPLETE",
                taskId = taskId,
                userId = userId,
                payload = json.encodeToString(TaskCompletePayload(webhookUrl))
            )

            taskDao.updateSyncStatus(taskId, "PENDING", System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to complete task")

            enqueueSyncOperation(
                operationType = "TASK_COMPLETE",
                taskId = taskId,
                userId = userId,
                payload = json.encodeToString(TaskCompletePayload(webhookUrl))
            )

            Result.success(Unit)
        }
    }

    override suspend fun deleteAllForUser(userId: String) = withContext(Dispatchers.IO) {
        taskDao.deleteAllForUser(userId)
        Timber.i("Deleted all tasks for user $userId")
    }

    override suspend fun getPendingSyncCount(): Int = withContext(Dispatchers.IO) {
        syncQueueDao.getPendingCount()
    }

    override suspend fun getSyncQueueStatistics(): List<com.example.bitrix_app.data.local.dao.SyncStatusStatistic> = withContext(Dispatchers.IO) {
        syncQueueDao.getStatusStatistics()
    }

    override suspend fun syncPendingOperations(): Result<Unit> = withContext(Dispatchers.IO) {
        // Получаем операции, которые ожидают синхронизации или готовы к повторной попытке
        val pendingOps = syncQueueDao.getPendingOperationsReadyForRetry() 
        var failCount = 0

        if (pendingOps.isEmpty()) {
            return@withContext Result.success(Unit)
        }

        Timber.d("Starting sync of ${pendingOps.size} pending operations")

        for (op in pendingOps) {
            try {
                val result = processOperation(op)
                if (result.isSuccess) {
                    syncQueueDao.updateStatus(op.id, SyncStatus.COMPLETED)
                    Timber.d("Operation ${op.id} (${op.operationType}) synced successfully")
                } else {
                    val errorMsg = result.exceptionOrNull()?.message
                    handleSyncFailure(op, errorMsg)
                    failCount++
                }
            } catch (e: Exception) {
                handleSyncFailure(op, e.message)
                failCount++
            }
        }

        if (failCount > 0) {
            Result.failure(IOException("Failed to sync $failCount operations"))
        } else {
            Result.success(Unit)
        }
    }

    override suspend fun createTask(
        title: String,
        userId: String,
        webhookUrl: String,
        estimateMinutes: Int,
        groupId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val tempTaskId = "temp_${System.currentTimeMillis()}"
        val currentTime = System.currentTimeMillis()

        // 1. Create local entity for immediate UI update
        val newTaskEntity = TaskEntity(
            id = tempTaskId,
            userId = userId,
            title = title,
            description = "",
            timeSpent = 0,
            timeEstimate = estimateMinutes * 60,
            status = "2", // Pending/New
            deadline = null,
            changedDate = null,
            tags = "",
            isImportant = false,
            syncStatus = SyncStatus.PENDING,
            createdAt = currentTime,
            updatedAt = currentTime
        )

        try {
            // 2. Insert locally first
            taskDao.insertAll(listOf(newTaskEntity))

            // 3. Try immediate sync
            val result = trySyncCreateTask(webhookUrl, title, userId, estimateMinutes, groupId)

            if (result.isFailure) {
                // Queue for later
                enqueueSyncOperation(
                    operationType = "TASK_CREATE",
                    taskId = tempTaskId,
                    userId = userId,
                    payload = json.encodeToString(TaskCreatePayload(webhookUrl, title, userId, estimateMinutes, groupId))
                )
            } else {
                // If success, mark as SYNCED so refreshTasks will replace it with real task from server
                taskDao.updateSyncStatus(tempTaskId, "SYNCED", System.currentTimeMillis())
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to create task")
            // Ensure queued if exception
            enqueueSyncOperation(
                operationType = "TASK_CREATE",
                taskId = tempTaskId,
                userId = userId,
                payload = json.encodeToString(TaskCreatePayload(webhookUrl, title, userId, estimateMinutes, groupId))
            )
            // Return success to UI so dialog closes and task appears
            Result.success(Unit)
        }
    }

    private suspend fun processOperation(op: SyncQueueEntity): Result<Unit> {
        return when (op.operationType) {
            "TIME_SAVE" -> {
                val payload = json.decodeFromString<TimeSavePayload>(op.payload)
                trySyncTaskTime(payload.webhookUrl, op.taskId, payload.seconds, payload.comment, op.userId)
            }
            "COMMENT_ADD" -> {
                val payload = json.decodeFromString<CommentPayload>(op.payload)
                trySyncComment(payload.webhookUrl, op.taskId, payload.comment, op.userId)
            }
            "TASK_COMPLETE" -> {
                val payload = json.decodeFromString<TaskCompletePayload>(op.payload)
                trySyncCompleteTask(payload.webhookUrl, op.taskId)
            }
            "TASK_CREATE" -> {
                val payload = json.decodeFromString<TaskCreatePayload>(op.payload)
                trySyncCreateTask(payload.webhookUrl, payload.title, payload.userId, payload.estimateMinutes, payload.groupId)
            }
            else -> Result.failure(Exception("Unknown operation type: ${op.operationType}"))
        }
    }

    private suspend fun handleSyncFailure(op: SyncQueueEntity, error: String?) {
        val newRetryCount = op.retryCount + 1
        Timber.w("Sync failed for op ${op.id}. Retry $newRetryCount/${op.maxRetries}. Error: $error")

        if (newRetryCount >= op.maxRetries) {
            syncQueueDao.updateStatusWithError(op.id, SyncStatus.FAILED, error)
        } else {
            // Exponential backoff: 1s, 2s, 4s, 8s...
            val delay = 1000L * (1 shl newRetryCount)
            val currentTime = System.currentTimeMillis()
            syncQueueDao.updateForRetry(op.id, newRetryCount, currentTime, currentTime + delay, error)
        }
    }

    // ========== Private Helper Methods ==========

    /**
     * Fetch tasks from Bitrix24 API
     */
    private fun fetchTasksFromApi(webhookUrl: String, userId: String): List<Task> {
        val safeWebhookUrl = if (webhookUrl.endsWith("/")) webhookUrl else "$webhookUrl/"
        val url = "${safeWebhookUrl}tasks.task.list"
        
        val tasksList = mutableListOf<JSONObject>()
        var start = 0
        
        while (true) {
            Timber.d("Fetching tasks page starting at $start")
            val formBody = FormBody.Builder()
                .add("filter[RESPONSIBLE_ID]", userId)
                // Removed filter[!STATUS]=5 to include completed tasks
                .add("start", start.toString())
                .build()

            val request = Request.Builder()
                .url(url)
                .post(formBody)
                .build()

            val response = httpClient.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                Timber.e("Failed to fetch tasks. HTTP ${response.code}: ${response.message}. Body: $errorBody")
                throw IOException("HTTP ${response.code}: ${response.message}")
            }

            val responseBody = response.body?.string() ?: throw IOException("Empty response body")
            val jsonResponse = JSONObject(responseBody)

            if (jsonResponse.has("error")) {
                val errorDescription = jsonResponse.getString("error_description")
                throw IOException("API error: $errorDescription")
            }

            val result = jsonResponse.get("result")
            var itemsInPage = 0

            when (result) {
                is JSONObject -> {
                    if (result.has("tasks")) {
                        val tasksArray = result.getJSONArray("tasks")
                        itemsInPage = tasksArray.length()
                        for (i in 0 until tasksArray.length()) {
                            tasksList.add(tasksArray.getJSONObject(i))
                        }
                    } else {
                        val keys = result.keys()
                        while (keys.hasNext()) {
                            val obj = result.optJSONObject(keys.next())
                            if (obj != null) {
                                tasksList.add(obj)
                                itemsInPage++
                            }
                        }
                    }
                }
                is JSONArray -> {
                    itemsInPage = result.length()
                    for (i in 0 until result.length()) {
                        tasksList.add(result.getJSONObject(i))
                    }
                }
            }
            
            if (jsonResponse.has("next")) {
                start = jsonResponse.getInt("next")
            } else {
                break
            }
        }

        val currentTime = System.currentTimeMillis()

        return tasksList.map { taskJson ->
            Task(
                id = taskJson.optString("id", taskJson.optString("ID")),
                userId = userId,  // Add userId
                title = taskJson.optString("title", taskJson.optString("TITLE", "Без названия")),
                description = taskJson.optString("description", taskJson.optString("DESCRIPTION", "")),
                timeSpent = taskJson.optInt("timeSpentInLogs", taskJson.optInt("TIME_SPENT_IN_LOGS", 0)),
                timeEstimate = taskJson.optInt("timeEstimate", taskJson.optInt("TIME_ESTIMATE", 0)),
                deadline = taskJson.optString("deadline", taskJson.optString("DEADLINE", null)),
                status = taskJson.optString("status", taskJson.optString("STATUS", "2")), // Default to "In Progress"
                tags = emptyList(),  // Parse from API if needed
                isImportant = taskJson.optString("priority", taskJson.optString("PRIORITY")) == "2",
                syncStatus = "SYNCED",
                createdAt = currentTime,
                updatedAt = currentTime
            )
        }
    }

    /**
     * Try to sync task time to server
     */
    private fun trySyncTaskTime(
        webhookUrl: String,
        taskId: String,
        seconds: Int,
        comment: String,
        userId: String
    ): Result<Unit> {
        return try {
            val url = "${webhookUrl}task.elapseditem.add.json"

            val requestBody = FormBody.Builder()
                .add("taskId", taskId)
                .add("arFields[SECONDS]", seconds.toString())
                .add("arFields[COMMENT_TEXT]", comment)
                .add("arFields[USER_ID]", userId)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(IOException("HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync task time")
            Result.failure(e)
        }
    }

    /**
     * Try to sync comment to server
     */
    private fun trySyncComment(
        webhookUrl: String,
        taskId: String,
        comment: String,
        userId: String
    ): Result<Unit> {
        return try {
            val url = "${webhookUrl}task.commentitem.add.json"

            val requestBody = FormBody.Builder()
                .add("taskId", taskId)
                .add("fields[POST_MESSAGE]", comment)
                .add("fields[AUTHOR_ID]", userId)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(IOException("HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync comment")
            Result.failure(e)
        }
    }

    /**
     * Try to sync task completion to server
     */
    private fun trySyncCompleteTask(
        webhookUrl: String,
        taskId: String
    ): Result<Unit> {
        return try {
            val url = "${webhookUrl}tasks.task.complete.json"

            val requestBody = FormBody.Builder()
                .add("taskId", taskId)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(IOException("HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync task completion")
            Result.failure(e)
        }
    }

    /**
     * Enqueue sync operation for later retry
     */
    private suspend fun enqueueSyncOperation(
        operationType: String,
        taskId: String,
        userId: String,
        payload: String
    ) {
        val operation = SyncQueueEntity(
            operationType = operationType,
            taskId = taskId,
            userId = userId,
            payload = payload,
            status = "PENDING",
            retryCount = 0,
            maxRetries = 5,
            lastAttemptAt = null,
            nextRetryAt = System.currentTimeMillis(),  // Immediate retry
            errorMessage = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        syncQueueDao.insert(operation)
        Timber.d("Enqueued sync operation: $operationType for task $taskId")

        // TODO: Trigger WorkManager to process queue
        // WorkManager.getInstance(context).enqueue(SyncQueueWorker.createWorkRequest())
    }

    // ========== Payload Data Classes ==========

    @Serializable
    private data class TimeSavePayload(
        val webhookUrl: String,
        val seconds: Int,
        val comment: String
    )

    @Serializable
    private data class CommentPayload(
        val webhookUrl: String,
        val comment: String
    )

    @Serializable
    private data class TaskCompletePayload(
        val webhookUrl: String
    )

    @Serializable
    private data class TaskCreatePayload(
        val webhookUrl: String,
        val title: String,
        val userId: String,
        val estimateMinutes: Int,
        val groupId: String
    )

    private suspend fun trySyncCreateTask(
        webhookUrl: String,
        title: String,
        userId: String,
        estimateMinutes: Int,
        groupId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("${webhookUrl}tasks.task.add")
                .post(FormBody.Builder()
                    .add("fields[TITLE]", title)
                    .add("fields[RESPONSIBLE_ID]", userId)
                    .add("fields[TIME_ESTIMATE]", (estimateMinutes * 60).toString())
                    .add("fields[GROUP_ID]", groupId)
                    .build())
                .build()

            val response = httpClient.newCall(request).execute()

            if (response.isSuccessful) {
                Timber.i("Task created: $title")
                Result.success(Unit)
            } else {
                Result.failure(IOException("HTTP ${response.code}"))
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to create task")
            Result.failure(e)
        }
    }
}
