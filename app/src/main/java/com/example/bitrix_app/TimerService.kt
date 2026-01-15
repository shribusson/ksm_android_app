package com.example.bitrix_app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.Locale

// Состояние таймера, управляемое сервисом
data class TimerServiceState(
    val userId: String, // ID пользователя, к которому относится это состояние
    val userName: String?,   // Имя пользователя для отображения
    val activeTaskId: String? = null,
    val activeTaskTitle: String? = null,
    val timerSeconds: Int = 0,
    val initialSeconds: Int = 0,
    val isUserPaused: Boolean = false
) {
    val isEffectivelyPaused: Boolean
        get() = isUserPaused
}

class TimerService : Service() {

    private val binder = LocalBinder()
    private val userTimerJobs = mutableMapOf<String, Job>() // Job для каждого пользователя
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Хранит состояния таймеров для всех пользователей (userId -> TimerServiceState)
    private val _allUserStates = MutableStateFlow<Map<String, TimerServiceState>>(emptyMap())

    // Выдает состояние таймера для текущего активного пользователя в UI
    private val _currentUserSpecificStateFlow = MutableStateFlow<TimerServiceState?>(null)
    val serviceStateFlow = _currentUserSpecificStateFlow.asStateFlow() // ViewModel будет подписываться на это

    private var currentUiUserId: String? = null
    private var currentUiUserName: String? = null


    inner class LocalBinder : Binder() {
        fun getService(): TimerService = this@TimerService
    }

    companion object {
        const val TIMER_SERVICE_CHANNEL_ID = "TimerServiceChannel"
        const val TIMER_SERVICE_CHANNEL_NAME = "Timer Service"
        const val TIMER_NOTIFICATION_ID = 1

        // Для управления сервисом через Intent (если не используется связывание для всего)
        // Однако, с Binder, эти Intent-ы могут быть не нужны для основного управления таймером
        const val ACTION_START_FOREGROUND_SERVICE = "com.example.bitrix_app.action.START_FOREGROUND_SERVICE"
        const val ACTION_STOP_FOREGROUND_SERVICE = "com.example.bitrix_app.action.STOP_FOREGROUND_SERVICE"

        // Можно добавить и другие actions, если потребуется управлять сервисом без прямого связывания
    }

