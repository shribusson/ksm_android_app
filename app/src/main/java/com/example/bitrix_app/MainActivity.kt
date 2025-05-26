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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
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
data class User(val name: String, val webhookUrl: String, val userId: String, val avatar: String)

data class Task(
    val id: String,
    val title: String,
    val description: String,
    val timeSpent: Int,
    val timeEstimate: Int,
    val status: String = "",
    var isTimerRunning: Boolean = false
) {
    val progressPercent: Int get() = if (timeEstimate > 0) (timeSpent * 100 / timeEstimate) else 0
    val isOverdue: Boolean get() = progressPercent > 100
    val isCompleted: Boolean get() = status == "5" // 5 = Завершена
    val isInProgress: Boolean get() = status == "2" // 2 = В работе
    val isPending: Boolean get() = status == "3" // 3 = Ждет выполнения
    // Уберем isTimerRunning из Task, так как это состояние теперь будет управляться в UserTimerData
    // var isTimerRunning: Boolean = false 

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
}

enum class WorkStatus { BEFORE_WORK, WORKING, BREAK, LUNCH, AFTER_WORK }

// Данные состояния таймера для одного пользователя
data class UserTimerData(
    val activeTimerId: String? = null,
    val timerSeconds: Int = 0,
    val isPausedForUserAction: Boolean = false, // Пауза, инициированная пользователем или сменой задачи
    val pausedTaskIdForUserAction: String? = null, // ID задачи, если таймер был приостановлен для нее пользователем
    val pausedTimerSecondsForUserAction: Int = 0, // Секунды, если таймер был приостановлен для задачи пользователем
    val isSystemPaused: Boolean = false // Пауза из-за системных событий (перерыв, обед)
)

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
    var sendComments by mutableStateOf(true) // Настройка отправки комментариев

    // Состояние таймеров для всех пользователей
    private var userTimerDataMap by mutableStateOf<Map<String, UserTimerData>>(emptyMap())

    // Вспомогательная функция для получения данных таймера текущего пользователя
    fun getCurrentUserTimerData(): UserTimerData {
        val userId = users[currentUserIndex].userId
        return userTimerDataMap[userId] ?: UserTimerData()
    }

    // Вспомогательная функция для обновления данных таймера текущего пользователя
    private fun updateCurrentUserTimerData(data: UserTimerData) {
        val userId = users[currentUserIndex].userId
        userTimerDataMap = userTimerDataMap + (userId to data)
        // Обновляем tasks, чтобы отразить isTimerRunning для UI
        val currentData = getCurrentUserTimerData()
        tasks = tasks.map { task ->
            task.copy(/*isTimerRunning = task.id == currentData.activeTimerId && !currentData.isPausedForUserAction && !currentData.isSystemPaused*/)
            // isTimerRunning убрали из Task, теперь это вычисляется в UI
        }
    }
    var currentTime by mutableStateOf("")

    init {
        updateWorkStatus()
        loadTasks()
        startPeriodicUpdates()
        startPeriodicTaskUpdates()
        startTimeUpdates()
        startUniversalTimerLoop() // Запускаем универсальный цикл таймера
    }

    fun switchUser(index: Int) {
        // При смене пользователя, предыдущий таймер (если был) продолжает свое состояние
        // в userTimerDataMap. Новый пользователь подхватит свое состояние.
        currentUserIndex = index
        loadTasks() // Загружаем задачи для нового пользователя
        // Обновляем tasks, чтобы отразить isTimerRunning для UI нового пользователя
        val currentData = getCurrentUserTimerData()
        tasks = tasks.map { task ->
            task.copy(/*isTimerRunning = task.id == currentData.activeTimerId && !currentData.isPausedForUserAction && !currentData.isSystemPaused*/)
        }
    }

    fun loadTasks() {
        isLoading = true
        errorMessage = null
        val user = users[currentUserIndex]
        val currentUserDataBeforeLoad = getCurrentUserTimerData() // Сохраняем текущее состояние таймера пользователя

        // Получаем ВСЕ задачи пользователя без фильтрации по статусу
        val url = "${user.webhookUrl}tasks.task.list" +
                "?filter[RESPONSIBLE_ID]=${user.userId}" +
                "&select[]=ID" +
                "&select[]=TITLE" +
                "&select[]=DESCRIPTION" +
                "&select[]=TIME_SPENT_IN_LOGS" +
                "&select[]=TIME_ESTIMATE" +
                "&select[]=STATUS" +
                "&select[]=RESPONSIBLE_ID"

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

                                // Сортируем задачи: активный таймер в начале, затем по завершенности и ID
                                tasks = tasksList.sortedWith(
                                    compareBy<Task> { it.id != currentUserDataBeforeLoad.activeTimerId }
                                        .thenBy { it.isCompleted }
                                        .thenBy { it.id.toIntOrNull() ?: 0 }
                                )
                                println("Loaded ${tasksList.size} tasks")

                                // Проверяем, существует ли еще активная задача после загрузки
                                val activeTaskExists = tasks.any { it.id == currentUserDataBeforeLoad.activeTimerId }
                                if (currentUserDataBeforeLoad.activeTimerId != null && !activeTaskExists) {
                                    // Активная задача больше не существует, сбрасываем таймер для этого пользователя
                                    updateCurrentUserTimerData(UserTimerData())
                                } else {
                                    // Восстанавливаем isTimerRunning для UI на основе сохраненных данных
                                    // (Это больше не нужно, так как isTimerRunning убрано из Task)
                                    // tasks = tasks.map { task ->
                                    //    task.copy(isTimerRunning = task.id == currentUserDataBeforeLoad.activeTimerId && !currentUserDataBeforeLoad.isPausedForUserAction && !currentUserDataBeforeLoad.isSystemPaused)
                                    // }
                                }


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
                                            tasks = tasksList.sortedWith(
                                                compareBy<Task> { it.id != getCurrentUserTimerData().activeTimerId }
                                                    .thenBy { it.isCompleted }
                                                    .thenBy { it.id.toIntOrNull() ?: 0 }
                                            )
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
                                            tasks = tasksList.sortedWith(
                                                compareBy<Task> { it.id != getCurrentUserTimerData().activeTimerId }
                                                    .thenBy { it.isCompleted }
                                                    .thenBy { it.id.toIntOrNull() ?: 0 }
                                            )
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
            status = taskJson.optString("status", taskJson.optString("STATUS", ""))
        )
    }

    fun toggleTimer(task: Task) {
        val currentUserData = getCurrentUserTimerData()

        if (currentUserData.activeTimerId == task.id && !currentUserData.isSystemPaused) {
            // Таймер активен для этой задачи и не на системной паузе -> останавливаем
            stopTimerAndSaveTime(task, currentUserData.timerSeconds)
            if (sendComments) {
                sendTimerComment(task, "Таймер остановлен", currentUserData.timerSeconds)
            }
            updateCurrentUserTimerData(UserTimerData()) // Сбрасываем таймер для пользователя
        } else {
            // Останавливаем любой другой активный таймер (если он был)
            if (currentUserData.activeTimerId != null && currentUserData.activeTimerId != task.id) {
                val previousTask = tasks.find { it.id == currentUserData.activeTimerId }
                previousTask?.let {
                    stopTimerAndSaveTime(it, currentUserData.timerSeconds)
                    if (sendComments) {
                        sendTimerComment(it, "Таймер остановлен (переключение)", currentUserData.timerSeconds)
                    }
                }
            }

            var newTimerSeconds = 0
            var commentAction = "Таймер запущен"

            if (currentUserData.pausedTaskIdForUserAction == task.id) {
                // Возобновляем таймер, который был приостановлен пользователем для этой задачи
                newTimerSeconds = currentUserData.pausedTimerSecondsForUserAction
                commentAction = "Таймер возобновлен"
            }

            updateCurrentUserTimerData(
                UserTimerData(
                    activeTimerId = task.id,
                    timerSeconds = newTimerSeconds,
                    isPausedForUserAction = false,
                    pausedTaskIdForUserAction = null,
                    pausedTimerSecondsForUserAction = 0,
                    isSystemPaused = currentUserData.isSystemPaused // Сохраняем состояние системной паузы
                )
            )

            if (sendComments) {
                sendTimerComment(task, commentAction, newTimerSeconds)
            }

            // Перемещаем задачу с активным таймером в начало списка
            tasks = tasks.sortedWith(
                compareBy<Task> { it.id != task.id }
                    .thenBy { it.isCompleted }
                    .thenBy { it.id.toIntOrNull() ?: 0 }
            )
            // startUniversalTimerLoop уже работает, он подхватит изменения
        }
    }

    // Отправка комментария о состоянии таймера
    private fun sendTimerComment(task: Task, action: String, currentSeconds: Int) {
        val user = users[currentUserIndex]
        val url = "${user.webhookUrl}task.commentitem.add"

        val commentText = "$action - ${user.name} (${formatTime(currentSeconds)})"

        val formBody = FormBody.Builder()
            .add("taskId", task.id)
            .add("arFields[POST_MESSAGE]", commentText)
            .add("arFields[AUTHOR_ID]", user.userId)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                println("Comment send error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.let { body ->
                    val responseText = body.string()
                    println("Comment response: $responseText")
                }
            }
        })
    }

    // Сохранение времени в Битрикс при остановке таймера
    private fun stopTimerAndSaveTime(task: Task, secondsToSave: Int) {
        // Сохраняем время только если прошло больше 10 секунд
        if (secondsToSave < 10) {
            println("Timer too short (${secondsToSave}s), not saving to Bitrix for task ${task.id}")
            return
        }

        val user = users[currentUserIndex]
        val url = "${user.webhookUrl}task.elapseditem.add"

        // Используем правильную структуру для task.elapseditem.add
        val formBody = FormBody.Builder()
            .add("taskId", task.id)
            .add("arFields[SECONDS]", secondsToSave.toString())
            .add("arFields[COMMENT_TEXT]", "Работа над задачей (${formatTime(secondsToSave)})")
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
                                saveTimeSimplified(task, secondsToSave)
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
    private fun saveTimeSimplified(task: Task, secondsToSave: Int) {
        val user = users[currentUserIndex]
        val url = "${user.webhookUrl}task.elapseditem.add"

        val formBody = FormBody.Builder()
            .add("taskId", task.id)
            .add("arFields[SECONDS]", secondsToSave.toString())
            .add("arFields[COMMENT_TEXT]", "Работа над задачей (${formatTime(secondsToSave)})")
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

    // Приостановка таймера из-за системных событий (перерыв, обед)
    private fun systemPauseTimer() {
        val currentUserData = getCurrentUserTimerData()
        if (currentUserData.activeTimerId != null && !currentUserData.isSystemPaused) {
            val task = tasks.find { it.id == currentUserData.activeTimerId }
            updateCurrentUserTimerData(
                currentUserData.copy(
                    isSystemPaused = true
                )
            )
            if (sendComments && task != null) {
                sendTimerComment(task, "Таймер системно приостановлен (перерыв/обед)", currentUserData.timerSeconds)
            }
            println("Timer system-paused for task ${currentUserData.activeTimerId} with ${currentUserData.timerSeconds}s")
        }
    }

    // Возобновление таймера после системных событий
    private fun systemResumeTimer() {
        val currentUserData = getCurrentUserTimerData()
        if (currentUserData.activeTimerId != null && currentUserData.isSystemPaused) {
            val task = tasks.find { it.id == currentUserData.activeTimerId }
            updateCurrentUserTimerData(
                currentUserData.copy(
                    isSystemPaused = false
                )
            )
            if (sendComments && task != null) {
                sendTimerComment(task, "Таймер системно возобновлен", currentUserData.timerSeconds)
            }
            println("Timer system-resumed for task ${currentUserData.activeTimerId} with ${currentUserData.timerSeconds}s")
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
        val currentUserData = getCurrentUserTimerData()
        // Если есть активный таймер на этой задаче, сначала сохраняем время
        if (currentUserData.activeTimerId == task.id && currentUserData.timerSeconds > 0) {
            stopTimerAndSaveTime(task, currentUserData.timerSeconds)
            if (sendComments) {
                sendTimerComment(task, "Задача завершена, таймер остановлен", currentUserData.timerSeconds)
            }
            updateCurrentUserTimerData(UserTimerData()) // Сбрасываем таймер для пользователя

            // Ждем секунду, чтобы время сохранилось, потом завершаем задачу
            viewModelScope.launch {
                delay(1500) // Даем время на сохранение
                completeTaskInBitrix(task)
            }
        } else {
            // Если таймер не был активен для этой задачи, или время 0, просто завершаем
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

    fun toggleComments() {
        sendComments = !sendComments
    }

    private fun startUniversalTimerLoop() {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                val currentUserData = getCurrentUserTimerData()
                if (currentUserData.activeTimerId != null && !currentUserData.isPausedForUserAction && !currentUserData.isSystemPaused) {
                    updateCurrentUserTimerData(currentUserData.copy(timerSeconds = currentUserData.timerSeconds + 1))
                }
            }
        }
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

        // Автоматическая пауза/возобновление таймера
        if (previousStatus == WorkStatus.WORKING &&
            (workStatus == WorkStatus.BREAK || workStatus == WorkStatus.LUNCH)) {
            // Переходим на перерыв - системно приостанавливаем таймер
            systemPauseTimer()
        } else if ((previousStatus == WorkStatus.BREAK || previousStatus == WorkStatus.LUNCH) &&
            workStatus == WorkStatus.WORKING) {
            // Возвращаемся с перерыва - системно возобновляем таймер
            systemResumeTimer()
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
                // Состояние таймера теперь хранится в userTimerDataMap и будет сохранено при loadTasks
                val currentUserId = users[currentUserIndex].userId
                val timerDataBeforeReload = userTimerDataMap[currentUserId]

                loadTasks() // loadTasks теперь сам обрабатывает сохранение/восстановление состояния таймера для текущего пользователя

                // Если после loadTasks таймер для текущего пользователя был сброшен (например, задача исчезла),
                // а до этого он был активен, то это уже обработано в loadTasks.
                // Если таймер был активен и задача осталась, его состояние в userTimerDataMap сохранится.
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
    var isSettingsExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Верхняя панель: пользователь, время, статус работы, настройки
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

            // Меню настроек и иконка статуса работы
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Кнопка настроек
                Box {
                    IconButton(
                        onClick = { isSettingsExpanded = true }
                    ) {
                        Text("⚙️", fontSize = 20.sp)
                    }

                    DropdownMenu(
                        expanded = isSettingsExpanded,
                        onDismissRequest = { isSettingsExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (viewModel.sendComments) "✓ " else "   ",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text("Отправлять комментарии")
                                }
                            },
                            onClick = {
                                viewModel.toggleComments()
                                isSettingsExpanded = false
                            }
                        )
                    }
                }

                // Иконка статуса работы
                WorkStatusIcon(workStatus = viewModel.workStatus)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val currentUserTimerData = viewModel.getCurrentUserTimerData()

        // Активный таймер (если есть и не на системной паузе)
        if (currentUserTimerData.activeTimerId != null && !currentUserTimerData.isSystemPaused) {
            val task = viewModel.tasks.find { it.id == currentUserTimerData.activeTimerId }
            task?.let {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (currentUserTimerData.isPausedForUserAction) Color(0xFFFFF9C4) /* Светло-желтый для пользовательской паузы */
                                         else Color(0xFFE3F2FD) /* Голубой для активного */
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = if (currentUserTimerData.isPausedForUserAction) "⏸️ Таймер приостановлен (пользователем)" else "🕐 Активный таймер",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (currentUserTimerData.isPausedForUserAction) Color(0xFFF57F17) else MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = it.title,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = viewModel.formatTime(
                                if (currentUserTimerData.isPausedForUserAction) currentUserTimerData.pausedTimerSecondsForUserAction
                                else currentUserTimerData.timerSeconds
                            ),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (currentUserTimerData.isPausedForUserAction) Color(0xFFF57F17) else MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // Системно приостановленный таймер (если есть)
        if (currentUserTimerData.activeTimerId != null && currentUserTimerData.isSystemPaused) {
            val task = viewModel.tasks.find { it.id == currentUserTimerData.activeTimerId }
            task?.let {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)) // Оранжевый для системной паузы
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "⏸️ Таймер на системной паузе (${
                                when (viewModel.workStatus) {
                                    WorkStatus.BREAK -> "Перерыв"
                                    WorkStatus.LUNCH -> "Обед"
                                    else -> "Системная пауза"
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
                            text = viewModel.formatTime(currentUserTimerData.timerSeconds), // Показываем текущие секунды активного таймера
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
                val timerData = viewModel.getCurrentUserTimerData()
                val isTaskActive = timerData.activeTimerId == task.id && !timerData.isSystemPaused && !timerData.isPausedForUserAction
                val isTaskUserPaused = timerData.pausedTaskIdForUserAction == task.id
                val isTaskSystemPaused = timerData.activeTimerId == task.id && timerData.isSystemPaused


                TaskCard(
                    task = task,
                    onTimerToggle = { viewModel.toggleTimer(it) },
                    onCompleteTask = { viewModel.completeTask(it) },
                    isTimerRunningForThisTask = isTaskActive,
                    isTimerUserPausedForThisTask = isTaskUserPaused,
                    isTimerSystemPausedForThisTask = isTaskSystemPaused
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
            .background(Color(0xFF8D6E63)), // Коричневый цвет
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = user.avatar, // Инициалы
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
    isTimerRunningForThisTask: Boolean,
    isTimerUserPausedForThisTask: Boolean,
    isTimerSystemPausedForThisTask: Boolean
) {
    var isExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        colors = CardDefaults.cardColors(
            containerColor = when {
                task.isCompleted -> Color(0xFFE8F5E8) // Зеленый для завершенных
                isTimerRunningForThisTask -> Color(0xFFE3F2FD) // Голубой для активного таймера
                isTimerUserPausedForThisTask -> Color(0xFFFFF9C4) // Светло-желтый для пользовательской паузы
                isTimerSystemPausedForThisTask -> Color(0xFFFFF3E0) // Оранжевый для системной паузы
                task.isOverdue -> Color(0xFFFFEBEE) // Розовый для просроченных
                else -> MaterialTheme.colorScheme.surface // Стандартный
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
                    enabled = !isTimerSystemPausedForThisTask, // Блокируем кнопку если таймер на системной паузе
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            isTimerRunningForThisTask -> Color(0xFFE57373) // Красный для стоп
                            isTimerUserPausedForThisTask -> Color(0xFF66BB6A) // Зеленый для продолжить пользовательскую паузу
                            isTimerSystemPausedForThisTask -> Color.Gray // Серый, если системная пауза (кнопка disabled)
                            else -> MaterialTheme.colorScheme.primary // Синий для старт
                        },
                        disabledContainerColor = Color.LightGray // Цвет для заблокированной кнопки
                    )
                ) {
                    Text(
                        text = when {
                            isTimerRunningForThisTask -> "⏹️ Стоп"
                            isTimerUserPausedForThisTask -> "▶️ Продолжить"
                            isTimerSystemPausedForThisTask -> "⏸️ Пауза" // Показываем, что на паузе
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
