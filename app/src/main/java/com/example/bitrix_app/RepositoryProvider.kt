package com.example.bitrix_app.di

import android.content.Context
import com.example.bitrix_app.data.local.BitrixDatabase
import com.example.bitrix_app.data.repository.TaskRepositoryImpl
import com.example.bitrix_app.domain.repository.TaskRepository
import com.example.bitrix_app.domain.repository.UserRepository
import com.example.bitrix_app.domain.model.User
import okhttp3.OkHttpClient

object RepositoryProvider {
    private var taskRepository: TaskRepository? = null
    private var userRepository: UserRepository? = null
    private var httpClient: OkHttpClient? = null

    fun provideTaskRepository(context: Context): TaskRepository {
        return taskRepository ?: synchronized(this) {
            val db = BitrixDatabase.getDatabase(context)
            val client = provideHttpClient()
            val repo = TaskRepositoryImpl(db.taskDao(), db.syncQueueDao(), client)
            taskRepository = repo
            repo
        }
    }

    fun provideHttpClient(): OkHttpClient {
        return httpClient ?: synchronized(this) {
            OkHttpClient.Builder().build().also { httpClient = it }
        }
    }

    fun provideUserRepository(context: Context): UserRepository {
        return userRepository ?: object : UserRepository {
            override suspend fun getActiveUsers(): List<User> = emptyList()
            override suspend fun updateUser(user: User) {}
            override suspend fun saveUsers(users: List<User>) {}
        }.also { userRepository = it }
    }
}