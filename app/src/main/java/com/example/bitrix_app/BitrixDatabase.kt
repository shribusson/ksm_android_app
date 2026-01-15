package com.example.bitrix_app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.bitrix_app.data.local.dao.SyncQueueDao
import com.example.bitrix_app.data.local.dao.TaskDao
import com.example.bitrix_app.data.local.entity.SyncQueueEntity
import com.example.bitrix_app.data.local.entity.TaskEntity

@Database(entities = [TaskEntity::class, SyncQueueEntity::class], version = 1, exportSchema = false)
abstract class BitrixDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao
    abstract fun syncQueueDao(): SyncQueueDao

    companion object {
        @Volatile
        private var INSTANCE: BitrixDatabase? = null

        fun getDatabase(context: Context): BitrixDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BitrixDatabase::class.java,
                    "bitrix_app_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}