package com.example.bitrix_app.data.repository

import com.example.bitrix_app.data.local.dao.SyncQueueDao
import com.example.bitrix_app.data.local.dao.TaskDao
import com.example.bitrix_app.data.local.entity.SyncQueueEntity
import com.example.bitrix_app.data.local.entity.SyncStatus
import com.example.bitrix_app.data.local.entity.TaskEntity
import com.example.bitrix_app.data.local.mapper.toDomain
import com.example.bitrix_app.data.local.mapper.toEntity
import com.example.bitrix_app.data.remote.BitrixApi
import com.example.bitrix_app.domain.model.ChecklistItem
import com.example.bitrix_app.domain.model.Task
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
import timber.log.Timber
import java.io.IOException
import javax.inject.Inject

/**
 * Implementation TaskRepository with offline-first architecture.
 */
class TaskRepositoryImpl @Inject constructor(
    private val taskDao: TaskDao,
    private val syncQueueDao: SyncQueueDao,
    private val bitrixApi: BitrixApi,
    private val json: Json
) : TaskRepository {

    private val mutex = Mutex()

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
                    android.util.Log.d("BITRIX_REPO", "Refreshing tasks for user $userId from $webhookUrl")
                    Timber.d("Refreshing tasks for user $userId from server")

                    // 1. Fetch from API
                    // Sort by REAL_STATUS ASC (Active first) then DEADLINE ASC (Urgent first), then ID DESC
                    val params = mapOf(
                        "filter[RESPONSIBLE_ID]" to userId,
                        "order[REAL_STATUS]" to "asc", 
                        "order[DEADLINE]" to "asc",
                        "order[ID]" to "desc"
                    )
                    
                    val response = bitrixApi.getTasks(
                        url = "${webhookUrl}tasks.task.list",
                        params = params
                    )
                    
                    val taskDtos = response.result?.tasks ?: emptyList()
                    if (response.error != null) {
                         Timber.e("API Error in getTasks: ${response.error}")
                    }
                    // android.util.Log.d("BITRIX_REPO", "Received ${taskDtos.size} tasks from API")

                    // 2. Filter out tasks with null IDs (invalid data)
                    val validTasks = taskDtos.filter { it.id != null }
                    if (validTasks.size < taskDtos.size) {
                        android.util.Log.e("BITRIX_REPO", "Filtered out ${taskDtos.size - validTasks.size} tasks with null IDs")
                    }

                    // 3. Update DB (single source of truth)
                    val taskEntities = validTasks.map { it.toEntity(userId) }
                    
                    taskDao.deleteAllForUser(userId)
                    taskDao.insertAll(taskEntities)

                    android.util.Log.d("BITRIX_REPO", "Successfully refreshed ${taskEntities.size} tasks for user $userId")
                    Timber.i("Successfully refreshed ${taskEntities.size} tasks for user $userId")
                    Result.success(Unit)
                } catch (e: Exception) {
                    android.util.Log.e("BITRIX_REPO", "Error refreshing tasks", e)
                    Timber.e(e, "Error refreshing tasks")
                    Result.failure(e)
                }
            }
        }

    override suspend fun updateTimeSpent(taskId: String, seconds: Int): Result<Unit> =
        withContext(Dispatchers.IO) {
            try {
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
        android.util.Log.d("BITRIX_REPO", "saveTaskTime called for taskId=$taskId, seconds=$seconds")
        try {
            updateTimeSpent(taskId, seconds)

            val apiResult = try {
                 bitrixApi.addElapsedTime(
                    url = "${webhookUrl}task.elapseditem.add",
                    taskId = taskId,
                    seconds = seconds,
                    comment = comment,
                    userId = userId
                )
                Result.success(Unit)
            } catch (e: Exception) {
                android.util.Log.e("BITRIX_REPO", "API Failed for saveTaskTime", e)
                Result.failure(e)
            }

            if (apiResult.isSuccess) {
                android.util.Log.d("BITRIX_REPO", "API Success for saveTaskTime")
                taskDao.updateSyncStatus(taskId, SyncStatus.SYNCED, System.currentTimeMillis())
                return@withContext Result.success(Unit)
            }

            android.util.Log.d("BITRIX_REPO", "Enqueuing sync for TIME_SAVE")
            enqueueSyncOperation(
                operationType = "TIME_SAVE",
                taskId = taskId,
                userId = userId,
                payload = json.encodeToString(TimeSavePayload(webhookUrl, seconds, comment))
            )
            taskDao.updateSyncStatus(taskId, SyncStatus.PENDING, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("BITRIX_REPO", "Exception in saveTaskTime, enqueuing.", e)
            enqueueSyncOperation(
                operationType = "TIME_SAVE",
                taskId = taskId,
                userId = userId,
                payload = json.encodeToString(TimeSavePayload(webhookUrl, seconds, comment))
            )
            Result.success(Unit)
        }
    }

    override suspend fun addComment(
        taskId: String,
        userId: String,
        webhookUrl: String,
        comment: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        android.util.Log.d("BITRIX_REPO", "addComment called for taskId=$taskId")
        try {
            val apiResult = try {
                bitrixApi.addComment(
                    url = "${webhookUrl}task.commentitem.add",
                    taskId = taskId,
                    comment = comment,
                    userId = userId
                )
                Result.success(Unit)
            } catch (e: Exception) {
                 android.util.Log.e("BITRIX_REPO", "API Failed for addComment", e)
                 Result.failure(e)
            }

            if (apiResult.isSuccess) {
                android.util.Log.d("BITRIX_REPO", "API Success for addComment")
                return@withContext Result.success(Unit)
            }
            
            android.util.Log.d("BITRIX_REPO", "Enqueuing sync for COMMENT_ADD")
            enqueueSyncOperation(
                operationType = "COMMENT_ADD",
                taskId = taskId,
                userId = userId,
                payload = json.encodeToString(CommentPayload(webhookUrl, comment))
            )
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("BITRIX_REPO", "Exception in addComment, enqueuing.", e)
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
        android.util.Log.d("BITRIX_REPO", "completeTask called for taskId=$taskId")
        try {
            taskDao.updateStatus(taskId, "5") // 5 = Closed/Completed

            val apiResult = try {
                bitrixApi.completeTask(url = "${webhookUrl}tasks.task.complete", taskId = taskId)
                Result.success(Unit)
            } catch (e: Exception) {
                android.util.Log.e("BITRIX_REPO", "API Failed for completeTask", e)
                Result.failure(e)
            }

            if (apiResult.isSuccess) {
                android.util.Log.d("BITRIX_REPO", "API Success for completeTask")
                taskDao.updateSyncStatus(taskId, SyncStatus.SYNCED, System.currentTimeMillis())
                return@withContext Result.success(Unit)
            }

            android.util.Log.d("BITRIX_REPO", "Enqueuing sync for TASK_COMPLETE")
            enqueueSyncOperation(
                operationType = "TASK_COMPLETE",
                taskId = taskId,
                userId = userId,
                payload = json.encodeToString(TaskCompletePayload(webhookUrl))
            )
            taskDao.updateSyncStatus(taskId, SyncStatus.PENDING, System.currentTimeMillis())
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("BITRIX_REPO", "Exception in completeTask, enqueuing.", e)
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
    }

    override suspend fun getPendingSyncCount(): Int = withContext(Dispatchers.IO) {
        syncQueueDao.getPendingCount()
    }

    override suspend fun getSyncQueueStatistics() = withContext(Dispatchers.IO) {
        syncQueueDao.getStatusStatistics()
    }

    override suspend fun syncPendingOperations(): Result<Unit> = withContext(Dispatchers.IO) {
        val pendingOps = syncQueueDao.getPendingOperationsReadyForRetry() 
        var failCount = 0

        if (pendingOps.isEmpty()) {
            return@withContext Result.success(Unit)
        }

        android.util.Log.e("BITRIX_REPO", "Syncing ${pendingOps.size} pending operations")

        for (op in pendingOps) {
            try {
                android.util.Log.d("BITRIX_REPO", "Processing op ${op.id}: ${op.operationType} for task ${op.taskId}")
                val result = processOperation(op)
                if (result.isSuccess) {
                    // DELETE the operation instead of marking COMPLETED to prevent re-execution
                    syncQueueDao.deleteById(op.id)
                    android.util.Log.d("BITRIX_REPO", "Op ${op.id} SUCCESS - DELETED from queue")
                } else {
                    val errorMsg = result.exceptionOrNull()?.message
                    android.util.Log.e("BITRIX_REPO", "Op ${op.id} FAILED: $errorMsg")
                    handleSyncFailure(op, errorMsg)
                    failCount++
                }
            } catch (e: Exception) {
                android.util.Log.e("BITRIX_REPO", "Op ${op.id} EXCEPTION", e)
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
        groupId: String,
        deadline: Long?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val tempTaskId = "temp_${System.currentTimeMillis()}"
        val currentTime = System.currentTimeMillis()
        
        // Format deadline for API if present
        val deadlineStr = deadline?.let {
             // Bitrix usually accepts ISO 8601 or d.M.yyyy H:m:s
             // Use ISO
             java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.getDefault()).format(java.util.Date(it))
        }

        val newTaskEntity = TaskEntity(
            id = tempTaskId,
            userId = userId,
            title = title,
            description = "",
            timeSpent = 0,
            timeEstimate = estimateMinutes * 60,
            status = "2",
            deadline = deadlineStr,
            changedDate = null,
            tags = "",
            isImportant = false,
            syncStatus = SyncStatus.PENDING,
            createdAt = currentTime,
            updatedAt = currentTime
        )

        try {
            taskDao.insertAll(listOf(newTaskEntity))

            val apiResult = try {
                bitrixApi.createTask(
                    url = "${webhookUrl}tasks.task.add",
                    title = title,
                    responsibleId = userId,
                    timeEstimate = estimateMinutes * 60,
                    groupId = groupId,
                    deadline = deadlineStr
                )
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }

            if (apiResult.isFailure) {
                enqueueSyncOperation(
                    operationType = "TASK_CREATE",
                    taskId = tempTaskId,
                    userId = userId,
                    payload = json.encodeToString(TaskCreatePayload(webhookUrl, title, userId, estimateMinutes, groupId))
                )
            } else {
                taskDao.updateSyncStatus(tempTaskId, SyncStatus.SYNCED, System.currentTimeMillis())
            }
            Result.success(Unit)
        } catch (e: Exception) {
            enqueueSyncOperation(
                operationType = "TASK_CREATE",
                taskId = tempTaskId,
                userId = userId,
                payload = json.encodeToString(TaskCreatePayload(webhookUrl, title, userId, estimateMinutes, groupId))
            )
            Result.success(Unit)
        }
    }

    override suspend fun deleteTask(taskId: String, userId: String, webhookUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = try {
                bitrixApi.deleteTask(url = "${webhookUrl}tasks.task.delete", taskId = taskId)
            } catch (e: Exception) {
                null // Network error captured later
                // Actually if exception, we treat as RETRYABLE (null response)
                // We'll handle it below
                throw e
            }

            // Check for explicit API error
            if (response.error != null) {
                if (response.error == "ACCESS_DENIED" || response.error_description?.contains("Access denied", true) == true) {
                     Timber.e("Access Denied deleting task $taskId: ${response.error}")
                     return@withContext Result.failure(Exception("ACCESS_DENIED"))
                     // EXIT without deleting locally
                }
                // Other API errors (e.g. not found) -> maybe delete?
                // If "Task not found", we should delete locally to clean up.
                if (response.error == "TASK_NOT_FOUND" || response.error == "TASK_NOT_FOUND_OR_NOT_ACCESSIBLE") {
                     taskDao.deleteById(taskId)
                     return@withContext Result.success(Unit)
                }
                
                // Unknown API error: Retry via sync?
                Result.failure(Exception(response.error))
            } else {
                 // Success
                 taskDao.deleteById(taskId)
                 return@withContext Result.success(Unit)
            }
        } catch (e: Exception) {
             // Exception (Network etc) -> Queue for Sync and Delete Locally (Optimistic)
             // But wait, if it was Access Denied inside try block? We handled it.
             // This catch is for Network exceptions.
             
            enqueueSyncOperation(
                operationType = "TASK_DELETE",
                taskId = taskId,
                userId = userId,
                payload = json.encodeToString(TaskDeletePayload(webhookUrl))
            )
            taskDao.deleteById(taskId) 
            
            Result.success(Unit)
        }
    }

    override suspend fun fetchChecklist(taskId: String, webhookUrl: String): Result<List<ChecklistItem>> = withContext(Dispatchers.IO) {
        try {
            val response = bitrixApi.getTaskChecklist(url = "${webhookUrl}task.checklistitem.getlist", taskId = taskId)
            val items = response.result?.map { 
                ChecklistItem(
                    id = it.id,
                    title = it.title,
                    isComplete = it.isComplete == "Y"
                )
            } ?: emptyList()
            Result.success(items)
        } catch (e: Exception) {
            Timber.e(e, "Error fetching checklist")
            Result.failure(e)
        }
    }

    override suspend fun toggleChecklistItem(taskId: String, itemId: String, action: String, webhookUrl: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val urlAction = if (action == "renew") "task.checklistitem.renew" else "task.checklistitem.complete"
            
            val apiResult = try {
                 if (action == "renew") {
                     bitrixApi.renewChecklistItem(url = "${webhookUrl}task.checklistitem.renew", taskId = taskId, itemId = itemId)
                 } else {
                     bitrixApi.completeChecklistItem(url = "${webhookUrl}task.checklistitem.complete", taskId = taskId, itemId = itemId)
                 }
                 Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }

            if (apiResult.isSuccess) {
                return@withContext Result.success(Unit)
            }

            // Sync logic
            enqueueSyncOperation(
                operationType = "CHECKLIST_TOGGLE",
                taskId = taskId,
                userId = "0", // UserId might not be strictly needed for checklist toggle if webhook has auth context
                payload = json.encodeToString(ChecklistTogglePayload(webhookUrl, itemId, action))
            )
            Result.success(Unit)
        } catch (e: Exception) {
             enqueueSyncOperation(
                operationType = "CHECKLIST_TOGGLE",
                taskId = taskId,
                userId = "0",
                payload = json.encodeToString(ChecklistTogglePayload(webhookUrl, itemId, action))
            )
            Result.success(Unit)
        }
    }

    override suspend fun getGroups(webhookUrl: String): Result<List<Pair<String, String>>> = withContext(Dispatchers.IO) {
        try {
            val response = bitrixApi.getGroups(url = "${webhookUrl}sonet_group.get.json")
            val groups = response.result?.map { it.id to it.name } ?: emptyList()
            Result.success(groups)
        } catch (e: Exception) {
             Timber.e(e, "Error fetching groups")
             Result.failure(e)
        }
    }


    private suspend fun processOperation(op: SyncQueueEntity): Result<Unit> {
        return when (op.operationType) {
            "TIME_SAVE" -> {
                val payload = json.decodeFromString<TimeSavePayload>(op.payload)
                try {
                    bitrixApi.addElapsedTime(
                        url = "${payload.webhookUrl}task.elapseditem.add",
                        taskId = op.taskId,
                        seconds = payload.seconds,
                        comment = payload.comment,
                        userId = op.userId
                    )
                    Result.success(Unit)
                } catch(e: Exception) { Result.failure(e) }
            }
            "COMMENT_ADD" -> {
                val payload = json.decodeFromString<CommentPayload>(op.payload)
                try {
                    bitrixApi.addComment(
                        url = "${payload.webhookUrl}task.commentitem.add",
                        taskId = op.taskId,
                        comment = payload.comment,
                        userId = op.userId
                    )
                    Result.success(Unit)
                } catch(e: Exception) { Result.failure(e) }
            }
            "TASK_COMPLETE" -> {
                val payload = json.decodeFromString<TaskCompletePayload>(op.payload)
                try {
                    bitrixApi.completeTask(url = "${payload.webhookUrl}tasks.task.complete", taskId = op.taskId)
                    Result.success(Unit)
                } catch(e: Exception) { Result.failure(e) }
            }
            "TASK_CREATE" -> {
                 val payload = json.decodeFromString<TaskCreatePayload>(op.payload)
                 try {
                     bitrixApi.createTask(
                         url = "${payload.webhookUrl}tasks.task.add",
                         title = payload.title,
                         responsibleId = payload.userId,
                         timeEstimate = payload.estimateMinutes * 60,
                         groupId = payload.groupId
                     )
                     Result.success(Unit)
                 } catch(e: Exception) { Result.failure(e) }
            }
            "TASK_DELETE" -> {
                val payload = json.decodeFromString<TaskDeletePayload>(op.payload)
                try {
                    bitrixApi.deleteTask(url = "${payload.webhookUrl}tasks.task.delete", taskId = op.taskId)
                    Result.success(Unit)
                } catch(e: Exception) { Result.failure(e) }
            }
            "CHECKLIST_TOGGLE" -> {
                val payload = json.decodeFromString<ChecklistTogglePayload>(op.payload)
                try {
                    if (payload.action == "renew") {
                        bitrixApi.renewChecklistItem(url = "${payload.webhookUrl}task.checklistitem.renew", taskId = op.taskId, itemId = payload.itemId)
                    } else {
                        bitrixApi.completeChecklistItem(url = "${payload.webhookUrl}task.checklistitem.complete", taskId = op.taskId, itemId = payload.itemId)
                    }
                    Result.success(Unit)
                } catch(e: Exception) { Result.failure(e) }
            }
            else -> Result.failure(Exception("Unknown operation type: ${op.operationType}"))
        }
    }

    private suspend fun handleSyncFailure(op: SyncQueueEntity, error: String?) {
        val newRetryCount = op.retryCount + 1
        if (newRetryCount >= op.maxRetries) {
            syncQueueDao.updateStatusWithError(op.id, SyncStatus.FAILED, error)
        } else {
            val delay = 1000L * (1 shl newRetryCount)
            val currentTime = System.currentTimeMillis()
            syncQueueDao.updateForRetry(op.id, newRetryCount, currentTime, currentTime + delay, error)
        }
    }

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
            status = SyncStatus.PENDING,
            retryCount = 0,
            maxRetries = 5,
            lastAttemptAt = null,
            nextRetryAt = System.currentTimeMillis(),
            errorMessage = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        syncQueueDao.insert(operation)
    }

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

    @Serializable
    private data class TaskDeletePayload(
        val webhookUrl: String
    )

    @Serializable
    private data class ChecklistTogglePayload(
        val webhookUrl: String,
        val itemId: String,
        val action: String
    )

    override suspend fun clearAllSyncQueue(): Unit = withContext(Dispatchers.IO) {
        val deleted = syncQueueDao.deleteAll()
        android.util.Log.e("BITRIX_REPO", "Cleared $deleted sync queue entries")
    }
}
