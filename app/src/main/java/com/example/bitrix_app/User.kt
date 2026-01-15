package com.example.bitrix_app.domain.model

data class User(
    val userId: String,
    val name: String,
    val webhookUrl: String,
    val avatar: String,
    val supervisorId: String? = null,
    val isActive: Boolean = true,
    val lastSelectedAt: Long? = null
)