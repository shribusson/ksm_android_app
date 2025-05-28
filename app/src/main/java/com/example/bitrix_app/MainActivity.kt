package com.example.bitrix_app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed // Добавляем этот импорт
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack // Для кнопки "Назад"
import androidx.compose.material.icons.filled.Check // Для галочки завершения
import androidx.compose.material.icons.filled.ExpandLess // Для иконки "свернуть"
import androidx.compose.material.icons.filled.ExpandMore // Для иконки "развернуть"
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh // Для кнопки "Обновить"
import androidx.compose.material.icons.filled.Stop // Для иконки остановки записи
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow // <--- Добавляем этот импорт
import androidx.compose.ui.graphics.Color
import android.Manifest // Для запроса разрешений
import android.content.pm.PackageManager // Для проверки разрешений
import android.media.MediaRecorder
import android.util.Base64 // Для кодирования в Base64
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext // Для LocalContext.current
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily // Для моноширинного шрифта
import androidx.compose.ui.text.font.FontWeight
import androidx.core.content.ContextCompat // Для проверки разрешений
import androidx.core.content.FileProvider // Для FileProvider
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult // Для запроса разрешений
import androidx.activity.result.contract.ActivityResultContracts // Для запроса разрешений
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bitrix_app.ui.theme.* // Импортируем все из пакета темы
import timber.log.Timber
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.*
import kotlin.coroutines.resume

// Модели данных
data class User(val name: String, val webhookUrl: String, val userId: String, val avatar: String)

