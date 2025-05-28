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
    val activeTaskId: String? = null,
    val activeTaskTitle: String? = null,
    val timerSeconds: Int = 0,
    val isUserPaused: Boolean = false,
    val isSystemPaused: Boolean = false,
    val currentUserName: String? = null
) {
    val isEffectivelyPaused: Boolean
        get() = isUserPaused || isSystemPaused
}

class TimerService : Service() {

    private val binder = LocalBinder()
    private var timerJob: Job? = null
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _serviceStateFlow = MutableStateFlow(TimerServiceState())
    val serviceStateFlow = _serviceStateFlow.asStateFlow()

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
                stopTimerJob()
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
        // stopTimerJob() // Раскомментировать, если таймер должен останавливаться при отвязке всех клиентов
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        Timber.i("TimerService onDestroy")
        stopTimerJob()
        serviceScope.cancel() // Отменяем все корутины в serviceScope
        super.onDestroy()
    }

    // --- Публичные методы для управления из ViewModel через Binder ---
    fun setCurrentUser(userName: String?) {
        _serviceStateFlow.value = _serviceStateFlow.value.copy(currentUserName = userName)
        updateNotification()
        Timber.d("Service: User set to $userName")
    }

    fun startTaskTimer(taskId: String, taskTitle: String) {
        Timber.i("Service: Starting timer for task '$taskTitle' (ID: $taskId)")
        // Если уже был активный таймер, его время "сбрасывается" (не сохраняется здесь, это делает ViewModel)
        _serviceStateFlow.value = _serviceStateFlow.value.copy(
            activeTaskId = taskId,
            activeTaskTitle = taskTitle,
            timerSeconds = 0,
            isUserPaused = false,
            isSystemPaused = false // При старте новой задачи системная пауза тоже снимается
        )
        startTimerJob()
        updateNotification()
    }

    fun stopTaskTimer(): Int {
        val currentTime = _serviceStateFlow.value.timerSeconds
        Timber.i("Service: Stopping timer for task '${_serviceStateFlow.value.activeTaskTitle}'. Final time: $currentTime seconds.")
        _serviceStateFlow.value = _serviceStateFlow.value.copy(
            activeTaskId = null,
            activeTaskTitle = null,
            // timerSeconds остается для ViewModel, чтобы забрать его
            isUserPaused = false,
            isSystemPaused = false
        )
        stopTimerJob()
        updateNotification()
        return currentTime // Возвращаем время для сохранения в ViewModel
    }

    fun userPauseTaskTimer() {
        if (_serviceStateFlow.value.activeTaskId != null) {
            _serviceStateFlow.value = _serviceStateFlow.value.copy(isUserPaused = true)
            Timber.i("Service: User paused timer for task '${_serviceStateFlow.value.activeTaskTitle}'")
            updateNotification()
            // Таймер Job сам остановит инкремент секунд из-за isEffectivelyPaused
        }
    }

    fun userResumeTaskTimer() {
        if (_serviceStateFlow.value.activeTaskId != null) {
            _serviceStateFlow.value = _serviceStateFlow.value.copy(isUserPaused = false)
            Timber.i("Service: User resumed timer for task '${_serviceStateFlow.value.activeTaskTitle}'")
            startTimerJob() // Убедимся, что таймер запущен, если он был остановлен
            updateNotification()
        }
    }

    fun systemPauseTaskTimer() {
        if (_serviceStateFlow.value.activeTaskId != null) {
            _serviceStateFlow.value = _serviceStateFlow.value.copy(isSystemPaused = true)
            Timber.i("Service: System paused timer for task '${_serviceStateFlow.value.activeTaskTitle}'")
            updateNotification()
        }
    }

    fun systemResumeTaskTimer() {
        if (_serviceStateFlow.value.activeTaskId != null) {
            // Возобновляем системную паузу, только если нет пользовательской паузы
            if (!_serviceStateFlow.value.isUserPaused) {
                _serviceStateFlow.value = _serviceStateFlow.value.copy(isSystemPaused = false)
                Timber.i("Service: System resumed timer for task '${_serviceStateFlow.value.activeTaskTitle}'")
                startTimerJob()
                updateNotification()
            } else {
                Timber.i("Service: System resume requested, but user pause is active for task '${_serviceStateFlow.value.activeTaskTitle}'. Keeping system pause effectively.")
                 // Если есть пользовательская пауза, то системную можно снять, но таймер все равно не пойдет.
                 // Для консистентности isSystemPaused можно установить в false.
                _serviceStateFlow.value = _serviceStateFlow.value.copy(isSystemPaused = false)
                updateNotification() // Обновить уведомление, т.к. isSystemPaused изменился
            }
        }
    }
    // --- Конец публичных методов ---

    private fun startTimerJob() {
        if (timerJob?.isActive == true) {
            // Timber.v("Timer job already active.")
            return
        }
        timerJob = serviceScope.launch {
            Timber.d("Timer job started.")
            try {
                while (isActive) { // Цикл работает, пока корутина активна
                    delay(1000)
                    val currentState = _serviceStateFlow.value
                    if (currentState.activeTaskId != null && !currentState.isEffectivelyPaused) {
                        _serviceStateFlow.value = currentState.copy(timerSeconds = currentState.timerSeconds + 1)
                        // Обновление уведомления здесь может быть слишком частым.
                        // Лучше обновлять уведомление при изменении состояния (старт/стоп/пауза/смена задачи).
                        // Но для отображения секунд в уведомлении - нужно. Решим позже, если будет проблема с производительностью.
                        updateNotification() // Обновляем уведомление с новым временем
                    } else if (currentState.activeTaskId == null) {
                        // Если нет активной задачи, останавливаем job, чтобы не тикал впустую
                        Timber.d("No active task, stopping timer job from within.")
                        stopTimerJob() // Это остановит внешний while isActive
                    }
                }
            } catch (e: CancellationException) {
                Timber.i("Timer job cancelled.")
            } finally {
                Timber.d("Timer job finished.")
            }
        }
    }

    private fun stopTimerJob() {
        timerJob?.cancel()
        timerJob = null
        Timber.d("Timer job stopped.")
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
        val state = _serviceStateFlow.value
        val title: String
        val text: String

        if (state.activeTaskId != null) {
            val taskTitle = state.activeTaskTitle ?: "Задача"
            val timeStr = formatTimeForNotification(state.timerSeconds)
            title = "${state.currentUserName ?: "Таймер"}: $taskTitle"
            text = when {
                state.isSystemPaused && state.isUserPaused -> "Пауза (система и пользователь) - $timeStr"
                state.isSystemPaused -> "Пауза (система) - $timeStr"
                state.isUserPaused -> "Пауза (пользователь) - $timeStr"
                else -> "В работе - $timeStr"
            }
        } else {
            title = "${state.currentUserName ?: "Bitrix App"} - Таймер"
            text = "Нет активной задачи"
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
