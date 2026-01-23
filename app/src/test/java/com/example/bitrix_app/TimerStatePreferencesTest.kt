package com.example.bitrix_app

import android.content.Context
import android.content.SharedPreferences
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class TimerStatePreferencesTest {

    private lateinit var timerStatePrefs: TimerStatePreferences
    private val mockContext: Context = mock()
    private val mockSharedPrefs: SharedPreferences = mock()
    private val mockEditor: SharedPreferences.Editor = mock()

    @Before
    fun setUp() {
        whenever(mockContext.getSharedPreferences("TimerStatePrefs", Context.MODE_PRIVATE))
            .thenReturn(mockSharedPrefs)
        whenever(mockSharedPrefs.edit()).thenReturn(mockEditor)
        whenever(mockEditor.putString(any(), any())).thenReturn(mockEditor)
        whenever(mockEditor.putInt(any(), any())).thenReturn(mockEditor)
        whenever(mockEditor.putBoolean(any(), any())).thenReturn(mockEditor)
        whenever(mockEditor.putLong(any(), any())).thenReturn(mockEditor)
        whenever(mockEditor.remove(any())).thenReturn(mockEditor)
        whenever(mockEditor.apply()).thenAnswer { }

        timerStatePrefs = TimerStatePreferences(mockContext)
    }

    @Test
    fun `saveTimerState should save all fields correctly`() {
        // Given
        val userId = "123"
        val userName = "Test User"
        val activeTaskId = "456"
        val activeTaskTitle = "Test Task"
        val timerSeconds = 3600
        val initialSeconds = 1800
        val isUserPaused = false
        val timestamp = System.currentTimeMillis()

        // When
        timerStatePrefs.saveTimerState(
            userId = userId,
            userName = userName,
            activeTaskId = activeTaskId,
            activeTaskTitle = activeTaskTitle,
            timerSeconds = timerSeconds,
            initialSeconds = initialSeconds,
            isUserPaused = isUserPaused,
            lastSavedTimestamp = timestamp
        )

        // Then
        verify(mockEditor).putString("${userId}_userName", userName)
        verify(mockEditor).putString("${userId}_activeTaskId", activeTaskId)
        verify(mockEditor).putString("${userId}_activeTaskTitle", activeTaskTitle)
        verify(mockEditor).putInt("${userId}_timerSeconds", timerSeconds)
        verify(mockEditor).putInt("${userId}_initialSeconds", initialSeconds)
        verify(mockEditor).putBoolean("${userId}_isUserPaused", isUserPaused)
        verify(mockEditor).putLong("${userId}_lastSavedTimestamp", timestamp)
        verify(mockEditor).apply()
    }

    @Test
    fun `loadTimerState should return null when no active task exists`() {
        // Given
        val userId = "123"
        whenever(mockSharedPrefs.getString("${userId}_activeTaskId", null))
            .thenReturn(null)

        // When
        val result = timerStatePrefs.loadTimerState(userId)

        // Then
        assertNull(result)
    }

    @Test
    fun `loadTimerState should restore state correctly when active task exists`() {
        // Given
        val userId = "123"
        val userName = "Test User"
        val activeTaskId = "456"
        val activeTaskTitle = "Test Task"
        val timerSeconds = 3600
        val initialSeconds = 1800
        val isUserPaused = true
        val timestamp = System.currentTimeMillis() - 1000 // 1 second ago

        whenever(mockSharedPrefs.getString("${userId}_activeTaskId", null))
            .thenReturn(activeTaskId)
        whenever(mockSharedPrefs.getString("${userId}_userName", null))
            .thenReturn(userName)
        whenever(mockSharedPrefs.getString("${userId}_activeTaskTitle", null))
            .thenReturn(activeTaskTitle)
        whenever(mockSharedPrefs.getInt("${userId}_timerSeconds", 0))
            .thenReturn(timerSeconds)
        whenever(mockSharedPrefs.getInt("${userId}_initialSeconds", 0))
            .thenReturn(initialSeconds)
        whenever(mockSharedPrefs.getBoolean("${userId}_isUserPaused", false))
            .thenReturn(isUserPaused)
        whenever(mockSharedPrefs.getLong("${userId}_lastSavedTimestamp", 0))
            .thenReturn(timestamp)

        // When
        val result = timerStatePrefs.loadTimerState(userId)

        // Then
        assertNotNull(result)
        assertEquals(userId, result.userId)
        assertEquals(userName, result.userName)
        assertEquals(activeTaskId, result.activeTaskId)
        assertEquals(activeTaskTitle, result.activeTaskTitle)
        assertEquals(timerSeconds, result.timerSeconds) // Should not add elapsed time when paused
        assertEquals(initialSeconds, result.initialSeconds)
        assertEquals(isUserPaused, result.isUserPaused)
    }

    @Test
    fun `loadTimerState should add elapsed time when timer was not paused`() {
        // Given
        val userId = "123"
        val userName = "Test User"
        val activeTaskId = "456"
        val activeTaskTitle = "Test Task"
        val timerSeconds = 3600
        val initialSeconds = 1800
        val isUserPaused = false
        val elapsedTimeMs = 60000L // 60 seconds ago
        val timestamp = System.currentTimeMillis() - elapsedTimeMs
        val expectedElapsedSeconds = (elapsedTimeMs / 1000).toInt()
        val toleranceSeconds = 1 // Allow 1 second tolerance for test execution time

        whenever(mockSharedPrefs.getString("${userId}_activeTaskId", null))
            .thenReturn(activeTaskId)
        whenever(mockSharedPrefs.getString("${userId}_userName", null))
            .thenReturn(userName)
        whenever(mockSharedPrefs.getString("${userId}_activeTaskTitle", null))
            .thenReturn(activeTaskTitle)
        whenever(mockSharedPrefs.getInt("${userId}_timerSeconds", 0))
            .thenReturn(timerSeconds)
        whenever(mockSharedPrefs.getInt("${userId}_initialSeconds", 0))
            .thenReturn(initialSeconds)
        whenever(mockSharedPrefs.getBoolean("${userId}_isUserPaused", false))
            .thenReturn(isUserPaused)
        whenever(mockSharedPrefs.getLong("${userId}_lastSavedTimestamp", 0))
            .thenReturn(timestamp)

        // When
        val result = timerStatePrefs.loadTimerState(userId)

        // Then
        assertNotNull(result)
        val expectedMin = timerSeconds + expectedElapsedSeconds - toleranceSeconds
        val expectedMax = timerSeconds + expectedElapsedSeconds + toleranceSeconds
        // Should have added approximately 60 seconds (allow some margin for test execution time)
        assert(result.timerSeconds >= expectedMin && result.timerSeconds <= expectedMax) {
            "Expected timer seconds to be around ${timerSeconds + expectedElapsedSeconds}, but was ${result.timerSeconds}"
        }
    }

    @Test
    fun `clearTimerState should remove all fields`() {
        // Given
        val userId = "123"

        // When
        timerStatePrefs.clearTimerState(userId)

        // Then
        verify(mockEditor).remove("${userId}_userName")
        verify(mockEditor).remove("${userId}_activeTaskId")
        verify(mockEditor).remove("${userId}_activeTaskTitle")
        verify(mockEditor).remove("${userId}_timerSeconds")
        verify(mockEditor).remove("${userId}_initialSeconds")
        verify(mockEditor).remove("${userId}_isUserPaused")
        verify(mockEditor).remove("${userId}_lastSavedTimestamp")
        verify(mockEditor).apply()
    }

    @Test
    fun `getAllActiveUserIds should return users with active tasks`() {
        // Given
        val activeUsers = mapOf(
            "123_activeTaskId" to "task1",
            "456_activeTaskId" to "task2",
            "789_activeTaskId" to null, // No active task
            "123_userName" to "User 1", // Not a taskId key
            "999_activeTaskId" to "task3"
        )
        
        whenever(mockSharedPrefs.all).thenReturn(activeUsers)
        whenever(mockSharedPrefs.getString("123_activeTaskId", null)).thenReturn("task1")
        whenever(mockSharedPrefs.getString("456_activeTaskId", null)).thenReturn("task2")
        whenever(mockSharedPrefs.getString("789_activeTaskId", null)).thenReturn(null)
        whenever(mockSharedPrefs.getString("999_activeTaskId", null)).thenReturn("task3")

        // When
        val result = timerStatePrefs.getAllActiveUserIds()

        // Then
        assertEquals(3, result.size)
        assert(result.contains("123"))
        assert(result.contains("456"))
        assert(result.contains("999"))
        assert(!result.contains("789")) // Should not include user with null taskId
    }
}