data class Task(
    val id: String,
    val title: String,
    val description: String,
    val timeSpent: Int,
    val timeEstimate: Int,
    val status: String = "",
    val changedDate: String? = null // Добавлено поле для даты изменения
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

// Enum для выбора темы
enum class AppThemeOptions {
    SYSTEM, LIGHT, DARK
}

// ViewModel
class MainViewModel : ViewModel() {
    private val client = OkHttpClient()

    // Пользователи с их ID в системе и аватарами
    val users = listOf(
        User("Денис Мелков", "https://bitrix.tooksm.kz/rest/320/gwx0v32nqbiwu7ww/", "320", "ДМ"),
        User("Владислав Малай", "https://bitrix.tooksm.kz/rest/321/smczp19q348xui28/", "321", "ВМ"),
        User("Ким Филби", "https://bitrix.tooksm.kz/rest/253/tk5y2f3sukqxn5bi/", "253", "КФ")
        // User("Тестовый Пользователь", "https://your_bitrix_domain/rest/user_id/webhook_code/", "user_id", "ТП")
    )

    var currentUserIndex by mutableStateOf(0)
    var tasks by mutableStateOf<List<Task>>(emptyList())
    var workStatus by mutableStateOf(WorkStatus.WORKING)
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var sendComments by mutableStateOf(true) // Настройка отправки комментариев

    // Состояние таймеров для всех пользователей
    private var userTimerDataMap by mutableStateOf<Map<String, UserTimerData>>(emptyMap())

    // Состояние раскрытия карточек задач
    var expandedTaskIds by mutableStateOf<Set<String>>(emptySet())
        private set

    // Состояния для чек-листов и подзадач
    var checklistsMap by mutableStateOf<Map<String, List<ChecklistItem>>>(emptyMap())
        private set
    var subtasksMap by mutableStateOf<Map<String, List<Task>>>(emptyMap())
        private set
    var loadingChecklistMap by mutableStateOf<Map<String, Boolean>>(emptyMap())
        private set
    var loadingSubtasksMap by mutableStateOf<Map<String, Boolean>>(emptyMap())
        private set

    // Состояния для записи аудио
    var currentRecordingTask by mutableStateOf<Task?>(null)
        private set
    var isRecordingAudio by mutableStateOf(false)
        private set
    var audioProcessingMessage by mutableStateOf<String?>(null)
        private set

    private var mediaRecorder: MediaRecorder? = null
    private var audioOutputFile: java.io.File? = null // Используем java.io.File

    // Состояние для отображения логов
    var logLines by mutableStateOf<List<String>>(emptyList())
        private set

    // Состояние для выбранной темы для каждого пользователя
    private var userSelectedThemeMap by mutableStateOf<Map<String, AppThemeOptions>>(emptyMap())


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

    fun toggleTaskExpansion(taskId: String) {
        expandedTaskIds = if (expandedTaskIds.contains(taskId)) {
            expandedTaskIds - taskId
        } else {
            expandedTaskIds + taskId
        }
        Timber.d("Toggled expansion for task $taskId. Expanded IDs: $expandedTaskIds")
    }

    var currentTime by mutableStateOf("")

    init {
        Timber.d("MainViewModel initialized.")
        updateWorkStatus() // Важно вызвать до loadTasks, чтобы timeman статус был актуален
        loadTasks()
        startPeriodicUpdates()
        startPeriodicTaskUpdates()
        startTimeUpdates()
        startUniversalTimerLoop() // Запускаем универсальный цикл таймера
    }

    fun getCurrentUserTheme(): AppThemeOptions {
        val userId = users[currentUserIndex].userId
        return userSelectedThemeMap[userId] ?: AppThemeOptions.SYSTEM
    }

    fun selectTheme(theme: AppThemeOptions) {
        val userId = users[currentUserIndex].userId
        userSelectedThemeMap = userSelectedThemeMap + (userId to theme)
        Timber.i("App theme changed to: $theme for user $userId (${users[currentUserIndex].name})")
    }

    fun switchUser(index: Int) {
        Timber.i("Switching user to index $index: ${users.getOrNull(index)?.name ?: "Unknown"}")
        // При смене пользователя, предыдущий таймер (если был) продолжает свое состояние
        // в userTimerDataMap. Новый пользователь подхватит свое состояние.
        currentUserIndex = index
        updateWorkStatus() // Обновляем статус рабочего дня для нового пользователя
        loadTasks() // Загружаем задачи для нового пользователя
        // Обновление tasks здесь больше не нужно, так как isTimerRunning удалено из Task,
        // а состояние для UI вычисляется на лету из getCurrentUserTimerData().
    }

    fun loadTasks() {
        Timber.d("loadTasks called for user: ${users[currentUserIndex].name}")
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
                "&select[]=RESPONSIBLE_ID" +
                "&select[]=CHANGED_DATE" // Добавляем CHANGED_DATE

        Timber.d("Loading tasks with URL: $url")

        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                viewModelScope.launch {
                    isLoading = false
                    errorMessage = "Ошибка подключения: ${e.message}"
                    Timber.e(e, "Network error while loading tasks")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                viewModelScope.launch {
                    isLoading = false
                    if (response.isSuccessful) {
                        response.body?.let { body ->
                            try {
                                val responseText = body.string()
                                Timber.d("Load tasks API Response: $responseText")

                                val json = JSONObject(responseText)

                                // Проверяем наличие ошибки в ответе
                                if (json.has("error")) {
                                    val error = json.getJSONObject("error")
                                    errorMessage = "Ошибка API: ${error.optString("error_description", "Неизвестная ошибка")}"
                                    Timber.w("API error in loadTasks: $errorMessage")
                                    return@launch
                                }

                                val newRawTasksList = mutableListOf<Task>()

                                // Обрабатываем результат
                                if (json.has("result")) {
                                    val result = json.get("result")
                                    when (result) {
                                        is JSONObject -> {
                                            if (result.has("tasks")) {
                                                processTasks(result.get("tasks"), newRawTasksList)
                                            } else {
                                                processTasks(result, newRawTasksList)
                                            }
                                        }
                                        is JSONArray -> processTasks(result, newRawTasksList)
                                    }
                                }

                                val newSortedTasksList = newRawTasksList.sortedWith(
                                    compareBy<Task> { it.id != currentUserDataBeforeLoad.activeTimerId } // Активная задача текущего пользователя в приоритете
                                        .thenBy { it.isCompleted }
                                        .thenByDescending { it.changedDate } // Сначала новые по дате изменения
                                        .thenBy { it.id.toIntOrNull() ?: 0 }
                                )

                                // Логика восстановления/сброса таймера на основе нового списка
                                var timerDataToUpdate = currentUserDataBeforeLoad
                                val activeTaskStillExistsInNewList = newSortedTasksList.any { it.id == currentUserDataBeforeLoad.activeTimerId }

                                if (currentUserDataBeforeLoad.activeTimerId != null && !activeTaskStillExistsInNewList) {
                                    Timber.i("Active task ${currentUserDataBeforeLoad.activeTimerId} no longer exists in fetched list for user ${user.name}. Resetting timer for this user.")
                                    timerDataToUpdate = UserTimerData()
                                }
                                // Если таймер нужно было изменить (например, сбросить), обновляем его
                                if (timerDataToUpdate != currentUserDataBeforeLoad) {
                                    updateCurrentUserTimerData(timerDataToUpdate)
                                }

                                // Сравниваем новый отсортированный список с текущим списком задач
                                if (!areTaskListsFunctionallyEquivalent(newSortedTasksList, tasks)) {
                                    Timber.i("Task list for user ${user.name} has changed. Updating UI with ${newSortedTasksList.size} tasks.")
                                    tasks = newSortedTasksList
                                } else {
                                    Timber.i("Task list for user ${user.name} has not changed (${newSortedTasksList.size} tasks). No UI update for tasks list.")
                                }

                                if (newRawTasksList.isEmpty()) {
                                    Timber.w("No tasks found for user ${user.name} with primary query. Trying simple query.")
                                    loadTasksSimple()
                                }

                            } catch (e: Exception) {
                                errorMessage = "Ошибка парсинга: ${e.message}"
                                Timber.e(e, "Parse error in loadTasks for user ${user.name}")
                            }
                        }
                    } else {
                        errorMessage = "Ошибка сервера: ${response.code} - ${response.message}"
                        Timber.e("HTTP error in loadTasks: ${response.code} - ${response.message}")
                    }
                }
            }
        })
    }

    // Простой метод загрузки без фильтров
    private fun loadTasksSimple() {
        val user = users[currentUserIndex]
        // Добавляем CHANGED_DATE и в простой запрос
        val url = "${user.webhookUrl}tasks.task.list?select[]=ID&select[]=TITLE&select[]=DESCRIPTION&select[]=TIME_SPENT_IN_LOGS&select[]=TIME_ESTIMATE&select[]=STATUS&select[]=CHANGED_DATE"

        Timber.d("Trying simple URL with basic fields for user ${user.name}: $url")

        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                viewModelScope.launch {
                    Timber.e(e, "Simple task load failed for user ${user.name}. Trying alternative.")
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
                                Timber.d("Simple API Response for user ${user.name}: $responseText")

                                val json = JSONObject(responseText)
                                if (json.has("result")) {
                                    val newRawTasksList = mutableListOf<Task>()
                                    val result = json.get("result")

                                    if (result is JSONObject && result.has("tasks")) {
                                        processTasks(result.get("tasks"), newRawTasksList)
                                    } else if (result is JSONArray) { // Если result это массив
                                        processTasks(result, newRawTasksList)
                                    } else if (result is JSONObject) { // Если result это объект задач
                                        processTasks(result, newRawTasksList)
                                    }


                                    if (newRawTasksList.isNotEmpty()) {
                                        val currentUserData = getCurrentUserTimerData()
                                        val newSortedTasksList = newRawTasksList.sortedWith(
                                            compareBy<Task> { it.id != currentUserData.activeTimerId }
                                                .thenBy { it.isCompleted }
                                                .thenByDescending { it.changedDate }
                                                .thenBy { it.id.toIntOrNull() ?: 0 }
                                        )

                                        if (!areTaskListsFunctionallyEquivalent(newSortedTasksList, tasks)) {
                                            Timber.i("Task list (simple) for user ${user.name} has changed. Updating UI.")
                                            tasks = newSortedTasksList
                                        } else {
                                            Timber.i("Task list (simple) for user ${user.name} has not changed. No UI update.")
                                        }
                                        errorMessage = null // Сбрасываем ошибку, так как что-то загрузили
                                        Timber.i("Successfully processed ${newRawTasksList.size} tasks from simple method for user ${user.name}")

                                    } else {
                                        Timber.w("Simple method yielded no tasks for user ${user.name}. Trying alternative.")
                                        loadTasksAlternative()
                                    }
                                } else {
                                     Timber.w("Simple method response for user ${user.name} does not have 'result' or tasks. Trying alternative.")
                                     loadTasksAlternative() // Пробуем альтернативный, если нет result или задач
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Simple parse error for user ${user.name}. Trying alternative.")
                                // Пробуем альтернативный метод
                                loadTasksAlternative()
                            }
                        }
                    } else {
                        Timber.w("Simple method HTTP error for user ${user.name}: ${response.code}. Trying alternative.")
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
                "?order[ID]=desc" + // Оставляем сортировку по ID для альтернативного варианта
                // "&filter[CREATED_BY]=${user.userId}" + // Убираем фильтр по CREATED_BY, он может быть слишком строгим
                "&select[]=ID&select[]=TITLE&select[]=DESCRIPTION&select[]=TIME_SPENT_IN_LOGS&select[]=TIME_ESTIMATE&select[]=STATUS&select[]=CHANGED_DATE" // Добавляем CHANGED_DATE

        Timber.d("Trying alternative URL for user ${user.name}: $url")

        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                viewModelScope.launch {
                    errorMessage = "Альтернативный запрос тоже не удался: ${e.message}"
                    Timber.e(e, "Alternative task load failed for user ${user.name}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                viewModelScope.launch {
                    if (response.isSuccessful) {
                        response.body?.let { body ->
                            try {
                                val responseText = body.string()
                                Timber.d("Alternative API Response for user ${user.name}: $responseText")

                                val json = JSONObject(responseText)
                                if (json.has("result")) {
                                    val newRawTasksList = mutableListOf<Task>()
                                    val result = json.get("result")

                                    if (result is JSONObject && result.has("tasks")) {
                                        processTasks(result.get("tasks"), newRawTasksList)
                                    } else if (result is JSONArray) {
                                        processTasks(result, newRawTasksList)
                                    } else if (result is JSONObject) {
                                        processTasks(result, newRawTasksList)
                                    }

                                    if (newRawTasksList.isNotEmpty()) {
                                        val currentUserData = getCurrentUserTimerData()
                                        val newSortedTasksList = newRawTasksList.sortedWith(
                                            compareBy<Task> { it.id != currentUserData.activeTimerId }
                                                .thenBy { it.isCompleted }
                                                .thenByDescending { it.changedDate }
                                                .thenBy { it.id.toIntOrNull() ?: 0 }
                                        )

                                        if (!areTaskListsFunctionallyEquivalent(newSortedTasksList, tasks)) {
                                            Timber.i("Task list (alternative) for user ${user.name} has changed. Updating UI.")
                                            tasks = newSortedTasksList
                                        } else {
                                            Timber.i("Task list (alternative) for user ${user.name} has not changed. No UI update.")
                                        }
                                        errorMessage = null
                                        Timber.i("Successfully processed ${newRawTasksList.size} tasks from alternative method for user ${user.name}")
                                    } else {
                                        Timber.w("Alternative method also yielded no tasks for user ${user.name}.")
                                        // Здесь можно установить сообщение, что задачи не найдены, если это последний метод загрузки
                                        if (tasks.isEmpty()) { // Если текущий список задач тоже пуст
                                            errorMessage = "Задачи не найдены для пользователя ${user.name}."
                                        }
                                    }
                                } else {
                                    Timber.w("Alternative method response for user ${user.name} does not have 'result' or tasks.")
                                    if (tasks.isEmpty()) {
                                        errorMessage = "Ошибка загрузки задач или задачи отсутствуют для пользователя ${user.name}."
                                    }
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Alternative parse error for user ${user.name}")
                                if (tasks.isEmpty()) {
                                     errorMessage = "Ошибка обработки задач для ${user.name}."
                                }
                            }
                        }
                    } else {
                         Timber.w("Alternative method HTTP error for user ${user.name}: ${response.code}.")
                    }
                }
            }
        })
    }

    private fun processTasks(tasksData: Any, tasksList: MutableList<Task>) {
        Timber.d("Processing tasks from data type: ${tasksData.javaClass.simpleName}")
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
        Timber.d("Processed ${tasksList.size} tasks.")
    }

    private fun createTaskFromJson(taskJson: JSONObject, fallbackId: String = ""): Task {
        // Timber.v("Creating task from JSON: ${taskJson.toString().take(100)}...") // Может быть слишком многословно
        val timeSpent = taskJson.optInt("timeSpentInLogs",
            taskJson.optInt("TIME_SPENT_IN_LOGS", 0))

        return Task(
            id = taskJson.optString("id", taskJson.optString("ID", fallbackId)),
            title = taskJson.optString("title", taskJson.optString("TITLE", "Задача без названия")),
            description = taskJson.optString("description", taskJson.optString("DESCRIPTION", "")),
            timeSpent = timeSpent,
            timeEstimate = taskJson.optInt("timeEstimate", taskJson.optInt("TIME_ESTIMATE", 7200)),
            status = taskJson.optString("status", taskJson.optString("STATUS", "")),
            changedDate = taskJson.optString("changedDate", taskJson.optString("CHANGED_DATE", null))
        )
    }

    // Функция для сравнения списков задач
    private fun areTaskListsFunctionallyEquivalent(newList: List<Task>, oldList: List<Task>): Boolean {
        if (newList.size != oldList.size) {
            Timber.d("Task lists differ in size. New: ${newList.size}, Old: ${oldList.size}")
            return false
        }

        // Сравниваем содержимое каждой задачи по ключевым полям
        // Задачи в обоих списках должны быть отсортированы одинаково перед этим сравнением,
        // или мы должны использовать Map для сравнения по ID.
        // Так как мы сортируем newSortedTasksList перед сравнением, и this.tasks также должен быть результатом предыдущей сортировки,
        // прямое поэлементное сравнение после проверки размеров должно работать, если порядок сортировки стабилен.
        // Однако, для большей надежности, лучше сравнивать по ID.

        val oldTasksMap = oldList.associateBy { it.id }

        for (newTask in newList) {
            val oldTask = oldTasksMap[newTask.id]
            if (oldTask == null) { // Новая задача, которой не было
                Timber.d("Task lists differ: New task found with ID ${newTask.id}")
                return false
            }
            // Сравниваем ключевые поля. Добавьте другие поля при необходимости.
            if (newTask.title != oldTask.title ||
                newTask.status != oldTask.status ||
                newTask.timeSpent != oldTask.timeSpent ||
                newTask.timeEstimate != oldTask.timeEstimate ||
                newTask.changedDate != oldTask.changedDate ||
                newTask.isCompleted != oldTask.isCompleted // Важно, если статус не покрывает это
            ) {
                Timber.d("Task lists differ: Task with ID ${newTask.id} has changed fields.")
                // Логирование конкретных изменений для отладки:
                // if (newTask.title != oldTask.title) Timber.v("Task ${newTask.id} title changed: '${oldTask.title}' -> '${newTask.title}'")
                // if (newTask.status != oldTask.status) Timber.v("Task ${newTask.id} status changed: '${oldTask.status}' -> '${newTask.status}'")
                // if (newTask.timeSpent != oldTask.timeSpent) Timber.v("Task ${newTask.id} timeSpent changed: ${oldTask.timeSpent} -> ${newTask.timeSpent}")
                // if (newTask.timeEstimate != oldTask.timeEstimate) Timber.v("Task ${newTask.id} timeEstimate changed: ${oldTask.timeEstimate} -> ${newTask.timeEstimate}")
                // if (newTask.changedDate != oldTask.changedDate) Timber.v("Task ${newTask.id} changedDate changed: '${oldTask.changedDate}' -> '${newTask.changedDate}'")
                return false
            }
        }
        // Проверяем, что в старом списке нет задач, которые исчезли из нового (удаление)
        if (oldList.any { oldTask -> newList.none { newTask -> newTask.id == oldTask.id } }) {
            Timber.d("Task lists differ: Some tasks were removed.")
            return false
        }


        return true // Списки идентичны по ключевым полям
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
                    Timber.e(e, "Failed to fetch checklist for task $taskId")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                viewModelScope.launch {
                    loadingChecklistMap = loadingChecklistMap - taskId
                    if (response.isSuccessful) {
                        response.body?.let { body ->
                            try {
                                val responseText = body.string()
                                Timber.d("Checklist response for task $taskId: $responseText")
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
                                    Timber.i("Fetched ${itemsList.size} checklist items for task $taskId.")
                                    // itemsList.forEach { item ->
                                    // Timber.v("  - ID: ${item.id}, Title: ${item.title}, IsComplete: ${item.isComplete}")
                                    // }
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Error parsing checklist for task $taskId")
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
        Timber.d("Fetching subtasks for task $taskId for user ${user.name}")
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
                    Timber.e(e, "Failed to fetch subtasks for task $taskId")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                viewModelScope.launch {
                    loadingSubtasksMap = loadingSubtasksMap - taskId
                    if (response.isSuccessful) {
                        response.body?.let { body ->
                            try {
                                val responseText = body.string()
                                Timber.d("Subtasks response for task $taskId: $responseText")
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
                                Timber.i("Fetched ${subtasksList.size} subtasks for task $taskId.")
                            } catch (e: Exception) {
                                Timber.e(e, "Error parsing subtasks for task $taskId")
                            }
                        }
                    }
                }
            }
        })
    }

    fun toggleChecklistItemStatus(taskId: String, checklistItemId: String, currentIsComplete: Boolean) {
        val user = users[currentUserIndex]
        val action = if (currentIsComplete) "task.checklistitem.renew" else "task.checklistitem.complete"
        val url = "${user.webhookUrl}$action"

        Timber.i("Toggling checklist item: URL=$url, TASKID=$taskId, ITEMID=$checklistItemId, Action=${if (currentIsComplete) "renew" else "complete"} for user ${user.name}")

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
                    Timber.e(e, "Failed to toggle checklist item $checklistItemId for task $taskId")
                    // Откатываем изменение в случае ошибки
                    checklistsMap = checklistsMap + (taskId to oldChecklist)
                    // Можно добавить сообщение об ошибке для пользователя
                }
            }

            override fun onResponse(call: Call, response: Response) {
                viewModelScope.launch {
                    val responseBody = response.body?.string()
                    if (!response.isSuccessful) {
                        Timber.w("Error toggling checklist item $checklistItemId for task $taskId: ${response.code}. Response: $responseBody")
                        // Откатываем изменение в случае ошибки от сервера
                        checklistsMap = checklistsMap + (taskId to oldChecklist)
                    } else {
                        // Если успешно, данные уже оптимистично обновлены.
                        // Можно дополнительно перезапросить чек-лист для полной синхронизации, если необходимо.
                        // fetchChecklistForTask(taskId) // Раскомментировать, если нужна полная синхронизация
                        Timber.i("Successfully toggled checklist item $checklistItemId for task $taskId. New state: ${!currentIsComplete}. Response: $responseBody")
                    }
                    response.close()
                }
            }
        })
    }


    fun toggleTimer(task: Task) {
        val currentUserData = getCurrentUserTimerData()
        val user = users[currentUserIndex]
        Timber.i("toggleTimer called for task '${task.title}' (ID: ${task.id}) for user ${user.name}")

        if (currentUserData.activeTimerId == task.id && !currentUserData.isSystemPaused && !currentUserData.isPausedForUserAction) {
            // Таймер активен для этой задачи и не на системной паузе/пользовательской паузе -> останавливаем
            Timber.d("Stopping active timer for task ${task.id}")
            stopTimerAndSaveTime(task, currentUserData.timerSeconds)
            if (sendComments) {
                sendTimerComment(task, "Таймер остановлен", currentUserData.timerSeconds)
            }
            updateCurrentUserTimerData(
                currentUserData.copy(
                    isPausedForUserAction = true, // Ставим на пользовательскую паузу
                    pausedTaskIdForUserAction = task.id,
                    pausedTimerSecondsForUserAction = currentUserData.timerSeconds
                    // activeTimerId и timerSeconds остаются, чтобы показать "приостановленный" таймер
                )
            )
        } else {
            // Либо таймер для другой задачи, либо этот на паузе (системной/пользовательской), либо нет активного таймера
            Timber.d("Starting or resuming timer for task ${task.id}")
            // Останавливаем любой другой активный таймер (если он был и не на паузе)
            if (currentUserData.activeTimerId != null && currentUserData.activeTimerId != task.id && !currentUserData.isPausedForUserAction && !currentUserData.isSystemPaused) {
                val previousTask = tasks.find { it.id == currentUserData.activeTimerId }
                previousTask?.let {
                    Timber.d("Stopping timer for previous task ${it.id} due to switching to task ${task.id}")
                    stopTimerAndSaveTime(it, currentUserData.timerSeconds)
                    if (sendComments) {
                        sendTimerComment(it, "Таймер остановлен (переключение на задачу ${task.id})", currentUserData.timerSeconds)
                    }
                }
            }

            var newTimerSeconds = 0
            var commentAction = "Таймер запущен"

            if (currentUserData.pausedTaskIdForUserAction == task.id) {
                // Возобновляем таймер, который был приостановлен пользователем для этой задачи
                Timber.d("Resuming user-paused timer for task ${task.id} from ${currentUserData.pausedTimerSecondsForUserAction}s")
                newTimerSeconds = currentUserData.pausedTimerSecondsForUserAction
                commentAction = "Таймер возобновлен"
            } else if (currentUserData.activeTimerId == task.id && currentUserData.isPausedForUserAction) {
                 // Это условие дублирует предыдущее, если pausedTaskIdForUserAction == task.id.
                 // Если activeTimerId == task.id, но pausedTaskIdForUserAction другой или null, это новая логика.
                 // По факту, если isPausedForUserAction, то pausedTaskIdForUserAction должен быть равен activeTimerId.
                 // Оставляем для ясности, что мы возобновляем именно пользовательскую паузу.
                Timber.d("Resuming (from isPausedForUserAction) timer for task ${task.id} from ${currentUserData.timerSeconds}s")
                newTimerSeconds = currentUserData.timerSeconds // Используем текущие timerSeconds, если они были сохранены на паузе
                commentAction = "Таймер возобновлен"
            }


            updateCurrentUserTimerData(
                UserTimerData(
                    activeTimerId = task.id,
                    timerSeconds = newTimerSeconds,
                    isPausedForUserAction = false, // Снимаем пользовательскую паузу
                    pausedTaskIdForUserAction = null,
                    pausedTimerSecondsForUserAction = 0,
                    isSystemPaused = currentUserData.isSystemPaused // Сохраняем состояние системной паузы
                )
            )

            if (sendComments) {
                sendTimerComment(task, commentAction, newTimerSeconds)
            }
            Timber.i("$commentAction for task ${task.id} at ${newTimerSeconds}s")

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
        Timber.d("Sending timer comment for task ${task.id}, action: '$action', user: ${user.name}, time: ${formatTime(currentSeconds)}")
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
                Timber.e(e, "Comment send error for task ${task.id}")
            }

            override fun onResponse(call: Call, response: Response) {
                response.body?.let { body ->
                    val responseText = body.string()
                    if (response.isSuccessful) {
                        Timber.d("Comment sent successfully for task ${task.id}. Response: $responseText")
                    } else {
                        Timber.w("Failed to send comment for task ${task.id}. Code: ${response.code}. Response: $responseText")
                    }
                }
                response.close()
            }
        })
    }

    // Сохранение времени в Битрикс при остановке таймера
    private fun stopTimerAndSaveTime(task: Task, secondsToSave: Int) {
        val user = users[currentUserIndex]
        Timber.i("stopTimerAndSaveTime called for task ${task.id}, user ${user.name}, seconds: $secondsToSave")
        // Сохраняем время только если прошло больше 10 секунд
        if (secondsToSave < 10) {
            Timber.i("Timer too short (${secondsToSave}s), not saving to Bitrix for task ${task.id}")
            return
        }

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
                    Timber.e(e, "Save time network error for task ${task.id}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                viewModelScope.launch {
                    response.body?.let { body ->
                        val responseText = body.string()
                        Timber.d("Save time response for task ${task.id}: $responseText")

                        try {
                            val json = JSONObject(responseText)
                            if (json.has("error")) {
                                val errorDesc = json.optString("error_description", "Unknown error")
                                Timber.w("Error saving time for task ${task.id}: $errorDesc. Trying simplified parameters...")
                                saveTimeSimplified(task, secondsToSave)
                            } else if (json.has("result")) {
                                Timber.i("Time saved successfully for task ${task.id}. Reloading tasks.")
                                // Успешно сохранено - обновляем задачи без уведомления
                                delay(1000)
                                loadTasks()
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Parse error in save time response for task ${task.id}")
                        }
                    }
                    response.close()
                }
            }
        })
    }

    // Упрощенный способ сохранения времени без USER_ID
    private fun saveTimeSimplified(task: Task, secondsToSave: Int) {
        val user = users[currentUserIndex]
        Timber.i("saveTimeSimplified called for task ${task.id}, user ${user.name}, seconds: $secondsToSave")
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
                    Timber.e(e, "Simplified save time error for task ${task.id}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                viewModelScope.launch {
                    response.body?.let { body ->
                        val responseText = body.string()
                        Timber.d("Simplified save time response for task ${task.id}: $responseText")

                        try {
                            val json = JSONObject(responseText)
                            if (json.has("result")) {
                                Timber.i("Time saved successfully (simplified) for task ${task.id}. Reloading tasks.")
                                // Успешно сохранено - обновляем задачи
                                delay(1000)
                                loadTasks()
                            } else {
                                val errorDesc = json.optString("error_description", "Unknown error")
                                Timber.w("Error saving time (simplified) for task ${task.id}: $errorDesc")
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Simplified parse error in save time response for task ${task.id}")
                        }
                    }
                    response.close()
                }
            }
        })
    }

    // Приостановка таймеров ЗАДАЧ для ВСЕХ пользователей из-за системных событий
    private fun systemPauseAllTaskTimers() {
        Timber.i("System pausing task timers for ALL users.")
        val newMap = userTimerDataMap.toMutableMap()
        var changed = false
        users.forEach { user ->
            val userData = userTimerDataMap[user.userId] ?: UserTimerData()
            if (userData.activeTimerId != null && !userData.isSystemPaused) {
                newMap[user.userId] = userData.copy(isSystemPaused = true)
                changed = true
                // Комментарий здесь не отправляем, т.к. tasks относится к текущему пользователю,
                // а функция глобальная. Логирование остается.
                Timber.i("Task timer system-paused for task ${userData.activeTimerId} for user ${user.name} with ${userData.timerSeconds}s")
            }
        }
        if (changed) {
            userTimerDataMap = newMap.toMap()
        }
    }

    // Возобновление таймеров ЗАДАЧ для ВСЕХ пользователей после системных событий
    private fun systemResumeAllTaskTimers() {
        Timber.i("System resuming task timers for ALL users.")
        val newMap = userTimerDataMap.toMutableMap()
        var changed = false
        users.forEach { user ->
            val userData = userTimerDataMap[user.userId] ?: UserTimerData()
            if (userData.activeTimerId != null && userData.isSystemPaused) {
                newMap[user.userId] = userData.copy(isSystemPaused = false)
                changed = true
                // Комментарий здесь не отправляем.
                Timber.i("Task timer system-resumed for task ${userData.activeTimerId} for user ${user.name} with ${userData.timerSeconds}s")
            }
        }
        if (changed) {
            userTimerDataMap = newMap.toMap()
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
        val user = users[currentUserIndex]
        Timber.i("Complete task called for task ${task.id} by user ${user.name}")

        // Если есть активный таймер на этой задаче (не на системной паузе и не на пользовательской), сначала сохраняем время
        if (currentUserData.activeTimerId == task.id &&
            !currentUserData.isSystemPaused &&
            !currentUserData.isPausedForUserAction && // Убедимся, что таймер действительно "шел"
            currentUserData.timerSeconds > 0) {
            Timber.d("Task ${task.id} has an active timer with ${currentUserData.timerSeconds}s. Stopping and saving time first.")
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
            if (currentUserData.activeTimerId == task.id && currentUserData.isPausedForUserAction && currentUserData.pausedTimerSecondsForUserAction > 0) {
                // Если таймер был на пользовательской паузе с накопленным временем
                Timber.d("Task ${task.id} was on user pause with ${currentUserData.pausedTimerSecondsForUserAction}s. Saving this time.")
                stopTimerAndSaveTime(task, currentUserData.pausedTimerSecondsForUserAction)
                 if (sendComments) {
                    sendTimerComment(task, "Задача завершена (с учетом времени на паузе), таймер остановлен", currentUserData.pausedTimerSecondsForUserAction)
                }
                updateCurrentUserTimerData(UserTimerData()) // Сбрасываем таймер
                 viewModelScope.launch {
                    delay(1500) // Даем время на сохранение
                    completeTaskInBitrix(task)
                }
            } else {
                 // Если таймер не был активен для этой задачи, или время 0, просто завершаем
                Timber.d("Task ${task.id} timer was not active or had 0 seconds. Completing directly.")
                completeTaskInBitrix(task)
            }
        }
    }

    private fun completeTaskInBitrix(task: Task) {
        val user = users[currentUserIndex]
        Timber.i("Sending completeTaskInBitrix for task ${task.id}, user ${user.name}")
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
                    Timber.e(e, "Task complete network error for task ${task.id}")
                }
            }

            override fun onResponse(call: Call, response: Response) {
                viewModelScope.launch {
                    response.body?.let { body ->
                        val responseText = body.string()
                        if (response.isSuccessful) {
                            Timber.i("Task ${task.id} completed successfully in Bitrix. Response: $responseText")
                        } else {
                            Timber.w("Failed to complete task ${task.id} in Bitrix. Code: ${response.code}. Response: $responseText")
                        }

                        // В любом случае обновляем задачи через 1 секунду
                        // (задача скорее всего завершена успешно или статус изменился)
                        delay(1000)
                        loadTasks()
                    }
                    response.close()
                }
            }
        })
    }

    fun toggleComments() {
        sendComments = !sendComments
        Timber.i("Send comments toggled to: $sendComments")
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

        val previousGlobalStatus = workStatus // Глобальный предыдущий статус

        // Новое расписание:
        // Начало работы: 08:00 (480 минут)
        // Перерыв 1: 09:45 - 10:00 (585 до 599)
        // Обед:     12:00 - 12:50 (720 до 769)
        // Перерыв 2: 14:45 - 15:00 (885 до 899)
        // Конец работы: 17:00 (1020 минут)
        val newGlobalWorkStatus = when {
            currentMinutes < 8 * 60 -> WorkStatus.BEFORE_WORK                                  // До 08:00
            currentMinutes in (9 * 60 + 45) until (10 * 60) -> WorkStatus.BREAK              // 09:45 - 09:59
            currentMinutes in (12 * 60) until (12 * 60 + 50) -> WorkStatus.LUNCH             // 12:00 - 12:49
            currentMinutes in (14 * 60 + 45) until (15 * 60) -> WorkStatus.BREAK             // 14:45 - 14:59
            currentMinutes >= 17 * 60 -> WorkStatus.AFTER_WORK                                 // С 17:00
            currentMinutes >= 8 * 60 && currentMinutes < 17*60 -> WorkStatus.WORKING // Рабочее время между 08:00 и 17:00, исключая перерывы
            else -> WorkStatus.WORKING // По умолчанию рабочее, если не попало в другие условия (например, точно 08:00)
        }

        if (previousGlobalStatus != newGlobalWorkStatus) {
            Timber.i("Global work status changing from $previousGlobalStatus to $newGlobalWorkStatus")
            workStatus = newGlobalWorkStatus // Обновляем глобальный статус для UI

            users.forEach { user -> // Применяем действия timeman для КАЖДОГО пользователя
                Timber.d("Applying timeman actions for user ${user.name} due to global status change from $previousGlobalStatus to $newGlobalWorkStatus")
                when {
                    // Начало рабочего дня или возобновление работы
                    (previousGlobalStatus == WorkStatus.BEFORE_WORK || previousGlobalStatus == WorkStatus.BREAK || previousGlobalStatus == WorkStatus.LUNCH) && newGlobalWorkStatus == WorkStatus.WORKING -> {
                        timemanOpenWorkDay(user)
                    }
                    // Уход на перерыв/обед
                    previousGlobalStatus == WorkStatus.WORKING && (newGlobalWorkStatus == WorkStatus.BREAK || newGlobalWorkStatus == WorkStatus.LUNCH) -> {
                        timemanPauseWorkDay(user)
                    }
                    // Конец рабочего дня
                    previousGlobalStatus == WorkStatus.WORKING && newGlobalWorkStatus == WorkStatus.AFTER_WORK -> {
                        timemanCloseWorkDay(user)
                    }
                    else -> {
                        Timber.d("No specific timeman action for user ${user.name} for transition from $previousGlobalStatus to $newGlobalWorkStatus.")
                    }
                }
            }

            // Обновляем состояние системной паузы для таймеров ЗАДАЧ ВСЕХ пользователей
            when {
                newGlobalWorkStatus == WorkStatus.WORKING && (previousGlobalStatus == WorkStatus.BREAK || previousGlobalStatus == WorkStatus.LUNCH || previousGlobalStatus == WorkStatus.BEFORE_WORK) -> {
                    systemResumeAllTaskTimers()
                }
                (newGlobalWorkStatus == WorkStatus.BREAK || newGlobalWorkStatus == WorkStatus.LUNCH || newGlobalWorkStatus == WorkStatus.AFTER_WORK) && previousGlobalStatus == WorkStatus.WORKING -> {
                    systemPauseAllTaskTimers()
                }
            }

        } else {
            // Статус не изменился, но если мы в рабочем состоянии и день не открыт (например, после перезапуска приложения), пытаемся открыть.
            // Это более сложная логика, требующая проверки timeman.status, пока опустим для простоты.
            // Можно добавить проверку и вызов timemanOpenWorkDay для всех, если newGlobalWorkStatus == WorkStatus.WORKING и их timeman.status != 'OPENED'
        }
    }


    // --- Timeman API Calls ---
    private fun timemanOpenWorkDay(user: User) {
        Timber.i("Attempting to open workday for user ${user.name} (ID: ${user.userId})")
        val url = "${user.webhookUrl}timeman.open"
        val request = Request.Builder()
            .url(url)
            .post(FormBody.Builder().build()) // Обычно параметры не нужны
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Timber.e(e, "Failed to open workday for user ${user.name}")
            }
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (response.isSuccessful) {
                    Timber.i("Successfully opened workday for user ${user.name}. Response: $responseBody")
                } else {
                    Timber.w("Failed to open workday for user ${user.name}. Code: ${response.code}. Response: $responseBody")
                }
                response.close()
            }
        })
    }

    private fun timemanPauseWorkDay(user: User) {
        Timber.i("Attempting to pause workday for user ${user.name} (ID: ${user.userId})")
        val url = "${user.webhookUrl}timeman.pause"
        val request = Request.Builder()
            .url(url)
            .post(FormBody.Builder().build()) // Обычно параметры не нужны
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Timber.e(e, "Failed to pause workday for user ${user.name}")
            }
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (response.isSuccessful) {
                    Timber.i("Successfully paused workday for user ${user.name}. Response: $responseBody")
                } else {
                    Timber.w("Failed to pause workday for user ${user.name}. Code: ${response.code}. Response: $responseBody")
                }
                response.close()
            }
        })
    }

    private fun timemanCloseWorkDay(user: User) {
        Timber.i("Attempting to close workday for user ${user.name} (ID: ${user.userId})")
        val url = "${user.webhookUrl}timeman.close"
        val request = Request.Builder()
            .url(url)
            .post(FormBody.Builder().build()) // Обычно параметры не нужны
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Timber.e(e, "Failed to close workday for user ${user.name}")
            }
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                if (response.isSuccessful) {
                    Timber.i("Successfully closed workday for user ${user.name}. Response: $responseBody")
                } else {
                    Timber.w("Failed to close workday for user ${user.name}. Code: ${response.code}. Response: $responseBody")
                }
                response.close()
            }
        })
    }
    // --- End Timeman API Calls ---


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

    fun toggleAudioRecording(task: Task, context: Context) {
        if (isRecordingAudio) {
            if (currentRecordingTask?.id == task.id) {
                stopAudioRecordingAndProcess(context)
            } else {
                // Если запись идет для другой задачи, сначала остановим ее (можно без сохранения)
                Timber.w("Audio recording for task ${currentRecordingTask?.id} was interrupted to record for task ${task.id}")
                stopAudioRecordingAndProcess(context, discard = true) // Останавливаем и отменяем предыдущую
                startAudioRecording(task, context) // Начинаем новую
            }
        } else {
            startAudioRecording(task, context)
        }
    }

    private fun startAudioRecording(task: Task, context: Context) {
        currentRecordingTask = task
        val fileName = "audio_comment_${task.id}_${System.currentTimeMillis()}.m4a" // Используем M4A формат
        audioOutputFile = java.io.File(context.cacheDir, fileName)

        mediaRecorder = MediaRecorder(context).apply { // Для API 31+ нужен контекст
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)   // Используем M4A формат
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)      // Используем AAC кодек
            setOutputFile(audioOutputFile?.absolutePath)
            try {
                prepare()
                start()
                isRecordingAudio = true
                audioProcessingMessage = "Идет запись для '${task.title}'..."
                Timber.i("Audio recording started for task ${task.id} to file ${audioOutputFile?.absolutePath}")
            } catch (e: IOException) {
                Timber.e(e, "MediaRecorder prepare() failed for task ${task.id}")
                audioProcessingMessage = "Ошибка начала записи: ${e.message}"
                resetAudioRecordingState()
            } catch (e: IllegalStateException) {
                Timber.e(e, "MediaRecorder start() failed for task ${task.id}")
                audioProcessingMessage = "Ошибка старта записи: ${e.message}"
                resetAudioRecordingState()
            }
        }
    }

    private fun stopAudioRecordingAndProcess(context: Context, discard: Boolean = false) {
        if (!isRecordingAudio && mediaRecorder == null) { // Если уже остановлено или не начиналось
            Timber.d("stopAudioRecordingAndProcess called but no active recording or recorder.")
            resetAudioRecordingState() // Просто сбрасываем состояние на всякий случай
            return
        }

        try {
            mediaRecorder?.stop()
        } catch (e: RuntimeException) {
            // Часто возникает, если stop() вызывается слишком быстро после start() или в неправильном состоянии
            Timber.w(e, "MediaRecorder stop() failed. May be called too soon or in wrong state.")
            audioOutputFile?.delete()
            audioOutputFile = null
        } finally {
            mediaRecorder?.release()
            mediaRecorder = null
        }

        val recordedFile = audioOutputFile
        val taskToAttach = currentRecordingTask

        isRecordingAudio = false

        if (discard || recordedFile == null || !recordedFile.exists() || recordedFile.length() == 0L || taskToAttach == null) {
            audioProcessingMessage = if (discard) "Запись отменена." else "Ошибка: аудиофайл не создан или пуст."
            Timber.w("Audio recording processing aborted. Discard: $discard, File: ${recordedFile?.path}, Exists: ${recordedFile?.exists()}, Length: ${recordedFile?.length()}, Task: ${taskToAttach?.id}")
            recordedFile?.delete()
            resetAudioRecordingState(clearMessageDelay = 3000L)
            return
        }

        audioProcessingMessage = "Обработка аудио для '${taskToAttach.title}'..."
        Timber.i("Audio recording stopped for task ${taskToAttach.id}. File: ${recordedFile.absolutePath}, Size: ${recordedFile.length()} bytes.")
        uploadAudioAndCreateComment(taskToAttach, recordedFile, context)

        audioOutputFile = null
    }


    private suspend fun fetchUserStorageId(user: User): String? {
        // Первая попытка: с фильтром по USER и ENTITY_ID
        val specificUrl = "${user.webhookUrl}disk.storage.getlist?filter[ENTITY_TYPE]=USER&filter[ENTITY_ID]=${user.userId}"
        Timber.d("Attempt 1: Fetching storage ID for user ${user.userId} with URL: $specificUrl")
        var storageId = makeStorageRequest(specificUrl, user, true)

        if (storageId != null) {
            Timber.i("Found storage ID '${storageId}' for user ${user.userId} using specific filter.")
            return storageId
        }

        // Вторая попытка: без фильтров, с последующей фильтрацией на клиенте
        Timber.w("Specific storage filter failed for user ${user.userId}. Attempt 2: Fetching all storages and filtering client-side.")
        val genericUrl = "${user.webhookUrl}disk.storage.getlist"
        Timber.d("Attempt 2: Fetching all storages for user ${user.userId} (webhook context) with URL: $genericUrl")
        storageId = makeStorageRequest(genericUrl, user, false)

        if (storageId != null) {
            Timber.i("Found storage ID '${storageId}' for user ${user.userId} by filtering all storages.")
            return storageId
        }

        Timber.e("Failed to find any suitable storage for user ${user.userId} after two attempts. Ensure the webhook for user ${user.name} (ID: ${user.userId}) has the 'disk' permission scope in Bitrix24 and the user has an accessible storage.")
        return null
    }

    private suspend fun makeStorageRequest(url: String, user: User, isSpecificFilter: Boolean): String? = suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Timber.e(e, "Failed to fetch storage list from URL: $url")
                if (continuation.isActive) continuation.resume(null)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBody = response.body?.string()

                    if (!response.isSuccessful) {
                        Timber.w("Fetch storage list from URL $url failed. Code: ${response.code}, Message: ${response.message}, Body: $responseBody")
                        if (continuation.isActive) continuation.resume(null)
                        return
                    }

                    if (responseBody == null) {
                        Timber.w("Fetch storage list response body is null from URL: $url")
                        if (continuation.isActive) continuation.resume(null)
                        return
                    }
                    Timber.d("Storage list response from URL $url: $responseBody")
                    val json = JSONObject(responseBody)

                    if (json.has("error")) {
                        val errorDescription = json.optString("error_description", json.optString("error", "Unknown API error"))
                        Timber.w("API error in fetch storage list response from URL $url: $errorDescription. Full response: $responseBody")
                        if (continuation.isActive) continuation.resume(null)
                        return
                    }

                    val resultArray = json.optJSONArray("result")
                    if (resultArray != null && resultArray.length() > 0) {
                        if (isSpecificFilter) {
                            // Если это был специфический запрос, и он вернул результат, берем первый ID
                            val firstStorageObject = resultArray.getJSONObject(0)
                            val id = firstStorageObject.optString("ID")
                            if (id.isNotEmpty()) {
                                if (continuation.isActive) continuation.resume(id)
                                return
                            }
                        } else {
                            // Если это был общий запрос, ищем подходящее хранилище
                            for (i in 0 until resultArray.length()) {
                                val storageObject = resultArray.getJSONObject(i)
                                val entityId = storageObject.optString("ENTITY_ID")
                                val entityType = storageObject.optString("ENTITY_TYPE")
                                val id = storageObject.optString("ID")
                                val name = storageObject.optString("NAME", "N/A") // Логируем также имя хранилища для информации
                                Timber.d("Checking storage object: ID=$id, NAME=$name, ENTITY_ID=$entityId, ENTITY_TYPE=$entityType for user ${user.userId}")

                                if (id.isNotEmpty() && entityId == user.userId && entityType.equals("USER", ignoreCase = true)) {
                                    Timber.i("Found matching user storage: ID=$id, NAME=$name, ENTITY_ID=$entityId, ENTITY_TYPE=$entityType for user ${user.userId}")
                                    if (continuation.isActive) continuation.resume(id)
                                    return
                                }
                            }
                            // Если не нашли точное совпадение, можно попробовать взять первое хранилище типа USER, если оно есть
                            Timber.d("Exact match for user ${user.userId} not found. Looking for first available 'USER' type storage.")
                            for (i in 0 until resultArray.length()) {
                                val storageObject = resultArray.getJSONObject(i)
                                val entityType = storageObject.optString("ENTITY_TYPE")
                                val id = storageObject.optString("ID")
                                val name = storageObject.optString("NAME", "N/A")
                                if (id.isNotEmpty() && entityType.equals("USER", ignoreCase = true)) {
                                    Timber.w("Could not find exact user storage for ${user.userId}. Using first available USER storage: ID=$id, NAME=$name, ENTITY_TYPE=$entityType")
                                    if (continuation.isActive) continuation.resume(id)
                                    return
                                }
                            }
                        }
                    }
                    
                    Timber.w("No suitable storage found in response from URL $url. Response: $responseBody. Ensure the webhook for user ${user.name} (ID: ${user.userId}) has the 'disk' permission scope in Bitrix24.")
                    if (continuation.isActive) continuation.resume(null)
                } catch (e: Exception) {
                    Timber.e(e, "Error parsing storage list response from URL $url. Raw response might have been logged above. Ensure the webhook for user ${user.name} (ID: ${user.userId}) has the 'disk' permission scope in Bitrix24.")
                    if (continuation.isActive) continuation.resume(null)
                } finally {
                    response.close()
                }
            }
        })
        continuation.invokeOnCancellation {
            Timber.d("makeStorageRequest coroutine cancelled for URL: $url")
        }
    }

    private suspend fun uploadFileToStorage(user: User, storageId: String, file: java.io.File): String? = suspendCancellableCoroutine { continuation ->
        val url = "${user.webhookUrl}disk.storage.uploadfile"
        Timber.d("Uploading file ${file.name} to storage $storageId for user ${user.userId} using Base64. URL: $url")

        val fileBytes = try {
            file.readBytes()
        } catch (e: IOException) {
            Timber.e(e, "Failed to read file ${file.name} for Base64 encoding")
            if (continuation.isActive) continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        val fileBase64 = Base64.encodeToString(fileBytes, Base64.NO_WRAP)

        val formBody = FormBody.Builder()
            .add("id", storageId) // ID хранилища
            .add("data[NAME]", file.name) // Имя файла, как описано для disk.folder.uploadfile
            .add("fileContent[0]", file.name) // Имя файла как первый элемент массива fileContent
            .add("fileContent[1]", fileBase64) // Содержимое файла в Base64 как второй элемент
            .build()
        // При отправке Base64 MIME-тип в RequestBody не используется напрямую для файла,
        // но если бы мы отправляли бинарный файл (не Base64), здесь был бы, например, audio/webm
        // val requestBodyForBinaryFile = file.asRequestBody("audio/webm".toMediaTypeOrNull())

        val request = Request.Builder().url(url).post(formBody).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Timber.e(e, "Failed to upload file ${file.name}")
                if (continuation.isActive) continuation.resume(null)
            }

            override fun onResponse(call: Call, response: Response) {
                try {
                    val responseBodyString = response.body?.string() // Читаем тело ответа один раз

                    if (!response.isSuccessful) {
                        Timber.w("File upload failed for ${file.name}. Code: ${response.code}, Message: ${response.message}. Response Body: $responseBodyString")
                        if (continuation.isActive) continuation.resume(null)
                        return
                    }
                    // val responseBody = response.body?.string() // Уже прочитано выше
                    if (responseBodyString == null) { // Используем прочитанное тело
                        Timber.w("File upload response body is null for ${file.name}")
                        if (continuation.isActive) continuation.resume(null)
                        return
                    }
                    Timber.d("File upload response for ${file.name}: $responseBodyString")
                    val json = JSONObject(responseBodyString)
                    if (json.has("result")) {
                        val resultObject = json.getJSONObject("result")
                        val diskObjectId = resultObject.optString("ID") // ID объекта файла на Диске
                        val bFileId = resultObject.optString("FILE_ID") // ID файла в b_file (для информации)

                        if (diskObjectId.isNotEmpty()) {
                            Timber.i("Disk Object ID: '$diskObjectId', b_file ID: '$bFileId'. Using Disk Object ID ('$diskObjectId') for UF_FORUM_MESSAGE_DOC.")
                            if (continuation.isActive) continuation.resume(diskObjectId) // Возвращаем ID объекта Диска
                            return
                        }
                    }
                    Timber.w("Disk Object ID (result.ID) not found or empty in upload response for ${file.name}")
                    if (continuation.isActive) continuation.resume(null)
                } catch (e: Exception) {
                    Timber.e(e, "Error parsing file upload response for ${file.name}")
                    if (continuation.isActive) continuation.resume(null)
                } finally {
                    response.close()
                }
            }
        })
        continuation.invokeOnCancellation {
            Timber.d("uploadFileToStorage coroutine cancelled for file ${file.name}")
        }
    }

    private suspend fun addCommentToTask(user: User, taskId: String, uploadedFileId: String): Boolean = suspendCancellableCoroutine { continuation ->
        val url = "${user.webhookUrl}task.commentitem.add"
        val postMessageText = "Аудиокомментарий к задаче (см. вложение)." // Общий текст для сообщения
        val fileIdForUf = "n$uploadedFileId" // Добавляем префикс 'n' к ID файла Диска
        Timber.d("Adding comment to task $taskId with file ID $fileIdForUf (as UF_FORUM_MESSAGE_DOC). User: ${user.name}. URL: $url. Message: $postMessageText")

        val formBody = FormBody.Builder()
            .add("TASK_ID", taskId)
            .add("FIELDS[POST_MESSAGE]", postMessageText)
            .add("FIELDS[UF_FORUM_MESSAGE_DOC][0]", fileIdForUf) // Прикрепляем файл через UF_FORUM_MESSAGE_DOC
            .add("FIELDS[AUTHOR_ID]", user.userId)
            .build()

        val request = Request.Builder().url(url).post(formBody).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Timber.e(e, "Failed to add comment to task $taskId")
                if (continuation.isActive) continuation.resume(false)
            }

            override fun onResponse(call: Call, response: Response) {
                var success = false
                try {
                    val responseBody = response.body?.string() // Читаем тело ответа один раз
                    if (!response.isSuccessful) {
                        Timber.w("Add comment failed for task $taskId. Code: ${response.code}, Message: ${response.message}. Body: $responseBody")
                    } else {
                        Timber.d("Add comment response for task $taskId: $responseBody")
                        if (responseBody != null) {
                            val json = JSONObject(responseBody)
                            success = json.has("result") && json.optInt("result", 0) > 0
                            if (!success) {
                                Timber.w("Add comment response indicates failure or no comment ID for task $taskId. Response: $responseBody")
                            }
                        } else {
                            Timber.w("Add comment response body is null for task $taskId")
                        }
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error parsing add comment response for task $taskId")
                } finally {
                    response.close()
                    if (continuation.isActive) continuation.resume(success)
                }
            }
        })
        continuation.invokeOnCancellation {
            Timber.d("addCommentToTask coroutine cancelled for task $taskId")
        }
    }

    private fun uploadAudioAndCreateComment(task: Task, audioFileToUpload: java.io.File, context: Context) {
        viewModelScope.launch {
            audioProcessingMessage = "Подготовка к загрузке '${audioFileToUpload.name}'..."
            val user = users[currentUserIndex]

            val storageId = fetchUserStorageId(user)
            if (storageId == null) {
                audioProcessingMessage = "Ошибка: Не удалось найти хранилище для пользователя."
                Timber.e("Failed to get storage ID for user ${user.userId}")
                resetAudioRecordingState(clearMessageDelay = 5000L)
                return@launch
            }
            Timber.d("Using storage ID: $storageId for user ${user.userId}")

            audioProcessingMessage = "Загрузка файла '${audioFileToUpload.name}'..."
            val uploadedFileId = uploadFileToStorage(user, storageId, audioFileToUpload)
            if (uploadedFileId == null) {
                audioProcessingMessage = "Ошибка: Не удалось загрузить аудиофайл."
                Timber.e("Failed to upload audio file ${audioFileToUpload.name} for task ${task.id}")
                resetAudioRecordingState(clearMessageDelay = 5000L)
                return@launch
            }
            Timber.i("File ${audioFileToUpload.name} uploaded successfully. ID: $uploadedFileId")

            audioProcessingMessage = "Создание комментария для '${task.title}'..."
            val commentAdded = addCommentToTask(user, task.id, uploadedFileId)

            if (commentAdded) {
                audioProcessingMessage = "Аудиокомментарий успешно добавлен к '${task.title}'."
                Timber.i("Audio comment successfully added to task ${task.id}")
                audioFileToUpload.delete()
            } else {
                audioProcessingMessage = "Ошибка: Не удалось добавить комментарий к задаче '${task.title}'."
                Timber.e("Failed to add comment to task ${task.id} after uploading file $uploadedFileId")
            }
            resetAudioRecordingState(clearMessageDelay = 5000L)
        }
    }

    fun setAudioPermissionDeniedMessage() {
        audioProcessingMessage = "Разрешение на запись аудио не предоставлено."
        viewModelScope.launch {
            delay(3000)
            if (audioProcessingMessage == "Разрешение на запись аудио не предоставлено.") {
                audioProcessingMessage = null
            }
        }
    }

    private fun resetAudioRecordingState(clearMessageDelay: Long? = null) {
        mediaRecorder?.release()
        mediaRecorder = null
        isRecordingAudio = false
        audioOutputFile?.delete() // Удаляем файл, если он еще существует и не был обработан
        audioOutputFile = null
        currentRecordingTask = null
        if (clearMessageDelay != null) {
            viewModelScope.launch {
                delay(clearMessageDelay)
                audioProcessingMessage = null
            }
        } else {
            audioProcessingMessage = null
        }
        Timber.d("Audio recording state reset.")
    }

    fun shareLogs(context: Context) {
        viewModelScope.launch {
            try {
                val logFile = FileLoggingTree.getLogFile(context)
                if (logFile.exists()) {
                    val authority = "${context.packageName}.provider"
                    val logUri = FileProvider.getUriForFile(context, authority, logFile)

                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain" // или "application/octet-stream"
                        putExtra(Intent.EXTRA_STREAM, logUri)
                        putExtra(Intent.EXTRA_SUBJECT, "Логи приложения Bitrix App")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    // Создаем chooser, чтобы пользователь мог выбрать, как отправить файл
                    val chooserIntent = Intent.createChooser(shareIntent, "Поделиться логами через...")
                    chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // Необходимо, если вызываем из ViewModel/не Activity контекста

                    // Так как мы в ViewModel, нам нужен способ запустить Intent.
                    // Обычно это делается через Activity. Можно передать callback или использовать LiveData/Flow для сигнала Activity.
                    // Для простоты, пока просто логируем, что нужно запустить Intent.
                    // В реальном приложении, это нужно будет обработать в Activity.
                    // Однако, если context - это Activity, то можно сделать так:
                    if (context is ComponentActivity) { // Проверяем, является ли контекст Activity
                         context.startActivity(chooserIntent)
                         audioProcessingMessage = "Подготовка к отправке логов..." // Используем существующее поле для сообщения
                         delay(2000)
                         audioProcessingMessage = null
                    } else {
                         Timber.e("Cannot start share intent from non-Activity context. Context type: ${context.javaClass.name}")
                         errorMessage = "Не удалось инициировать отправку логов: неверный контекст."
                    }

                    Timber.i("Share logs intent created for URI: $logUri")
                } else {
                    Timber.w("Log file not found for sharing.")
                    errorMessage = "Файл логов не найден."
                }
            } catch (e: Exception) {
                Timber.e(e, "Error sharing log file")
                errorMessage = "Ошибка при отправке логов: ${e.message}"
            }
        }
    }

    fun loadLogContent(context: Context) {
        viewModelScope.launch {
            try {
                val logFile = FileLoggingTree.getLogFile(context)
                if (logFile.exists()) {
                    val lines = logFile.readLines().reversed() // Читаем строки и переворачиваем (новые сверху)
                    logLines = lines
                    Timber.i("Loaded ${lines.size} log lines from ${logFile.name}")
                } else {
                    logLines = listOf("Файл логов не найден: ${logFile.absolutePath}")
                    Timber.w("Log file not found for viewing: ${logFile.absolutePath}")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading log file for viewing")
                logLines = listOf("Ошибка при чтении файла логов: ${e.message}")
            }
        }
    }
}

