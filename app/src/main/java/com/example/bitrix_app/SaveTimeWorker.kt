package com.example.bitrix_app

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkerParameters
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.TimeUnit

class SaveTimeWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_TASK_ID = "TASK_ID"
        const val KEY_SECONDS = "SECONDS"
        const val KEY_USER_ID = "USER_ID"
        const val KEY_WEBHOOK_URL = "WEBHOOK_URL"
        const val KEY_USER_NAME = "USER_NAME"
    }

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID)
        val seconds = inputData.getInt(KEY_SECONDS, 0)
        val userId = inputData.getString(KEY_USER_ID)
        val webhookUrl = inputData.getString(KEY_WEBHOOK_URL)
        val userName = inputData.getString(KEY_USER_NAME) ?: "Unknown User"

        if (taskId.isNullOrBlank() || userId.isNullOrBlank() || webhookUrl.isNullOrBlank() || seconds <= 0) {
            Timber.e("SaveTimeWorker: Invalid input data. Cannot perform work.")
            return Result.failure()
        }

        Timber.i("SaveTimeWorker: Starting work for task $taskId, user $userName, seconds $seconds.")

        val client = OkHttpClient()
        val url = "${webhookUrl}task.elapseditem.add"

        val formBody = FormBody.Builder()
            .add("taskId", taskId)
            .add("arFields[SECONDS]", seconds.toString())
            .add("arFields[COMMENT_TEXT]", "Работа над задачей (синхронизировано офлайн)")
            .add("arFields[USER_ID]", userId)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()

        return try {
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val json = JSONObject(responseBody)
                if (json.has("result")) {
                    Timber.i("SaveTimeWorker: Successfully synced time for task $taskId.")
                    Result.success()
                } else {
                    val errorDesc = json.optString("error_description", "Unknown API error")
                    Timber.e("SaveTimeWorker: API error for task $taskId: $errorDesc")
                    // Это ошибка API, а не сети, поэтому, вероятно, не следует повторять попытку.
                    Result.failure()
                }
            } else {
                Timber.w("SaveTimeWorker: Server error for task $taskId. Code: ${response.code}. Retrying.")
                Result.retry()
            }
        } catch (e: IOException) {
            Timber.e(e, "SaveTimeWorker: Network error for task $taskId. Retrying.")
            Result.retry()
        } catch (e: Exception) {
            Timber.e(e, "SaveTimeWorker: Unexpected error for task $taskId. Failing.")
            Result.failure()
        }
    }
}
