package com.example.bitrix_app.di

import android.content.Context
import androidx.room.Room
import com.example.bitrix_app.data.local.BitrixDatabase
import com.example.bitrix_app.data.local.dao.SyncQueueDao
import com.example.bitrix_app.data.local.dao.TaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideBitrixDatabase(@ApplicationContext context: Context): BitrixDatabase {
        return BitrixDatabase.getDatabase(context)
    }

    @Provides
    fun provideTaskDao(database: BitrixDatabase): TaskDao {
        return database.taskDao()
    }

    @Provides
    fun provideSyncQueueDao(database: BitrixDatabase): SyncQueueDao {
        return database.syncQueueDao()
    }
}
