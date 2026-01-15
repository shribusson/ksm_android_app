package com.example.bitrix_app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChecklistItemDto(
    @SerialName("ID") val id: String,
    @SerialName("TITLE") val title: String,
    @SerialName("IS_COMPLETE") val isComplete: String // "Y" or "N"
)
