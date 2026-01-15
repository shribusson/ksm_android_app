package com.example.bitrix_app.domain.repository

import com.example.bitrix_app.domain.model.User

interface UserRepository {
    suspend fun getActiveUsers(): List<User>
    suspend fun updateUser(user: User)
    suspend fun saveUsers(users: List<User>)
}