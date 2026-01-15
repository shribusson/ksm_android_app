package com.example.bitrix_app.data.repository

import com.example.bitrix_app.data.local.preferences.EncryptedPreferences
import com.example.bitrix_app.domain.model.User
import com.example.bitrix_app.domain.repository.UserRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class UserRepositoryImpl @Inject constructor(
    private val encryptedPreferences: EncryptedPreferences,
    private val json: Json
) : UserRepository {

    override suspend fun getActiveUsers(): List<User> {
        val jsonString = encryptedPreferences.getUsersJson() ?: return emptyList()
        return try {
            json.decodeFromString<List<User>>(jsonString)
        } catch (e: Exception) {
            emptyList()
        }
    }

    override suspend fun updateUser(user: User) {
        val currentUsers = getActiveUsers().toMutableList()
        val index = currentUsers.indexOfFirst { it.userId == user.userId }
        if (index != -1) {
            currentUsers[index] = user
            saveUsers(currentUsers)
        }
    }

    override suspend fun saveUsers(users: List<User>) {
        val jsonString = json.encodeToString(users)
        encryptedPreferences.saveUsersJson(jsonString)
    }
}
