package com.example.bitrix_app.domain.usecase

import com.example.bitrix_app.domain.repository.TaskRepository
import javax.inject.Inject

class CreateTaskUseCase @Inject constructor(
    private val repository: TaskRepository
) {
    suspend operator fun invoke(
        title: String,
        userId: String,
        webhookUrl: String,
        estimateMinutes: Int,
        groupId: String
    ): Result<Unit> {
        // Default deadline to today EOD or just Now + 1 day? 
        // User said: "automatically set to today".
        // Today EOD? ex: 19:00? 
        // Or just current time?
        // Usually deadline "today" implies end of work day (18:00 or 19:00).
        // Let's set it to 19:00 of today.
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 19)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        
        return repository.createTask(title, userId, webhookUrl, estimateMinutes, groupId, calendar.timeInMillis)
    }
}
