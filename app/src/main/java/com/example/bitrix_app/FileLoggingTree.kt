package com.example.bitrix_app

import android.content.Context
import android.util.Log
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

class FileLoggingTree(private val context: Context) : Timber.DebugTree() {

    private val logDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        try {
            val logFile = getLogFile(context)
            val logTimeStamp = logDateFormat.format(Date())
            val priorityChar = when (priority) {
                Log.VERBOSE -> "V"
                Log.DEBUG -> "D"
                Log.INFO -> "I"
                Log.WARN -> "W"
                Log.ERROR -> "E"
                Log.ASSERT -> "A"
                else -> "?"
            }

            val logMessage = "$logTimeStamp $priorityChar/${tag ?: "App"}: $message\n"
            
            FileWriter(logFile, true).use {
                it.append(logMessage)
                if (t != null) {
                    it.append(Log.getStackTraceString(t))
                    it.append("\n")
                }
            }
        } catch (e: Exception) {
            // Если не удалось записать в файл, выводим в Logcat
            super.log(Log.ERROR, "FileLoggingTree", "Error writing to log file: ${e.message}", e)
            super.log(priority, tag, message, t) // Также выводим исходное сообщение
        }
    }

    companion object {
        private const val LOG_FILE_NAME = "app_log.txt"

        fun getLogFile(context: Context): File {
            // Сохраняем в директории, специфичной для приложения, во внешнем хранилище (доступна пользователю)
            // Не требует специальных разрешений на запись для API 29+
            // Для API < 29 может потребоваться WRITE_EXTERNAL_STORAGE, но minSdk = 24, targetSdk = 35
            // getExternalFilesDir доступен и без разрешений для записи в папку приложения.
            val logDir = context.getExternalFilesDir("logs")
            if (logDir != null && !logDir.exists()) {
                logDir.mkdirs()
            }
            return File(logDir, LOG_FILE_NAME)
        }
    }
}
