package com.example.bitrix_app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class BitrixResponse<T>(
    val result: T? = null,
    val total: Int? = null,
    val next: Int? = null,
    val error: String? = null,
    val error_description: String? = null
)