    override fun onCreate() {
        super.onCreate()
        Timber.i("TimerService onCreate")
        createNotificationChannel()
        startForeground(TIMER_NOTIFICATION_ID, createNotification()) // Запускаем сразу с пустым уведомлением
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("TimerService onStartCommand, action: ${intent?.action}")
        when (intent?.action) {
            ACTION_START_FOREGROUND_SERVICE -> {
                Timber.i("TimerService received START_FOREGROUND_SERVICE action.")
                // Логика запуска уже в onCreate/startForeground
            }
            ACTION_STOP_FOREGROUND_SERVICE -> {
                Timber.i("TimerService stopping via intent...")
                stopAllTimerJobs()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        Timber.d("TimerService onBind")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Timber.d("TimerService onUnbind")
        // Если все клиенты отвязались, можно остановить таймер, если он не должен работать без UI.
        // Но для нашего случая, таймер должен продолжать работать.
        // stopAllTimerJobs() // Раскомментировать, если таймер должен останавливаться при отвязке всех клиентов
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Timber.i("TimerService onDestroy")
        stopAllTimerJobs()
        serviceScope.cancel() // Отменяем все корутины в serviceScope
        super.onDestroy()
    }

    // --- Публичные методы для управления из ViewModel через Binder ---
    fun setCurrentUser(userId: String, userName: String?) {
        this.currentUiUserId = userId
        this.currentUiUserName = userName
        _currentUserSpecificStateFlow.value = _allUserStates.value[userId] ?: TimerServiceState(userId = userId, userName = userName)
        updateNotification()
        Timber.d("Service: Current UI User set to $userName (ID: $userId)")
    }

    fun startTaskTimer(userId: String, userName: String?, taskId: String, taskTitle: String, initialSeconds: Int) {
        Timber.i("Service: Starting timer for task '$taskTitle' (ID: $taskId) for user $userName (ID: $userId) with initial time $initialSeconds seconds.")
        val newState = TimerServiceState(
            userId = userId,
            userName = userName,
            activeTaskId = taskId,
            activeTaskTitle = taskTitle,
            timerSeconds = initialSeconds, 
            initialSeconds = initialSeconds, // Store initial
            isUserPaused = false
        )
        _allUserStates.value = _allUserStates.value + (userId to newState)
        if (userId == currentUiUserId) {
            _currentUserSpecificStateFlow.value = newState
        }
        startTimerJob(userId)
        updateNotification()
    }

    fun stopTaskTimer(userId: String): Int {
        val userState = _allUserStates.value[userId] ?: return 0
        val currentTotalTime = userState.timerSeconds
        val initialTime = userState.initialSeconds
        val deltaSeconds = if (currentTotalTime >= initialTime) currentTotalTime - initialTime else 0
        
        Timber.i("Service: Stopping timer for task '${userState.activeTaskTitle}' for user ${userState.userName}. Total: $currentTotalTime, Initial: $initialTime, Delta: $deltaSeconds.")
        
        val newState = userState.copy(
            activeTaskId = null,
            activeTaskTitle = null,
            isUserPaused = false,
            initialSeconds = 0 // Reset
            // timerSeconds remains for UI if needed, but we returned delta
        )
        _allUserStates.value = _allUserStates.value + (userId to newState)
        if (userId == currentUiUserId) {
            _currentUserSpecificStateFlow.value = newState
        }
        stopTimerJob(userId)
        updateNotification()
        return deltaSeconds
    }

    fun userPauseTaskTimer(userId: String) {
        _allUserStates.value[userId]?.let { currentState ->
            if (currentState.activeTaskId != null) {
                val newState = currentState.copy(isUserPaused = true)
                _allUserStates.value = _allUserStates.value + (userId to newState)
                if (userId == currentUiUserId) {
                    _currentUserSpecificStateFlow.value = newState
                }
                Timber.i("Service: User paused timer for task '${currentState.activeTaskTitle}' for user ${currentState.userName}")
                updateNotification()
            }
        }
    }

    fun userResumeTaskTimer(userId: String) {
        _allUserStates.value[userId]?.let { currentState ->
            if (currentState.activeTaskId != null) {
                val newState = currentState.copy(isUserPaused = false)
                _allUserStates.value = _allUserStates.value + (userId to newState)
                if (userId == currentUiUserId) {
                    _currentUserSpecificStateFlow.value = newState
                }
                Timber.i("Service: User resumed timer for task '${currentState.activeTaskTitle}' for user ${currentState.userName}")
                startTimerJob(userId) // Убедимся, что таймер запущен
                updateNotification()
            }
        }
    }

    // Методы системной паузы удалены

    // --- Конец публичных методов ---

    private fun startTimerJob(userId: String) {
        if (userTimerJobs[userId]?.isActive == true) {
            return
        }
        userTimerJobs[userId]?.cancel() // Отменяем предыдущий job для этого пользователя, если есть

        userTimerJobs[userId] = serviceScope.launch {
            Timber.d("Timer job started for user ID: $userId.")
            try {
                while (isActive) {
                    delay(1000)
                    _allUserStates.value[userId]?.let { currentUserState ->
                        if (currentUserState.activeTaskId != null && !currentUserState.isEffectivelyPaused) {
                            val newSeconds = currentUserState.timerSeconds + 1
                            val updatedUserState = currentUserState.copy(timerSeconds = newSeconds)
                            _allUserStates.value = _allUserStates.value + (userId to updatedUserState)

                            if (userId == currentUiUserId) {
                                _currentUserSpecificStateFlow.value = updatedUserState
                                updateNotification() // Обновляем уведомление, если это текущий пользователь и таймер тикает
                            }
                        } else if (currentUserState.activeTaskId == null) {
                            Timber.d("No active task for user ID: $userId, stopping timer job from within.")
                            stopTimerJob(userId) // Это остановит внешний while isActive для этого job'a
                        }
                    } ?: run {
                        Timber.w("No state found for user ID: $userId in timer job. Stopping job.")
                        stopTimerJob(userId)
                    }
                }
            } catch (e: CancellationException) {
                Timber.i("Timer job cancelled for user ID: $userId.")
            } finally {
                Timber.d("Timer job finished for user ID: $userId.")
                // userTimerJobs.remove(userId) // Можно удалить job из карты после завершения
            }
        }
    }

    private fun stopTimerJob(userId: String) {
        userTimerJobs[userId]?.cancel()
        userTimerJobs.remove(userId)
        Timber.d("Timer job stopped for user ID: $userId.")
    }

    private fun stopAllTimerJobs() {
        Timber.d("Stopping all timer jobs.")
        userTimerJobs.keys.toList().forEach { userId -> // Копируем ключи, чтобы избежать ConcurrentModificationException
            stopTimerJob(userId)
        }
        userTimerJobs.clear()
    }

    private fun updateNotification() {
        val notification = createNotification()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(TIMER_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                TIMER_SERVICE_CHANNEL_ID,
                TIMER_SERVICE_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW // Используем LOW, чтобы не было звука/вибрации
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
            Timber.d("Notification channel created: $TIMER_SERVICE_CHANNEL_ID")
        }
    }

    private fun formatTimeForNotification(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format(Locale.getDefault(), "%02d:%02d", minutes, secs)
        }
    }

    private fun createNotification(): Notification {
        val uiState = _currentUserSpecificStateFlow.value // Состояние текущего пользователя в UI
        val title: String
        val text: String

        if (uiState != null && uiState.activeTaskId != null) {
            val taskTitle = uiState.activeTaskTitle ?: "Задача"
            val timeStr = formatTimeForNotification(uiState.timerSeconds)
            // Используем userName из состояния конкретного пользователя
            title = "${uiState.userName ?: "Таймер"}: $taskTitle"
            text = when {
                uiState.isUserPaused -> "Пауза - $timeStr"
                else -> "В работе - $timeStr"
            }
        } else {
            // Если у текущего UI пользователя нет активной задачи, или currentUiUserId не установлен
            val activeTimersCount = _allUserStates.value.values.count { it.activeTaskId != null && !it.isEffectivelyPaused }
            title = "${currentUiUserName ?: "Bitrix App"} - Таймер"
            if (activeTimersCount > 0) {
                text = "Нет активной задачи для Вас. Всего активных таймеров: $activeTimersCount"
            } else {
                text = "Нет активных задач"
            }
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        return NotificationCompat.Builder(this, TIMER_SERVICE_CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher) // Замените на свою иконку
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
