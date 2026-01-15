package com.example.bitrix_app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TaskDto(
    @SerialName("id") val id: String? = null,
    @SerialName("title") val title: String? = null,
    @SerialName("description") val description: String? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("priority") val priority: String? = null,
    @SerialName("deadline") val deadline: String? = null,
    @SerialName("timeEstimate") val timeEstimate: String? = null,
    @SerialName("timeSpentInLogs") val timeSpentInLogs: String? = null,
    @SerialName("responsibleId") val responsibleId: String? = null,
    @SerialName("createdDate") val createdDate: String? = null,
    @SerialName("changedDate") val changedDate: String? = null,
    @SerialName("closedDate") val closedDate: String? = null,
    @SerialName("activityDate") val activityDate: String? = null,
    @SerialName("dateStart") val dateStart: String? = null,
    @SerialName("groupId") val groupId: String? = null
)

// Extension to handle case inconsistency if needed, or just cleaner mapping
fun TaskDto.getTimeEstimateSeconds(): Int {
    return timeEstimate?.toIntOrNull() ?: 0
}

fun TaskDto.getTimeSpentSeconds(): Int {
    return timeSpentInLogs?.toIntOrNull() ?: 0
}
