package com.example.bitrix_app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import com.example.bitrix_app.ui.screen.TaskListScreen
import com.example.bitrix_app.ui.theme.Bitrix_appTheme
import com.example.bitrix_app.ui.viewmodel.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    
    private var timerService: TimerService? = null
    private var isBound = false

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            val binder = service as TimerService.LocalBinder
            timerService = binder.getService()
            viewModel.connectToTimerService(timerService)
            isBound = true
            Timber.d("TimerService connected")
        }

        override fun onServiceDisconnected(arg0: ComponentName) {
            timerService = null
            viewModel.connectToTimerService(null)
            isBound = false
            Timber.d("TimerService disconnected")
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Start TimerService
        val intent = Intent(this, TimerService::class.java)
        startService(intent) // Ensure started
        bindService(intent, connection, Context.BIND_AUTO_CREATE)
        
        // Schedule SyncWorker
        val syncRequest = androidx.work.PeriodicWorkRequestBuilder<com.example.bitrix_app.worker.SyncWorker>(15, java.util.concurrent.TimeUnit.MINUTES)
            .setConstraints(androidx.work.Constraints.Builder()
                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                .build())
            .build()
        
        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "BitrixBackgroundSync",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )

        viewModel.initViewModel()

        setContent {
            Bitrix_appTheme {
                TaskListScreen(viewModel = viewModel)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(connection)
            isBound = false
        }
    }
}
