package com.example.bitrix_app.domain.usecase

import com.example.bitrix_app.domain.model.ChecklistItem
import com.example.bitrix_app.domain.repository.TaskRepository
import javax.inject.Inject

class FetchChecklistUseCase @Inject constructor(
    private val repository: TaskRepository
) {
    suspend operator fun invoke(taskId: String, webhookUrl: String): Result<List<ChecklistItem>> {
        return repository.fetchChecklist(taskId, webhookUrl)
    }
}

class ToggleChecklistItemUseCase @Inject constructor(
    private val repository: TaskRepository
) {
    suspend operator fun invoke(taskId: String, itemId: String, action: String, webhookUrl: String): Result<Unit> {
        return repository.toggleChecklistItem(taskId, itemId, action, webhookUrl)
    }
}
