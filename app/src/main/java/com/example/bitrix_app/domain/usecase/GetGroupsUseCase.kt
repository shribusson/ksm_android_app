package com.example.bitrix_app.domain.usecase

import com.example.bitrix_app.domain.repository.TaskRepository
import javax.inject.Inject

class GetGroupsUseCase @Inject constructor(
    private val repository: TaskRepository
) {
    suspend operator fun invoke(webhookUrl: String): Result<List<Pair<String, String>>> {
        return repository.getGroups(webhookUrl)
    }
}
