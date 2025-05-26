package com.example.bitrix_app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bitrix_app.ui.theme.Bitrix_appTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.*

// Модели данных
data class User(
    val name: String,
    val webhookUrl: String,
    val userId: String,
    val avatar: String,
    var photoUrl: String = ""
)

data class UserTimerState(
    var activeTaskId: String? = null,
    var timerSeconds: Int = 0,
    var pausedTaskId: String? = null,
    var pausedSeconds: Int = 0,
    var isTimerPaused: Boolean = false
)

data class Task(
    val id: String,
    val title: String,
    val description: String,
    val timeSpent: Int,
    val timeEstimate: Int,
    val status: String = "",
    val createdDate: String = "",
    val deadline: String = "",
    var isTimerRunning: Boolean = false
) {
    val progressPercent: Int get() = if (timeEstimate > 0) (timeSpent * 100 / timeEstimate) else 0
    val isOverdue: Boolean get() = progressPercent > 100
    val isCompleted: Boolean get() = status == "5" // 5 = Завершена
    val isInProgress: Boolean get() = status == "2" // 2 = В работе
    val isPending: Boolean get() = status == "3" // 3 = Ждет выполнения

    val statusText: String get() = when (status) {
        "1" -> "Новая"
        "2" -> "В работе"
        "3" -> "Ждет выполнения"
        "4" -> "Предположительно завершена"
        "5" -> "Завершена"
        "6" -> "Отложена"
        "7" -> "Отклонена"
        else -> "Неизвестный статус"
    }

    val formattedTime: String get() {
        val spentHours = timeSpent / 3600
        val spentMinutes = (timeSpent % 3600) / 60
        val estimateHours = timeEstimate / 3600
        val estimateMinutes = (timeEstimate % 3600) / 60
        return String.format("%d:%02d / %d:%02d", spentHours, spentMinutes, estimateHours, estimateMinutes)
    }

    val formattedCreatedDate: String get() {
        return if (createdDate.isNotEmpty()) {
            try {
                // Извлекаем дату из формата "2025-04-25T05:32:42+05:00"
                createdDate.substring(0, 10)
            } catch (e: Exception) {
                createdDate
            }
        } else {
            "Не указана"
        }
    }

    val formattedDeadline: String get() {
        return if (deadline.isNotEmpty()) {
            try {
                deadline.substring(0, 10)
            } catch (e: Exception) {
                deadline
            }
        } else {
            "Не указан"
        }
    }
}

enum class WorkStatus { BEFORE_WORK, WORKING, BREAK, LUNCH, AFTER_WORK }

// ViewModel
class MainViewModel : ViewModel() {
    private val client = OkHttpClient()

    // Пользователи с их ID в системе и аватарами
    val users = listOf(
        User("Денис Мелков", "https://bitrix.tooksm.kz/rest/320/gwx0v32nqbiwu7ww/", "320", "ДМ"),
        User("Владислав Малай", "https://bitrix.tooksm.kz/rest/321/smczp19q348xui28/", "321", "ВМ"),
        User("Ким Филби", "https://bitrix.tooksm.kz/rest/253/tk5y2f3sukqxn5bi/", "253", "КФ")
    )

