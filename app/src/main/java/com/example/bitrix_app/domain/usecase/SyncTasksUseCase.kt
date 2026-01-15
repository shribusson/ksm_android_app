package com.example.bitrix_app.domain.usecase

import com.example.bitrix_app.domain.repository.TaskRepository
import javax.inject.Inject

class SyncTasksUseCase @Inject constructor(
    private val repository: TaskRepository
) {
    suspend operator fun invoke(userId: String, webhookUrl: String): Result<Unit> {
        return repository.refreshTasks(userId, webhookUrl)
    }
}