// UI компоненты
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Инициализация Timber для логирования в файл
        // Делаем это один раз при создании Activity
        if (Timber.treeCount == 0) { // Проверяем, чтобы не добавить дерево логгера многократно
            Timber.plant(FileLoggingTree(applicationContext))
            Timber.i("MainActivity onCreate: Timber FileLoggingTree planted.")
        } else {
            Timber.i("MainActivity onCreate: Timber already planted.")
        }


        setContent {
            val viewModel: MainViewModel = viewModel() // viewModel создается здесь

            // Запрос разрешения на уведомления для Android 13+
            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
                onResult = { isGranted ->
                    if (isGranted) {
                        Timber.i("Notification permission granted.")
                        startTimerService()
                    } else {
                        Timber.w("Notification permission denied.")
                        // Можно показать диалог или сообщение пользователю
                        // Для Foreground Service уведомление обязательно, но если разрешение не дано,
                        // приложение может упасть на Android 13+ при попытке показать уведомление.
                        // Однако, система может разрешить показ уведомления для Foreground Service
                        // даже без явного разрешения, но это поведение может отличаться.
                        // Лучше всего - убедиться, что сервис запускается после получения разрешения.
                        // Если разрешение не дано, сервис может не запуститься корректно или упасть.
                        // Пока просто логируем.
                        startTimerService() // Пытаемся запустить сервис в любом случае
                    }
                }
            )

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13 (API 33)
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        Timber.d("Notification permission already granted for Android 13+.")
                        startTimerService()
                    } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                        // Показать объяснение, почему нужно разрешение (если это не первый запрос)
                        // Здесь можно показать диалог
                        Timber.d("Showing rationale for notification permission.")
                        // После показа объяснения, снова запросить
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    else {
                        Timber.d("Requesting notification permission for Android 13+.")
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    // Для версий ниже Android 13 разрешение POST_NOTIFICATIONS не требуется явно
                    Timber.d("No need to request notification permission (SDK < 33).")
                    startTimerService()
                }
            }


            Bitrix_appTheme(appTheme = viewModel.getCurrentUserTheme()) { // Передаем тему текущего пользователя
                var showLogScreen by remember { mutableStateOf(false) }

                if (showLogScreen) {
                    LogViewerScreen(
                        logLines = viewModel.logLines,
                        onRefresh = { viewModel.loadLogContent(applicationContext) },
                        onBack = { showLogScreen = false }
                    )
                } else {
                    MainScreen(
                        viewModel = viewModel,
                        onShowLogs = {
                            viewModel.loadLogContent(applicationContext) // Загружаем логи перед показом
                            showLogScreen = true
                        }
                    )
                }
            }
        }
    }

    private fun startTimerService() {
        Timber.d("Attempting to start TimerService.")
        val serviceIntent = Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_START_SERVICE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Можно рассмотреть остановку сервиса здесь, если это требуется по логике приложения,
        // но обычно Foreground Service продолжает работать, пока его явно не остановят
        // или пока задача, для которой он был запущен, не будет завершена.
        // Intent(this, TimerService::class.java).also { intent ->
        //    intent.action = TimerService.ACTION_STOP_SERVICE
        //    startService(intent) // или startForegroundService, если нужно
        // }
        Timber.i("MainActivity onDestroy")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(
    logLines: List<String>,
    onRefresh: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Просмотр логов") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Обновить")
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 8.dp)
        ) {
            if (logLines.isEmpty()) {
                item {
                    Text(
                        "Логи пусты или еще не загружены.",
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                itemsIndexed(logLines, key = { index, _ -> index }) { _, line ->
                    Text(
                        text = line,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Divider(thickness = 0.5.dp)
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel(), onShowLogs: () -> Unit) { // Добавлен параметр onShowLogs
    var isUserMenuExpanded by remember { mutableStateOf(false) }
    var isSettingsExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current // Получаем контекст здесь, в Composable области

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp) // Немного уменьшим основной отступ для баланса с тенями
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
                    val currentUser = viewModel.getCurrentUser()
                    UserAvatar(user = currentUser, size = 60) // Увеличиваем аватар
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
                        onClick = { isSettingsExpanded = true },
                        modifier = Modifier.size(48.dp) // Явно задаем размер для области касания
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
                        // --- Пункты выбора темы ---
                        AppThemeOptions.values().forEach { themeOption ->
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (viewModel.getCurrentUserTheme() == themeOption) { // Проверяем тему текущего пользователя
                                            Text("✓ ", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        } else {
                                            Text("   ") // Для выравнивания
                                        }
                                        Text(when(themeOption) {
                                            AppThemeOptions.SYSTEM -> "Как в системе"
                                            AppThemeOptions.LIGHT -> "Светлая"
                                            AppThemeOptions.DARK -> "Темная"
                                            // OCEAN и FOREST удалены
                                        })
                                    }
                                },
                                onClick = {
                                    viewModel.selectTheme(themeOption)
                                    isSettingsExpanded = false
                                }
                            )
                        }
                        Divider() // Разделитель перед другими опциями
                        // --- Конец пунктов выбора темы ---
                        DropdownMenuItem(
                            text = { Text("Посмотреть логи") },
                            onClick = {
                                onShowLogs()
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
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp), // Добавляем тень
                    colors = CardDefaults.elevatedCardColors( // Используем elevatedCardColors
                        containerColor = if (currentUserTimerData.isPausedForUserAction) StatusYellow
                                         else StatusBlue
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp) // Стандартный отступ
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
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp), // Добавляем тень
                    colors = CardDefaults.elevatedCardColors(containerColor = StatusOrange) // Используем elevatedCardColors
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp) // Стандартный отступ
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
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp), // Добавляем тень
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer) // Используем elevatedCardColors
            ) {
                Text(
                    text = error,
                    modifier = Modifier.padding(16.dp), // Стандартный отступ
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
            }
            Spacer(modifier = Modifier.height(16.dp)) // Стандартный отступ
        }

        // Список задач
        // Сообщение о процессе записи/обработки аудио
        viewModel.audioProcessingMessage?.let { message ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), // Небольшая тень для сообщения
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer) // Используем elevatedCardColors
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    textAlign = TextAlign.Center
                )
            }
        }

        LazyColumn {
            items(viewModel.tasks, key = { task -> task.id }) { task ->
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
                Spacer(modifier = Modifier.height(10.dp)) // Немного уменьшим отступ, т.к. карточки теперь с тенью
            }
        }
    }
}

