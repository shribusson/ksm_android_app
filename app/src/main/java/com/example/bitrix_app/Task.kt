package com.example.bitrix_app.domain.model

import java.util.Locale

data class Task(
    val id: String,
    val userId: String,
    val title: String,
    val description: String,
    val timeSpent: Int,
    val timeEstimate: Int,
    val status: String = "",
    val deadline: String? = null,
    val changedDate: String? = null,
    val tags: List<String> = emptyList(),
    val isImportant: Boolean = false,
    val syncStatus: String = "SYNCED",
    val createdAt: Long = 0,
    val updatedAt: Long = 0
) {
    val progressPercent: Int get() = if (timeEstimate > 0) (timeSpent * 100 / timeEstimate) else 0
    val isOverdue: Boolean get() = progressPercent > 100
    val isCompleted: Boolean get() = status == "5"
    val isInProgress: Boolean get() = status == "2"
    val isPending: Boolean get() = status == "3"
    val isWaitingForControl: Boolean get() = status == "4"

    val formattedTime: String get() {
        val spentHours = timeSpent / 3600
        val spentMinutes = (timeSpent % 3600) / 60
        val estimateHours = timeEstimate / 3600
        val estimateMinutes = (timeEstimate % 3600) / 60
        return String.format(Locale.getDefault(), "%d:%02d / %d:%02d", spentHours, spentMinutes, estimateHours, estimateMinutes)
    }
}