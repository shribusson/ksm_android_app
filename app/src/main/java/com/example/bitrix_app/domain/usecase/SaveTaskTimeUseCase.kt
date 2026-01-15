package com.example.bitrix_app.domain.usecase

import com.example.bitrix_app.domain.repository.TaskRepository
import javax.inject.Inject

class SaveTaskTimeUseCase @Inject constructor(
    private val repository: TaskRepository
) {
    suspend operator fun invoke(taskId: String, userId: String, webhookUrl: String, seconds: Int, comment: String = ""): Result<Unit> {
        return repository.saveTaskTime(taskId, userId, webhookUrl, seconds, comment)
    }
}
