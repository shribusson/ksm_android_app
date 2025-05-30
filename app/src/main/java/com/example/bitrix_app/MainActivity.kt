package com.example.bitrix_app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build // Добавленный импорт
import android.os.Bundle
import android.os.IBinder
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
import androidx.compose.material.icons.filled.Pause // Для иконки паузы
import androidx.compose.material.icons.filled.PlayArrow // Для иконки старт/продолжить
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

    // statusText больше не используется в TaskCard в текущей конфигурации, но оставим на случай будущего использования
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

// Enum AppThemeOptions удален, так как тема будет фиксированной

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
    var sendComments by mutableStateOf(false) // Настройка отправки комментариев (по умолчанию отключена)

    // Состояние раскрытия карточек задач
    var expandedTaskIds by mutableStateOf<Set<String>>(emptySet())
        private set

    // Enum для стандартных типов задач
    enum class StandardTaskType(val titlePrefix: String, val emoji: String, val defaultPriority: String = "1") {
        RIGGING("Такелаж", "🏗️"), // U+1F3D7
        FIX_MISTAKES("Исправление косяков", "🛠️"), // U+1F6E0
        UNEXPECTED("Неожиданная задача", "✨", "2") // U+2728, High priority
    }

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

    // Состояние userSelectedThemeMap удалено

    // Ссылка на сервис таймера
    var timerService by mutableStateOf<TimerService?>(null)
        private set

    // Состояние таймера, полученное от сервиса
    var timerServiceState by mutableStateOf<TimerServiceState?>(null) // Сделаем nullable
        private set

    // Состояние для обратной связи при быстром создании задач
    var quickTaskCreationStatus by mutableStateOf<String?>(null)
        private set


    // --- Управление SharedPreferences для currentUserIndex ---
    private val sharedPreferencesName = "BitrixAppPrefs"
    private val currentUserIndexKey = "currentUserIndex"

    private fun saveCurrentUserIndex(context: Context, index: Int) {
        val prefs = context.getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)
        prefs.edit().putInt(currentUserIndexKey, index).apply()
        Timber.d("Saved currentUserIndex: $index")
    }

    private fun loadCurrentUserIndex(context: Context): Int {
        val prefs = context.getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)
        val loadedIndex = prefs.getInt(currentUserIndexKey, 0)
        Timber.d("Loaded currentUserIndex: $loadedIndex")
        return if (loadedIndex >= 0 && loadedIndex < users.size) loadedIndex else 0
    }
    // --- Конец SharedPreferences ---

    fun connectToTimerService(service: TimerService?) {
        timerService = service
        if (service != null) {
            // Подписываемся на обновления состояния от сервиса
            viewModelScope.launch {
                service.serviceStateFlow.collect { newState ->
                    timerServiceState = newState
                    // Timber.v("ViewModel observed new TimerServiceState: $newState") // Закомментировано для уменьшения логов
                }
            }
            // Сообщаем сервису текущего пользователя
            val currentUser = users[currentUserIndex]
            service.setCurrentUser(currentUser.userId, currentUser.name)
        }
    }

    fun toggleTaskExpansion(taskId: String) {
        expandedTaskIds = if (expandedTaskIds.contains(taskId)) {
            expandedTaskIds - taskId
        } else {
            expandedTaskIds + taskId
        }
        Timber.d("Toggled expansion for task $taskId. Expanded IDs: $expandedTaskIds")
    }

    // var currentTime by mutableStateOf("") // Удалено

    // Контекст нужен для SharedPreferences
    fun initViewModel(context: Context) {
        if (isInitialized) return
        Timber.d("MainViewModel initializing with context...")
        currentUserIndex = loadCurrentUserIndex(context) // Загружаем сохраненный индекс
        updateWorkStatus() // Важно вызвать до loadTasks, чтобы timeman статус был актуален
        loadTasks()
        startPeriodicUpdates()
        startPeriodicTaskUpdates()
        // startTimeUpdates() // Удалено
        // startUniversalTimerLoop() // Удалено, логика таймера теперь в сервисе
        val currentUserForInit = users[currentUserIndex]
        timerService?.setCurrentUser(currentUserForInit.userId, currentUserForInit.name) // Уведомляем сервис, если он уже подключен
        isInitialized = true
        Timber.d("MainViewModel initialized. Current user: ${users[currentUserIndex].name}")
    }
    private var isInitialized = false

    // Функции getCurrentUserTheme и selectTheme удалены

    fun switchUser(index: Int, context: Context) {
        Timber.i("Switching user to index $index: ${users.getOrNull(index)?.name ?: "Unknown"}")
        isLoading = true // Показываем загрузку немедленно
        tasks = emptyList() // Очищаем задачи предыдущего пользователя
        errorMessage = null // Сбрасываем предыдущие ошибки

        saveCurrentUserIndex(context, index) // Сохраняем новый индекс
        currentUserIndex = index
        val switchedUser = users[index]
        timerService?.setCurrentUser(switchedUser.userId, switchedUser.name) // Уведомляем сервис о смене пользователя

        // Если таймер был активен для предыдущего пользователя, его нужно остановить (или решить, как обрабатывать)
        // Текущая логика сервиса предполагает один активный таймер. При смене пользователя,
        // если таймер был запущен, он продолжит тикать "для нового пользователя", если не остановить явно.
        // Пока что, если таймер был активен, он просто "переключится" на нового пользователя без сброса.
        // Это может потребовать доработки, если нужно сохранять время для предыдущего пользователя.
        // Для простоты, пока оставляем так. ViewModel может решить остановить таймер перед сменой.
        // Например, можно вызвать timerService?.stopTaskTimer() здесь, если это нужно.

        updateWorkStatus() // Обновляем статус рабочего дня для нового пользователя
        loadTasks() // Загружаем задачи для нового пользователя
    }

    fun loadTasks() {
        Timber.d("loadTasks called for user: ${users[currentUserIndex].name}")
        isLoading = true
        errorMessage = null
        val user = users[currentUserIndex]
        // val currentUserDataBeforeLoad = getCurrentUserTimerData() // Удалено, состояние таймера в сервисе

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
                                    compareBy<Task> { it.id != timerServiceState?.activeTaskId } // Используем ID из timerServiceState, безопасно
                                        .thenBy { it.isCompleted }
                                        .thenByDescending { it.changedDate } // Сначала новые по дате изменения
                                        .thenBy { it.id.toIntOrNull() ?: 0 }
                                )
                                // Логика остановки таймера, если активная задача не принадлежит текущему пользователю, удалена,
                                // чтобы таймер продолжал работать при смене пользователя.

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
                                        // val currentUserData = getCurrentUserTimerData() // Удалено
                                        val currentServiceState = timerServiceState // Это TimerServiceState?
                                        val newSortedTasksList = newRawTasksList.sortedWith(
                                            compareBy<Task> { it.id != currentServiceState?.activeTaskId } // Сравниваем с ID из сервиса, безопасно
                                                .thenBy { it.isCompleted }
                                                .thenByDescending { it.changedDate }
                                                .thenBy { it.id.toIntOrNull() ?: 0 }
                                        )
                                        // Логика остановки таймера, если активная задача не принадлежит текущему пользователю, удалена.

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
                                        // val currentUserData = getCurrentUserTimerData() // Удалено
                                        val currentServiceState = timerServiceState // Это TimerServiceState?
                                        val newSortedTasksList = newRawTasksList.sortedWith(
                                            compareBy<Task> { it.id != currentServiceState?.activeTaskId } // Сравниваем с ID из сервиса, безопасно
                                                .thenBy { it.isCompleted }
                                                .thenByDescending { it.changedDate }
                                                .thenBy { it.id.toIntOrNull() ?: 0 }
                                        )
                                        // Логика остановки таймера, если активная задача не принадлежит текущему пользователю, удалена.

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
        val service = timerService ?: return // Если сервис не подключен, ничего не делаем
        val currentServiceState = timerServiceState // Берем актуальное состояние из сервиса
        val user = users[currentUserIndex]
        Timber.i("toggleTimer called for task '${task.title}' (ID: ${task.id}) for user ${user.name}. Service state: $currentServiceState")

        val currentUser = users[currentUserIndex]

        if (currentServiceState?.activeTaskId == task.id && currentServiceState.isEffectivelyPaused == false) {
            // Таймер активен для этой задачи -> ставим на пользовательскую паузу
            Timber.d("User pausing active timer for task ${task.id}")
            service.userPauseTaskTimer(currentUser.userId)
            if (sendComments) {
                sendTimerComment(task, "Таймер приостановлен (пользователем)", currentServiceState.timerSeconds)
            }
        } else if (currentServiceState?.activeTaskId == task.id && currentServiceState.isUserPaused == true) {
            // Таймер на пользовательской паузе для этой задачи -> возобновляем
            Timber.d("User resuming timer for task ${task.id}")
            service.userResumeTaskTimer(currentUser.userId)
            if (sendComments) {
                sendTimerComment(task, "Таймер возобновлен (пользователем)", currentServiceState.timerSeconds)
            }
        } else {
            // Запускаем таймер для новой задачи (или для задачи, которая была на системной паузе, но пользователь нажал на нее)
            // Сначала останавливаем предыдущий таймер, если он был для другой задачи
            if (currentServiceState?.activeTaskId != null && currentServiceState.activeTaskId != task.id) {
                Timber.d("Stopping timer for previous task ${currentServiceState.activeTaskId} before starting new one.")
                val previousTask = tasks.find { it.id == currentServiceState.activeTaskId }
                if (previousTask != null) {
                    // Важно: stopTaskTimer() в сервисе вернет время, которое нужно сохранить
                    val secondsToSaveForPrevious = service.stopTaskTimer(currentUser.userId) // Останавливаем в сервисе
                    stopTimerAndSaveTime(previousTask, secondsToSaveForPrevious) // Сохраняем время в Bitrix
                    if (sendComments) {
                        sendTimerComment(previousTask, "Таймер остановлен (переключение на задачу ${task.id})", secondsToSaveForPrevious)
                    }
                } else {
                     service.stopTaskTimer(currentUser.userId) // Просто останавливаем в сервисе, если задачи нет в списке ViewModel
                }
            }

            Timber.d("Starting timer for task ${task.id}")
            service.startTaskTimer(currentUser.userId, currentUser.name, task.id, task.title)
            if (sendComments) {
                sendTimerComment(task, "Таймер запущен", 0) // Время 0 при старте
            }
            // Перемещаем задачу с активным таймером в начало списка
            tasks = tasks.sortedWith(
                compareBy<Task> { it.id != task.id }
                    .thenBy { it.isCompleted }
                    .thenByDescending { it.changedDate } // Сохраняем существующую сортировку
                    .thenBy { it.id.toIntOrNull() ?: 0 }
            )
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

    // Сохранение времени в Битрикс при остановке таймера (вызывается ViewModel)
    private fun stopTimerAndSaveTime(task: Task, secondsToSave: Int) {
        val user = users[currentUserIndex]
        Timber.i("stopTimerAndSaveTime (ViewModel) called for task ${task.id}, user ${user.name}, seconds: $secondsToSave")

        if (secondsToSave < 10) {
            Timber.i("Timer too short (${secondsToSave}s), not saving to Bitrix for task ${task.id}")
            return // Не сохраняем, если времени мало
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
        val service = timerService ?: return
        val currentServiceState = timerServiceState
        val user = users[currentUserIndex]
        Timber.i("Complete task called for task ${task.id} by user ${user.name}. Service state: $currentServiceState")

        var secondsToSave = 0
        var timerWasActiveOrPausedForThisTask = false

        if (currentServiceState?.activeTaskId == task.id) {
            timerWasActiveOrPausedForThisTask = true
            secondsToSave = service.stopTaskTimer(user.userId) // Останавливаем таймер в сервисе и получаем время
            Timber.d("Task ${task.id} timer was active/paused. Stopped in service. Seconds from service: $secondsToSave")
        }

        if (timerWasActiveOrPausedForThisTask && secondsToSave > 0) {
            stopTimerAndSaveTime(task, secondsToSave)
            if (sendComments) {
                sendTimerComment(task, "Задача завершена, таймер остановлен", secondsToSave)
            }
            // Запускаем завершение задачи в Bitrix после небольшой задержки для сохранения времени
            viewModelScope.launch {
                delay(1500)
                completeTaskInBitrixInternal(task)
            }
        } else {
            // Если таймер не был активен для этой задачи или время 0, просто завершаем
            Timber.d("Task ${task.id} timer was not active for it or had 0 seconds. Completing directly in Bitrix.")
            completeTaskInBitrixInternal(task)
        }
    }

    private fun completeTaskInBitrixInternal(task: Task) {
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

    // startUniversalTimerLoop() удален

    private fun updateWorkStatus() {
        val service = timerService // Используем локальную копию для безопасности в корутине
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

            // Применяем timeman действия для ТЕКУЩЕГО пользователя, если ViewModel им управляет
            // или для всех, если это глобальная логика.
            // Пока оставим для всех пользователей, как было.
            users.forEach { user ->
                Timber.d("Applying timeman actions for user ${user.name} due to global status change from $previousGlobalStatus to $newGlobalWorkStatus")
                when {
                    (previousGlobalStatus == WorkStatus.BEFORE_WORK || previousGlobalStatus == WorkStatus.BREAK || previousGlobalStatus == WorkStatus.LUNCH) && newGlobalWorkStatus == WorkStatus.WORKING -> {
                        timemanOpenWorkDay(user)
                    }
                    previousGlobalStatus == WorkStatus.WORKING && (newGlobalWorkStatus == WorkStatus.BREAK || newGlobalWorkStatus == WorkStatus.LUNCH) -> {
                        timemanPauseWorkDay(user)
                    }
                    previousGlobalStatus == WorkStatus.WORKING && newGlobalWorkStatus == WorkStatus.AFTER_WORK -> {
                        timemanCloseWorkDay(user)
                    }
                    else -> {
                        Timber.d("No specific timeman action for user ${user.name} for transition from $previousGlobalStatus to $newGlobalWorkStatus.")
                    }
                }
            }

            // Обновляем состояние системной паузы для таймера в СЕРВИСЕ
            val currentServiceState = timerServiceState
            if (currentServiceState?.activeTaskId != null) { // Только если есть активный таймер
                when {
                    newGlobalWorkStatus == WorkStatus.WORKING && (previousGlobalStatus == WorkStatus.BREAK || previousGlobalStatus == WorkStatus.LUNCH || previousGlobalStatus == WorkStatus.BEFORE_WORK) -> {
                        Timber.i("Requesting SYSTEM RESUME from ViewModel due to work status change.")
                        service?.systemResumeAllApplicableTimers()
                    }
                    (newGlobalWorkStatus == WorkStatus.BREAK || newGlobalWorkStatus == WorkStatus.LUNCH || newGlobalWorkStatus == WorkStatus.AFTER_WORK) && previousGlobalStatus == WorkStatus.WORKING -> {
                        Timber.i("Requesting SYSTEM PAUSE from ViewModel due to work status change.")
                        service?.systemPauseAllApplicableTimers()
                    }
                }
            }
        } else {
             // Логика для случая, когда статус не изменился, но нужно проверить состояние timeman.open
             // Например, при старте приложения, если сейчас рабочее время, но день не открыт.
             // Это требует запроса timeman.status, что выходит за рамки текущего рефакторинга.
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
                // Состояние таймера теперь управляется TimerService.
                // loadTasks() уже содержит логику для остановки таймера в сервисе,
                // если активная задача больше не существует в загруженном списке.
                loadTasks()
            }
        }
    }

    // private fun startTimeUpdates() // Удалено
    // private fun updateCurrentTime() // Удалено

    fun createStandardTask(taskType: StandardTaskType, context: Context) {
        viewModelScope.launch {
            // Используем quickTaskCreationStatus для индикации загрузки этого конкретного действия
            quickTaskCreationStatus = "Создание задачи '${taskType.titlePrefix}'..."
            errorMessage = null // Сбрасываем общую ошибку перед новой операцией
            val user = users[currentUserIndex]
            // val timestamp = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date()) // Удаляем timestamp
            val taskTitle = "${taskType.titlePrefix} - ${user.name}" // Название задачи без времени

            val url = "${user.webhookUrl}tasks.task.add"
            val formBodyBuilder = FormBody.Builder()
                .add("fields[TITLE]", taskTitle)
                .add("fields[RESPONSIBLE_ID]", user.userId)
                .add("fields[CREATED_BY]", user.userId)
                .add("fields[DESCRIPTION]", "Стандартная задача, создана автоматически из приложения.")
                .add("fields[PRIORITY]", taskType.defaultPriority)

            val request = Request.Builder().url(url).post(formBodyBuilder.build()).build()
            Timber.d("Creating standard task: ${taskType.titlePrefix} for user ${user.name}. URL: $url, Title: $taskTitle")

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    viewModelScope.launch {
                        quickTaskCreationStatus = "Ошибка создания задачи: ${e.message}"
                        Timber.e(e, "Network error while creating standard task '${taskType.titlePrefix}'")
                        delay(3500) // Даем время прочитать сообщение
                        quickTaskCreationStatus = null
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    viewModelScope.launch {
                        val responseText = response.body?.string()
                        if (response.isSuccessful && responseText != null) {
                            try {
                                val json = JSONObject(responseText)
                                if (json.has("result") && json.getJSONObject("result").has("task")) {
                                    val createdTaskJson = json.getJSONObject("result").getJSONObject("task")
                                    val createdTaskId = createdTaskJson.optString("id", "N/A") // Получаем ID созданной задачи
                                    quickTaskCreationStatus = "Задача '${taskType.titlePrefix}' (ID: $createdTaskId) создана! Запускаем таймер..."
                                    Timber.i("Standard task '${taskType.titlePrefix}' (ID: $createdTaskId) created successfully. Response: $responseText")

                                    // Создаем объект Task из ответа, чтобы запустить таймер
                                    val newlyCreatedTask = createTaskFromJson(createdTaskJson, createdTaskId)

                                    // Запускаем таймер для новой задачи
                                    toggleTimer(newlyCreatedTask)

                                    // Обновляем список задач
                                    // Небольшая задержка перед loadTasks, чтобы toggleTimer успел отработать с UI (сортировка)
                                    // и чтобы сообщение о создании было видно чуть дольше перед обновлением списка
                                    delay(1500)
                                    loadTasks()
                                    // quickTaskCreationStatus будет сброшен через 3.5 секунды общего таймера ниже
                                } else if (json.has("error")) {
                                    val errorDesc = json.optString("error_description", "Неизвестная ошибка API")
                                    quickTaskCreationStatus = "Ошибка API: $errorDesc"
                                    Timber.w("API error creating standard task '${taskType.titlePrefix}': $errorDesc. Response: $responseText")
                                } else {
                                    quickTaskCreationStatus = "Неизвестный ответ от сервера."
                                    Timber.w("Unknown response while creating standard task '${taskType.titlePrefix}'. Response: $responseText")
                                }
                            } catch (e: Exception) {
                                quickTaskCreationStatus = "Ошибка обработки ответа: ${e.message}"
                                Timber.e(e, "Parse error in create standard task response for '${taskType.titlePrefix}'")
                            }
                        } else {
                            quickTaskCreationStatus = "Ошибка сервера: ${response.code}"
                            Timber.e("HTTP error creating standard task '${taskType.titlePrefix}': ${response.code} - ${response.message}. Body: $responseText")
                        }
                        delay(3500) // Даем время прочитать сообщение
                        quickTaskCreationStatus = null
                    }
                }
            })
        }
    }

    fun stopAndSaveCurrentTimer() {
        val service = timerService ?: return
        val currentServiceState = timerServiceState ?: return
        val activeTaskId = currentServiceState.activeTaskId ?: return
        val currentUser = users[currentUserIndex]

        Timber.i("stopAndSaveCurrentTimer called for task ID $activeTaskId by user ${currentUser.name}")

        val task = tasks.find { it.id == activeTaskId }
        if (task == null) {
            Timber.w("Task with ID $activeTaskId not found in ViewModel's list. Cannot save time.")
            // Попытаемся остановить таймер в сервисе в любом случае, но без сохранения/комментария
            service.stopTaskTimer(currentUser.userId)
            errorMessage = "Активная задача не найдена, таймер остановлен."
            return
        }

        val secondsToSave = service.stopTaskTimer(currentUser.userId)
        Timber.d("Timer stopped for task ${task.id} via stopAndSaveCurrentTimer. Seconds from service: $secondsToSave")

        if (secondsToSave > 0) { // stopTimerAndSaveTime имеет свою проверку на >= 10 секунд
            stopTimerAndSaveTime(task, secondsToSave)
            if (sendComments) {
                sendTimerComment(task, "Таймер остановлен, время учтено", secondsToSave)
            }
        } else {
            Timber.i("Timer for task ${task.id} had 0 seconds or less. Not saving time or sending comment.")
        }
        // Обновление списка задач (loadTasks()) вызывается внутри stopTimerAndSaveTime
        // Состояние timerServiceState обновится автоматически, и карточка активного таймера исчезнет.
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
                        // Сообщение об отправке логов теперь может быть другим или отсутствовать,
                        // т.к. audioProcessingMessage используется для аудио.
                        // Можно добавить новое состояние для сообщений общего назначения или использовать errorMessage.
                        // Пока оставим как есть, но это место для улучшения.
                        // audioProcessingMessage = "Подготовка к отправке логов..."
                        // delay(2000)
                        // audioProcessingMessage = null
                        Timber.i("Share logs intent started.")
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
                    val rawLines = logFile.readLines().reversed() // Читаем строки и переворачиваем (новые сверху)
                    logLines = rawLines.mapNotNull { formatLogLineForDisplay(it) }
                    Timber.i("Loaded and formatted ${logLines.size} log lines from ${logFile.name}")
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

    private fun formatLogLineForDisplay(line: String): String? {
        // Пример строки: 2023-10-27 15:30:45.123 I/MyActivity: Activity created
        val regex = """^\d{4}-\d{2}-\d{2} (\d{2}:\d{2}:\d{2})\.\d{3} ([VDIWEA])/(.*?): (.*)$""".toRegex()
        val match = regex.find(line)
        return if (match != null) {
            val time = match.groupValues[1]
            val levelChar = match.groupValues[2]
            // val tag = match.groupValues[3] // Тег пока не используем в упрощенном отображении
            val message = match.groupValues[4]

            val levelStr = when (levelChar) {
                "V" -> "VERBOSE"
                "D" -> "DEBUG"
                "I" -> "INFO"
                "W" -> "WARN"
                "E" -> "ERROR"
                "A" -> "ASSERT"
                else -> levelChar
            }
            "$time $levelStr: $message"
        } else {
            line // Если не совпало с форматом, возвращаем как есть (или null, чтобы отфильтровать)
            // Для более чистого отображения, можно вернуть null, если строка не соответствует ожидаемому формату.
            // null // Возвращаем null, если строка не соответствует ожидаемому формату
        }
    }

    fun exportDetailedLogs(context: Context) {
        // Эта функция просто вызывает существующую shareLogs,
        // так как shareLogs уже отправляет полный, неформатированный файл логов.
        Timber.i("exportDetailedLogs called, invoking shareLogs.")
        shareLogs(context)
    }
}

