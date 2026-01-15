package com.example.bitrix_app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val description: String,
    val timeSpent: Int,
    val timeEstimate: Int,
    val status: String,
    val deadline: String?,
    val changedDate: String?,
    val tags: String, // Stored as comma-separated string
    val isImportant: Boolean,
    val syncStatus: String,
    val createdAt: Long,
    val updatedAt: Long
)