    var currentUserIndex by mutableStateOf(0)
    var tasks by mutableStateOf<List<Task>>(emptyList())
    var workStatus by mutableStateOf(WorkStatus.WORKING)
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)

    // Состояние таймеров для каждого пользователя
    private val userTimerStates = mutableMapOf<String, UserTimerState>()
    var currentTime by mutableStateOf("")

    // Инициализация состояний таймеров для всех пользователей
    init {
        users.forEach { user ->
            userTimerStates[user.userId] = UserTimerState()
        }
        updateWorkStatus()
        loadTasks()
        startPeriodicUpdates()
        startPeriodicTaskUpdates()
        startTimeUpdates()
        loadUserPhotos()
        startGlobalTimer()
    }

    // Получение текущего состояния таймера
    private fun getCurrentTimerState(): UserTimerState {
        return userTimerStates[users[currentUserIndex].userId] ?: UserTimerState()
    }

    // Геттеры для UI (работают с текущим пользователем)
    val activeTimer: String? get() = getCurrentTimerState().activeTaskId
    val timerSeconds: Int get() = getCurrentTimerState().timerSeconds
    val pausedTimerTaskId: String? get() = getCurrentTimerState().pausedTaskId
    val pausedTimerSeconds: Int get() = getCurrentTimerState().pausedSeconds
    val isTimerPaused: Boolean get() = getCurrentTimerState().isTimerPaused

    fun switchUser(index: Int) {
        // Просто переключаем пользователя, таймеры продолжают работать независимо
        currentUserIndex = index
        loadTasks()
    }

    fun loadTasks() {
        isLoading = true
        errorMessage = null
        val user = users[currentUserIndex]

        // Получаем ВСЕ задачи пользователя без фильтрации по статусу
        val url = "${user.webhookUrl}tasks.task.list" +
                "?filter[RESPONSIBLE_ID]=${user.userId}" +
                "&select[]=ID" +
                "&select[]=TITLE" +
                "&select[]=DESCRIPTION" +
                "&select[]=TIME_SPENT_IN_LOGS" +
                "&select[]=TIME_ESTIMATE" +
                "&select[]=STATUS" +
                "&select[]=RESPONSIBLE_ID" +
                "&select[]=CREATED_DATE" +
                "&select[]=DEADLINE"

        println("Loading tasks with URL: $url")

        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                viewModelScope.launch {
                    isLoading = false
                    errorMessage = "Ошибка подключения: ${e.message}"
                    println("Network error: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                viewModelScope.launch {
                    isLoading = false
                    if (response.isSuccessful) {
                        response.body?.let { body ->
                            try {
                                val responseText = body.string()
                                println("API Response: $responseText")

                                val json = JSONObject(responseText)

                                // Проверяем наличие ошибки в ответе
                                if (json.has("error")) {
                                    val error = json.getJSONObject("error")
                                    errorMessage = "Ошибка API: ${error.optString("error_description", "Неизвестная ошибка")}"
                                    return@launch
                                }

                                val tasksList = mutableListOf<Task>()

                                // Обрабатываем результат
                                if (json.has("result")) {
                                    val result = json.get("result")

                                    when (result) {
                                        is JSONObject -> {
                                            // Если result - объект с tasks
                                            if (result.has("tasks")) {
                                                val tasksData = result.get("tasks")
                                                processTasks(tasksData, tasksList)
                                            } else {
                                                // Если result сам содержит задачи как объект
                                                processTasks(result, tasksList)
                                            }
                                        }
                                        is JSONArray -> {
                                            // Если result - массив задач
                                            processTasks(result, tasksList)
                                        }
                                    }
                                }

                                // Восстанавливаем состояние таймера для текущего пользователя
                                val currentTimerState = getCurrentTimerState()
                                tasksList.forEach { task ->
                                    if (task.id == currentTimerState.activeTaskId) {
                                        task.isTimerRunning = true
                                    }
                                }

                                tasks = tasksList.sortedWith(compareBy<Task> { it.isCompleted }.thenBy { it.id.toIntOrNull() ?: 0 })
                                println("Loaded ${tasksList.size} tasks")

                                if (tasksList.isEmpty()) {
                                    // Попробуем альтернативный запрос без фильтров
                                    loadTasksSimple()
                                }

                            } catch (e: Exception) {
                                errorMessage = "Ошибка парсинга: ${e.message}"
                                println("Parse error: ${e.message}")
                                e.printStackTrace()
                            }
                        }
                    } else {
                        errorMessage = "Ошибка сервера: ${response.code} - ${response.message}"
                        println("HTTP error: ${response.code} - ${response.message}")
                    }
                }
            }
        })
    }

    // Простой метод загрузки без фильтров
    private fun loadTasksSimple() {
        val user = users[currentUserIndex]
        val url = "${user.webhookUrl}tasks.task.list"

        println("Trying simple URL without filters: $url")

        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                viewModelScope.launch {
                    // Теперь пробуем альтернативный запрос
                    loadTasksAlternative()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                viewModelScope.launch {
                    if (response.isSuccessful) {
                        response.body?.let { body ->
                            try {
                                val responseText = body.string()
                                println("Simple API Response: $responseText")

                                val json = JSONObject(responseText)
                                if (json.has("result")) {
                                    val tasksList = mutableListOf<Task>()
                                    val result = json.get("result")

                                    // Правильно обрабатываем структуру ответа
                                    if (result is JSONObject && result.has("tasks")) {
                                        val tasksData = result.get("tasks")
                                        processTasks(tasksData, tasksList)

                                        if (tasksList.isNotEmpty()) {
                                            tasks = tasksList.sortedWith(compareBy<Task> { it.isCompleted }.thenBy { it.id.toIntOrNull() ?: 0 })
                                            errorMessage = null
                                            println("Successfully loaded ${tasksList.size} tasks from simple method")
                                        } else {
                                            // Если все еще пусто, пробуем альтернативный
                                            loadTasksAlternative()
                                        }
                                    } else {
                                        // Пробуем альтернативный метод
                                        loadTasksAlternative()
                                    }
                                }
                            } catch (e: Exception) {
                                println("Simple parse error: ${e.message}")
                                // Пробуем альтернативный метод
                                loadTasksAlternative()
                            }
                        }
                    } else {
                        // Пробуем альтернативный метод
                        loadTasksAlternative()
                    }
                }
            }
        })
    }

    // Альтернативный метод загрузки без фильтров
    private fun loadTasksAlternative() {
        val user = users[currentUserIndex]
        val url = "${user.webhookUrl}tasks.task.list" +
                "?order[ID]=desc" +
                "&filter[CREATED_BY]=${user.userId}"

        println("Trying alternative URL: $url")

        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                viewModelScope.launch {
                    errorMessage = "Альтернативный запрос тоже не удался: ${e.message}"
                }
            }

            override fun onResponse(call: Call, response: Response) {
                viewModelScope.launch {
                    if (response.isSuccessful) {
                        response.body?.let { body ->
                            try {
                                val responseText = body.string()
                                println("Alternative API Response: $responseText")

                                val json = JSONObject(responseText)
                                if (json.has("result")) {
                                    val tasksList = mutableListOf<Task>()
                                    val result = json.get("result")

                                    // Правильно обрабатываем структуру ответа
                                    if (result is JSONObject && result.has("tasks")) {
                                        val tasksData = result.get("tasks")
                                        processTasks(tasksData, tasksList)

                                        if (tasksList.isNotEmpty()) {
                                            tasks = tasksList.sortedWith(compareBy<Task> { it.isCompleted }.thenBy { it.id.toIntOrNull() ?: 0 })
                                            errorMessage = null
                                            println("Successfully loaded ${tasksList.size} tasks from alternative method")
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                println("Alternative parse error: ${e.message}")
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
        })
    }

    private fun processTasks(tasksData: Any, tasksList: MutableList<Task>) {
        when (tasksData) {
            is JSONObject -> {
                val tasksIterator = tasksData.keys()
                while (tasksIterator.hasNext()) {
                    val taskId = tasksIterator.next()
                    val taskJson = tasksData.getJSONObject(taskId)
                    tasksList.add(createTaskFromJson(taskJson, taskId))
                }
            }
            is JSONArray -> {
                for (i in 0 until tasksData.length()) {
                    val taskJson = tasksData.getJSONObject(i)
                    tasksList.add(createTaskFromJson(taskJson))
                }
            }
        }
        println("Processed ${tasksList.size} tasks from data type: ${tasksData.javaClass.simpleName}")
    }

    private fun createTaskFromJson(taskJson: JSONObject, fallbackId: String = ""): Task {
        val timeSpent = taskJson.optInt("timeSpentInLogs",
            taskJson.optInt("TIME_SPENT_IN_LOGS", 0))

        return Task(
            id = taskJson.optString("id", taskJson.optString("ID", fallbackId)),
            title = taskJson.optString("title", taskJson.optString("TITLE", "Задача без названия")),
            description = taskJson.optString("description", taskJson.optString("DESCRIPTION", "")),
            timeSpent = timeSpent,
            timeEstimate = taskJson.optInt("timeEstimate", taskJson.optInt("TIME_ESTIMATE", 7200)),
            status = taskJson.optString("status", taskJson.optString("STATUS", "")),
            createdDate = taskJson.optString("createdDate", taskJson.optString("CREATED_DATE", "")),
            deadline = taskJson.optString("deadline", taskJson.optString("DEADLINE", ""))
        )
    }

    fun toggleTimer(task: Task) {
        val currentTimerState = getCurrentTimerState()

        if (currentTimerState.activeTaskId == task.id) {
            // Остановить таймер и записать время в Битрикс
            stopTimerAndSaveTime(task)
            currentTimerState.activeTaskId = null
            tasks = tasks.map { if (it.id == task.id) it.copy(isTimerRunning = false) else it }
        } else {
            // Сначала останавливаем предыдущий таймер, если есть
            currentTimerState.activeTaskId?.let { currentTaskId ->
                val currentTask = tasks.find { it.id == currentTaskId }
                currentTask?.let { stopTimerAndSaveTime(it) }
            }

            // Проверяем, есть ли приостановленный таймер для этой задачи
            if (currentTimerState.pausedTaskId == task.id) {
                // Возобновляем приостановленный таймер
                currentTimerState.timerSeconds = currentTimerState.pausedSeconds
                currentTimerState.pausedTaskId = null
                currentTimerState.pausedSeconds = 0
                currentTimerState.isTimerPaused = false
            } else {
                // Запускаем новый таймер
                currentTimerState.timerSeconds = 0
            }

            // Запустить новый таймер
            tasks = tasks.map { it.copy(isTimerRunning = false) }
            currentTimerState.activeTaskId = task.id
            tasks = tasks.map { if (it.id == task.id) it.copy(isTimerRunning = true) else it }
        }
    }

    // Сохранение времени в Битрикс при остановке таймера
    private fun stopTimerAndSaveTime(task: Task) {
        val currentTimerState = getCurrentTimerState()
        // Сохраняем время только если прошло больше 10 секунд
        if (currentTimerState.timerSeconds < 10) {
            println("Timer too short (${currentTimerState.timerSeconds}s), not saving to Bitrix")
            return
        }

        val user = users[currentUserIndex]
        val url = "${user.webhookUrl}task.elapseditem.add"

        // Используем правильную структуру для task.elapseditem.add
        val formBody = FormBody.Builder()
            .add("taskId", task.id)
            .add("arFields[SECONDS]", currentTimerState.timerSeconds.toString())
            .add("arFields[COMMENT_TEXT]", "Работа над задачей (${formatTime(currentTimerState.timerSeconds)})")
            .add("arFields[USER_ID]", user.userId)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                viewModelScope.launch {
                    println("Save time network error: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                viewModelScope.launch {
                    response.body?.let { body ->
                        val responseText = body.string()
                        println("Save time response: $responseText")

                        try {
                            val json = JSONObject(responseText)
                            if (json.has("error")) {
                                val errorCode = json.optInt("error", 0)

                                // Пробуем упрощенный вариант
                                println("Trying simplified parameters...")
                                saveTimeSimplified(task, currentTimerState.timerSeconds)
                            } else if (json.has("result")) {
                                // Успешно сохранено - обновляем задачи без уведомления
                                delay(1000)
                                loadTasks()
                            }
                        } catch (e: Exception) {
                            println("Parse error: ${e.message}")
                        }
                    }
                }
            }
        })
    }

    // Упрощенный способ сохранения времени без USER_ID
    private fun saveTimeSimplified(task: Task, seconds: Int) {
        val user = users[currentUserIndex]
        val url = "${user.webhookUrl}task.elapseditem.add"

        val formBody = FormBody.Builder()
            .add("taskId", task.id)
            .add("arFields[SECONDS]", seconds.toString())
            .add("arFields[COMMENT_TEXT]", "Работа над задачей (${formatTime(seconds)})")
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                viewModelScope.launch {
                    println("Simplified save time error: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                viewModelScope.launch {
                    response.body?.let { body ->
                        val responseText = body.string()
                        println("Simplified save time response: $responseText")

                        try {
                            val json = JSONObject(responseText)
                            if (json.has("result")) {
                                // Успешно сохранено - обновляем задачи
                                delay(1000)
                                loadTasks()
                            }
                        } catch (e: Exception) {
                            println("Simplified parse error: ${e.message}")
                        }
                    }
                }
            }
        })
    }

    // Глобальный таймер, который работает для всех пользователей одновременно
    private fun startGlobalTimer() {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                // Обновляем таймеры для всех пользователей
                userTimerStates.values.forEach { timerState ->
                    if (timerState.activeTaskId != null) {
                        timerState.timerSeconds++
                    }
                }
            }
        }
    }

    // Форматирование времени для отображения
    fun formatTime(seconds: Int): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return when {
            hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, secs)
            else -> String.format("%d:%02d", minutes, secs)
        }
    }

    fun completeTask(task: Task) {
        val currentTimerState = getCurrentTimerState()
        // Если есть активный таймер на этой задаче, сначала сохраняем время
        if (currentTimerState.activeTaskId == task.id && currentTimerState.timerSeconds > 0) {
            stopTimerAndSaveTime(task)
            currentTimerState.activeTaskId = null
            currentTimerState.timerSeconds = 0
            tasks = tasks.map { it.copy(isTimerRunning = false) }

            // Ждем секунду, чтобы время сохранилось, потом завершаем задачу
            viewModelScope.launch {
                delay(1500)
                completeTaskInBitrix(task)
            }
        } else {
            completeTaskInBitrix(task)
        }
    }

    private fun completeTaskInBitrix(task: Task) {
        val user = users[currentUserIndex]
        val url = "${user.webhookUrl}tasks.task.complete"

        val formBody = FormBody.Builder()
            .add("taskId", task.id)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                viewModelScope.launch {
                    println("Task complete network error: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                viewModelScope.launch {
                    response.body?.let { body ->
                        val responseText = body.string()
                        println("Task complete response: $responseText")

                        // В любом случае обновляем задачи через 1 секунду
                        // (задача скорее всего завершена успешно)
                        delay(1000)
                        loadTasks()
                    }
                }
            }
        })
    }

    private fun updateWorkStatus() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val currentMinutes = hour * 60 + minute

        val previousStatus = workStatus

        workStatus = when {
            currentMinutes < 7 * 60 + 50 -> WorkStatus.BEFORE_WORK
            currentMinutes in (9 * 60 + 45)..(10 * 60) -> WorkStatus.BREAK
            currentMinutes in (12 * 60)..(12 * 60 + 48) -> WorkStatus.LUNCH
            currentMinutes in (14 * 60 + 45)..(15 * 60) -> WorkStatus.BREAK
            currentMinutes >= 17 * 60 -> WorkStatus.AFTER_WORK
            else -> WorkStatus.WORKING
        }

        // Автоматическая пауза/возобновление таймера для ВСЕХ пользователей
        if (previousStatus == WorkStatus.WORKING &&
            (workStatus == WorkStatus.BREAK || workStatus == WorkStatus.LUNCH)) {
            // Переходим на перерыв - приостанавливаем таймеры всех пользователей
            userTimerStates.values.forEach { timerState ->
                if (timerState.activeTaskId != null) {
                    timerState.pausedTaskId = timerState.activeTaskId
                    timerState.pausedSeconds = timerState.timerSeconds
                    timerState.isTimerPaused = true
                    timerState.activeTaskId = null
                    timerState.timerSeconds = 0
                }
            }
            // Обновляем UI для текущего пользователя
            tasks = tasks.map { it.copy(isTimerRunning = false) }
        } else if ((previousStatus == WorkStatus.BREAK || previousStatus == WorkStatus.LUNCH) &&
            workStatus == WorkStatus.WORKING) {
            // Возвращаемся с перерыва - возобновляем таймеры всех пользователей
            userTimerStates.values.forEach { timerState ->
                if (timerState.pausedTaskId != null) {
                    timerState.activeTaskId = timerState.pausedTaskId
                    timerState.timerSeconds = timerState.pausedSeconds
                    timerState.pausedTaskId = null
                    timerState.pausedSeconds = 0
                    timerState.isTimerPaused = false
                }
            }
            // Обновляем UI для текущего пользователя
            val currentTimerState = getCurrentTimerState()
            tasks = tasks.map {
                if (it.id == currentTimerState.activeTaskId) it.copy(isTimerRunning = true)
                else it.copy(isTimerRunning = false)
            }
        }
    }

    private fun startPeriodicUpdates() {
        viewModelScope.launch {
            while (true) {
                updateWorkStatus()
                delay(30000) // каждые 30 секунд обновляем статус работы
            }
        }
    }

    private fun startPeriodicTaskUpdates() {
        viewModelScope.launch {
            while (true) {
                delay(300000) // каждые 5 минут
                // Обновляем задачи без сброса состояния таймеров
                loadTasks()
            }
        }
    }

    private fun startTimeUpdates() {
        viewModelScope.launch {
            while (true) {
                updateCurrentTime()
                delay(1000) // каждую секунду
            }
        }
    }

    private fun updateCurrentTime() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)
        val second = calendar.get(Calendar.SECOND)
        currentTime = String.format("%02d:%02d:%02d", hour, minute, second)
    }

    private fun loadUserPhotos() {
        users.forEachIndexed { index, user ->
            // Используем user.current для получения информации о пользователе
            val url = "${user.webhookUrl}user.current"

            val request = Request.Builder().url(url).build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    println("Failed to load photo for user ${user.name}: ${e.message}")
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        response.body?.let { body ->
                            try {
                                val responseText = body.string()
                                println("User.current response for ${user.name}: $responseText")
                                val json = JSONObject(responseText)

                                if (json.has("result")) {
                                    val userInfo = json.getJSONObject("result")
                                    val photoPath = userInfo.optString("PERSONAL_PHOTO", "")

                                    if (photoPath.isNotEmpty()) {
                                        // Формируем полный URL: берем домен из webhookUrl и добавляем путь к фото
                                        val baseUrl = user.webhookUrl.substringBefore("/rest/")
                                        val fullPhotoUrl = "$baseUrl$photoPath"

                                        // Обновляем пользователя с URL фотографии
                                        users.getOrNull(index)?.photoUrl = fullPhotoUrl
                                        println("Updated photo for ${user.name}: $fullPhotoUrl")
                                    } else {
                                        println("No photo found for ${user.name}")
                                    }
                                }
                            } catch (e: Exception) {
                                println("Error parsing user info for ${user.name}: ${e.message}")
                                e.printStackTrace()
                            }
                        }
                    } else {
                        println("HTTP error for ${user.name}: ${response.code}")
                    }
                }
            })
        }
    }

    fun getCurrentUser() = users[currentUserIndex]
}

