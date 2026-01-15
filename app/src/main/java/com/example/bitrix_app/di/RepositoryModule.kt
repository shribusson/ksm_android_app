package com.example.bitrix_app.di

import com.example.bitrix_app.data.repository.TaskRepositoryImpl
import com.example.bitrix_app.domain.repository.TaskRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTaskRepository(
        taskRepositoryImpl: TaskRepositoryImpl
    ): TaskRepository

    @Binds
    @Singleton
    abstract fun bindUserRepository(
        userRepositoryImpl: com.example.bitrix_app.data.repository.UserRepositoryImpl
    ): com.example.bitrix_app.domain.repository.UserRepository
}
