package com.example.bitrix_app.domain.usecase

import com.example.bitrix_app.domain.model.Task
import com.example.bitrix_app.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetTasksUseCase @Inject constructor(
    private val repository: TaskRepository
) {
    operator fun invoke(userId: String): Flow<List<Task>> {
        return repository.observeTasks(userId)
    }
}
