package com.example.bitrix_app.data.local.mapper

import com.example.bitrix_app.data.local.entity.TaskEntity
import com.example.bitrix_app.domain.model.Task

fun TaskEntity.toDomain(): Task {
    return Task(
        id = id,
        userId = userId,
        title = title,
        description = description,
        timeSpent = timeSpent,
        timeEstimate = timeEstimate,
        status = status,
        deadline = deadline,
        changedDate = changedDate,
        tags = if (tags.isBlank()) emptyList() else tags.split(","),
        isImportant = isImportant,
        syncStatus = syncStatus,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

fun Task.toEntity(): TaskEntity {
    return TaskEntity(
        id = id,
        userId = userId,
        title = title,
        description = description,
        timeSpent = timeSpent,
        timeEstimate = timeEstimate,
        status = status,
        deadline = deadline,
        changedDate = changedDate,
        tags = tags.joinToString(","),
        isImportant = isImportant,
        syncStatus = syncStatus,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}