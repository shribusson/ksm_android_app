package com.example.bitrix_app.domain.usecase

import com.example.bitrix_app.domain.repository.TaskRepository
import javax.inject.Inject

class CompleteTaskUseCase @Inject constructor(
    private val repository: TaskRepository
) {
    suspend operator fun invoke(taskId: String, userId: String, webhookUrl: String): Result<Unit> {
        return repository.completeTask(taskId, userId, webhookUrl)
    }
}
