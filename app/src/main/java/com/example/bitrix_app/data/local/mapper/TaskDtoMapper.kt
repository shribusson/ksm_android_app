package com.example.bitrix_app.data.local.mapper

import com.example.bitrix_app.data.local.entity.SyncStatus
import com.example.bitrix_app.data.local.entity.TaskEntity
import com.example.bitrix_app.data.remote.dto.TaskDto
import com.example.bitrix_app.data.remote.dto.getTimeEstimateSeconds
import com.example.bitrix_app.data.remote.dto.getTimeSpentSeconds

fun TaskDto.toEntity(userId: String): TaskEntity {
    return TaskEntity(
        id = id!!, // Safe because we filter null IDs before mapping
        userId = userId,
        title = title ?: "",
        description = description ?: "",
        timeSpent = getTimeSpentSeconds(),
        timeEstimate = getTimeEstimateSeconds(),
        status = status ?: "2",
        deadline = deadline,
        changedDate = null, // API usually gives changed date, add to DTO if needed
        tags = "", // Implement tags parsing if needed
        isImportant = priority == "2",
        syncStatus = SyncStatus.SYNCED,
        createdAt = try {
            val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", java.util.Locale.getDefault())
            format.parse(createdDate ?: "")?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        },
        updatedAt = System.currentTimeMillis()
    )
}
