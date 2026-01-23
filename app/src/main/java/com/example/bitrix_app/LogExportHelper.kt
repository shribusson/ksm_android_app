package com.example.bitrix_app

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import timber.log.Timber
import java.io.File

/**
 * Helper class for exporting application logs
 * Can be used to share logs via email or other apps for remote debugging
 */
object LogExportHelper {

    /**
     * Share the application log file via Android's share intent
     * This allows logs to be sent via email, messaging apps, cloud storage, etc.
     */
    fun shareLogFile(context: Context) {
        try {
            val logFile = FileLoggingTree.getLogFile(context)
            
            if (!logFile.exists()) {
                Timber.w("Log file does not exist")
                return
            }

            // Use FileProvider to share the log file securely
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                logFile
            )

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Bitrix App Logs")
                putExtra(Intent.EXTRA_TEXT, "Attached are the application logs from ${Build.MODEL}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooserIntent = Intent.createChooser(shareIntent, "Share logs via")
            chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooserIntent)
            
            Timber.i("Log file shared successfully")
        } catch (e: Exception) {
            Timber.e(e, "Error sharing log file")
        }
    }

    /**
     * Get the log file size in KB
     */
    fun getLogFileSizeKB(context: Context): Long {
        val logFile = FileLoggingTree.getLogFile(context)
        return if (logFile.exists()) {
            logFile.length() / 1024
        } else {
            0
        }
    }

    /**
     * Clear the log file
     */
    fun clearLogFile(context: Context) {
        try {
            val logFile = FileLoggingTree.getLogFile(context)
            if (logFile.exists()) {
                logFile.delete()
                Timber.i("Log file cleared")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error clearing log file")
        }
    }
}
