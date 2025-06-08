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
// import androidx.compose.material.icons.filled.Mic // Удалено
import androidx.compose.material.icons.filled.Pause // Для иконки паузы
import androidx.compose.material.icons.filled.Add // Для кнопки выпадающего списка быстрых задач
import androidx.compose.material.icons.filled.AddComment // Для добавления текстового комментария
import androidx.compose.material.icons.filled.PlayArrow // Для иконки старт/продолжить
import androidx.compose.material.icons.filled.PowerSettingsNew // Для кнопки управления рабочим днем
import androidx.compose.material.icons.filled.Refresh // Для кнопки "Обновить"
import androidx.compose.material.icons.filled.Save // Для иконки сохранения (дискета)
import androidx.compose.material.icons.filled.Stop // Для иконки остановки записи
import androidx.compose.material.icons.filled.Delete // Для иконки удаления
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow // <--- Добавляем этот импорт
import androidx.compose.ui.graphics.Brush // For gradient
import androidx.compose.ui.graphics.Color
import android.Manifest // Для запроса разрешений (все еще нужен для POST_NOTIFICATIONS)
import android.content.pm.PackageManager // Для проверки разрешений (все еще нужен для POST_NOTIFICATIONS)
// import android.media.MediaRecorder // Удалено
// import android.util.Base64 // Для кодирования в Base64 - удалено, если не используется в другом месте
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
import androidx.compose.foundation.ExperimentalFoundationApi // Для combinedClickable
import androidx.compose.foundation.combinedClickable // Для long press
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
import kotlinx.coroutines.Dispatchers // Добавляем импорт
import kotlinx.coroutines.withContext // Добавляем импорт
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONArray
import org.json.JSONException // Добавляем этот импорт
import org.json.JSONObject
import java.io.IOException
import java.util.*
import kotlin.coroutines.resume
import java.text.SimpleDateFormat // Добавим для formatDeadline

// Вспомогательная функция для форматирования крайнего срока
fun formatDeadline(deadlineStr: String?): String? {
    if (deadlineStr.isNullOrBlank()) return null // Handle null or blank
    val outputFormatter = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    // Список возможных форматов даты, которые может вернуть API Bitrix для DEADLINE
    val parsers = listOf(
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault()), // Полный формат с часовым поясом
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()),     // Распространенный формат без пояса
        SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())               // Только дата
    )

    for (parser in parsers) {
        try {
            val parsedDate = parser.parse(deadlineStr)
            if (parsedDate != null) { // Добавлена проверка на null после parse
                return outputFormatter.format(parsedDate)
            }
        } catch (e: java.text.ParseException) {
            // Попробовать следующий парсер
        }
    }
    Timber.w("Could not parse deadline: $deadlineStr with any known format.")
    return deadlineStr // Вернуть оригинальную строку, если все попытки парсинга не удались
}

// Модели данных
data class User(
    val name: String,
    val webhookUrl: String,
    val userId: String,
    val avatar: String,
    val supervisorId: String? = null // ID руководителя
)

