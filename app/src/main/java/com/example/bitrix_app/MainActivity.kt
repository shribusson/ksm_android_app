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
import com.example.bitrix_app.ui.theme.* // Импортируем все из пакета темы
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
    val status: String = ""
    // Поле isTimerRunning удалено, так как состояние таймера управляется в UserTimerData
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

data class ChecklistItem(
    val id: String,
    val title: String,
    val isComplete: Boolean
)

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

    // Состояния для чек-листов и подзадач
    var checklistsMap by mutableStateOf<Map<String, List<ChecklistItem>>>(emptyMap())
        private set
    var subtasksMap by mutableStateOf<Map<String, List<Task>>>(emptyMap())
        private set
    var loadingChecklistMap by mutableStateOf<Map<String, Boolean>>(emptyMap())
        private set
    var loadingSubtasksMap by mutableStateOf<Map<String, Boolean>>(emptyMap())
        private set


    // Вспомогательная функция для получения данных таймера текущего пользователя
    fun getCurrentUserTimerData(): UserTimerData {
        val userId = users[currentUserIndex].userId
        return userTimerDataMap[userId] ?: UserTimerData()
    }

    // Вспомогательная функция для обновления данных таймера текущего пользователя
    private fun updateCurrentUserTimerData(data: UserTimerData) {
        val userId = users[currentUserIndex].userId
        userTimerDataMap = userTimerDataMap + (userId to data)
        // Обновление tasks здесь больше не нужно, так как isTimerRunning удалено из Task,
        // а состояние для UI вычисляется на лету из getCurrentUserTimerData().
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
        // Обновление tasks здесь больше не нужно, так как isTimerRunning удалено из Task,
        // а состояние для UI вычисляется на лету из getCurrentUserTimerData().
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

    fun fetchChecklistForTask(taskId: String) {
        val user = users[currentUserIndex] // Используем текущего пользователя для API вызова
        loadingChecklistMap = loadingChecklistMap + (taskId to true)
        val url = "${user.webhookUrl}task.checklistitem.getlist?taskId=$taskId"
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                viewModelScope.launch {
                    loadingChecklistMap = loadingChecklistMap - taskId
                    // Можно добавить обработку ошибки, например, в errorMessage
                    println("Failed to fetch checklist for task $taskId: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                viewModelScope.launch {
                    loadingChecklistMap = loadingChecklistMap - taskId
                    if (response.isSuccessful) {
                        response.body?.let { body ->
                            try {
                                val responseText = body.string()
                                println("Checklist response for task $taskId: $responseText")
                                val json = JSONObject(responseText)
                                if (json.has("result")) {
                                    val itemsArray = json.getJSONArray("result")
                                    val itemsList = mutableListOf<ChecklistItem>()
                                    for (i in 0 until itemsArray.length()) {
                                        val itemJson = itemsArray.getJSONObject(i)
                                        itemsList.add(
                                            ChecklistItem(
                                                id = itemJson.getString("ID"),
                                                title = itemJson.getString("TITLE"),
                                                isComplete = itemJson.getString("IS_COMPLETE") == "Y"
                                            )
                                        )
                                    }
                                    checklistsMap = checklistsMap + (taskId to itemsList)
                                    println("Fetched checklist items for task $taskId:")
                                    itemsList.forEach { item ->
                                        println("  - ID: ${item.id}, Title: ${item.title}, IsComplete: ${item.isComplete}")
                                    }
                                }
                            } catch (e: Exception) {
                                println("Error parsing checklist for task $taskId: ${e.message}")
                            }
                        }
                    }
                }
            }
        })
    }

    fun fetchSubtasksForTask(taskId: String) {
        val user = users[currentUserIndex]
        loadingSubtasksMap = loadingSubtasksMap + (taskId to true)
        // Запрашиваем основные поля для подзадач
        val url = "${user.webhookUrl}tasks.task.list" +
                "?filter[PARENT_ID]=$taskId" +
                "&select[]=ID" +
                "&select[]=TITLE" +
                "&select[]=DESCRIPTION" +
                "&select[]=TIME_SPENT_IN_LOGS" +
                "&select[]=TIME_ESTIMATE" +
                "&select[]=STATUS"

        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                viewModelScope.launch {
                    loadingSubtasksMap = loadingSubtasksMap - taskId
                    println("Failed to fetch subtasks for task $taskId: ${e.message}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                viewModelScope.launch {
                    loadingSubtasksMap = loadingSubtasksMap - taskId
                    if (response.isSuccessful) {
                        response.body?.let { body ->
                            try {
                                val responseText = body.string()
                                println("Subtasks response for task $taskId: $responseText")
                                val json = JSONObject(responseText)
                                val subtasksList = mutableListOf<Task>()
                                if (json.has("result")) {
                                    val result = json.get("result")
                                     // processTasks ожидает, что задачи могут быть в result.tasks или просто в result
                                    val tasksDataToProcess = if (result is JSONObject && result.has("tasks")) {
                                        result.get("tasks")
                                    } else {
                                        result
                                    }
                                    processTasks(tasksDataToProcess, subtasksList)
                                }
                                subtasksMap = subtasksMap + (taskId to subtasksList)
                            } catch (e: Exception) {
                                println("Error parsing subtasks for task $taskId: ${e.message}")
                            }
                        }
                    }
                }
            }
        })
    }

    fun toggleChecklistItemStatus(taskId: String, checklistItemId: String, currentIsComplete: Boolean) {
        val user = users[currentUserIndex]
        // Исправляем имя метода: добавляем .task. между tasks. и checklistitem.
        val action = if (currentIsComplete) "task.checklistitem.renew" else "task.checklistitem.complete"
        val url = "${user.webhookUrl}$action"

        println("Toggling checklist item: URL=$url, TASKID=$taskId, ITEMID=$checklistItemId, Action=${if (currentIsComplete) "renew" else "complete"}")

        val formBody = FormBody.Builder()
            .add("TASKID", taskId)
            .add("ITEMID", checklistItemId)
            .build()

        val request = Request.Builder()
            .url(url)
            .post(formBody)
            .build()

        // Оптимистичное обновление UI
        val oldChecklist = checklistsMap[taskId] ?: emptyList()
        val updatedChecklist = oldChecklist.map {
            if (it.id == checklistItemId) it.copy(isComplete = !currentIsComplete) else it
        }
        checklistsMap = checklistsMap + (taskId to updatedChecklist)

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                viewModelScope.launch {
                    println("Failed to toggle checklist item $checklistItemId for task $taskId: ${e.message}")
                    // Откатываем изменение в случае ошибки
                    checklistsMap = checklistsMap + (taskId to oldChecklist)
                    // Можно добавить сообщение об ошибке для пользователя
                }
            }

            override fun onResponse(call: Call, response: Response) {
                viewModelScope.launch {
                    if (!response.isSuccessful) {
                        println("Error toggling checklist item $checklistItemId for task $taskId: ${response.code}")
                        // Откатываем изменение в случае ошибки от сервера
                        checklistsMap = checklistsMap + (taskId to oldChecklist)
                    } else {
                        // Если успешно, данные уже оптимистично обновлены.
                        // Можно дополнительно перезапросить чек-лист для полной синхронизации, если необходимо.
                        // fetchChecklistForTask(taskId) // Раскомментировать, если нужна полная синхронизация
                        println("Successfully toggled checklist item $checklistItemId for task $taskId. New state: ${!currentIsComplete}")
                    }
                    response.body?.close()
                }
            }
        })
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
                val newMap = userTimerDataMap.toMutableMap()
                var mapChanged = false
                userTimerDataMap.forEach { (userId, data) ->
                    if (data.activeTimerId != null && !data.isPausedForUserAction && !data.isSystemPaused) {
                        val newData = data.copy(timerSeconds = data.timerSeconds + 1)
                        newMap[userId] = newData
                        mapChanged = true
                    }
                }
                if (mapChanged) {
                    userTimerDataMap = newMap.toMap() // Обновляем состояние, чтобы Compose среагировал
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
            .padding(20.dp) // Увеличиваем основной отступ
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
                    modifier = Modifier.size(60.dp) // Увеличиваем размер кнопки аватара
                ) {
                    UserAvatar(user = viewModel.getCurrentUser(), size = 60) // Увеличиваем аватар
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
                fontSize = 32.sp, // Увеличиваем шрифт времени
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
                        Text("⚙️", fontSize = 28.sp) // Увеличиваем иконку настроек
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

        Spacer(modifier = Modifier.height(20.dp)) // Увеличиваем отступ

        val currentUserTimerData = viewModel.getCurrentUserTimerData()

        // Активный таймер (если есть и не на системной паузе)
        if (currentUserTimerData.activeTimerId != null && !currentUserTimerData.isSystemPaused) {
            val task = viewModel.tasks.find { it.id == currentUserTimerData.activeTimerId }
            task?.let {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (currentUserTimerData.isPausedForUserAction) StatusYellow
                                         else StatusBlue
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp) // Увеличиваем отступ
                    ) {
                        Text(
                            text = if (currentUserTimerData.isPausedForUserAction) "⏸️ Таймер приостановлен (пользователем)" else "🕐 Активный таймер",
                            fontSize = 18.sp, // Увеличиваем шрифт
                            fontWeight = FontWeight.Bold,
                            color = if (currentUserTimerData.isPausedForUserAction) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = it.title,
                            fontSize = 16.sp, // Увеличиваем шрифт
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = viewModel.formatTime(
                                if (currentUserTimerData.isPausedForUserAction) currentUserTimerData.pausedTimerSecondsForUserAction
                                else currentUserTimerData.timerSeconds
                            ),
                            fontSize = 20.sp, // Увеличиваем шрифт
                            fontWeight = FontWeight.Bold,
                            color = if (currentUserTimerData.isPausedForUserAction) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp)) // Увеличиваем отступ
            }
        }

        // Системно приостановленный таймер (если есть)
        if (currentUserTimerData.activeTimerId != null && currentUserTimerData.isSystemPaused) {
            val task = viewModel.tasks.find { it.id == currentUserTimerData.activeTimerId }
            task?.let {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = StatusOrange)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp) // Увеличиваем отступ
                    ) {
                        Text(
                            text = "⏸️ Таймер на системной паузе (${
                                when (viewModel.workStatus) {
                                    WorkStatus.BREAK -> "Перерыв"
                                    WorkStatus.LUNCH -> "Обед"
                                    else -> "Системная пауза"
                                }
                            })",
                            fontSize = 18.sp, // Увеличиваем шрифт
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = it.title,
                            fontSize = 16.sp, // Увеличиваем шрифт
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = viewModel.formatTime(currentUserTimerData.timerSeconds), // Показываем текущие секунды активного таймера
                            fontSize = 20.sp, // Увеличиваем шрифт
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.height(20.dp)) // Увеличиваем отступ
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
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(20.dp), // Увеличиваем отступ
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(20.dp)) // Увеличиваем отступ
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
                    isTimerSystemPausedForThisTask = isTaskSystemPaused,
                    viewModel = viewModel // Передаем ViewModel в TaskCard
                )
                Spacer(modifier = Modifier.height(12.dp)) // Увеличиваем отступ между карточками
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
            .background(AvatarBackground), // Используем цвет из Color.kt
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = user.avatar, // Инициалы
            fontSize = (size * 0.45).sp, // Немного увеличиваем относительный размер шрифта
            fontWeight = FontWeight.Bold,
            color = LightOnPrimary, // Используем цвет текста из темы
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun WorkStatusIcon(workStatus: WorkStatus) {
    val (icon, color, contentColor) = when (workStatus) {
        WorkStatus.BEFORE_WORK -> Triple("🌅", Color.Gray, MaterialTheme.colorScheme.onSurface)
        WorkStatus.WORKING -> Triple("💼", MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer)
        WorkStatus.BREAK -> Triple("☕", StatusOrange, MaterialTheme.colorScheme.onSurfaceVariant) // Используем StatusOrange для фона
        WorkStatus.LUNCH -> Triple("🍽️", StatusRed, MaterialTheme.colorScheme.onSurfaceVariant) // Используем StatusRed для фона (или другой подходящий)
        WorkStatus.AFTER_WORK -> Triple("🌆", Color.Gray, MaterialTheme.colorScheme.onSurface)
    }

    Text(
        text = icon,
        fontSize = 30.sp, // Увеличиваем иконку
        color = contentColor,
        modifier = Modifier
            .background(color.copy(alpha = 0.2f), CircleShape) // Немного увеличиваем alpha для видимости фона
            .padding(10.dp) // Увеличиваем отступ
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
    isTimerSystemPausedForThisTask: Boolean,
    viewModel: MainViewModel // Передаем ViewModel для доступа к данным и функциям
) {
    var isExpanded by remember { mutableStateOf(false) }

    // Загрузка чек-листов и подзадач при раскрытии карточки
    LaunchedEffect(task.id, isExpanded) {
        if (isExpanded) {
            if (viewModel.checklistsMap[task.id] == null && viewModel.loadingChecklistMap[task.id] != true) {
                viewModel.fetchChecklistForTask(task.id)
            }
            if (viewModel.subtasksMap[task.id] == null && viewModel.loadingSubtasksMap[task.id] != true) {
                viewModel.fetchSubtasksForTask(task.id)
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        colors = CardDefaults.cardColors(
            containerColor = when {
                task.isCompleted -> StatusGreen
                isTimerRunningForThisTask -> StatusBlue
                isTimerUserPausedForThisTask -> StatusYellow
                isTimerSystemPausedForThisTask -> StatusOrange
                task.isOverdue -> StatusRed
                else -> MaterialTheme.colorScheme.surfaceVariant // Используем surfaceVariant для стандартного состояния
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp) // Увеличиваем отступ
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
                        fontSize = 18.sp, // Увеличиваем шрифт
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
                            .size(28.dp) // Увеличиваем иконку
                            .padding(start = 8.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = task.statusText,
                    fontSize = 14.sp, // Увеличиваем шрифт
                    color = when { // Используем цвета из темы или определенные статусные
                        task.isCompleted -> MaterialTheme.colorScheme.tertiary
                        task.isInProgress -> MaterialTheme.colorScheme.primary
                        task.isPending -> StatusOrange // или другой подходящий
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .background(
                            when {
                                task.isCompleted -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
                                task.isInProgress -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                task.isPending -> StatusOrange.copy(alpha = 0.3f)
                                else -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                            },
                            CircleShape
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp) // Увеличиваем отступы
                )
            }

            // Краткая информация (всегда видна)
            Spacer(modifier = Modifier.height(12.dp)) // Увеличиваем отступ

            // Прогресс-бар времени
            val progress = if (task.timeEstimate > 0) {
                (task.timeSpent.toFloat() / task.timeEstimate.toFloat()).coerceAtMost(1f)
            } else 0f

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().height(8.dp), // Увеличиваем толщину
                color = when {
                    task.isOverdue -> ProgressBarRed
                    progress > 0.8f -> ProgressBarOrange
                    else -> ProgressBarGreen
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp)) // Увеличиваем отступ

            // Краткая информация о времени
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Время: ${task.formattedTime}",
                    fontSize = 14.sp, // Увеличиваем шрифт
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${task.progressPercent}%",
                    fontSize = 14.sp, // Увеличиваем шрифт
                    color = if (task.isOverdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Развернутая информация
            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp)) // Увеличиваем отступ

                // Разделитель
                Divider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )

                Spacer(modifier = Modifier.height(16.dp)) // Увеличиваем отступ

                // Описание (если есть)
                if (task.description.isNotEmpty()) {
                    Text(
                        text = "Описание:",
                        fontSize = 16.sp, // Увеличиваем шрифт
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp)) // Увеличиваем отступ
                    Text(
                        text = task.description,
                        fontSize = 16.sp, // Увеличиваем шрифт
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp)) // Увеличиваем отступ
                }

                // Подробная информация о времени
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp) // Увеличиваем отступ
                    ) {
                        Text(
                            text = "⏱️ Временные показатели",
                            fontSize = 16.sp, // Увеличиваем шрифт
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(12.dp)) // Увеличиваем отступ

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Потрачено:",
                                    fontSize = 14.sp, // Увеличиваем шрифт
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${task.timeSpent / 3600}:${String.format("%02d", (task.timeSpent % 3600) / 60)}",
                                    fontSize = 16.sp, // Увеличиваем шрифт
                                    fontWeight = FontWeight.Bold,
                                    color = if (task.isOverdue) MaterialTheme.colorScheme.error else ProgressBarGreen
                                )
                            }

                            Column {
                                Text(
                                    text = "Планируется:",
                                    fontSize = 14.sp, // Увеличиваем шрифт
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${task.timeEstimate / 3600}:${String.format("%02d", (task.timeEstimate % 3600) / 60)}",
                                    fontSize = 16.sp, // Увеличиваем шрифт
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary // Или другой подходящий
                                )
                            }

                            Column {
                                Text(
                                    text = "Процент:",
                                    fontSize = 14.sp, // Увеличиваем шрифт
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${task.progressPercent}%",
                                    fontSize = 16.sp, // Увеличиваем шрифт
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        task.progressPercent >= 100 -> ProgressBarRed
                                        task.progressPercent >= 80 -> ProgressBarOrange
                                        else -> ProgressBarGreen
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp)) // Увеличиваем отступ

                // Чек-листы
                val checklist = viewModel.checklistsMap[task.id]
                val isLoadingChecklist = viewModel.loadingChecklistMap[task.id] == true
                if (isLoadingChecklist) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else if (!checklist.isNullOrEmpty()) {
                    Text(
                        text = "Чек-лист:",
                        fontSize = 16.sp, // Увеличиваем шрифт
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp)) // Увеличиваем отступ
                    checklist.forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.toggleChecklistItemStatus(task.id, item.id, item.isComplete)
                                }
                                .padding(vertical = 4.dp) // Добавляем вертикальный отступ для лучшего касания
                        ) {
                            Checkbox(
                                checked = item.isComplete,
                                onCheckedChange = {
                                    viewModel.toggleChecklistItemStatus(task.id, item.id, item.isComplete)
                                },
                                enabled = true,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MaterialTheme.colorScheme.primary,
                                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp)) // Отступ между чекбоксом и текстом
                            Text(
                                text = item.title,
                                fontSize = 16.sp, // Увеличиваем шрифт
                                color = if (item.isComplete) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp)) // Увеличиваем отступ
                }

                // Подзадачи
                val subtasks = viewModel.subtasksMap[task.id]
                val isLoadingSubtasks = viewModel.loadingSubtasksMap[task.id] == true
                if (isLoadingSubtasks) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                } else if (!subtasks.isNullOrEmpty()) {
                    Text(
                        text = "Подзадачи:",
                        fontSize = 16.sp, // Увеличиваем шрифт
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp)) // Увеличиваем отступ
                    subtasks.forEach { subtask ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp), // Увеличиваем отступ
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) { // Увеличиваем отступ
                                Text(
                                    text = subtask.title,
                                    fontSize = 16.sp, // Увеличиваем шрифт
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(6.dp)) // Увеличиваем отступ
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Статус: ${subtask.statusText}",
                                        fontSize = 14.sp, // Увеличиваем шрифт
                                        color = when { // Используем цвета из темы или определенные статусные
                                            subtask.isCompleted -> MaterialTheme.colorScheme.tertiary
                                            subtask.isInProgress -> MaterialTheme.colorScheme.primary
                                            subtask.isPending -> StatusOrange // или другой подходящий
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                    Text(
                                        text = "Время: ${subtask.formattedTime}",
                                        fontSize = 14.sp, // Увеличиваем шрифт
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp)) // Увеличиваем отступ
                }
            }

            Spacer(modifier = Modifier.height(16.dp)) // Увеличиваем отступ

            // Кнопки действий
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp) // Увеличиваем расстояние между кнопками
            ) {
                // Кнопка таймера
                Button(
                    onClick = { onTimerToggle(task) },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp), // Увеличиваем высоту кнопки
                    enabled = !isTimerSystemPausedForThisTask,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            isTimerRunningForThisTask -> MaterialTheme.colorScheme.error // Используем цвет ошибки для "Стоп"
                            isTimerUserPausedForThisTask -> MaterialTheme.colorScheme.tertiary // Зеленый для "Продолжить"
                            isTimerSystemPausedForThisTask -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f) // Цвет для disabled
                            else -> MaterialTheme.colorScheme.primary // Основной цвет для "Старт"
                        },
                        contentColor = when {
                            isTimerRunningForThisTask -> MaterialTheme.colorScheme.onError
                            isTimerUserPausedForThisTask -> MaterialTheme.colorScheme.onTertiary
                            isTimerSystemPausedForThisTask -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) // Заменяем на фактическое значение
                            else -> MaterialTheme.colorScheme.onPrimary
                        },
                        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) // Заменяем на фактическое значение
                    )
                ) {
                    Text(
                        text = when {
                            isTimerRunningForThisTask -> "⏹️ Стоп"
                            isTimerUserPausedForThisTask -> "▶️ Продолжить"
                            isTimerSystemPausedForThisTask -> "⏸️ Пауза"
                            else -> "▶️ Старт"
                        },
                        fontSize = 16.sp // Увеличиваем шрифт
                    )
                }

                // Кнопка завершения (только для незавершенных задач)
                if (!task.isCompleted) {
                    Button(
                        onClick = { onCompleteTask(task) },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp), // Увеличиваем высоту кнопки
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ProgressBarGreen, // Используем наш зеленый для завершения
                            contentColor = MaterialTheme.colorScheme.onPrimary // или другой контрастный
                        )
                    ) {
                        Text(
                            text = "✅ Завершить",
                            fontSize = 16.sp // Увеличиваем шрифт
                        )
                    }
                }
            }
        }
    }
}
