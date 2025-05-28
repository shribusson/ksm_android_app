package com.example.bitrix_app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import timber.log.Timber

class TimerService : Service() {

    companion object {
        const val TIMER_SERVICE_CHANNEL_ID = "TimerServiceChannel"
        const val TIMER_SERVICE_CHANNEL_NAME = "Timer Service"
        const val TIMER_NOTIFICATION_ID = 1
        const val ACTION_START_SERVICE = "com.example.bitrix_app.action.START_SERVICE"
        const val ACTION_STOP_SERVICE = "com.example.bitrix_app.action.STOP_SERVICE"
    }

    override fun onCreate() {
        super.onCreate()
        Timber.i("TimerService onCreate")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("TimerService onStartCommand, action: ${intent?.action}")

        when (intent?.action) {
            ACTION_START_SERVICE -> {
                startForeground(TIMER_NOTIFICATION_ID, createNotification("Таймер активен"))
                Timber.i("TimerService started in foreground.")
                // Здесь будет основная логика работы таймера в фоне
            }
            ACTION_STOP_SERVICE -> {
                Timber.i("TimerService stopping...")
                stopForeground(STOP_FOREGROUND_REMOVE) // Удаляем уведомление
                stopSelf() // Останавливаем сервис
                return START_NOT_STICKY // Не перезапускать сервис
            }
        }
        // START_STICKY - сервис будет перезапущен, если система его убьет
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? {
        Timber.d("TimerService onBind")
        // Пока не используем связывание, возвращаем null
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.i("TimerService onDestroy")
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

    private fun createNotification(contentText: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        return NotificationCompat.Builder(this, TIMER_SERVICE_CHANNEL_ID)
            .setContentTitle("Bitrix App Таймер")
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher) // Замените на свою иконку
            .setContentIntent(pendingIntent)
            .setOngoing(true) // Уведомление нельзя будет смахнуть
            .setSilent(true) // Не издавать звук при обновлении уведомления
            .build()
    }
}