data class Task(
    val id: String,
    val title: String,
    val description: String,
    val timeSpent: Int,
    val timeEstimate: Int,
    val status: String = "",
    val deadline: String? = null, // Крайний срок задачи
    val changedDate: String? = null, // Добавлено поле для даты изменения
    val attachedFileIds: List<String> = emptyList() // ID прикрепленных файлов (UF_TASK_WEBDAV_FILES)
    // Поле isTimerRunning удалено, так как состояние таймера управляется в UserTimerData
    // parentId удален
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

data class AttachedFile(
    val id: String, // ID файла на диске
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long
) {
    val formattedSize: String get() {
        val kb = sizeBytes / 1024
        val mb = kb / 1024
        return when {
            mb > 0 -> String.format("%.2f MB", mb.toFloat())
            kb > 0 -> String.format("%d KB", kb)
            else -> String.format("%d Bytes", sizeBytes)
        }
    }
}

// Enum AppThemeOptions удален, так как тема будет фиксированной

// ViewModel

// Вспомогательный data class для результата обработки задач в фоновом потоке
data class TaskProcessingOutput(
    val processedTasks: List<Task>,
    val rawTaskCount: Int, // Для определения необходимости fallback
    val processingError: String? = null // Ошибка, возникшая во время обработки
)

// Enum для статусов Timeman API
enum class TimemanApiStatus { OPENED, PAUSED, CLOSED, UNKNOWN }

class MainViewModel : ViewModel() {
    private val client = OkHttpClient()

    // Пользователи с их ID в системе и аватарами
    val users = listOf(
        User("Денис Мелков", "https://bitrix.tooksm.kz/rest/320/gwx0v32nqbiwu7ww/", "320", "ДМ", supervisorId = "253"), // Ким Филби - руководитель
        User("Владислав Малай", "https://bitrix.tooksm.kz/rest/321/smczp19q348xui28/", "321", "ВМ", supervisorId = "253"), // Ким Филби - руководитель
        User("Ким Филби", "https://bitrix.tooksm.kz/rest/253/tk5y2f3sukqxn5bi/", "253", "КФ", supervisorId = null) // У Кима нет руководителя в данном контексте
        // User("Тестовый Пользователь", "https://your_bitrix_domain/rest/user_id/webhook_code/", "user_id", "ТП", supervisorId = "ID_РУКОВОДИТЕЛЯ")
    )

    var currentUserIndex by mutableStateOf(0)
    var tasks by mutableStateOf<List<Task>>(emptyList())
    var workStatus by mutableStateOf(WorkStatus.WORKING)
    var isLoading by mutableStateOf(false)
    var errorMessage by mutableStateOf<String?>(null)
    var sendComments by mutableStateOf(false) // Настройка отправки комментариев (по умолчанию отключена)
    var showCompletedTasks by mutableStateOf(true) // Настройка отображения завершенных задач
    var quickTaskDisplayMode by mutableStateOf(QuickTaskDisplayMode.ICONS) // Режим отображения быстрых задач

    // Состояние раскрытия карточек задач
    var expandedTaskIds by mutableStateOf<Set<String>>(emptySet())
        private set

    // Enum для стандартных типов задач
    enum class StandardTaskType(val titlePrefix: String, val emoji: String, val defaultPriority: String = "1") {
        RIGGING("Такелаж", "🏗️"), // U+1F3D7
        FIX_MISTAKES("Исправление косяков", "🛠️"), // U+1F6E0
        UNEXPECTED("Неожиданная задача", "✨", "2") // U+2728, High priority
    }

    enum class QuickTaskDisplayMode { ICONS, DROPDOWN }

    // Состояния для чек-листов и подзадач
    var checklistsMap by mutableStateOf<Map<String, List<ChecklistItem>>>(emptyMap())
        private set
    var loadingChecklistMap by mutableStateOf<Map<String, Boolean>>(emptyMap())
        private set

    // Состояния для прикрепленных файлов
    var fileDetailsMap by mutableStateOf<Map<String, AttachedFile>>(emptyMap()) // Map<fileId, AttachedFile>
        private set
    var loadingFilesForTaskMap by mutableStateOf<Map<String, Boolean>>(emptyMap()) // Map<taskId, isLoading>
        private set

    // Состояния для записи аудио - УДАЛЕНО
    // var currentRecordingTask by mutableStateOf<Task?>(null)
    //     private set
    // var isRecordingAudio by mutableStateOf(false)
    //     private set
    // var audioProcessingMessage by mutableStateOf<String?>(null) // Удалено, т.к. было только для аудио
    //     private set

    // private var mediaRecorder: MediaRecorder? = null // Удалено
    // private var audioOutputFile: java.io.File? = null // Удалено

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

    // Состояния для диалога добавления текстового комментария
    var showAddCommentDialogForTask by mutableStateOf<Task?>(null)
        private set
    var commentTextInput by mutableStateOf("")
        // private set // Removed private set to allow UI to update this
    var textCommentStatusMessage by mutableStateOf<String?>(null) // Сообщение о статусе добавления комментария
        private set

    // Состояния для управления рабочим днем
    var timemanCurrentApiStatus by mutableStateOf(TimemanApiStatus.UNKNOWN)
        private set
    var timemanStatusLoading by mutableStateOf(false) // Индикатор загрузки статуса дня
        private set
    var timemanActionInProgress by mutableStateOf(false) // Индикатор выполнения действия (открыть/закрыть день)
        private set
    var timemanInfoMessage by mutableStateOf<String?>(null) // Сообщения о статусе операций с рабочим днем
        private set

    // Состояния для диалога подтверждения удаления задачи
    var showDeleteConfirmDialogForTask by mutableStateOf<Task?>(null)
        private set
    var deleteTaskStatusMessage by mutableStateOf<String?>(null)
        private set


    // --- Управление SharedPreferences ---
    private val sharedPreferencesName = "BitrixAppPrefs"
    private val currentUserIndexKey = "currentUserIndex"
    private val quickTaskDisplayModeKey = "quickTaskDisplayMode"

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

    private fun saveQuickTaskDisplayMode(context: Context, mode: QuickTaskDisplayMode) {
        val prefs = context.getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)
        prefs.edit().putString(quickTaskDisplayModeKey, mode.name).apply()
        Timber.d("Saved QuickTaskDisplayMode: ${mode.name}")
    }

    private fun loadQuickTaskDisplayMode(context: Context): QuickTaskDisplayMode {
        val prefs = context.getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)
        val modeName = prefs.getString(quickTaskDisplayModeKey, QuickTaskDisplayMode.ICONS.name)
        return try {
            QuickTaskDisplayMode.valueOf(modeName ?: QuickTaskDisplayMode.ICONS.name)
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Failed to parse QuickTaskDisplayMode, defaulting to ICONS.")
            QuickTaskDisplayMode.ICONS
        }.also {
            Timber.d("Loaded QuickTaskDisplayMode: $it")
        }
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
        quickTaskDisplayMode = loadQuickTaskDisplayMode(context) // Загружаем режим отображения быстрых задач
        updateWorkStatus() // Важно вызвать до loadTasks, чтобы timeman статус был актуален
        loadTasks()
        startPeriodicUpdates()
        startPeriodicTaskUpdates()
        val currentUserForInit = users[currentUserIndex]
        fetchTimemanStatus(currentUserForInit) // Получаем статус рабочего дня при инициализации
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
        timemanInfoMessage = null // Сбрасываем сообщение о статусе дня

        saveCurrentUserIndex(context, index) // Сохраняем новый индекс
        currentUserIndex = index
        val switchedUser = users[index]
        timerService?.setCurrentUser(switchedUser.userId, switchedUser.name) // Уведомляем сервис о смене пользователя

        updateWorkStatus() // Обновляем статус рабочего дня для нового пользователя
        loadTasks() // Загружаем задачи для нового пользователя
        fetchTimemanStatus(switchedUser) // Получаем статус рабочего дня для нового пользователя
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
                "&select[]=DEADLINE" + // Добавляем DEADLINE
                "&select[]=CHANGED_DATE" + // Добавляем CHANGED_DATE
                "&select[]=UF_TASK_WEBDAV_FILES" + // По-прежнему запрашиваем его явно
                "&select[]=UF_*" // Запрашиваем все пользовательские поля для диагностики
                // PARENT_ID удален

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
                            val responseText = body.string() // Получаем текст ответа
                            viewModelScope.launch { // Запускаем корутину для обработки и обновления UI
                                try {
                                    val output = withContext(Dispatchers.Default) {
                                        // Вся обработка JSON и списков происходит в фоновом потоке
                                        Timber.d("Load tasks (bg): Processing ${responseText.length} chars for user ${user.name}")
                                        try {
                                            val json = JSONObject(responseText)
                                            if (json.has("error")) {
                                                val error = json.getJSONObject("error")
                                                val apiErrorMessage = "Ошибка API: ${error.optString("error_description", "Неизвестная ошибка")}"
                                                Timber.w("API error in loadTasks (bg): $apiErrorMessage")
                                                return@withContext TaskProcessingOutput(emptyList(), 0, apiErrorMessage)
                                            }

                                            val newRawTasksList = mutableListOf<Task>()
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

                                            val calendar = Calendar.getInstance()
                                            calendar.add(Calendar.DAY_OF_YEAR, -2)
                                            val twoDaysAgo = calendar.time
                                            // dateFormat для changedDate
                                            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
                                            // Форматы для deadline
                                            val deadlineDateFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
                                            val simpleDeadlineDateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())


                                            val tasksForStatusFiltering = newRawTasksList
                                            val filteredTasksList = tasksForStatusFiltering.filter { task ->
                                                if (!task.isCompleted) true
                                                else {
                                                    if (showCompletedTasks) {
                                                        task.changedDate?.let { dateStr ->
                                                            try {
                                                                val taskChangedDate = dateFormat.parse(dateStr)
                                                                taskChangedDate != null && taskChangedDate.after(twoDaysAgo)
                                                            } catch (e: java.text.ParseException) {
                                                                Timber.w(e, "Failed to parse changedDate '$dateStr' for task ${task.id}. Filtering out.")
                                                                false
                                                            }
                                                        } ?: false
                                                    } else false
                                                }
                                            }
                                            Timber.d("Raw tasks (bg): ${newRawTasksList.size}, Filtered (bg, showCompleted=$showCompletedTasks): ${filteredTasksList.size} for user ${user.name}")

                                            val newSortedTasksList = filteredTasksList.sortedWith(
                                                compareBy<Task> { it.isCompleted } // Завершенные задачи в конце
                                                    .thenBy { task -> // Сортировка по крайнему сроку (по возрастанию, nulls/ошибки парсинга в конце)
                                                        task.deadline?.takeIf { it.isNotBlank() }?.let { deadlineStr ->
                                                            try {
                                                                deadlineDateFormat.parse(deadlineStr)
                                                            } catch (e: java.text.ParseException) {
                                                                try {
                                                                    simpleDeadlineDateFormat.parse(deadlineStr)
                                                                } catch (e2: java.text.ParseException) {
                                                                    Timber.w(e, "Failed to parse deadline '$deadlineStr' for task ${task.id} in loadTasks, treating as far future.")
                                                                    Date(Long.MAX_VALUE)
                                                                }
                                                            }
                                                        } ?: Date(Long.MAX_VALUE) // Задачи без крайнего срока или с пустым значением - в конец
                                                    }
                                                    .thenByDescending { task -> // Затем по дате изменения (новые сначала)
                                                        task.changedDate?.let { dateStr ->
                                                            try {
                                                                dateFormat.parse(dateStr)
                                                            } catch (e: java.text.ParseException) {
                                                                null // Ошибки парсинга даты изменения приведут к неопределенному порядку для этого критерия
                                                            }
                                                        }
                                                    }
                                                    .thenBy { it.id.toIntOrNull() ?: 0 } // Наконец, по ID
                                            )
                                            TaskProcessingOutput(newSortedTasksList, newRawTasksList.size, null)
                                        } catch (e: Exception) {
                                            Timber.e(e, "Error during background task processing for user ${user.name}")
                                            TaskProcessingOutput(emptyList(), 0, "Ошибка обработки данных: ${e.message}")
                                        }
                                    } // Конец withContext(Dispatchers.Default)

                                    // Обновление UI на основном потоке
                                    if (output.processingError != null) {
                                        errorMessage = output.processingError
                                        // Если основная загрузка вернула ошибку API и нет задач, пробуем fallback
                                        if (tasks.isEmpty()) { // Проверяем, что это была основная загрузка (tasks еще не обновлены)
                                            Timber.w("Primary loadTasks resulted in processing error '${output.processingError}' and no tasks currently displayed. Trying simple query.")
                                            loadTasksSimple()
                                        }
                                    } else {
                                        if (!areTaskListsFunctionallyEquivalent(output.processedTasks, tasks)) {
                                            Timber.i("Task list for user ${user.name} has changed. Updating UI with ${output.processedTasks.size} tasks.")
                                            tasks = output.processedTasks
                                        } else {
                                            Timber.i("Task list for user ${user.name} has not changed (${output.processedTasks.size} tasks). No UI update for tasks list.")
                                        }
                                        errorMessage = null // Очищаем ошибку при успехе

                                        if (output.rawTaskCount == 0) {
                                            Timber.w("No tasks found for user ${user.name} with primary query (raw list empty). Trying simple query.")
                                            loadTasksSimple()
                                        } else if (output.processedTasks.isEmpty() && tasks.isEmpty()) {
                                            Timber.w("No displayable tasks for user ${user.name} after filtering in loadTasks. Current tasks list is also empty.")
                                        }
                                    }
                                } catch (e: Exception) { // Ошибки от body.string() или другие ошибки на основном потоке
                                    errorMessage = "Ошибка чтения ответа: ${e.message}"
                                    Timber.e(e, "Error in loadTasks onResponse (main thread part) for user ${user.name}")
                                }
                            } // Конец viewModelScope.launch
                        } ?: run { // response.body is null
                             viewModelScope.launch {
                                errorMessage = "Пустой ответ от сервера."
                                Timber.w("Response body is null in loadTasks for user ${user.name}")
                             }
                        }
                    } else { // response not successful
                        viewModelScope.launch {
                            errorMessage = "Ошибка сервера: ${response.code} - ${response.message}"
                            Timber.e("HTTP error in loadTasks: ${response.code} - ${response.message}")
                            // Если основная загрузка не удалась и нет задач, пробуем fallback
                            if (tasks.isEmpty()) {
                                Timber.w("Primary loadTasks HTTP error and no tasks currently displayed. Trying simple query.")
                                loadTasksSimple()
                            }
                        }
                    }
                }
            }
        })
    }

    // Простой метод загрузки без фильтров
    private fun loadTasksSimple() {
        val user = users[currentUserIndex]
        // Возвращаем UF_TASK_WEBDAV_FILES и добавляем UF_*, DEADLINE в простой запрос
        val url = "${user.webhookUrl}tasks.task.list?select[]=ID&select[]=TITLE&select[]=DESCRIPTION&select[]=TIME_SPENT_IN_LOGS&select[]=TIME_ESTIMATE&select[]=STATUS&select[]=DEADLINE&select[]=CHANGED_DATE&select[]=UF_TASK_WEBDAV_FILES&select[]=UF_*"

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
                if (response.isSuccessful) {
                    response.body?.let { body ->
                        val responseText = body.string()
                        viewModelScope.launch {
                            try {
                                val output = withContext(Dispatchers.Default) {
                                    Timber.d("Simple API Response (bg): Received ${responseText.length} chars for user ${user.name}")
                                    try {
                                        val json = JSONObject(responseText)
                                        if (json.has("error")) { // Проверяем ошибку API внутри withContext
                                            val error = json.getJSONObject("error")
                                            val apiErrorMessage = "Ошибка API (simple): ${error.optString("error_description", "Неизвестная ошибка")}"
                                            Timber.w("API error in loadTasksSimple (bg): $apiErrorMessage")
                                            return@withContext TaskProcessingOutput(emptyList(), 0, apiErrorMessage)
                                        }
                                        if (json.has("result")) {
                                            val newRawTasksList = mutableListOf<Task>()
                                            val result = json.get("result")
                                            // ... (логика processTasks как в оригинале)
                                            if (result is JSONObject && result.has("tasks")) {
                                                processTasks(result.get("tasks"), newRawTasksList)
                                            } else if (result is JSONArray) {
                                                processTasks(result, newRawTasksList)
                                            } else if (result is JSONObject) {
                                                processTasks(result, newRawTasksList)
                                            }

                                            if (newRawTasksList.isNotEmpty()) {
                                                val calendar = Calendar.getInstance()
                                                calendar.add(Calendar.DAY_OF_YEAR, -2)
                                                val twoDaysAgo = calendar.time
                                                // dateFormat для changedDate
                                                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
                                                // Форматы для deadline
                                                val deadlineDateFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
                                                val simpleDeadlineDateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

                                                val filteredTasksList = newRawTasksList.filter { task ->
                                                    if (!task.isCompleted) true
                                                    else {
                                                        if (showCompletedTasks) {
                                                            task.changedDate?.let { dateStr ->
                                                                try { dateFormat.parse(dateStr)?.after(twoDaysAgo) ?: false }
                                                                catch (e: java.text.ParseException) { false }
                                                            } ?: false
                                                        } else false
                                                    }
                                                }
                                                val newSortedTasksList = filteredTasksList.sortedWith(
                                                    compareBy<Task> { it.isCompleted }
                                                        .thenBy { task ->
                                                            task.deadline?.takeIf { it.isNotBlank() }?.let { deadlineStr ->
                                                                try {
                                                                    deadlineDateFormat.parse(deadlineStr)
                                                                } catch (e: java.text.ParseException) {
                                                                    try {
                                                                        simpleDeadlineDateFormat.parse(deadlineStr)
                                                                    } catch (e2: java.text.ParseException) {
                                                                        Timber.w(e, "Failed to parse deadline '$deadlineStr' for task ${task.id} in loadTasksSimple, treating as far future.")
                                                                        Date(Long.MAX_VALUE)
                                                                    }
                                                                }
                                                            } ?: Date(Long.MAX_VALUE)
                                                        }
                                                        .thenByDescending { task ->
                                                            task.changedDate?.let { dateStr ->
                                                                try {
                                                                    dateFormat.parse(dateStr)
                                                                } catch (e: java.text.ParseException) {
                                                                    null
                                                                }
                                                            }
                                                        }
                                                        .thenBy { it.id.toIntOrNull() ?: 0 }
                                                )
                                                TaskProcessingOutput(newSortedTasksList, newRawTasksList.size, null)
                                            } else {
                                                TaskProcessingOutput(emptyList(), 0, null) // Нет сырых задач
                                            }
                                        } else {
                                            Timber.w("Simple method response (bg) for user ${user.name} does not have 'result'.")
                                            TaskProcessingOutput(emptyList(), 0, "Отсутствует 'result' в ответе (simple)")
                                        }
                                    } catch (e: Exception) {
                                        Timber.e(e, "Simple parse error (bg) for user ${user.name}.")
                                        TaskProcessingOutput(emptyList(), 0, "Ошибка парсинга (simple): ${e.message}")
                                    }
                                } // Конец withContext

                                if (output.processingError != null) {
                                    errorMessage = output.processingError // Показываем ошибку обработки
                                    Timber.w("Processing error in loadTasksSimple: ${output.processingError}. Trying alternative.")
                                    loadTasksAlternative() // Пробуем альтернативу при ошибке обработки
                                } else if (output.rawTaskCount == 0) {
                                    Timber.w("Simple method yielded no raw tasks for user ${user.name}. Trying alternative.")
                                    loadTasksAlternative()
                                } else {
                                    if (!areTaskListsFunctionallyEquivalent(output.processedTasks, tasks)) {
                                        Timber.i("Task list (simple) for user ${user.name} has changed. Updating UI with ${output.processedTasks.size} tasks.")
                                        tasks = output.processedTasks
                                    } else {
                                        Timber.i("Task list (simple) for user ${user.name} has not changed (${output.processedTasks.size} tasks). No UI update.")
                                    }
                                    errorMessage = null
                                    Timber.i("Successfully processed ${output.rawTaskCount} raw tasks (simple), resulting in ${output.processedTasks.size} displayable tasks for user ${user.name}")
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Error in loadTasksSimple onResponse (main thread part) for user ${user.name}")
                                errorMessage = "Ошибка чтения ответа (simple): ${e.message}"
                                loadTasksAlternative() // Пробуем альтернативу при других ошибках
                            }
                        } // Конец viewModelScope.launch
                    } ?: viewModelScope.launch {
                        Timber.w("Simple method response body is null for user ${user.name}. Trying alternative.")
                        loadTasksAlternative()
                    }
                } else { // response not successful
                    viewModelScope.launch {
                        Timber.w("Simple method HTTP error for user ${user.name}: ${response.code}. Trying alternative.")
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
                "&select[]=ID&select[]=TITLE&select[]=DESCRIPTION&select[]=TIME_SPENT_IN_LOGS&select[]=TIME_ESTIMATE&select[]=STATUS&select[]=DEADLINE&select[]=CHANGED_DATE&select[]=UF_TASK_WEBDAV_FILES&select[]=UF_*" // Возвращаем UF_TASK_WEBDAV_FILES, UF_* и DEADLINE

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
                if (response.isSuccessful) {
                    response.body?.let { body ->
                        val responseText = body.string()
                        viewModelScope.launch {
                            try {
                                val output = withContext(Dispatchers.Default) {
                                    Timber.d("Alternative API Response (bg): Received ${responseText.length} chars for user ${user.name}")
                                    try {
                                        val json = JSONObject(responseText)
                                        if (json.has("error")) { // Проверяем ошибку API внутри withContext
                                            val error = json.getJSONObject("error")
                                            val apiErrorMessage = "Ошибка API (alternative): ${error.optString("error_description", "Неизвестная ошибка")}"
                                            Timber.w("API error in loadTasksAlternative (bg): $apiErrorMessage")
                                            return@withContext TaskProcessingOutput(emptyList(), 0, apiErrorMessage)
                                        }

                                        if (json.has("result")) {
                                            val newRawTasksList = mutableListOf<Task>()
                                            val result = json.get("result")
                                            // ... (логика processTasks как в оригинале)
                                            if (result is JSONObject && result.has("tasks")) {
                                                processTasks(result.get("tasks"), newRawTasksList)
                                            } else if (result is JSONArray) {
                                                processTasks(result, newRawTasksList)
                                            } else if (result is JSONObject) {
                                                processTasks(result, newRawTasksList)
                                            }

                                            if (newRawTasksList.isNotEmpty()) {
                                                val calendar = Calendar.getInstance()
                                                calendar.add(Calendar.DAY_OF_YEAR, -2)
                                                val twoDaysAgo = calendar.time
                                                // dateFormat для changedDate
                                                val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
                                                // Форматы для deadline
                                                val deadlineDateFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
                                                val simpleDeadlineDateFormat = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

                                                val filteredTasksList = newRawTasksList.filter { task ->
                                                    if (!task.isCompleted) true
                                                    else {
                                                        if (showCompletedTasks) {
                                                            task.changedDate?.let { dateStr ->
                                                                try { dateFormat.parse(dateStr)?.after(twoDaysAgo) ?: false }
                                                                catch (e: java.text.ParseException) { false }
                                                            } ?: false
                                                        } else false
                                                    }
                                                }
                                                val newSortedTasksList = filteredTasksList.sortedWith(
                                                    compareBy<Task> { it.isCompleted }
                                                        .thenBy { task ->
                                                            task.deadline?.takeIf { it.isNotBlank() }?.let { deadlineStr ->
                                                                try {
                                                                    deadlineDateFormat.parse(deadlineStr)
                                                                } catch (e: java.text.ParseException) {
                                                                    try {
                                                                        simpleDeadlineDateFormat.parse(deadlineStr)
                                                                    } catch (e2: java.text.ParseException) {
                                                                        Timber.w(e, "Failed to parse deadline '$deadlineStr' for task ${task.id} in loadTasksAlternative, treating as far future.")
                                                                        Date(Long.MAX_VALUE)
                                                                    }
                                                                }
                                                            } ?: Date(Long.MAX_VALUE)
                                                        }
                                                        .thenByDescending { task ->
                                                            task.changedDate?.let { dateStr ->
                                                                try {
                                                                    dateFormat.parse(dateStr)
                                                                } catch (e: java.text.ParseException) {
                                                                    null
                                                                }
                                                            }
                                                        }
                                                        .thenBy { it.id.toIntOrNull() ?: 0 }
                                                )
                                                TaskProcessingOutput(newSortedTasksList, newRawTasksList.size, null)
                                            } else {
                                                TaskProcessingOutput(emptyList(), 0, null) // Нет сырых задач
                                            }
                                        } else {
                                            Timber.w("Alternative method response (bg) for user ${user.name} does not have 'result'.")
                                            TaskProcessingOutput(emptyList(), 0, "Отсутствует 'result' в ответе (alternative)")
                                        }
                                    } catch (e: Exception) {
                                        Timber.e(e, "Alternative parse error (bg) for user ${user.name}.")
                                        TaskProcessingOutput(emptyList(), 0, "Ошибка парсинга (alternative): ${e.message}")
                                    }
                                } // Конец withContext

                                if (output.processingError != null) {
                                    errorMessage = output.processingError
                                    if (tasks.isEmpty()) { // Если и после этого нет задач, показываем ошибку
                                         errorMessage = "Не удалось загрузить задачи: ${output.processingError}"
                                    }
                                } else if (output.rawTaskCount == 0) {
                                    Timber.w("Alternative method also yielded no raw tasks for user ${user.name}.")
                                    if (tasks.isEmpty()) { // Только если текущий список задач пуст
                                        errorMessage = "Задачи не найдены для пользователя ${user.name}."
                                    }
                                } else {
                                    if (!areTaskListsFunctionallyEquivalent(output.processedTasks, tasks)) {
                                        Timber.i("Task list (alternative) for user ${user.name} has changed. Updating UI with ${output.processedTasks.size} tasks.")
                                        tasks = output.processedTasks
                                    } else {
                                        Timber.i("Task list (alternative) for user ${user.name} has not changed (${output.processedTasks.size} tasks). No UI update.")
                                    }
                                    errorMessage = null
                                    Timber.i("Successfully processed ${output.rawTaskCount} raw tasks (alternative), resulting in ${output.processedTasks.size} displayable tasks for user ${user.name}")
                                    if (output.processedTasks.isEmpty() && tasks.isEmpty()) {
                                        errorMessage = "Актуальные задачи не найдены для пользователя ${user.name}."
                                    }
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Error in loadTasksAlternative onResponse (main thread part) for user ${user.name}")
                                if (tasks.isEmpty()) {
                                    errorMessage = "Ошибка чтения ответа (alternative): ${e.message}"
                                }
                            }
                        } // Конец viewModelScope.launch
                    } ?: viewModelScope.launch {
                        Timber.w("Alternative method response body is null for user ${user.name}.")
                        if (tasks.isEmpty()) {
                            errorMessage = "Пустой ответ от сервера (alternative)."
                        }
                    }
                } else { // response not successful
                    viewModelScope.launch {
                        Timber.w("Alternative method HTTP error for user ${user.name}: ${response.code}.")
                        if (tasks.isEmpty()) {
                            errorMessage = "Ошибка сервера (alternative): ${response.code}."
                        }
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
        val currentTaskIdForLog = taskJson.optString("id", taskJson.optString("ID", fallbackId))

        // Диагностическое логирование всех UF_ полей
        taskJson.keys().forEach { key ->
            if (key.startsWith("UF_")) {
                Timber.d("Task ID $currentTaskIdForLog: Diagnostic - Found UF field: $key, Value: ${taskJson.opt(key)}")
            }
        }

        // Возвращаем парсинг UF_TASK_WEBDAV_FILES
        val fileIds = mutableListOf<String>()
        val filesValue = taskJson.opt("UF_TASK_WEBDAV_FILES")
        Timber.d("Task ID $currentTaskIdForLog: UF_TASK_WEBDAV_FILES raw value is '$filesValue' of type ${filesValue?.javaClass?.simpleName}")

        when (filesValue) {
            is JSONArray -> {
                for (i in 0 until filesValue.length()) {
                    val fileId = filesValue.optString(i)
                    if (fileId.isNotEmpty()) {
                        fileIds.add(fileId)
                    }
                }
                Timber.d("Task ID $currentTaskIdForLog: Parsed ${fileIds.size} file IDs from JSONArray: $fileIds")
            }
            is String -> {
                if (filesValue.isNotEmpty() && filesValue != "false") {
                    fileIds.add(filesValue)
                    Timber.w("Task ID $currentTaskIdForLog: UF_TASK_WEBDAV_FILES was a String '$filesValue'. Parsed as a single file ID.")
                } else {
                    Timber.d("Task ID $currentTaskIdForLog: UF_TASK_WEBDAV_FILES was an empty or 'false' string. No files.")
                }
            }
            is Boolean -> {
                Timber.d("Task ID $currentTaskIdForLog: UF_TASK_WEBDAV_FILES is boolean: $filesValue. No files.")
            }
            null -> {
                 Timber.d("Task ID $currentTaskIdForLog: UF_TASK_WEBDAV_FILES is null. No files.")
            }
            else -> {
                Timber.w("Task ID $currentTaskIdForLog: UF_TASK_WEBDAV_FILES has unexpected type: ${filesValue.javaClass.simpleName}. Value: '$filesValue'. Treating as no files.")
            }
        }

        return Task(
            id = taskJson.optString("id", taskJson.optString("ID", fallbackId)),
            title = taskJson.optString("title", taskJson.optString("TITLE", "Задача без названия")),
            description = taskJson.optString("description", taskJson.optString("DESCRIPTION", "")),
            timeSpent = timeSpent,
            timeEstimate = taskJson.optInt("timeEstimate", taskJson.optInt("TIME_ESTIMATE", 7200)),
            status = taskJson.optString("status", taskJson.optString("STATUS", "")),
            deadline = taskJson.optString("deadline", taskJson.optString("DEADLINE", null)),
            changedDate = taskJson.optString("changedDate", taskJson.optString("CHANGED_DATE", null)),
            attachedFileIds = fileIds // Присваиваем распарсенные ID
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
        // Если мы дошли до сюда, и размеры списков были одинаковы,
        // и все элементы из newList найдены в oldList с теми же значениями,
        // то списки эквивалентны. Дополнительная проверка на удаленные элементы не нужна.

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

    // fetchSubtasksForTask удален

    fun fetchFileDetailsForTaskIfNeeded(task: Task) {
        if (task.attachedFileIds.isEmpty()) {
            return
        }

        val idsToFetch = task.attachedFileIds.filter { !fileDetailsMap.containsKey(it) }
        if (idsToFetch.isEmpty()) {
            return
        }

        Timber.i("Fetching details for ${idsToFetch.size} file(s) for task ${task.id}: $idsToFetch using disk.file.getbatch")
        loadingFilesForTaskMap = loadingFilesForTaskMap + (task.id to true)
        val user = users[currentUserIndex]

        var url = "${user.webhookUrl}disk.file.getbatch?"
        idsToFetch.forEachIndexed { index, fileId ->
            url += "ID[$index]=$fileId&"
        }
        url = url.removeSuffix("&")

        val request = Request.Builder().url(url).build()
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                viewModelScope.launch {
                    Timber.e(e, "Failed to fetch file details for task ${task.id}")
                    loadingFilesForTaskMap = loadingFilesForTaskMap - task.id
                }
            }

            override fun onResponse(call: Call, response: Response) {
                viewModelScope.launch {
                    loadingFilesForTaskMap = loadingFilesForTaskMap - task.id
                    if (response.isSuccessful) {
                        response.body?.let { body ->
                            try {
                                val responseText = body.string()
                                Timber.d("disk.file.getbatch response for task ${task.id}: $responseText")
                                val json = JSONObject(responseText)
                                if (json.has("result")) {
                                    val filesArrayJson = json.getJSONArray("result")
                                    val newFileDetails = mutableMapOf<String, AttachedFile>()
                                    for (i in 0 until filesArrayJson.length()) {
                                        val fileJson = filesArrayJson.getJSONObject(i)
                                        val fileId = fileJson.getString("ID") // ID из disk.file.getbatch это ID самого файла
                                        newFileDetails[fileId] = AttachedFile(
                                            id = fileId,
                                            name = fileJson.getString("NAME"),
                                            downloadUrl = fileJson.getString("DOWNLOAD_URL"),
                                            sizeBytes = fileJson.getString("SIZE").toLongOrNull() ?: 0L
                                        )
                                    }
                                    fileDetailsMap = fileDetailsMap + newFileDetails // Добавляем новые детали к существующим
                                    Timber.i("Fetched and mapped ${newFileDetails.size} file details for task ${task.id}.")
                                } else if (json.has("error")) {
                                    val errorDesc = json.optString("error_description", "Unknown API error")
                                    Timber.w("API error fetching file details for task ${task.id} via getbatch: $errorDesc")
                                }
                            } catch (e: Exception) {
                                Timber.e(e, "Error parsing file details for task ${task.id} from getbatch")
                            }
                        }
                    } else {
                        Timber.w("Failed to fetch file details for task ${task.id} via getbatch. Code: ${response.code}")
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

            Timber.d("Starting timer for task ${task.id} with initial time ${task.timeSpent}")
            service.startTaskTimer(currentUser.userId, currentUser.name, task.id, task.title, task.timeSpent) // Передаем task.timeSpent
            if (sendComments) {
                sendTimerComment(task, "Таймер запущен", task.timeSpent) // Используем task.timeSpent для комментария
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

    fun toggleShowCompletedTasks() {
        showCompletedTasks = !showCompletedTasks
        Timber.i("Show completed tasks toggled to: $showCompletedTasks. Reloading tasks.")
        loadTasks() // Перезагружаем задачи, чтобы применить новый фильтр
    }

    fun toggleQuickTaskDisplayMode(context: Context) {
        quickTaskDisplayMode = if (quickTaskDisplayMode == QuickTaskDisplayMode.ICONS) {
            QuickTaskDisplayMode.DROPDOWN
        } else {
            QuickTaskDisplayMode.ICONS
        }
        saveQuickTaskDisplayMode(context, quickTaskDisplayMode)
        Timber.i("Quick task display mode toggled to: $quickTaskDisplayMode")
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

            // Автоматические вызовы timemanOpenWorkDay, timemanPauseWorkDay, timemanCloseWorkDay УДАЛЕНЫ.
            // Управление рабочим днем теперь ручное через кнопку.

            // Обновляем состояние системной паузы для таймера в СЕРВИСЕ (эта логика остается)
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

    private fun setTimedTimemanInfoMessage(message: String, durationMillis: Long = 3500L) {
        timemanInfoMessage = message
        viewModelScope.launch {
            delay(durationMillis)
            if (timemanInfoMessage == message) { // Очищаем, только если это то же самое сообщение
                timemanInfoMessage = null
            }
        }
    }

    fun fetchTimemanStatus(user: User = users[currentUserIndex], showLoadingIndicator: Boolean = true, onComplete: ((TimemanApiStatus) -> Unit)? = null) {
        if (showLoadingIndicator) timemanStatusLoading = true
        // timemanInfoMessage = null // Не очищаем здесь, чтобы не сбрасывать сообщения от open/close
        val url = "${user.webhookUrl}timeman.status"
        val request = Request.Builder().url(url).build()
        Timber.d("Fetching timeman status for user ${user.name}...")

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                viewModelScope.launch {
                    Timber.e(e, "Failed to fetch timeman status for user ${user.name}")
                    timemanCurrentApiStatus = TimemanApiStatus.UNKNOWN
                    if (showLoadingIndicator) timemanStatusLoading = false
                    errorMessage = "Ошибка сети (статус дня): ${e.message}"
                    onComplete?.invoke(TimemanApiStatus.UNKNOWN)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                viewModelScope.launch {
                    var newApiStatus = TimemanApiStatus.UNKNOWN
                    try {
                        val responseBody = response.body?.string()
                        if (response.isSuccessful && responseBody != null) {
                            Timber.d("Timeman status response for ${user.name}: $responseBody")
                            val json = JSONObject(responseBody)
                            if (json.has("result")) {
                                val result = json.getJSONObject("result")
                                val statusStr = result.optString("STATUS")
                                newApiStatus = when (statusStr) {
                                    "OPENED" -> TimemanApiStatus.OPENED
                                    "PAUSED" -> TimemanApiStatus.PAUSED
                                    "CLOSED" -> TimemanApiStatus.CLOSED
                                    else -> {
                                        Timber.w("Unknown timeman status string: '$statusStr' for user ${user.name}")
                                        TimemanApiStatus.UNKNOWN
                                    }
                                }
                                // Можно добавить информацию о времени начала/длительности в timemanInfoMessage, если нужно
                                // val duration = result.optString("DURATION", "")
                                // val timeStart = result.optString("TIME_START", "")
                                // setTimedTimemanInfoMessage("Статус: $newApiStatus, Начало: $timeStart, Длит: $duration")
                            } else if (json.has("error")) {
                                val errorDesc = json.optString("error_description", "API Error")
                                Timber.w("API error fetching timeman status for ${user.name}: $errorDesc")
                                errorMessage = "Ошибка API (статус дня): $errorDesc"
                            }
                        } else {
                            Timber.w("Failed to fetch timeman status for ${user.name}. Code: ${response.code}, Body: $responseBody")
                            errorMessage = "Ошибка сервера (статус дня): ${response.code}"
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error parsing timeman status for ${user.name}")
                        errorMessage = "Ошибка обработки (статус дня): ${e.message}"
                    } finally {
                        timemanCurrentApiStatus = newApiStatus
                        if (showLoadingIndicator) timemanStatusLoading = false
                        onComplete?.invoke(newApiStatus)
                    }
                }
            }
        })
    }

    fun manualToggleWorkdayStatus() {
        val user = users[currentUserIndex]
        timemanActionInProgress = true // Блокируем кнопку
        timemanInfoMessage = null      // Очищаем предыдущие сообщения
        errorMessage = null            // Очищаем предыдущие ошибки

        fetchTimemanStatus(user, showLoadingIndicator = false) { currentFetchedStatus ->
            viewModelScope.launch { // Убедимся, что мы в корутине ViewModel
                when (currentFetchedStatus) {
                    TimemanApiStatus.OPENED, TimemanApiStatus.PAUSED -> {
                        timemanCloseWorkDay(user) { success ->
                            if (success) {
                                setTimedTimemanInfoMessage("Рабочий день завершен.")
                                fetchTimemanStatus(user, showLoadingIndicator = false) // Обновляем статус для UI
                            } // Сообщение об ошибке уже установлено в timemanCloseWorkDay
                            timemanActionInProgress = false // Разблокируем кнопку
                        }
                    }
                    TimemanApiStatus.CLOSED, TimemanApiStatus.UNKNOWN -> {
                        timemanOpenWorkDay(user) { success ->
                            if (success) {
                                setTimedTimemanInfoMessage("Рабочий день начат.")
                                fetchTimemanStatus(user, showLoadingIndicator = false) // Обновляем статус для UI
                            } // Сообщение об ошибке уже установлено в timemanOpenWorkDay
                            timemanActionInProgress = false // Разблокируем кнопку
                        }
                    }
                }
            }
        }
    }

    private fun timemanOpenWorkDay(user: User, onComplete: ((Boolean) -> Unit)? = null) {
        Timber.i("Attempting to open workday for user ${user.name} (ID: ${user.userId})")
        val url = "${user.webhookUrl}timeman.open"
        val request = Request.Builder()
            .url(url)
            .post(FormBody.Builder().build())
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Timber.e(e, "Failed to open workday for user ${user.name}")
                viewModelScope.launch {
                    errorMessage = "Сеть (открытие дня): ${e.message}"
                    onComplete?.invoke(false)
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                viewModelScope.launch {
                    var success = false
                    if (response.isSuccessful && responseBody != null) {
                        try {
                            val json = JSONObject(responseBody)
                            if (json.has("result")) { // Bitrix часто возвращает {"result": true} или объект с деталями
                                success = true
                                Timber.i("Successfully opened workday for user ${user.name}. Response: $responseBody")
                            } else if (json.has("error")) {
                                val errorDesc = json.optString("error_description", "API Error")
                                Timber.w("API error opening workday for ${user.name}: $errorDesc. Response: $responseBody")
                                errorMessage = "API (открытие дня): $errorDesc"
                            } else {
                                Timber.w("Unknown response opening workday for ${user.name}. Code: ${response.code}. Response: $responseBody")
                                errorMessage = "Неизвестный ответ (открытие дня)."
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error parsing open workday response for ${user.name}. Response: $responseBody")
                            errorMessage = "Ошибка парсинга (открытие дня)."
                        }
                    } else { // HTTP error (e.g., 400, 401, 403, 500)
                        Timber.w("Failed to open workday for user ${user.name}. Code: ${response.code}. Response: $responseBody")
                        var displayErrorMessage = "Ошибка ${response.code} (открытие дня)"
                        var jsonParsedSuccessfully = false

                        if (responseBody != null) {
                            try {
                                val errorJson = JSONObject(responseBody)
                                jsonParsedSuccessfully = true // Assume parsing itself was successful

                                val errorVal = errorJson.optString("error")
                                val errorDescVal = errorJson.optString("error_description")

                                val extractedMessages = mutableListOf<String>()
                                if (errorVal.isNotBlank() && errorVal.lowercase() != "null") {
                                    extractedMessages.add(errorVal)
                                }
                                // Add description if it's present, not "null", and different from errorVal (if errorVal was also present)
                                if (errorDescVal.isNotBlank() && errorDescVal.lowercase() != "null") {
                                    if (extractedMessages.isEmpty() || extractedMessages.last() != errorDescVal) {
                                        extractedMessages.add(errorDescVal)
                                    }
                                }

                                if (extractedMessages.isNotEmpty()) {
                                    displayErrorMessage += ": ${extractedMessages.joinToString(" - ")}"
                                } else {
                                    // JSON was valid, but no 'error' or 'error_description' fields found or they were empty/"null".
                                    jsonParsedSuccessfully = false // Treat as if JSON didn't give useful info.
                                }
                            } catch (e: JSONException) {
                                Timber.w(e, "Could not parse JSON from error response body for timeman.open. Body: $responseBody")
                                // jsonParsedSuccessfully remains false
                            }

                            if (!jsonParsedSuccessfully && responseBody.isNotBlank()) {
                                // Append raw response body if JSON parsing failed or yielded no specific error messages,
                                // and the body is short.
                                if (responseBody.length < 150) { 
                                    val cleanedBody = responseBody.replace("\n", " ").replace("\r", "").trim()
                                    displayErrorMessage += ". Ответ: $cleanedBody"
                                }
                            }
                        }
                        errorMessage = displayErrorMessage
                    }
                    onComplete?.invoke(success)
                    response.close()
                }
            }
        })
    }

    private fun timemanPauseWorkDay(user: User, onComplete: ((Boolean) -> Unit)? = null) { // Добавлен колбэк, хотя пока не используется для ручного вызова
        Timber.i("Attempting to pause workday for user ${user.name} (ID: ${user.userId})")
        val url = "${user.webhookUrl}timeman.pause"
        val request = Request.Builder()
            .url(url)
            .post(FormBody.Builder().build())
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Timber.e(e, "Failed to pause workday for user ${user.name}")
                viewModelScope.launch {
                    errorMessage = "Сеть (пауза дня): ${e.message}"
                    onComplete?.invoke(false)
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                viewModelScope.launch {
                    var success = false
                    if (response.isSuccessful && responseBody != null) {
                         try {
                            val json = JSONObject(responseBody)
                            if (json.has("result")) {
                                success = true
                                Timber.i("Successfully paused workday for user ${user.name}. Response: $responseBody")
                            } else if (json.has("error")) {
                                val errorDesc = json.optString("error_description", "API Error")
                                Timber.w("API error pausing workday for ${user.name}: $errorDesc. Response: $responseBody")
                                errorMessage = "API (пауза дня): $errorDesc"
                            } else {
                                Timber.w("Unknown response pausing workday for ${user.name}. Code: ${response.code}. Response: $responseBody")
                                errorMessage = "Неизвестный ответ (пауза дня)."
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error parsing pause workday response for ${user.name}. Response: $responseBody")
                            errorMessage = "Ошибка парсинга (пауза дня)."
                        }
                    } else {
                        Timber.w("Failed to pause workday for user ${user.name}. Code: ${response.code}. Response: $responseBody")
                        errorMessage = "Сервер (пауза дня): ${response.code}"
                    }
                    onComplete?.invoke(success)
                    response.close()
                }
            }
        })
    }

    private fun timemanCloseWorkDay(user: User, onComplete: ((Boolean) -> Unit)? = null) {
        Timber.i("Attempting to close workday for user ${user.name} (ID: ${user.userId})")
        val url = "${user.webhookUrl}timeman.close"
        val request = Request.Builder()
            .url(url)
            .post(FormBody.Builder().build())
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Timber.e(e, "Failed to close workday for user ${user.name}")
                viewModelScope.launch {
                    errorMessage = "Сеть (закрытие дня): ${e.message}"
                    onComplete?.invoke(false)
                }
            }
            override fun onResponse(call: Call, response: Response) {
                val responseBody = response.body?.string()
                viewModelScope.launch {
                    var success = false
                    if (response.isSuccessful && responseBody != null) {
                        try {
                            val json = JSONObject(responseBody)
                            if (json.has("result")) {
                                success = true
                                Timber.i("Successfully closed workday for user ${user.name}. Response: $responseBody")
                            } else if (json.has("error")) {
                                val errorDesc = json.optString("error_description", "API Error")
                                Timber.w("API error closing workday for ${user.name}: $errorDesc. Response: $responseBody")
                                errorMessage = "API (закрытие дня): $errorDesc"
                            } else {
                                Timber.w("Unknown response closing workday for ${user.name}. Code: ${response.code}. Response: $responseBody")
                                errorMessage = "Неизвестный ответ (закрытие дня)."
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error parsing close workday response for ${user.name}. Response: $responseBody")
                            errorMessage = "Ошибка парсинга (закрытие дня)."
                        }
                    } else {
                        Timber.w("Failed to close workday for user ${user.name}. Code: ${response.code}. Response: $responseBody")
                        errorMessage = "Сервер (закрытие дня): ${response.code}"
                    }
                    onComplete?.invoke(success)
                    response.close()
                }
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
                .add("fields[RESPONSIBLE_ID]", user.userId) // Ответственный - текущий пользователь
                .add("fields[CREATED_BY]", "240") // Постановщик - Александр Немирович (ID 240)
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

    // --- Функции для текстовых комментариев ---
    fun prepareForTextComment(task: Task) {
        showAddCommentDialogForTask = task
        commentTextInput = "" // Очищаем поле ввода
        textCommentStatusMessage = null // Сбрасываем предыдущее сообщение
        errorMessage = null // Сбрасываем общую ошибку
    }

    fun dismissAddCommentDialog() {
        showAddCommentDialogForTask = null
        commentTextInput = ""
    }

    fun submitTextComment(taskId: String, commentText: String) {
        dismissAddCommentDialog() // Скрываем диалог
        val user = users[currentUserIndex]
        textCommentStatusMessage = "Отправка комментария..."
        Timber.i("Submitting text comment for task $taskId by user ${user.name}: '$commentText'")

        val url = "${user.webhookUrl}task.commentitem.add"
        val formBody = FormBody.Builder()
            .add("TASK_ID", taskId)
            .add("FIELDS[POST_MESSAGE]", commentText)
            .add("FIELDS[AUTHOR_ID]", user.userId) // Автор комментария - текущий пользователь
            .build()

        val request = Request.Builder().url(url).post(formBody).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                viewModelScope.launch {
                    Timber.e(e, "Failed to submit text comment for task $taskId")
                    textCommentStatusMessage = "Ошибка сети при отправке комментария."
                    delayAndClearTextCommentStatus()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                viewModelScope.launch {
                    val responseBody = response.body?.string()
                    if (response.isSuccessful && responseBody != null) {
                        try {
                            val json = JSONObject(responseBody)
                            if (json.has("result") && json.optInt("result", 0) > 0) {
                                Timber.i("Text comment submitted successfully for task $taskId. Response: $responseBody")
                                textCommentStatusMessage = "Комментарий успешно добавлен."
                                // Можно обновить детали задачи или чек-листы, если комментарии там отображаются
                                // fetchChecklistForTask(taskId) // Например, если комментарии влияют на чек-лист
                                // loadTasks() // Или полный перезапрос задач, если нужно обновить что-то в карточке
                            } else {
                                val errorDesc = json.optString("error_description", "Не удалось добавить комментарий")
                                Timber.w("API error submitting text comment for task $taskId: $errorDesc. Response: $responseBody")
                                textCommentStatusMessage = "Ошибка API: $errorDesc"
                            }
                        } catch (e: Exception) {
                            Timber.e(e, "Error parsing text comment response for task $taskId. Response: $responseBody")
                            textCommentStatusMessage = "Ошибка обработки ответа сервера."
                        }
                    } else {
                        Timber.w("Failed to submit text comment for task $taskId. Code: ${response.code}. Response: $responseBody")
                        textCommentStatusMessage = "Ошибка сервера: ${response.code}"
                    }
                    delayAndClearTextCommentStatus()
                }
            }
        })
    }

    private fun delayAndClearTextCommentStatus(durationMillis: Long = 3500L) {
        viewModelScope.launch {
            delay(durationMillis)
            // Очищаем сообщение, только если оно не было изменено за время задержки
            if (textCommentStatusMessage?.startsWith("Отправка комментария...") == false && // Не "Отправка..."
                textCommentStatusMessage?.contains("успешно добавлен") == true || // "успешно добавлен"
                textCommentStatusMessage?.contains("Ошибка") == true || // или содержит "Ошибка"
                textCommentStatusMessage?.contains("Failed") == true || // или "Failed"
                textCommentStatusMessage?.contains("не удалось") == true) { // или "не удалось"
                 // Это условие немного сложное, но идея в том, чтобы не стирать сообщение "Отправка..."
                 // и стирать только финальные сообщения (успех или ошибка)
            }
            // Простое решение: всегда очищать, если оно не null
            if (textCommentStatusMessage != null && textCommentStatusMessage != "Отправка комментария...") {
                 textCommentStatusMessage = null
            }
        }
    }
    // --- Конец функций для текстовых комментариев ---

    // Удаленные функции связанные с аудио:
    // toggleAudioRecording, startAudioRecording, stopAudioRecordingAndProcess,
    // fetchUserStorageId, makeStorageRequest, uploadFileToStorage, addCommentToTask (с файлом),
    // uploadAudioAndCreateComment, setAudioPermissionDeniedMessage, resetAudioRecordingState.

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

                    // Timber.i("Share logs intent created for URI: $logUri") // Закомментировано, т.к. Timber уже есть выше
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

    // --- Функции для удаления задач ---
    fun requestDeleteTask(task: Task) {
        showDeleteConfirmDialogForTask = task
        deleteTaskStatusMessage = null // Сбрасываем предыдущее сообщение
        errorMessage = null // Сбрасываем общую ошибку
        Timber.d("Requested deletion for task: ${task.title} (ID: ${task.id})")
    }

    fun dismissDeleteTaskDialog() {
        showDeleteConfirmDialogForTask = null
        Timber.d("Delete task dialog dismissed.")
    }

    fun confirmDeleteTask() {
        val taskToDelete = showDeleteConfirmDialogForTask ?: return
        dismissDeleteTaskDialog() // Скрываем диалог сразу

        val user = users[currentUserIndex]
        deleteTaskStatusMessage = "Удаление задачи '${taskToDelete.title}'..."
        Timber.i("Confirming deletion for task ${taskToDelete.id} by user ${user.name}")

        val url = "${user.webhookUrl}tasks.task.delete"
        val formBody = FormBody.Builder()
            .add("taskId", taskToDelete.id)
            .build()

        val request = Request.Builder().url(url).post(formBody).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                viewModelScope.launch {
                    Timber.e(e, "Failed to delete task ${taskToDelete.id}")
                    deleteTaskStatusMessage = "Ошибка сети при удалении задачи."
                    delayAndClearDeleteTaskStatus()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                viewModelScope.launch {
                    val responseBody = response.body?.string()
                    if (response.isSuccessful && responseBody != null) {
                        try {
                            val json = JSONObject(responseBody)
                            // {"result":true,"time":{"start":1717827895.120511,"finish":1717827895.156878,"duration":0.036366939544677734,"processing":0.00007009506225585938,"date_start":"2024-06-08T09:24:55+03:00","date_finish":"2024-06-08T09:24:55+03:00"}}
                            if (json.optBoolean("result", false)) {
                                Timber.i("Task ${taskToDelete.id} deleted successfully. Response: $responseBody")
                                deleteTaskStatusMessage = "Задача '${taskToDelete.title}' успешно удалена."
                                loadTasks() // Перезагружаем список задач
                            } else if (json.has("error")) {
                                val errorDesc = json.optString("error_description", "Не удалось удалить задачу")
                                Timber.w("API error deleting task ${taskToDelete.id}: $errorDesc. Response: $responseBody")
                                deleteTaskStatusMessage = "Ошибка API: $errorDesc"
                            } else {
                                // Иногда API может вернуть {"result": {"task_id": "ID", "success": true}} или просто {"result": null} при успехе
                                // или даже пустой result. Проверяем на отсутствие явной ошибки.
                                val resultObj = json.optJSONObject("result")
                                if (resultObj != null && resultObj.optBoolean("success", false)) {
                                     Timber.i("Task ${taskToDelete.id} deleted successfully (via result.success). Response: $responseBody")
                                     deleteTaskStatusMessage = "Задача '${taskToDelete.title}' успешно удалена."
                                     loadTasks()
                                } else if (resultObj == null && !json.has("error")) {
                                    // Если result null и нет ошибки, считаем успехом (некоторые API так себя ведут)
                                    Timber.i("Task ${taskToDelete.id} likely deleted (result is null, no error). Response: $responseBody")
                                    deleteTaskStatusMessage = "Задача '${taskToDelete.title}' удалена (ответ сервера неоднозначен, но нет ошибки)."
                                    loadTasks()
                                }
                                else {
                                    Timber.w("Failed to delete task ${taskToDelete.id}, unknown response structure. Response: $responseBody")
                                    deleteTaskStatusMessage = "Не удалось удалить задачу: неизвестный ответ сервера."
                                }
                            }
                        } catch (e: JSONException) {
                            Timber.e(e, "Error parsing delete task response (successful HTTP) for ${taskToDelete.id}. Response: $responseBody")
                            deleteTaskStatusMessage = "Ошибка обработки ответа (удаление): ${e.message}"
                        }
                    } else { // HTTP error (e.g., 400, 401, 403, 500)
                        Timber.w("Failed to delete task ${taskToDelete.id}. HTTP Code: ${response.code}. Response: $responseBody")
                        var displayErrorMessage = "Ошибка ${response.code} (удаление задачи)"
                        var jsonParsedSuccessfully = false
                        val currentUserForErrorMessage = user.name // Сохраняем имя пользователя для сообщения

                        if (responseBody != null) {
                            try {
                                val errorJson = JSONObject(responseBody)
                                jsonParsedSuccessfully = true

                                val errorVal = errorJson.optString("error")
                                val errorDescVal = errorJson.optString("error_description")

                                val extractedMessages = mutableListOf<String>()
                                if (errorVal.isNotBlank() && errorVal.lowercase() != "null") {
                                    extractedMessages.add(errorVal)
                                }
                                if (errorDescVal.isNotBlank() && errorDescVal.lowercase() != "null") {
                                    if (extractedMessages.isEmpty() || extractedMessages.last() != errorDescVal) {
                                        extractedMessages.add(errorDescVal)
                                    }
                                }

                                if (extractedMessages.isNotEmpty()) {
                                    val combinedErrorText = extractedMessages.joinToString(" - ")
                                    if (combinedErrorText.contains("Нет доступа", ignoreCase = true) ||
                                        combinedErrorText.contains("permission", ignoreCase = true) || // English check
                                        errorVal.contains("PERMISSIONS", ignoreCase = true) || // Check error type from API
                                        response.code == 403) { // HTTP 403 is explicitly Forbidden
                                        displayErrorMessage = "Нет прав (Ошибка ${response.code}): $combinedErrorText. Убедитесь, что пользователь '${currentUserForErrorMessage}' может удалять эту задачу."
                                    } else {
                                        displayErrorMessage += ": $combinedErrorText"
                                    }
                                } else {
                                    // JSON was valid, but no 'error' or 'error_description' fields found or they were empty/"null".
                                    jsonParsedSuccessfully = false // Treat as if JSON didn't give useful info.
                                }
                            } catch (e: JSONException) {
                                Timber.w(e, "Could not parse JSON from error response body for tasks.task.delete. Body: $responseBody")
                                // jsonParsedSuccessfully remains false
                            }

                            if (!jsonParsedSuccessfully && responseBody.isNotBlank()) {
                                // Append raw response body if JSON parsing failed or yielded no specific error messages,
                                // and the body is short and not HTML.
                                if (responseBody.length < 150 && !responseBody.trimStart().startsWith("<")) {
                                    val cleanedBody = responseBody.replace("\n", " ").replace("\r", "").trim()
                                    displayErrorMessage += ". Ответ: $cleanedBody"
                                }
                            }
                        }
                        // Ensure 403 is specifically handled if not caught by JSON logic above
                        if (response.code == 403 && !displayErrorMessage.startsWith("Нет прав")) {
                             displayErrorMessage = "Нет прав (Ошибка 403). Убедитесь, что пользователь '${currentUserForErrorMessage}' может удалять эту задачу."
                             if (responseBody != null && responseBody.length < 150 && responseBody.isNotBlank() && !jsonParsedSuccessfully && !responseBody.trimStart().startsWith("<")) {
                                 displayErrorMessage += " Ответ: ${responseBody.replace("\n", " ").trim()}"
                             }
                        }
                        deleteTaskStatusMessage = displayErrorMessage
                    }
                    delayAndClearDeleteTaskStatus()
                    response.close()
                }
            }
        })
    }

    private fun delayAndClearDeleteTaskStatus(durationMillis: Long = 3500L) {
        viewModelScope.launch {
            delay(durationMillis)
            if (deleteTaskStatusMessage != null && deleteTaskStatusMessage != "Удаление задачи '${showDeleteConfirmDialogForTask?.title ?: ""}'...") {
                deleteTaskStatusMessage = null
            }
        }
    }
    // --- Конец функций для удаления задач ---
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
    var isSettingsExpanded by remember { mutableStateOf(false) }
    var isQuickTaskDropdownExpanded by remember { mutableStateOf(false) } // Для нового дропдауна быстрых задач
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

            // Кнопка управления рабочим днем
            WorkDayControlButton(viewModel)

            // Блок для быстрых задач (иконки или выпадающий список)
            if (viewModel.quickTaskDisplayMode == MainViewModel.QuickTaskDisplayMode.ICONS) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MainViewModel.StandardTaskType.values().forEach { taskType ->
                        IconButton(
                            onClick = { viewModel.createStandardTask(taskType, context) },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Text(
                                text = taskType.emoji,
                                fontSize = 32.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else { // DROPDOWN mode
                Box {
                    IconButton(
                        onClick = { isQuickTaskDropdownExpanded = true },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Создать быструю задачу",
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = isQuickTaskDropdownExpanded,
                        onDismissRequest = { isQuickTaskDropdownExpanded = false }
                    ) {
                        MainViewModel.StandardTaskType.values().forEach { taskType ->
                            DropdownMenuItem(
                                text = { Text("${taskType.emoji} ${taskType.titlePrefix}") },
                                onClick = {
                                    viewModel.createStandardTask(taskType, context)
                                    isQuickTaskDropdownExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Иконка статуса работы, которая теперь также является кнопкой настроек
            Box {
                // WorkStatusIcon теперь кликабельный и открывает меню
                WorkStatusIcon(
                    workStatus = viewModel.workStatus,
                    modifier = Modifier.clickable { isSettingsExpanded = true }
                )

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
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (viewModel.showCompletedTasks) "✓ " else "   ",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("Показывать завершенные (2 дня)")
                            }
                        },
                        onClick = {
                            viewModel.toggleShowCompletedTasks()
                            isSettingsExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (viewModel.quickTaskDisplayMode == MainViewModel.QuickTaskDisplayMode.DROPDOWN) "✓ " else "   ",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("Быстрые задачи: список")
                            }
                        },
                        onClick = {
                            viewModel.toggleQuickTaskDisplayMode(context)
                            isSettingsExpanded = false
                        }
                    )
                    Divider() // Разделитель
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
        }

        Spacer(modifier = Modifier.height(16.dp)) // Уменьшаем отступ после верхней панели

        // Диалог добавления текстового комментария
        viewModel.showAddCommentDialogForTask?.let { task ->
            AddTextCommentDialog(
                taskTitle = task.title,
                currentComment = viewModel.commentTextInput,
                onCommentChange = { viewModel.commentTextInput = it },
                onConfirm = { comment ->
                    viewModel.submitTextComment(task.id, comment)
                },
                onDismiss = { viewModel.dismissAddCommentDialog() }
            )
        }


        val serviceState = viewModel.timerServiceState // Получаем состояние из ViewModel (TimerServiceState?)

        // Активный таймер (если есть) - переделан в одну строку
        if (serviceState?.activeTaskId != null) {
            val taskTitle = serviceState.activeTaskTitle ?: "Задача..."
            val cardColor = when {
                serviceState.isSystemPaused -> StatusOrange.copy(alpha = 0.8f) // Сделаем чуть прозрачнее для фона строки
                serviceState.isUserPaused -> StatusYellow.copy(alpha = 0.8f)
                else -> StatusBlue.copy(alpha = 0.8f)
            }
            val textColor = if (serviceState.isEffectivelyPaused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary

            // Ищем задачу в списке viewModel.tasks, чтобы получить timeEstimate
            val activeTaskDetails = viewModel.tasks.find { it.id == serviceState.activeTaskId }
            val timeEstimateFormatted = activeTaskDetails?.let {
                val estimateHours = it.timeEstimate / 3600
                val estimateMinutes = (it.timeEstimate % 3600) / 60
                String.format("%d:%02d", estimateHours, estimateMinutes)
            } ?: "--:--"


            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = cardColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp), // Уменьшенные отступы
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Название задачи (сокращенное)
                    Text(
                        text = taskTitle,
                        fontSize = 15.sp, // Чуть меньше для одной строки
                        fontWeight = FontWeight.Bold,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false) // Занимает доступное место, но может сжиматься
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Время (текущее / плановое)
                    Text(
                        text = "${viewModel.formatTime(serviceState.timerSeconds)} / $timeEstimateFormatted",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal, // Обычный шрифт для времени
                        color = textColor,
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Кнопка "Сохранить" с иконкой дискеты
                    IconButton(
                        onClick = { viewModel.stopAndSaveCurrentTimer() },
                        modifier = Modifier.size(40.dp) // Компактный размер для IconButton
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Save,
                            contentDescription = "Сохранить время и остановить",
                            tint = textColor, // Цвет иконки соответствует тексту
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp)) // Уменьшим отступ после карточки
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
        // Сообщения о статусе операций (быстрое создание задачи, статус дня, текстовый комментарий, удаление задачи)
        val taskCreationMessage = viewModel.quickTaskCreationStatus
        val timemanMessage = viewModel.timemanInfoMessage
        val textCommentMessage = viewModel.textCommentStatusMessage
        val deleteTaskMessage = viewModel.deleteTaskStatusMessage

        // Порядок приоритета: удаление, текст. коммент, день, задача
        val generalMessageToDisplay = deleteTaskMessage ?: textCommentMessage ?: timemanMessage ?: taskCreationMessage
        if (generalMessageToDisplay != null) {
            // Определение, является ли сообщение ошибкой
            val isGeneralError = viewModel.errorMessage != null || // Если есть глобальная ошибка
                                 generalMessageToDisplay.contains("Ошибка", ignoreCase = true) ||
                                 generalMessageToDisplay.contains("Failed", ignoreCase = true) ||
                                 generalMessageToDisplay.contains("не удалось", ignoreCase = true) ||
                                 (textCommentMessage != null && !textCommentMessage.contains("успешно", ignoreCase = true) && !textCommentMessage.startsWith("Отправка")) || // Сообщение о комменте не успешное и не "Отправка"
                                 (deleteTaskMessage != null && !deleteTaskMessage.contains("успешно", ignoreCase = true) && !deleteTaskMessage.startsWith("Удаление")) // Сообщение об удалении не успешное и не "Удаление"


            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = if (isGeneralError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer // Используем tertiary для инфо
                )
            ) {
                Text(
                    text = generalMessageToDisplay,
                    modifier = Modifier.padding(16.dp),
                    color = if (isGeneralError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                    textAlign = TextAlign.Center
                )
            }
        }


        // Box to hold LazyColumn and the top fading edge effect
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(), // LazyColumn fills the Box
                // Consider adding contentPadding if items should not start completely under the opaque part of the gradient
                // contentPadding = PaddingValues(top = 12.dp) // e.g., half of gradient height
            ) {
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
                        onAddCommentClick = { viewModel.prepareForTextComment(it) },
                        onLongPress = { viewModel.requestDeleteTask(it) }, // Обработчик долгого нажатия
                        isTimerRunningForThisTask = isTimerRunningForThisTask,
                        isTimerUserPausedForThisTask = isTimerUserPausedForThisTask,
                        isTimerSystemPausedForThisTask = isTimerSystemPausedForThisTask,
                        viewModel = viewModel,
                        context = context
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        } // End of LazyColumn

        // Gradient overlay at the top of the LazyColumn area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp) // Height of the fade effect, adjust as needed
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.background, // Opaque at the top (same as screen background)
                            MaterialTheme.colorScheme.background.copy(alpha = 0.0f) // Transparent at the bottom
                        )
                    )
                )
            // .align(Alignment.TopCenter) // Removed to see if it resolves the alignment error.
                                        // The Box will default to TopStart within its parent Box.
        )

        // Диалог подтверждения удаления задачи
        viewModel.showDeleteConfirmDialogForTask?.let { taskToDelete ->
            DeleteConfirmationDialog(
                taskTitle = taskToDelete.title,
                onConfirm = { viewModel.confirmDeleteTask() },
                onDismiss = { viewModel.dismissDeleteTaskDialog() }
            )
        }

    } // End of Box wrapper for LazyColumn and gradient
} // End of MainScreen's primary Column
// } // End of MainScreen composable - Эта скобка была лишней

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
fun WorkStatusIcon(workStatus: WorkStatus, modifier: Modifier = Modifier) { // Добавляем Modifier
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
        modifier = modifier // Применяем переданный Modifier
            .shadow(elevation = 2.dp, shape = CircleShape) // Добавляем небольшую тень
            .background(color.copy(alpha = 0.2f), CircleShape)
            .padding(10.dp) // Увеличиваем отступ
    )
}

@Composable
fun WorkDayControlButton(viewModel: MainViewModel) {
    val timemanStatus = viewModel.timemanCurrentApiStatus
    val isLoading = viewModel.timemanStatusLoading || viewModel.timemanActionInProgress
    val context = LocalContext.current // Для возможных Toast или других действий

    val buttonText = when (timemanStatus) {
        TimemanApiStatus.OPENED, TimemanApiStatus.PAUSED -> "Завершить день"
        TimemanApiStatus.CLOSED, TimemanApiStatus.UNKNOWN -> "Начать день"
    }
    val buttonIcon = when (timemanStatus) {
        TimemanApiStatus.OPENED, TimemanApiStatus.PAUSED -> Icons.Filled.PowerSettingsNew // Или Stop, EventBusy
        TimemanApiStatus.CLOSED, TimemanApiStatus.UNKNOWN -> Icons.Filled.PlayArrow // Или PowerSettingsNew с другим цветом
    }
    val buttonColors = ButtonDefaults.buttonColors(
        containerColor = when (timemanStatus) {
            TimemanApiStatus.OPENED, TimemanApiStatus.PAUSED -> MaterialTheme.colorScheme.errorContainer
            TimemanApiStatus.CLOSED, TimemanApiStatus.UNKNOWN -> MaterialTheme.colorScheme.primaryContainer
        },
        contentColor = when (timemanStatus) {
            TimemanApiStatus.OPENED, TimemanApiStatus.PAUSED -> MaterialTheme.colorScheme.onErrorContainer
            TimemanApiStatus.CLOSED, TimemanApiStatus.UNKNOWN -> MaterialTheme.colorScheme.onPrimaryContainer
        }
    )

    Button(
        onClick = { viewModel.manualToggleWorkdayStatus() },
        enabled = !isLoading,
        colors = buttonColors,
        modifier = Modifier.height(56.dp) // Сопоставимо с размером аватаров и иконок быстрых задач
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                strokeWidth = 2.dp,
                color = LocalContentColor.current // Цвет индикатора будет соответствовать цвету текста кнопки
            )
        } else {
            Icon(
                imageVector = buttonIcon,
                contentDescription = buttonText,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(buttonText, fontSize = 14.sp)
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskCard(
    task: Task,
    onTimerToggle: (Task) -> Unit,
    onCompleteTask: (Task) -> Unit,
    onAddCommentClick: (Task) -> Unit,
    onLongPress: (Task) -> Unit, // Для запроса на удаление задачи
    isTimerRunningForThisTask: Boolean,
    isTimerUserPausedForThisTask: Boolean,
    isTimerSystemPausedForThisTask: Boolean,
    viewModel: MainViewModel, // Передаем ViewModel для доступа к данным и функциям
    context: Context // Добавляем параметр context
) {
    // Определяем, есть ли у задачи описание и можно ли ее раскрывать
    val hasDescription = task.description.isNotEmpty()
    // Используем состояние из ViewModel для раскрытия карточки, только если есть описание
    val isExpanded = if (hasDescription) viewModel.expandedTaskIds.contains(task.id) else false
    Timber.d("TaskCard for task ${task.id} ('${task.title}'), hasDescription: $hasDescription, attachedFileIds: ${task.attachedFileIds}, isExpanded = $isExpanded")

    // Загрузка чек-листов и деталей файлов при раскрытии карточки (только если она может быть раскрыта и раскрыта)
    LaunchedEffect(task.id, isExpanded, hasDescription) {
        if (isExpanded && hasDescription) { // Добавлено условие hasDescription
            // Загрузка чек-листа
            if (viewModel.checklistsMap[task.id].isNullOrEmpty() && viewModel.loadingChecklistMap[task.id] != true) {
                viewModel.fetchChecklistForTask(task.id)
            }
            // Загрузка деталей прикрепленных файлов
            if (task.attachedFileIds.isNotEmpty() && viewModel.loadingFilesForTaskMap[task.id] != true) {
                val hasFilesToLoadDetailsFor = task.attachedFileIds.any { fileId ->
                    !viewModel.fileDetailsMap.containsKey(fileId)
                }
                Timber.d("TaskCard ${task.id} expanded. Files: attachedFileIds count ${task.attachedFileIds.size}. loadingFilesForTaskMap[${task.id}]? ${viewModel.loadingFilesForTaskMap[task.id]}. Has files to load details for? $hasFilesToLoadDetailsFor")
                if (hasFilesToLoadDetailsFor) {
                    viewModel.fetchFileDetailsForTaskIfNeeded(task)
                }
            }
        }
    }
    val scheme = MaterialTheme.colorScheme // Считываем схему один раз

    // Для combinedClickable
    @OptIn(ExperimentalFoundationApi::class)
    val cardModifier = Modifier
        .fillMaxWidth()
        .combinedClickable(
            onClick = {
                if (hasDescription) { // Клик для раскрытия, только если есть описание
                    viewModel.toggleTaskExpansion(task.id)
                }
            },
            onLongClick = { onLongPress(task) } // Долгое нажатие для удаления
        )


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
        modifier = cardModifier, // Используем новый модификатор с combinedClickable
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
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

                    // Иконка раскрытия, только если есть описание
                    if (hasDescription) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (isExpanded) "Свернуть" else "Развернуть",
                            modifier = Modifier
                                .size(28.dp) // Увеличиваем иконку
                                .padding(start = 8.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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

            // Отображение крайнего срока, если он есть
            task.deadline?.let { deadlineValue ->
                formatDeadline(deadlineValue)?.let { formattedDate ->
                    Spacer(modifier = Modifier.height(4.dp)) // Небольшой отступ перед крайним сроком
                    Text(
                        text = "Крайний срок: $formattedDate",
                        fontSize = 14.sp,
                        color = scheme.onSurfaceVariant, // Используем цвет из схемы
                        // Можно добавить выделение цветом, если срок просрочен или близок
                        // fontWeight = if (isDeadlineSoonOrOverdue) FontWeight.Bold else FontWeight.Normal,
                        // color = if (isDeadlineOverdue) scheme.error else scheme.onSurfaceVariant
                    )
                }
            }


            // Развернутая информация (только если есть описание и карточка раскрыта)
            if (isExpanded && hasDescription) {
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

                // Подробная информация о времени (УДАЛЕНО)
                // Spacer(modifier = Modifier.height(16.dp)) // Увеличиваем отступ (УДАЛЕНО)

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

                // Подзадачи - секция полностью удалена

                // Прикрепленные файлы
                if (isExpanded && task.attachedFileIds.isNotEmpty()) {
                    Timber.d("TaskCard for task ${task.id}: Displaying attached files section. File ID count: ${task.attachedFileIds.size}")
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Прикрепленные файлы:",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val isLoadingFileDetailsForThisTask = viewModel.loadingFilesForTaskMap[task.id] == true

                    task.attachedFileIds.forEach { fileId ->
                        val fileDetail = viewModel.fileDetailsMap[fileId]
                        if (fileDetail != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW).apply {
                                                data = android.net.Uri.parse(fileDetail.downloadUrl)
                                            }
                                            context.startActivity(intent)
                                        } catch (e: Exception) {
                                            Timber.e(e, "Could not open file URL: ${fileDetail.downloadUrl}")
                                        }
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_attachment),
                                    contentDescription = "Файл",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = fileDetail.name,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = fileDetail.formattedSize,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else if (isLoadingFileDetailsForThisTask) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Загрузка деталей файла ID: $fileId...", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        } else {
                             Text("Файл ID: $fileId (детали не загружены)", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 4.dp))
                             Timber.d("TaskCard for task ${task.id}: File ID $fileId details not found in map and not loading.")
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                } else if (isExpanded && task.attachedFileIds.isEmpty()) {
                    Timber.d("TaskCard for task ${task.id}: No attached file IDs to display, though card is expanded.")
                }
            }

            // Spacer(modifier = Modifier.height(16.dp)) // Этот Spacer, кажется, лишний здесь, был между подзадачами и кнопками. Убираем, если он относился к подзадачам.
            // Если отступ нужен перед кнопками действий основной задачи, его можно оставить или добавить здесь.
            // Судя по контексту, он был после блока подзадач, так что его удаление корректно.
            // Если после удаления подзадач нужен дополнительный отступ перед кнопками основной задачи, его можно добавить здесь:
            // if (isExpanded) { Spacer(modifier = Modifier.height(16.dp)) }

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
                val rememberedOnTimerToggle = remember(task) { { onTimerToggle(task) } }

                Button(
                    onClick = rememberedOnTimerToggle,
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
                    val rememberedOnCompleteTask = remember(task) { { onCompleteTask(task) } }
                    Button(
                        onClick = rememberedOnCompleteTask,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp), // Увеличиваем высоту кнопки
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
                        colors = rememberedCompleteButtonColors
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "Завершить", modifier = Modifier.size(28.dp)) // Увеличим иконку, т.к. текст убран
                        // Spacer(modifier = Modifier.width(4.dp)) // Больше не нужен
                        // Text( // Текст "Завершить" удален
                        //     text = "Завершить",
                        //     fontSize = 16.sp
                        // )
                    }
                }

                // Кнопка добавления текстового комментария (если задача не завершена)
                if (!task.isCompleted) {
                    IconButton(
                        onClick = { onAddCommentClick(task) },
                        modifier = Modifier
                            .weight(0.6f) // Дадим ей немного меньше места, чем основным кнопкам
                            .heightIn(min = 52.dp)
                            .shadow(elevation = 2.dp, shape = CircleShape)
                            .background(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = CircleShape
                            )
                            .padding(horizontal = 8.dp),
                        colors = IconButtonDefaults.iconButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddComment,
                            contentDescription = "Добавить комментарий",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTextCommentDialog(
    taskTitle: String,
    currentComment: String,
    onCommentChange: (String) -> Unit,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Комментарий к задаче: $taskTitle") },
        text = {
            OutlinedTextField(
                value = currentComment,
                onValueChange = onCommentChange,
                label = { Text("Текст комментария") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp), // Минимальная высота для удобного ввода
                maxLines = 10 // Ограничение по количеству строк
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (currentComment.isNotBlank()) { // Отправляем только непустые комментарии
                        onConfirm(currentComment)
                    }
                },
                enabled = currentComment.isNotBlank() // Кнопка активна, только если комментарий не пуст
            ) {
                Text("Отправить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}

@Composable
fun DeleteConfirmationDialog(
    taskTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить задачу?") },
        text = { Text("Вы уверены, что хотите удалить задачу \"$taskTitle\"? Это действие нельзя будет отменить.") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Удалить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
