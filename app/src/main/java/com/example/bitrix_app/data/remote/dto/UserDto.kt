package com.example.bitrix_app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    @SerialName("ID") val id: String,
    @SerialName("NAME") val name: String? = null,
    @SerialName("LAST_NAME") val lastName: String? = null,
    @SerialName("PERSONAL_PHOTO") val photoUrl: String? = null
)
