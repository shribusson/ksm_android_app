package com.example.bitrix_app.ui.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun formatDeadline(deadlineStr: String?): String? {
    if (deadlineStr.isNullOrBlank()) return null
    val outputFormatter = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    val parsers = listOf(
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()),
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    )

    for (parser in parsers) {
        try {
            val parsedDate = parser.parse(deadlineStr)
            if (parsedDate != null) {
                return outputFormatter.format(parsedDate)
            }
        } catch (e: Exception) {
            // continue
        }
    }
    return deadlineStr
}

fun formatTime(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return String.format("%d:%02d:%02d", hours, minutes, secs)
}