@Composable
fun UserAvatar(user: User, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .shadow(elevation = 4.dp, shape = CircleShape) // Добавляем тень
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
    val scheme = MaterialTheme.colorScheme // Считываем схему один раз
    val (icon, color, contentColor) = remember(workStatus, scheme, StatusOrange, StatusRed) {
        when (workStatus) {
            WorkStatus.BEFORE_WORK -> Triple("🌅", Color.Gray, scheme.onSurface)
            WorkStatus.WORKING -> Triple("💼", scheme.tertiaryContainer, scheme.onTertiaryContainer)
            WorkStatus.BREAK -> Triple("☕", StatusOrange, scheme.onSurfaceVariant)
            WorkStatus.LUNCH -> Triple("🍽️", StatusRed, scheme.onSurfaceVariant)
            WorkStatus.AFTER_WORK -> Triple("🌆", Color.Gray, scheme.onSurface)
        }
    }

    Text(
        text = icon,
        fontSize = 30.sp, // Увеличиваем иконку
        color = contentColor,
        modifier = Modifier
            .shadow(elevation = 2.dp, shape = CircleShape) // Добавляем небольшую тень
            .background(color.copy(alpha = 0.2f), CircleShape)
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
    // Используем состояние из ViewModel для раскрытия карточки
    val isExpanded = viewModel.expandedTaskIds.contains(task.id)

    // Загрузка чек-листов и подзадач при раскрытии карточки
    LaunchedEffect(task.id, isExpanded) { // Ключ теперь isExpanded из ViewModel
        if (isExpanded) {
            // Проверяем, есть ли уже данные или идет ли загрузка, перед тем как запрашивать
            if (viewModel.checklistsMap[task.id].isNullOrEmpty() && viewModel.loadingChecklistMap[task.id] != true) {
                viewModel.fetchChecklistForTask(task.id)
            }
            if (viewModel.subtasksMap[task.id].isNullOrEmpty() && viewModel.loadingSubtasksMap[task.id] != true) {
                viewModel.fetchSubtasksForTask(task.id)
            }
        }
    }
    val scheme = MaterialTheme.colorScheme // Считываем схему один раз

    val cardContainerColor = remember(
        task.isCompleted,
        isTimerRunningForThisTask,
        isTimerUserPausedForThisTask,
        isTimerSystemPausedForThisTask,
        task.isOverdue,
        scheme.surfaceVariant, // Используем считанную схему
        StatusGreen, StatusBlue, StatusYellow, StatusOrange, StatusRed
    ) {
        when {
            task.isCompleted -> StatusGreen
            isTimerRunningForThisTask -> StatusBlue
            isTimerUserPausedForThisTask -> StatusYellow
            isTimerSystemPausedForThisTask -> StatusOrange
            task.isOverdue -> StatusRed
            else -> scheme.surfaceVariant // Используем считанную схему
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { viewModel.toggleTaskExpansion(task.id) }, // Используем метод из ViewModel
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp), // Увеличиваем тень для TaskCard
        colors = CardDefaults.elevatedCardColors(containerColor = cardContainerColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp) // Стандартный отступ
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
                        imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (isExpanded) "Свернуть" else "Развернуть",
                        modifier = Modifier
                            .size(28.dp) // Увеличиваем иконку
                            .padding(start = 8.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // scheme уже определена выше в TaskCard
                val statusTextColor = remember(task.isCompleted, task.isInProgress, task.isPending, scheme, StatusOrange) {
                    when {
                        task.isCompleted -> scheme.tertiary
                        task.isInProgress -> scheme.primary
                        task.isPending -> StatusOrange
                        else -> scheme.onSurfaceVariant
                    }
                }
                val statusTextBackgroundColor = remember(task.isCompleted, task.isInProgress, task.isPending, scheme, StatusOrange) {
                    when {
                        task.isCompleted -> scheme.tertiaryContainer.copy(alpha = 0.3f)
                        task.isInProgress -> scheme.primaryContainer.copy(alpha = 0.3f)
                        task.isPending -> StatusOrange.copy(alpha = 0.3f)
                        else -> scheme.secondaryContainer.copy(alpha = 0.3f)
                    }
                }

                Text(
                    text = task.statusText,
                    fontSize = 14.sp, // Увеличиваем шрифт
                    color = statusTextColor,
                    modifier = Modifier
                        .background(
                            statusTextBackgroundColor,
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

            val progressIndicatorColor = remember(task.isOverdue, progress, ProgressBarRed, ProgressBarOrange, ProgressBarGreen) {
                when {
                    task.isOverdue -> ProgressBarRed
                    progress > 0.8f -> ProgressBarOrange
                    else -> ProgressBarGreen
                }
            }
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().height(8.dp), // Увеличиваем толщину
                color = progressIndicatorColor,
                trackColor = scheme.surfaceVariant // Используем scheme
            )

            Spacer(modifier = Modifier.height(12.dp)) // Увеличиваем отступ

            // Краткая информация о времени
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top // Выравниваем по верху для консистентности
            ) {
                Text(
                    text = "Время: ${task.formattedTime}",
                    fontSize = 14.sp, // Увеличиваем шрифт
                    color = scheme.onSurfaceVariant // Используем scheme
                )
                val progressTextColor = remember(task.isOverdue, scheme.error, scheme.onSurfaceVariant) {
                    if (task.isOverdue) scheme.error else scheme.onSurfaceVariant
                }
                Text(
                    text = "${task.progressPercent}%",
                    fontSize = 14.sp, // Увеличиваем шрифт
                    color = progressTextColor
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
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), // Небольшая тень для вложенной карточки
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface) // Используем elevatedCardColors
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp) // Уменьшим отступ для вложенной карточки
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
                                    color = scheme.onSurfaceVariant // Используем scheme
                                )
                                val spentTimeColor = remember(task.isOverdue, scheme.error, ProgressBarGreen) {
                                    if (task.isOverdue) scheme.error else ProgressBarGreen
                                }
                                Text(
                                    text = "${task.timeSpent / 3600}:${String.format("%02d", (task.timeSpent % 3600) / 60)}",
                                    fontSize = 16.sp, // Увеличиваем шрифт
                                    fontWeight = FontWeight.Bold,
                                    color = spentTimeColor
                                )
                            }

                            Column {
                                Text(
                                    text = "Планируется:",
                                    fontSize = 14.sp, // Увеличиваем шрифт
                                    color = scheme.onSurfaceVariant // Используем scheme
                                )
                                Text(
                                    text = "${task.timeEstimate / 3600}:${String.format("%02d", (task.timeEstimate % 3600) / 60)}",
                                    fontSize = 16.sp, // Увеличиваем шрифт
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.primary // Используем scheme
                                )
                            }

                            Column {
                                Text(
                                    text = "Процент:",
                                    fontSize = 14.sp, // Увеличиваем шрифт
                                    color = scheme.onSurfaceVariant // Используем scheme
                                )
                                val detailedProgressColor = remember(task.progressPercent, ProgressBarRed, ProgressBarOrange, ProgressBarGreen) {
                                    when {
                                        task.progressPercent >= 100 -> ProgressBarRed
                                        task.progressPercent >= 80 -> ProgressBarOrange
                                        else -> ProgressBarGreen
                                    }
                                }
                                Text(
                                    text = "${task.progressPercent}%",
                                    fontSize = 16.sp, // Увеличиваем шрифт
                                    fontWeight = FontWeight.Bold,
                                    color = detailedProgressColor
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
                } else if (!checklist.isNullOrEmpty() && checklist.any { !it.isComplete }) { // Скрываем, если все пункты выполнены
                    Text(
                        text = "Чек-лист:",
                        fontSize = 16.sp, // Увеличиваем шрифт
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp)) // Увеличиваем отступ
                    checklist.forEach { item ->
                        val onToggleItem = remember(task.id, item.id, item.isComplete) {
                            { viewModel.toggleChecklistItemStatus(task.id, item.id, item.isComplete) }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleItem() }
                                .padding(vertical = 4.dp) // Добавляем вертикальный отступ для лучшего касания
                        ) {
                            Checkbox(
                                checked = item.isComplete,
                                onCheckedChange = { _ -> onToggleItem() }, // Используем onToggleItem
                                enabled = true,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = scheme.primary, // Используем scheme
                                    uncheckedColor = scheme.onSurfaceVariant // Используем scheme
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp)) // Отступ между чекбоксом и текстом
                            val checklistItemColor = remember(item.isComplete, scheme.onSurfaceVariant, scheme.onSurface) {
                                if (item.isComplete) scheme.onSurfaceVariant else scheme.onSurface
                            }
                            Text(
                                text = item.title,
                                fontSize = 16.sp, // Увеличиваем шрифт
                                color = checklistItemColor
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
                                .padding(vertical = 4.dp), // Уменьшим вертикальный отступ
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)), // Используем elevatedCardColors
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp) // Минимальная тень для подзадач
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) { // Уменьшим отступ для подзадачи
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
                                    val subtaskStatusColor = remember(subtask.isCompleted, subtask.isInProgress, subtask.isPending, scheme, StatusOrange) {
                                        when {
                                            subtask.isCompleted -> scheme.tertiary
                                            subtask.isInProgress -> scheme.primary
                                            subtask.isPending -> StatusOrange
                                            else -> scheme.onSurfaceVariant
                                        }
                                    }
                                    Text(
                                        text = "Статус: ${subtask.statusText}",
                                        fontSize = 14.sp, // Увеличиваем шрифт
                                        color = subtaskStatusColor
                                    )
                                    Text(
                                        text = "Время: ${subtask.formattedTime}",
                                        fontSize = 14.sp, // Увеличиваем шрифт
                                        color = scheme.onSurfaceVariant // Используем scheme
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
                horizontalArrangement = Arrangement.spacedBy(8.dp) // Немного уменьшим расстояние, если добавляем кнопку
            ) {
                // Кнопка таймера
                val sErrorTimer = scheme.error // Переименовано для ясности
                val sTertiaryTimer = scheme.tertiary
                val sOnSurfaceTimer = scheme.onSurface
                val sPrimaryTimer = scheme.primary
                val sOnErrorTimer = scheme.onError
                val sOnTertiaryTimer = scheme.onTertiary
                val sOnPrimaryTimer = scheme.onPrimary

                val timerButtonColors = ButtonDefaults.elevatedButtonColors(
                    containerColor = when {
                        isTimerRunningForThisTask -> sErrorTimer
                        isTimerUserPausedForThisTask -> sTertiaryTimer
                        isTimerSystemPausedForThisTask -> sOnSurfaceTimer.copy(alpha = 0.12f)
                        else -> sPrimaryTimer
                    },
                    contentColor = when {
                        isTimerRunningForThisTask -> sOnErrorTimer
                        isTimerUserPausedForThisTask -> sOnTertiaryTimer
                        isTimerSystemPausedForThisTask -> sOnSurfaceTimer.copy(alpha = 0.38f)
                        else -> sOnPrimaryTimer
                    },
                    disabledContainerColor = sOnSurfaceTimer.copy(alpha = 0.12f),
                    disabledContentColor = sOnSurfaceTimer.copy(alpha = 0.38f)
                )

                Button(
                    onClick = { onTimerToggle(task) },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp), // Увеличиваем высоту кнопки
                    enabled = !isTimerSystemPausedForThisTask,
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
                    colors = timerButtonColors
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
                    val sOnPrimaryComplete = scheme.onPrimary // Используем отдельную переменную для ясности ключа
                    val rememberedCompleteButtonColors = ButtonDefaults.elevatedButtonColors(
                        containerColor = ProgressBarGreen,
                        contentColor = sOnPrimaryComplete
                    )
                    Button(
                        onClick = { onCompleteTask(task) },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp), // Увеличиваем высоту кнопки
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
                        colors = rememberedCompleteButtonColors
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "Завершить", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Завершить",
                            fontSize = 16.sp // Увеличиваем шрифт
                        )
                    }
                }

                // Кнопка записи аудиокомментария (только для незавершенных задач)
                if (!task.isCompleted) {
                    val context = LocalContext.current
                    val isCurrentlyRecordingThisTask = viewModel.isRecordingAudio && viewModel.currentRecordingTask?.id == task.id

                    // scheme определена выше в TaskCard
                    val sErrorContainer = scheme.errorContainer
                    val sSecondaryContainer = scheme.secondaryContainer
                    val iconButtonBackgroundColor = remember(isCurrentlyRecordingThisTask, sErrorContainer, sSecondaryContainer) {
                        if (isCurrentlyRecordingThisTask) sErrorContainer else sSecondaryContainer
                    }

                    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission(),
                        onResult = { isGranted ->
                            if (isGranted) {
                                viewModel.toggleAudioRecording(task, context)
                            } else {
                                viewModel.setAudioPermissionDeniedMessage() // Используем новый метод
                            }
                        }
                    )

                    IconButton(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                                viewModel.toggleAudioRecording(task, context)
                            } else {
                                recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        modifier = Modifier
                            .heightIn(min = 52.dp)
                            .shadow(elevation = 2.dp, shape = CircleShape) // Тень для IconButton
                            .background(
                                color = iconButtonBackgroundColor,
                                shape = CircleShape
                            )
                            .padding(horizontal = 8.dp),
                        enabled = !viewModel.isRecordingAudio || isCurrentlyRecordingThisTask // Кнопка активна если не идет запись ИЛИ идет запись именно этой задачи
                    ) {
                        // scheme уже определена выше в TaskCard
                        val sOnErrorContainer = scheme.onErrorContainer
                        val sOnSecondaryContainer = scheme.onSecondaryContainer
                        val iconAndTint = remember(isCurrentlyRecordingThisTask, sOnErrorContainer, sOnSecondaryContainer) {
                            if (isCurrentlyRecordingThisTask) {
                                Triple(Icons.Filled.Stop, "Остановить запись", sOnErrorContainer)
                            } else {
                                Triple(Icons.Filled.Mic, "Записать аудиокомментарий", sOnSecondaryContainer)
                            }
                        }
                        Icon(
                            imageVector = iconAndTint.first,
                            contentDescription = iconAndTint.second,
                            tint = iconAndTint.third,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}
