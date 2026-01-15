package com.example.bitrix_app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GroupDto(
    @SerialName("ID") val id: String,
    @SerialName("NAME") val name: String
)
