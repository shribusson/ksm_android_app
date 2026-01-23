package com.example.bitrix_app

import android.content.Context
import android.content.SharedPreferences
import timber.log.Timber

/**
 * Manages persistence of timer state to prevent data loss on app kill/restart.
 * Stores active timer information for each user so work is not lost.
 */
class TimerStatePreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        "TimerStatePrefs",
        Context.MODE_PRIVATE
    )

    /**
     * Save the current timer state for a user
     */
    fun saveTimerState(
        userId: String,
        userName: String?,
        activeTaskId: String?,
        activeTaskTitle: String?,
        timerSeconds: Int,
        initialSeconds: Int,
        isUserPaused: Boolean,
        lastSavedTimestamp: Long = System.currentTimeMillis()
    ) {
        prefs.edit().apply {
            putString("${userId}_userName", userName)
            putString("${userId}_activeTaskId", activeTaskId)
            putString("${userId}_activeTaskTitle", activeTaskTitle)
            putInt("${userId}_timerSeconds", timerSeconds)
            putInt("${userId}_initialSeconds", initialSeconds)
            putBoolean("${userId}_isUserPaused", isUserPaused)
            putLong("${userId}_lastSavedTimestamp", lastSavedTimestamp)
            apply()
        }
        Timber.d("Saved timer state for user $userId: task=$activeTaskId, seconds=$timerSeconds")
    }

    /**
     * Load timer state for a user
     */
    fun loadTimerState(userId: String): TimerServiceState? {
        val activeTaskId = prefs.getString("${userId}_activeTaskId", null)
        
        // If no active task, return null
        if (activeTaskId == null) {
            return null
        }

        val userName = prefs.getString("${userId}_userName", null)
        val activeTaskTitle = prefs.getString("${userId}_activeTaskTitle", null)
        val timerSeconds = prefs.getInt("${userId}_timerSeconds", 0)
        val initialSeconds = prefs.getInt("${userId}_initialSeconds", 0)
        val isUserPaused = prefs.getBoolean("${userId}_isUserPaused", false)
        val lastSavedTimestamp = prefs.getLong("${userId}_lastSavedTimestamp", 0)

        // Calculate elapsed time since last save (if timer was not paused)
        val currentTime = System.currentTimeMillis()
        val elapsedSecondsSinceLastSave = if (!isUserPaused && lastSavedTimestamp > 0) {
            ((currentTime - lastSavedTimestamp) / 1000).toInt()
        } else {
            0
        }

        val adjustedTimerSeconds = timerSeconds + elapsedSecondsSinceLastSave

        Timber.i("Restored timer state for user $userId: task=$activeTaskId, seconds=$timerSeconds + $elapsedSecondsSinceLastSave elapsed = $adjustedTimerSeconds")

        return TimerServiceState(
            userId = userId,
            userName = userName,
            activeTaskId = activeTaskId,
            activeTaskTitle = activeTaskTitle,
            timerSeconds = adjustedTimerSeconds,
            initialSeconds = initialSeconds,
            isUserPaused = isUserPaused
        )
    }

    /**
     * Clear timer state for a user (called when timer is stopped/saved)
     */
    fun clearTimerState(userId: String) {
        prefs.edit().apply {
            remove("${userId}_userName")
            remove("${userId}_activeTaskId")
            remove("${userId}_activeTaskTitle")
            remove("${userId}_timerSeconds")
            remove("${userId}_initialSeconds")
            remove("${userId}_isUserPaused")
            remove("${userId}_lastSavedTimestamp")
            apply()
        }
        Timber.d("Cleared timer state for user $userId")
    }

    /**
     * Get all user IDs that have active timer state
     */
    fun getAllActiveUserIds(): Set<String> {
        val activeUsers = mutableSetOf<String>()
        prefs.all.keys.forEach { key ->
            if (key.endsWith("_activeTaskId")) {
                val userId = key.removeSuffix("_activeTaskId")
                val taskId = prefs.getString(key, null)
                if (taskId != null) {
                    activeUsers.add(userId)
                }
            }
        }
        return activeUsers
    }
}
