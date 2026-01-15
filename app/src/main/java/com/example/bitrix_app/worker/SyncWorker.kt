package com.example.bitrix_app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.bitrix_app.domain.repository.TaskRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber

@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val taskRepository: TaskRepository
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Timber.d("SyncWorker started")
        return try {
            val result = taskRepository.syncPendingOperations()
            if (result.isSuccess) {
                Timber.d("SyncWorker success")
                Result.success()
            } else {
                Timber.e(result.exceptionOrNull(), "SyncWorker failed")
                Result.retry()
            }
        } catch (e: Exception) {
            Timber.e(e, "SyncWorker error")
            Result.failure()
        }
    }
}