// UI компоненты
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Инициализация Timber для логирования в файл
        if (Timber.treeCount == 0) {
            Timber.plant(FileLoggingTree(applicationContext))
            Timber.i("MainActivity onCreate: Timber FileLoggingTree planted.")
        } else {
            Timber.i("MainActivity onCreate: Timber already planted.")
        }

        // viewModel и его инициализация перенесены внутрь setContent для корректного Composable контекста

        setContent {
            val viewModel: MainViewModel = viewModel()
            LaunchedEffect(Unit) { // Вызываем initViewModel один раз при первой композиции
                viewModel.initViewModel(applicationContext)
            }

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
                    Timber.d("No need to request notification permission (SDK < 33).")
                    startTimerService() // Запускаем сервис
                }
            }

            // Подключение к сервису
            val serviceConnection = remember {
                object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, serviceBinder: IBinder?) {
                        val binder = serviceBinder as? TimerService.LocalBinder
                        viewModel.connectToTimerService(binder?.getService())
                        Timber.i("TimerService connected to MainActivity/ViewModel.")
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        viewModel.connectToTimerService(null)
                        Timber.w("TimerService disconnected from MainActivity/ViewModel.")
                    }
                }
            }

            // Привязка/отвязка сервиса
            DisposableEffect(Unit) {
                Timber.d("MainActivity DisposableEffect: Binding to TimerService.")
                Intent(this@MainActivity, TimerService::class.java).also { intent ->
                    bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
                }
                onDispose {
                    Timber.d("MainActivity DisposableEffect: Unbinding from TimerService.")
                    try {
                         unbindService(serviceConnection)
                         viewModel.connectToTimerService(null) // Явно обнуляем ссылку на сервис
                    } catch (e: IllegalArgumentException) {
                        Timber.w(e, "Error unbinding service. Already unbound or not bound?")
                    }
                }
            }

            // Вызов Bitrix_appTheme теперь без параметра appTheme
            Bitrix_appTheme {
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
        Timber.d("Attempting to start TimerService (Foreground).")
        val serviceIntent = Intent(this, TimerService::class.java).apply {
            // Используем ACTION_START_FOREGROUND_SERVICE, если он определен в сервисе для явного старта
            // В текущей реализации сервиса, он сам вызывает startForeground в onCreate.
            // Поэтому достаточно просто startService/startForegroundService.
            action = TimerService.ACTION_START_FOREGROUND_SERVICE // Или просто запуск без action, если сервис сам себя поднимает
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Отвязка от сервиса происходит в DisposableEffect.
        // Остановка сервиса:
        // Если сервис должен останавливаться при закрытии UI, то здесь:
        // Intent(this, TimerService::class.java).also { intent ->
        //    intent.action = TimerService.ACTION_STOP_FOREGROUND_SERVICE
        //    startService(intent) // или ContextCompat.startForegroundService
        // }
        // Но для сохранения таймера в фоне, сервис обычно не останавливают здесь.
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
                    }в
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
    // var isUserMenuExpanded by remember { mutableStateOf(false) } // Больше не нужно
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
            // Ряд с иконками пользователей
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp) // Пространство между аватарами
            ) {
                viewModel.users.forEachIndexed { index, user ->
                    val isSelected = index == viewModel.currentUserIndex
                    val avatarSize = if (isSelected) 56 else 48 // Размер активного аватара больше
                    val elevation = if (isSelected) 6.dp else 2.dp // Тень для активного аватара
                    Box(
                        modifier = Modifier
                            .size(avatarSize.dp)
                            .shadow(elevation = elevation, shape = CircleShape, clip = false) // Тень применяется к Box
                            .clip(CircleShape) // Обрезка для UserAvatar, если он сам не обрезает
                            .clickable {
                                if (!isSelected) { // Переключаем пользователя только если он не выбран
                                    viewModel.switchUser(index, context)
                                }
                            }
                            .padding(if (isSelected) 2.dp else 0.dp) // Небольшой отступ для "рамки" у выбранного
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent,
                                CircleShape
                            )

                    ) {
                        UserAvatar(user = user, size = avatarSize - (if (isSelected) 4 else 0)) // Уменьшаем размер аватара для рамки
                    }
                }
            }

            // Иконки для быстрого создания задач
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MainViewModel.StandardTaskType.values().forEach { taskType ->
                    IconButton(
                        onClick = { viewModel.createStandardTask(taskType, context) },
                        modifier = Modifier.size(48.dp) // Стандартизируем размер кнопки
                    ) {
                        Text(
                            text = taskType.emoji,
                            fontSize = 28.sp, // Размер эмодзи
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

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
                        /* // Отключено согласно задаче - скрыть настройку отправки комментариев
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
                        */
                        // --- Пункты выбора темы удалены ---
                        // Divider() // Разделитель перед другими опциями (если он был только для тем)

                        DropdownMenuItem(
                            text = { Text("Посмотреть логи (упрощенные)") },
                            onClick = {
                                onShowLogs()
                                isSettingsExpanded = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Выгрузить подробные логи") },
                            onClick = {
                                viewModel.exportDetailedLogs(context) // Используем context из LocalContext.current
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

        val serviceState = viewModel.timerServiceState // Получаем состояние из ViewModel (TimerServiceState?)

        // Активный таймер (если есть)
        if (serviceState?.activeTaskId != null) { // Проверяем на null
            val taskTitle = serviceState.activeTaskTitle ?: "Задача..."
            val cardColor = when {
                serviceState.isSystemPaused -> StatusOrange
                serviceState.isUserPaused -> StatusYellow
                else -> StatusBlue
            }
            val statusText = when {
                serviceState.isSystemPaused && serviceState.isUserPaused -> "⏸️ Пауза (система и пользователь)"
                serviceState.isSystemPaused -> "⏸️ Пауза (система: ${viewModel.workStatus.name.lowercase()})"
                serviceState.isUserPaused -> "⏸️ Таймер приостановлен (пользователем)"
                else -> "🕐 Активный таймер"
            }
            val textColor = if (serviceState.isEffectivelyPaused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = cardColor)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = statusText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Text(
                        text = taskTitle,
                        fontSize = 16.sp,
                        maxLines = 2, // Увеличим до 2 строк, если заголовок длинный
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = viewModel.formatTime(serviceState.timerSeconds), // serviceState здесь уже не null из-за if
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    // Кнопка "Сохранить время" для активного таймера
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = { viewModel.stopAndSaveCurrentTimer() },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text("Сохранить время и остановить", color = textColor)
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
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
        // Сообщения о статусе операций (аудио, быстрое создание задачи)
        val audioMessage = viewModel.audioProcessingMessage
        val taskCreationMessage = viewModel.quickTaskCreationStatus

        if (audioMessage != null || taskCreationMessage != null) {
            val messageToDisplay = taskCreationMessage ?: audioMessage // Приоритет у сообщения о создании задачи
            // Проверяем, является ли сообщение об ошибке (если errorMessage установлен И quickTaskCreationStatus содержит "Ошибка")
            // или если audioProcessingMessage содержит "Ошибка" (на случай ошибок аудио без установки errorMessage)
            val isError = (viewModel.errorMessage != null && taskCreationMessage?.contains("Ошибка", ignoreCase = true) == true) ||
                          (audioMessage?.contains("Ошибка", ignoreCase = true) == true && taskCreationMessage == null)


            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Text(
                    text = messageToDisplay!!, // messageToDisplay не будет null из-за условия if
                    modifier = Modifier.padding(16.dp),
                    color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                    textAlign = TextAlign.Center
                )
            }
        }


        LazyColumn {
            items(viewModel.tasks, key = { task -> task.id }) { task ->
                // Получаем состояние конкретно для этой задачи из общего состояния сервиса
                val sState = viewModel.timerServiceState // TimerServiceState?
                val isTimerRunningForThisTask = sState?.activeTaskId == task.id && sState.isEffectivelyPaused == false
                val isTimerUserPausedForThisTask = sState?.activeTaskId == task.id && sState.isUserPaused == true
                val isTimerSystemPausedForThisTask = sState?.activeTaskId == task.id && sState.isSystemPaused == true

                TaskCard(
                    task = task,
                    onTimerToggle = { viewModel.toggleTimer(it) },
                    onCompleteTask = { viewModel.completeTask(it) },
                    isTimerRunningForThisTask = isTimerRunningForThisTask,
                    isTimerUserPausedForThisTask = isTimerUserPausedForThisTask,
                    isTimerSystemPausedForThisTask = isTimerSystemPausedForThisTask,
                    viewModel = viewModel
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun RenderUserAvatar(user: User, size: Int) { // Принимаем User напрямую
    UserAvatar(user = user, size = size)
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
                // Отображение статуса задачи удалено
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
                // val isLoadingChecklist = viewModel.loadingChecklistMap[task.id] == true // Удалено
                if (!checklist.isNullOrEmpty() && checklist.any { !it.isComplete }) { // Скрываем, если все пункты выполнены
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
                // val isLoadingSubtasks = viewModel.loadingSubtasksMap[task.id] == true // Удалено
                if (!subtasks.isNullOrEmpty()) {
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

            // Spacer(modifier = Modifier.height(16.dp)) // Этот Spacer, кажется, лишний здесь, был между подзадачами и кнопками. Убираем.

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
                    val iconVector = when {
                        isTimerRunningForThisTask -> Icons.Filled.Pause // ИЗМЕНЕНО: Stop на Pause
                        isTimerUserPausedForThisTask -> Icons.Filled.PlayArrow
                        isTimerSystemPausedForThisTask -> Icons.Filled.Pause
                        else -> Icons.Filled.PlayArrow
                    }
                    val contentDescription = when {
                        isTimerRunningForThisTask -> "Приостановить таймер" // ИЗМЕНЕНО: "Остановить" на "Приостановить"
                        isTimerUserPausedForThisTask -> "Продолжить таймер"
                        isTimerSystemPausedForThisTask -> "Таймер на системной паузе"
                        else -> "Запустить таймер"
                    }
                    Icon(
                        imageVector = iconVector,
                        contentDescription = contentDescription,
                        modifier = Modifier.size(28.dp) // Увеличиваем размер иконки
                        // tint будет применен автоматически из ButtonDefaults
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

                // Кнопка записи аудиокомментария (теперь доступна и для завершенных задач)
                // if (!task.isCompleted) { // Условие удалено
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
                // } // Закрывающая скобка от if (!task.isCompleted) удалена
            }
        }
    }
}