// UI компоненты
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Bitrix_appTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    var isUserMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Верхняя панель: пользователь, время, статус работы
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Выпадающее меню пользователей
            Box {
                IconButton(
                    onClick = { isUserMenuExpanded = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    UserAvatar(user = viewModel.getCurrentUser(), size = 48)
                }

                DropdownMenu(
                    expanded = isUserMenuExpanded,
                    onDismissRequest = { isUserMenuExpanded = false }
                ) {
                    viewModel.users.forEachIndexed { index, user ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (index == viewModel.currentUserIndex) {
                                        Text("✓ ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                    } else {
                                        Text("   ")
                                    }
                                    Text(user.name)
                                }
                            },
                            onClick = {
                                viewModel.switchUser(index)
                                isUserMenuExpanded = false
                            }
                        )
                    }
                }
            }

            // Текущее время в центре
            Text(
                text = viewModel.currentTime,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            // Иконка статуса работы
            WorkStatusIcon(workStatus = viewModel.workStatus)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Активный таймер (если есть)
        viewModel.activeTimer?.let { taskId ->
            val task = viewModel.tasks.find { it.id == taskId }
            task?.let {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "🕐 Активный таймер",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = it.title,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = viewModel.formatTime(viewModel.timerSeconds),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Приостановленный таймер (если есть)
        viewModel.pausedTimerTaskId?.let { taskId ->
            val task = viewModel.tasks.find { it.id == taskId }
            task?.let {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "⏸️ Таймер на паузе (${
                                when (viewModel.workStatus) {
                                    WorkStatus.BREAK -> "Перерыв"
                                    WorkStatus.LUNCH -> "Обед"
                                    else -> "Пауза"
                                }
                            })",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF8F00)
                        )
                        Text(
                            text = it.title,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = viewModel.formatTime(viewModel.pausedTimerSeconds),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF8F00)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Состояние загрузки
        if (viewModel.isLoading) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }

        // Сообщение об ошибке
        viewModel.errorMessage?.let { error ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE))
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(16.dp),
                    color = Color(0xFFD32F2F)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Список задач
        LazyColumn {
            items(viewModel.tasks) { task ->
                TaskCard(
                    task = task,
                    onTimerToggle = { viewModel.toggleTimer(it) },
                    onCompleteTask = { viewModel.completeTask(it) },
                    isPaused = viewModel.pausedTimerTaskId == task.id
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun UserAvatar(user: User, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color(0xFF8D6E63)), // Коричневый некрасивый цвет
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = user.avatar, // Теперь это инициалы
            fontSize = (size * 0.4).sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun WorkStatusIcon(workStatus: WorkStatus) {
    val (icon, color) = when (workStatus) {
        WorkStatus.BEFORE_WORK -> "🌅" to Color.Gray
        WorkStatus.WORKING -> "💼" to Color(0xFF4CAF50)
        WorkStatus.BREAK -> "☕" to Color(0xFFFF9800)
        WorkStatus.LUNCH -> "🍽️" to Color(0xFFFF5722)
        WorkStatus.AFTER_WORK -> "🌆" to Color.Gray
    }

    Text(
        text = icon,
        fontSize = 24.sp,
        modifier = Modifier
            .background(color.copy(alpha = 0.1f), CircleShape)
            .padding(8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskCard(
    task: Task,
    onTimerToggle: (Task) -> Unit,
    onCompleteTask: (Task) -> Unit,
    isPaused: Boolean = false
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        colors = CardDefaults.cardColors(
            containerColor = when {
                task.isCompleted -> Color(0xFFE8F5E8)
                task.isTimerRunning -> Color(0xFFE3F2FD)
                isPaused -> Color(0xFFFFF3E0)
                task.isOverdue -> Color(0xFFFFEBEE)
                else -> MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Заголовок и статус
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = task.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Иконка раскрытия
                    Icon(
                        painter = painterResource(
                            id = if (isExpanded) android.R.drawable.arrow_up_float else android.R.drawable.arrow_down_float
                        ),
                        contentDescription = if (isExpanded) "Свернуть" else "Развернуть",
                        modifier = Modifier
                            .size(20.dp)
                            .padding(start = 8.dp),
                        tint = Color.Gray
                    )
                }

                Text(
                    text = task.statusText,
                    fontSize = 12.sp,
                    color = when {
                        task.isCompleted -> Color(0xFF4CAF50)
                        task.isInProgress -> Color(0xFF2196F3)
                        task.isPending -> Color(0xFFFF9800)
                        else -> Color.Gray
                    },
                    modifier = Modifier
                        .background(
                            when {
                                task.isCompleted -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                                task.isInProgress -> Color(0xFF2196F3).copy(alpha = 0.1f)
                                task.isPending -> Color(0xFFFF9800).copy(alpha = 0.1f)
                                else -> Color.Gray.copy(alpha = 0.1f)
                            },
                            CircleShape
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            // Краткая информация (всегда видна)
            Spacer(modifier = Modifier.height(8.dp))

            // Прогресс-бар времени
            val progress = if (task.timeEstimate > 0) {
                (task.timeSpent.toFloat() / task.timeEstimate.toFloat()).coerceAtMost(1f)
            } else 0f

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth(),
                color = when {
                    task.isOverdue -> Color(0xFFE57373)
                    progress > 0.8f -> Color(0xFFFFB74D)
                    else -> Color(0xFF81C784)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Краткая информация о времени
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Время: ${task.formattedTime}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Text(
                    text = "${task.progressPercent}%",
                    fontSize = 12.sp,
                    color = if (task.isOverdue) Color(0xFFE57373) else Color.Gray
                )
            }

            // Развернутая информация
            if (isExpanded) {
                Spacer(modifier = Modifier.height(12.dp))

                // Разделитель
                Divider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = 1.dp,
                    color = Color.Gray.copy(alpha = 0.3f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Описание (если есть)
                if (task.description.isNotEmpty()) {
                    Text(
                        text = "Описание:",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = task.description,
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Подробная информация о времени
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "⏱️ Временные показатели",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Потрачено:",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    text = "${task.timeSpent / 3600}:${String.format("%02d", (task.timeSpent % 3600) / 60)}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (task.isOverdue) Color(0xFFE57373) else Color(0xFF4CAF50)
                                )
                            }

                            Column {
                                Text(
                                    text = "Планируется:",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    text = "${task.timeEstimate / 3600}:${String.format("%02d", (task.timeEstimate % 3600) / 60)}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF2196F3)
                                )
                            }

                            Column {
                                Text(
                                    text = "Процент:",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    text = "${task.progressPercent}%",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        task.progressPercent >= 100 -> Color(0xFFE57373)
                                        task.progressPercent >= 80 -> Color(0xFFFF9800)
                                        else -> Color(0xFF4CAF50)
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Информация о датах
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "📅 Даты",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Создано:",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    text = task.formattedCreatedDate,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Column {
                                Text(
                                    text = "Дедлайн:",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    text = task.formattedDeadline,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (task.formattedDeadline != "Не указан") {
                                        // Можно добавить логику проверки просроченности
                                        Color(0xFFFF5722)
                                    } else {
                                        Color.Gray
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Техническая информация
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp)
                    ) {
                        Text(
                            text = "🔧 Техническая информация",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "ID задачи:",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    text = task.id,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Column {
                                Text(
                                    text = "Статус код:",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                                Text(
                                    text = task.status,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Кнопки действий
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Кнопка таймера
                Button(
                    onClick = { onTimerToggle(task) },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            task.isTimerRunning -> Color(0xFFE57373)
                            isPaused -> Color(0xFFFF8F00)
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                ) {
                    Text(
                        text = when {
                            task.isTimerRunning -> "⏹️ Стоп"
                            isPaused -> "▶️ Продолжить"
                            else -> "▶️ Старт"
                        },
                        fontSize = 14.sp
                    )
                }

                // Кнопка завершения (только для незавершенных задач)
                if (!task.isCompleted) {
                    Button(
                        onClick = { onCompleteTask(task) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Text(
                            text = "✅ Завершить",
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}