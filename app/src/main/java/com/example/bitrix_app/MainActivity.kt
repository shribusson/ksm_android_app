package com.example.bitrix_app

import android.app.Application
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import androidx.compose.material.icons.filled.Settings // Для иконки настроек
import androidx.compose.material.icons.filled.Share // Для кнопки "Поделиться"
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.work.*
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
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import java.text.SimpleDateFormat // Добавим для formatDeadline
import java.nio.charset.StandardCharsets

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
    val changedDate: String? = null // Добавлено поле для даты изменения
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

// WorkStatus enum удален

data class ChecklistItem(
    val id: String,
    val title: String,
    val isComplete: Boolean
)

// Data class AttachedFile удален, так как функционал файлов вырезан

// Enum AppThemeOptions удален, так как тема будет фиксированной

// ViewModel

// Вспомогательный data class для результата обработки задач в фоновом потоке
data class TaskProcessingOutput(
    val processedTasks: List<Task>,
    val rawTaskCount: Int, // Для определения необходимости fallback
    val processingError: String? = null // Ошибка, возникшая во время обработки
)

// TimemanApiStatus enum удален

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val client = OkHttpClient()

    // Пользователи теперь управляются через MutableState и SharedPreferences
    var users by mutableStateOf<List<User>>(emptyList())
        private set

    var currentUserIndex by mutableStateOf(0)
    var tasks by mutableStateOf<List<Task>>(emptyList())
    // workStatus удален
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

    // Состояния для прикрепленных файлов - УДАЛЕНО
    // var fileDetailsMap by mutableStateOf<Map<String, AttachedFile>>(emptyMap())
    //     private set
    // var loadingFilesForTaskMap by mutableStateOf<Map<String, Boolean>>(emptyMap())
    //     private set

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
    var isLogViewerVisible by mutableStateOf(false)
        private set
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

    // Состояния для управления рабочим днем - УДАЛЕНЫ
    // var timemanCurrentApiStatus by mutableStateOf(TimemanApiStatus.UNKNOWN)
    //     private set
    // var timemanStatusLoading by mutableStateOf(false) // Индикатор загрузки статуса дня
    //     private set
    // var timemanActionInProgress by mutableStateOf(false) // Индикатор выполнения действия (открыть/закрыть день)
    //     private set
    // var timemanInfoMessage by mutableStateOf<String?>(null) // Сообщения о статусе операций с рабочим днем
    //     private set

    // Состояния для диалога подтверждения удаления задачи
    var showDeleteConfirmDialogForTask by mutableStateOf<Task?>(null)
        private set
    var deleteTaskStatusMessage by mutableStateOf<String?>(null)
        private set

    // Состояние для офлайн-синхронизации
    var pendingSyncMessage by mutableStateOf<String?>(null)
        private set

    // --- Состояния для управления пользователями ---
    var showAddUserDialog by mutableStateOf(false)
        private set
    var showRemoveUserDialogFor by mutableStateOf<User?>(null)
        private set
    var newUserName by mutableStateOf("")
    var newUserWebhookUrl by mutableStateOf("")
    var newUserId by mutableStateOf("")
    var newUserAvatar by mutableStateOf("")
    var newUserSupervisorId by mutableStateOf("")


    // --- Управление SharedPreferences ---
    private val sharedPreferencesName = "BitrixAppPrefs"
    private val currentUserIndexKey = "currentUserIndex"
    private val quickTaskDisplayModeKey = "quickTaskDisplayMode"
    private val usersListKey = "usersListKey" // Ключ для сохранения списка пользователей

    private fun saveCurrentUserIndex(context: Context, index: Int) {
        val prefs = context.getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)
        prefs.edit().putInt(currentUserIndexKey, index).apply()
        Timber.d("Saved currentUserIndex: $index")
    }

    private fun loadCurrentUserIndex(context: Context): Int {
        val prefs = context.getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)
        val loadedIndex = prefs.getInt(currentUserIndexKey, 0)
        Timber.d("Loaded currentUserIndex: $loadedIndex")
        return if (users.isNotEmpty() && loadedIndex >= 0 && loadedIndex < users.size) loadedIndex else 0
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

    private fun saveUsers(context: Context, usersToSave: List<User>) {
        val prefs = context.getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)
        val jsonArray = JSONArray()
        usersToSave.forEach { user ->
            val userJson = JSONObject()
            userJson.put("name", user.name)
            userJson.put("webhookUrl", user.webhookUrl)
            userJson.put("userId", user.userId)
            userJson.put("avatar", user.avatar)
            userJson.put("supervisorId", user.supervisorId ?: JSONObject.NULL)
            jsonArray.put(userJson)
        }
        prefs.edit().putString(usersListKey, jsonArray.toString()).apply()
        Timber.d("Saved ${usersToSave.size} users to SharedPreferences.")
    }

    private fun loadUsers(context: Context): List<User> {
        val prefs = context.getSharedPreferences(sharedPreferencesName, Context.MODE_PRIVATE)
        val jsonString = prefs.getString(usersListKey, null)
        val loadedUsers = mutableListOf<User>()

        if (jsonString != null) {
            try {
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val userJson = jsonArray.getJSONObject(i)
                    loadedUsers.add(
                        User(
                            name = userJson.getString("name"),
                            webhookUrl = userJson.getString("webhookUrl"),
                            userId = userJson.getString("userId"),
                            avatar = userJson.getString("avatar"),
                            supervisorId = if (userJson.isNull("supervisorId")) null else userJson.getString("supervisorId")
                        )
                    )
                }
                Timber.d("Loaded ${loadedUsers.size} users from SharedPreferences.")
            } catch (e: JSONException) {
                Timber.e(e, "Failed to parse users from SharedPreferences.")
                // Fallback to default if parsing fails
            }
        }

        if (loadedUsers.isEmpty()) {
            Timber.w("No users in SharedPreferences or parsing failed. Loading default users.")
            // Return default users if nothing is loaded
            return listOf(
                User("Денис Мелков", "https://bitrix.tooksm.kz/rest/320/r8n6popybs2d7fmt/", "320", "ДМ", supervisorId = "253"),
                User("Владислав Малай", "https://bitrix.tooksm.kz/rest/321/cyoaxf04bwt9sbqg/", "321", "ВМ", supervisorId = "253"),
                User("Ким Филби", "https://bitrix.tooksm.kz/rest/253/gh1cy18ml0zn065x/", "253", "КФ", supervisorId = null)
            )
        }
        return loadedUsers
    }
    // --- Конец SharedPreferences ---

    // --- Управление пользователями ---
    fun prepareAddUserDialog() {
        newUserName = ""
        newUserWebhookUrl = ""
        newUserId = ""
        newUserAvatar = ""
        newUserSupervisorId = ""
        showAddUserDialog = true
    }

    fun dismissAddUserDialog() {
        showAddUserDialog = false
    }

    fun addUser(context: Context) {
        if (newUserName.isBlank() || newUserWebhookUrl.isBlank() || newUserId.isBlank() || newUserAvatar.isBlank()) {
            errorMessage = "Все поля, кроме ID руководителя, должны быть заполнены."
            return
        }
        val newUser = User(
            name = newUserName.trim(),
            webhookUrl = newUserWebhookUrl.trim(),
            userId = newUserId.trim(),
            avatar = newUserAvatar.trim(),
            supervisorId = newUserSupervisorId.trim().takeIf { it.isNotBlank() }
        )
        val updatedUsers = users + newUser
        users = updatedUsers
        saveUsers(context, updatedUsers)
        dismissAddUserDialog()
        Timber.i("Added new user: ${newUser.name}")
    }

    fun requestRemoveUser(user: User) {
        if (users.size <= 1) {
            errorMessage = "Нельзя удалить последнего пользователя."
            viewModelScope.launch {
                delay(3000)
                errorMessage = null
            }
            return
        }
        showRemoveUserDialogFor = user
    }

    fun dismissRemoveUserDialog() {
        showRemoveUserDialogFor = null
    }

    fun confirmRemoveUser(context: Context) {
        val userToRemove = showRemoveUserDialogFor ?: return
        val userIndexToRemove = users.indexOf(userToRemove)

        val updatedUsers = users.toMutableList().apply {
            remove(userToRemove)
        }.toList()

        users = updatedUsers
        saveUsers(context, updatedUsers)
        Timber.i("Removed user: ${userToRemove.name}")

        dismissRemoveUserDialog()

        if (currentUserIndex == userIndexToRemove) {
            switchUser(0, context)
        } else if (currentUserIndex > userIndexToRemove) {
            switchUser(currentUserIndex - 1, context)
        }
    }
    // --- Конец управления пользователями ---

    fun forceReloadTasks() {
        if (users.isEmpty()) return
        Timber.i("Force reloading tasks for user: ${users.getOrNull(currentUserIndex)?.name}")
        // Clear local "cache"
        tasks = emptyList()
        expandedTaskIds = emptySet()
        checklistsMap = emptyMap()
        errorMessage = null
        // Trigger reload
        loadTasks()
    }

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
            if (users.isNotEmpty()) {
                val currentUser = users[currentUserIndex]
                service.setCurrentUser(currentUser.userId, currentUser.name)
            }
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
        users = loadUsers(context)
        currentUserIndex = loadCurrentUserIndex(context) // Загружаем сохраненный индекс
        quickTaskDisplayMode = loadQuickTaskDisplayMode(context) // Загружаем режим отображения быстрых задач
        if (users.isNotEmpty()) {
            loadTasks()
            val currentUserForInit = users[currentUserIndex]
            timerService?.setCurrentUser(currentUserForInit.userId, currentUserForInit.name) // Уведомляем сервис, если он уже подключен
        }
        startPeriodicTaskUpdates()
        isInitialized = true
        Timber.d("MainViewModel initialized. Current user: ${users.getOrNull(currentUserIndex)?.name}")
    }
    private var isInitialized = false

    // Функции getCurrentUserTheme и selectTheme удалены

    fun switchUser(index: Int, context: Context) {
        if (index < 0 || index >= users.size) {
            Timber.e("Attempted to switch to invalid user index: $index. Users count: ${users.size}")
            return
        }
        Timber.i("Switching user to index $index: ${users.getOrNull(index)?.name ?: "Unknown"}")
        isLoading = true // Показываем загрузку немедленно
        tasks = emptyList() // Очищаем задачи предыдущего пользователя
        errorMessage = null // Сбрасываем предыдущие ошибки

        saveCurrentUserIndex(context, index) // Сохраняем новый индекс
        currentUserIndex = index
        val switchedUser = users[index]
        timerService?.setCurrentUser(switchedUser.userId, switchedUser.name) // Уведомляем сервис о смене пользователя

        loadTasks() // Загружаем задачи для нового пользователя
    }

    fun loadTasks() {
        if (users.isEmpty()) {
            isLoading = false
            errorMessage = "Нет пользователей для загрузки задач."
            tasks = emptyList()
            return
        }
        val user = users[currentUserIndex]
        Timber.d("loadTasks called for user: ${user.name}")
        isLoading = true
        errorMessage = null

        viewModelScope.launch {
            try {
                val allRawTasks = mutableListOf<Task>()
                var start = 0
                val calendar = Calendar.getInstance()
                calendar.add(Calendar.MONTH, -1) // Получаем дату месяц назад
                val oneMonthAgoDate = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault()).format(calendar.time)

                while (true) {
                    val url = "${user.webhookUrl}tasks.task.list" +
                            "?filter[MEMBER]=${user.userId}" +
                            "&filter[>CHANGED_DATE]=$oneMonthAgoDate" + // Фильтр по дате изменения
                            "&select[]=ID" +
                            "&select[]=TITLE" +
                            "&select[]=DESCRIPTION" +
                            "&select[]=TIME_SPENT_IN_LOGS" +
                            "&select[]=TIME_ESTIMATE" +
                            "&select[]=STATUS" +
                            "&select[]=RESPONSIBLE_ID" +
                            "&select[]=DEADLINE" +
                            "&select[]=CHANGED_DATE" +
                            "&start=$start" // Параметр пагинации

                    Timber.d("Loading tasks page with URL: $url")
                    val request = Request.Builder().url(url).build()

                    val response = withContext(Dispatchers.IO) { client.newCall(request).execute() }

                    if (response.isSuccessful) {
                        val responseText = response.body?.string()
                        if (responseText.isNullOrEmpty()) {
                            Timber.w("Empty response body for page at start=$start")
                            break
                        }
                        Timber.i("Bitrix Response for page at start=$start: $responseText")
                        val json = JSONObject(responseText)

                        if (json.has("error")) {
                            val error = json.getJSONObject("error")
                            val apiErrorMessage = "Ошибка API: ${error.optString("error_description", "Неизвестная ошибка")}"
                            Timber.w("API error in loadTasks (page $start): $apiErrorMessage")
                            errorMessage = apiErrorMessage
                            break
                        }

                        val tasksOnPage = mutableListOf<Task>()
                        if (json.has("result")) {
                            val result = json.get("result")
                            when (result) {
                                is JSONObject -> {
                                    if (result.has("tasks")) {
                                        processTasks(result.get("tasks"), tasksOnPage)
                                    } else {
                                        processTasks(result, tasksOnPage)
                                    }
                                }
                                is JSONArray -> processTasks(result, tasksOnPage)
                            }
                        }
                        allRawTasks.addAll(tasksOnPage)

                        if (json.has("next")) {
                            start = json.getInt("next")
                            Timber.d("Pagination: More tasks available. Next page starts at: $start")
                        } else {
                            Timber.d("Pagination: All pages loaded. Total raw tasks fetched: ${allRawTasks.size}")
                            break // Больше страниц нет
                        }
                    } else {
                        errorMessage = "Ошибка сервера: ${response.code} - ${response.message}"
                        Timber.e("HTTP error in loadTasks: ${response.code} - ${response.message}")
                        break // Прерываем при ошибке
                    }
                }

                // Обработка полного списка задач
                val output = withContext(Dispatchers.Default) {
                    val filteredTasksList = allRawTasks.filter { task ->
                        val keep = if (!task.isCompleted) {
                            true
                        } else {
                            showCompletedTasks
                        }
                        if (!keep) {
                            Timber.d("Task ${task.id} ('${task.title}') with status ${task.status} was filtered out (isCompleted: ${task.isCompleted}, showCompletedTasks: $showCompletedTasks).")
                        }
                        keep
                    }
                    Timber.d("Total raw tasks (last month): ${allRawTasks.size}, Filtered (showCompleted=$showCompletedTasks): ${filteredTasksList.size} for user ${user.name}")

                    val dateParsers = listOf(
                        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault()),
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    )
                    val deadlineDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
                    val simpleDeadlineDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

                    val newSortedTasksList = filteredTasksList.sortedWith(
                        compareBy<Task> { it.isCompleted }
                            .thenBy { task ->
                                task.deadline?.takeIf { it.isNotBlank() }?.let { deadlineStr ->
                                    try { deadlineDateFormat.parse(deadlineStr) } catch (e: Exception) {
                                        try { simpleDeadlineDateFormat.parse(deadlineStr) } catch (e2: Exception) { Date(Long.MAX_VALUE) }
                                    }
                                } ?: Date(Long.MAX_VALUE)
                            }
                            .thenByDescending { task ->
                                task.changedDate?.let { dateStr ->
                                    var parsedDate: Date? = null
                                    for (parser in dateParsers) {
                                        try { parsedDate = parser.parse(dateStr); if (parsedDate != null) break } catch (e: Exception) { /* continue */ }
                                    }
                                    parsedDate
                                }
                            }
                            .thenBy { it.id.toIntOrNull() ?: 0 }
                    )
                    TaskProcessingOutput(newSortedTasksList, allRawTasks.size, null)
                }

                // Обновление UI
                if (output.processingError != null) {
                    errorMessage = output.processingError
                } else {
                    if (!areTaskListsFunctionallyEquivalent(output.processedTasks, tasks)) {
                        Timber.i("Task list for user ${user.name} has changed. Updating UI with ${output.processedTasks.size} tasks.")
                        tasks = output.processedTasks
                    } else {
                        Timber.i("Task list for user ${user.name} has not changed (${output.processedTasks.size} tasks). No UI update for tasks list.")
                    }
                    errorMessage = null

                    if (output.processedTasks.isEmpty() && tasks.isEmpty()) {
                        Timber.w("No displayable tasks for user ${user.name} after filtering. Total raw tasks for last month: ${output.rawTaskCount}")
                        if (output.rawTaskCount > 0) {
                            errorMessage = "Задачи за последний месяц найдены, но отфильтрованы. Попробуйте изменить настройки отображения."
                        } else {
                            errorMessage = "Активные задачи за последний месяц не найдены."
                        }
                    }
                }

            } catch (e: Exception) {
                errorMessage = "Ошибка загрузки задач: ${e.message}"
                Timber.e(e, "Failed to load all tasks with pagination")
            } finally {
                isLoading = false
            }
        }
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
        val timeSpent = taskJson.optInt("timeSpentInLogs",
            taskJson.optInt("TIME_SPENT_IN_LOGS", 0))

        // Логика парсинга UF_TASK_WEBDAV_FILES удалена

        return Task(
            id = taskJson.optString("id", taskJson.optString("ID", fallbackId)),
            title = taskJson.optString("title", taskJson.optString("TITLE", "Задача без названия")),
            description = taskJson.optString("description", taskJson.optString("DESCRIPTION", "")),
            timeSpent = timeSpent,
            timeEstimate = taskJson.optInt("timeEstimate", taskJson.optInt("TIME_ESTIMATE", 7200)),
            status = taskJson.optString("status", taskJson.optString("STATUS", "")),
            deadline = taskJson.optString("deadline", taskJson.optString("DEADLINE", null)),
            changedDate = taskJson.optString("changedDate", taskJson.optString("CHANGED_DATE", null))
            // attachedFileIds удалено
        )
    }

    // Функция для сравнения списков задач
    private fun areTaskListsFunctionallyEquivalent(newList: List<Task>, oldList: List<Task>): Boolean {
        if (newList.size != oldList.size) {
            Timber.d("Task lists differ in size. New: ${newList.size}, Old: ${oldList.size}")
            return false
        }

        val oldTasksMap = oldList.associateBy { it.id }

        for (newTask in newList) {
            val oldTask = oldTasksMap[newTask.id]
            if (oldTask == null) {
                Timber.d("Task lists differ: New task found with ID ${newTask.id}")
                return false
            }
            if (newTask.title != oldTask.title ||
                newTask.status != oldTask.status ||
                newTask.timeSpent != oldTask.timeSpent ||
                newTask.timeEstimate != oldTask.timeEstimate ||
                newTask.changedDate != oldTask.changedDate ||
                newTask.isCompleted != oldTask.isCompleted
            ) {
                Timber.d("Task lists differ: Task with ID ${newTask.id} has changed fields.")
                return false
            }
        }
        return true
    }


    fun fetchChecklistForTask(taskId: String) {
        if (users.isEmpty()) return
        val user = users[currentUserIndex]
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

    // fetchFileDetailsForTaskIfNeeded удален

    fun toggleChecklistItemStatus(taskId: String, checklistItemId: String, currentIsComplete: Boolean) {
        if (users.isEmpty()) return
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
                        Timber.i("Successfully toggled checklist item $checklistItemId for task $taskId. New state: ${!currentIsComplete}. Response: $responseBody")
                    }
                    response.close()
                }
            }
        })
    }


    fun toggleTimer(context: Context, task: Task) {
        if (users.isEmpty()) return
        val service = timerService ?: return
        val currentServiceState = timerServiceState
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
            if (currentServiceState?.activeTaskId != null && currentServiceState.activeTaskId != task.id) {
                Timber.d("Stopping timer for previous task ${currentServiceState.activeTaskId} before starting new one.")
                val previousTask = tasks.find { it.id == currentServiceState.activeTaskId }
                if (previousTask != null) {
                    val secondsToSaveForPrevious = service.stopTaskTimer(currentUser.userId)
                    stopTimerAndSaveTime(context, previousTask, secondsToSaveForPrevious)
                    if (sendComments) {
                        sendTimerComment(previousTask, "Таймер остановлен (переключение на задачу ${task.id})", secondsToSaveForPrevious)
                    }
                } else {
                     service.stopTaskTimer(currentUser.userId)
                }
            }

            Timber.d("Starting timer for task ${task.id} with initial time ${task.timeSpent}")
            service.startTaskTimer(currentUser.userId, currentUser.name, task.id, task.title, task.timeSpent)
            if (sendComments) {
                sendTimerComment(task, "Таймер запущен", task.timeSpent)
            }
            tasks = tasks.sortedWith(
                compareBy<Task> { it.id != task.id }
                    .thenBy { it.isCompleted }
                    .thenByDescending { it.changedDate }
                    .thenBy { it.id.toIntOrNull() ?: 0 }
            )
        }
    }


    // Отправка комментария о состоянии таймера
    private fun sendTimerComment(task: Task, action: String, currentSeconds: Int) {
        if (users.isEmpty()) return
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
    private fun stopTimerAndSaveTime(context: Context, task: Task, secondsToSave: Int) {
        if (users.isEmpty()) return
        val user = users[currentUserIndex]
        
        // Рассчитываем время, прошедшее только за эту сессию
        val elapsedSecondsThisSession = secondsToSave - task.timeSpent

        Timber.i("stopTimerAndSaveTime called for task ${task.id}, user ${user.name}. Total seconds from service: $secondsToSave. Initial task time: ${task.timeSpent}. Elapsed this session: $elapsedSecondsThisSession")

        // Сохраняем только если прошло достаточно времени в этой сессии
        if (elapsedSecondsThisSession < 10) {
            Timber.i("Elapsed time for this session is too short (${elapsedSecondsThisSession}s), not saving to Bitrix for task ${task.id}")
            return
        }

        viewModelScope.launch {
            if (isNetworkAvailable()) {
                // Передаем только дельту времени
                val success = syncTimeDirectly(task, elapsedSecondsThisSession)
                if (!success) {
                    Timber.w("Direct sync failed, falling back to WorkManager for task ${task.id}")
                    enqueueSaveTimeWorker(context, task, elapsedSecondsThisSession)
                }
            } else {
                Timber.i("Network not available. Enqueuing save time worker for task ${task.id}")
                enqueueSaveTimeWorker(context, task, elapsedSecondsThisSession)
            }
        }
    }

    private suspend fun syncTimeDirectly(task: Task, secondsToSave: Int): Boolean = withContext(Dispatchers.IO) {
        val user = users[currentUserIndex]
        val url = "${user.webhookUrl}task.elapseditem.add"
        val formBody = FormBody.Builder()
            .add("taskId", task.id)
            .add("arFields[SECONDS]", secondsToSave.toString())
            .add("arFields[COMMENT_TEXT]", "Работа над задачей (${formatTime(secondsToSave)})")
            .add("arFields[USER_ID]", user.userId)
            .build()
        val request = Request.Builder().url(url).post(formBody).build()

        try {
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val responseText = response.body?.string()
                Timber.d("Save time response for task ${task.id}: $responseText")
                val json = JSONObject(responseText ?: "{}")
                if (json.has("result")) {
                    Timber.i("Time saved successfully for task ${task.id}. Reloading tasks.")
                    withContext(Dispatchers.Main) {
                        viewModelScope.launch {
                            delay(1000)
                            loadTasks()
                        }
                    }
                    return@withContext true
                } else {
                    val errorDesc = json.optString("error_description", "Unknown API error")
                    Timber.w("Error saving time for task ${task.id}: $errorDesc.")
                    withContext(Dispatchers.Main) {
                        errorMessage = "Ошибка сохранения времени: $errorDesc"
                    }
                    return@withContext true // Считается "обработанной", не нужно ставить в очередь.
                }
            } else {
                Timber.w("HTTP error saving time for task ${task.id}: ${response.code}. Fallback possible.")
                return@withContext false
            }
        } catch (e: IOException) {
            Timber.e(e, "Network error saving time for task ${task.id}. Fallback needed.")
            return@withContext false
        }
    }

    private fun enqueueSaveTimeWorker(context: Context, task: Task, seconds: Int) {
        val user = users[currentUserIndex]
        val workData = workDataOf(
            SaveTimeWorker.KEY_TASK_ID to task.id,
            SaveTimeWorker.KEY_SECONDS to seconds,
            SaveTimeWorker.KEY_USER_ID to user.userId,
            SaveTimeWorker.KEY_WEBHOOK_URL to user.webhookUrl,
            SaveTimeWorker.KEY_USER_NAME to user.name
        )

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val saveTimeWorkRequest = OneTimeWorkRequestBuilder<SaveTimeWorker>()
            .setInputData(workData)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .addTag("sync-time-${task.id}")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "saveTime_${task.id}_${System.currentTimeMillis()}",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            saveTimeWorkRequest
        )

        pendingSyncMessage = "Нет сети. Время для задачи '${task.title}' будет сохранено позже."
        viewModelScope.launch {
            delay(5000)
            if (pendingSyncMessage?.contains(task.title) == true) {
                pendingSyncMessage = null
            }
        }
        Timber.i("Enqueued SaveTimeWorker for task ${task.id}.")
    }

    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = connectivityManager.activeNetwork ?: return false
            val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
            return when {
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
                activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
                else -> false
            }
        } else {
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo ?: return false
            @Suppress("DEPRECATION")
            return networkInfo.isConnected
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

    fun completeTask(context: Context, task: Task) {
        if (users.isEmpty()) return
        val service = timerService ?: return
        val currentServiceState = timerServiceState
        val user = users[currentUserIndex]
        Timber.i("Complete task called for task ${task.id} by user ${user.name}. Service state: $currentServiceState")

        var secondsToSave = 0
        var timerWasActiveOrPausedForThisTask = false

        if (currentServiceState?.activeTaskId == task.id) {
            timerWasActiveOrPausedForThisTask = true
            secondsToSave = service.stopTaskTimer(user.userId)
            Timber.d("Task ${task.id} timer was active/paused. Stopped in service. Seconds from service: $secondsToSave")
        }

        if (timerWasActiveOrPausedForThisTask && secondsToSave > 0) {
            stopTimerAndSaveTime(context, task, secondsToSave)
            if (sendComments) {
                sendTimerComment(task, "Задача завершена, таймер остановлен", secondsToSave)
            }
            viewModelScope.launch {
                delay(1500)
                completeTaskInBitrixInternal(task)
            }
        } else {
            Timber.d("Task ${task.id} timer was not active for it or had 0 seconds. Completing directly in Bitrix.")
            completeTaskInBitrixInternal(task)
        }
    }

    private fun completeTaskInBitrixInternal(task: Task) {
        if (users.isEmpty()) return
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
        loadTasks()
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

    // updateWorkStatus удален

    // --- Timeman API Calls - УДАЛЕНЫ ---

    // startPeriodicUpdates удален

    private fun startPeriodicTaskUpdates() {
        viewModelScope.launch {
            while (true) {
                delay(300000)
                if (users.isNotEmpty()) {
                    loadTasks()
                }
            }
        }
    }

    fun createStandardTask(taskType: StandardTaskType, context: Context) {
        if (users.isEmpty()) return
        viewModelScope.launch {
            quickTaskCreationStatus = "Создание задачи '${taskType.titlePrefix}'..."
            errorMessage = null
            val user = users[currentUserIndex]
            val taskTitle = "${taskType.titlePrefix} - ${user.name}"

            val url = "${user.webhookUrl}tasks.task.add"
            val formBodyBuilder = FormBody.Builder()
                .add("fields[TITLE]", taskTitle)
                .add("fields[RESPONSIBLE_ID]", user.userId)
                .add("fields[CREATED_BY]", "240")
                .add("fields[DESCRIPTION]", "Стандартная задача, создана автоматически из приложения.")
                .add("fields[PRIORITY]", taskType.defaultPriority)

            val request = Request.Builder().url(url).post(formBodyBuilder.build()).build()
            Timber.d("Creating standard task: ${taskType.titlePrefix} for user ${user.name}. URL: $url, Title: $taskTitle")

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    viewModelScope.launch {
                        quickTaskCreationStatus = "Ошибка создания задачи: ${e.message}"
                        Timber.e(e, "Network error while creating standard task '${taskType.titlePrefix}'")
                        delay(3500)
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
                                    val createdTaskId = createdTaskJson.optString("id", "N/A")
                                    quickTaskCreationStatus = "Задача '${taskType.titlePrefix}' (ID: $createdTaskId) создана! Запускаем таймер..."
                                    Timber.i("Standard task '${taskType.titlePrefix}' (ID: $createdTaskId) created successfully. Response: $responseText")

                                    val newlyCreatedTask = createTaskFromJson(createdTaskJson, createdTaskId)
                                    toggleTimer(context, newlyCreatedTask)

                                    delay(1500)
                                    loadTasks()
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
                        delay(3500)
                        quickTaskCreationStatus = null
                    }
                }
            })
        }
    }

    fun stopAndSaveCurrentTimer(context: Context) {
        if (users.isEmpty()) return
        val service = timerService ?: return
        val currentServiceState = timerServiceState ?: return
        val activeTaskId = currentServiceState.activeTaskId ?: return
        val currentUser = users[currentUserIndex]

        Timber.i("stopAndSaveCurrentTimer called for task ID $activeTaskId by user ${currentUser.name}")

        val task = tasks.find { it.id == activeTaskId }
        if (task == null) {
            Timber.w("Task with ID $activeTaskId not found in ViewModel's list. Cannot save time.")
            service.stopTaskTimer(currentUser.userId)
            errorMessage = "Активная задача не найдена, таймер остановлен."
            return
        }

        val secondsToSave = service.stopTaskTimer(currentUser.userId)
        Timber.d("Timer stopped for task ${task.id} via stopAndSaveCurrentTimer. Seconds from service: $secondsToSave")

        if (secondsToSave > 0) {
            stopTimerAndSaveTime(context, task, secondsToSave)
            if (sendComments) {
                sendTimerComment(task, "Таймер остановлен, время учтено", secondsToSave)
            }
        } else {
            Timber.i("Timer for task ${task.id} had 0 seconds or less. Not saving time or sending comment.")
        }
    }

    fun getCurrentUser() = if (users.isNotEmpty()) users[currentUserIndex] else null

    // --- Функции для текстовых комментариев ---
    fun prepareForTextComment(task: Task) {
        showAddCommentDialogForTask = task
        commentTextInput = ""
        textCommentStatusMessage = null
        errorMessage = null
    }

    fun dismissAddCommentDialog() {
        showAddCommentDialogForTask = null
        commentTextInput = ""
    }

    fun submitTextComment(taskId: String, commentText: String) {
        if (users.isEmpty()) return
        dismissAddCommentDialog()
        val user = users[currentUserIndex]
        textCommentStatusMessage = "Отправка комментария..."
        Timber.i("Submitting text comment for task $taskId by user ${user.name}: '$commentText'")

        val url = "${user.webhookUrl}task.commentitem.add"
        val formBody = FormBody.Builder()
            .add("TASK_ID", taskId)
            .add("FIELDS[POST_MESSAGE]", commentText)
            .add("FIELDS[AUTHOR_ID]", user.userId)
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
            if (textCommentStatusMessage != null && textCommentStatusMessage != "Отправка комментария...") {
                 textCommentStatusMessage = null
            }
        }
    }
    // --- Конец функций для текстовых комментариев ---

    // --- Функции для просмотра логов ---
    fun showLogViewer(context: Context) {
        loadLogContent(context)
        isLogViewerVisible = true
    }

    fun hideLogViewer() {
        isLogViewerVisible = false
    }

    fun loadLogContent(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val logFile = FileLoggingTree.getLogFile(context)
                if (logFile.exists()) {
                    val lines = logFile.readLines().reversed()
                    withContext(Dispatchers.Main) {
                        logLines = lines
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        logLines = listOf("Файл логов не найден.")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to read log file.")
                withContext(Dispatchers.Main) {
                    logLines = listOf("Ошибка чтения файла логов: ${e.message}")
                }
            }
        }
    }

    fun shareLogs(context: Context) {
        viewModelScope.launch {
            val logFile = FileLoggingTree.getLogFile(context)
            if (logFile.exists()) {
                try {
                    val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", logFile)
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_STREAM, contentUri)
                        type = "text/plain"
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = Intent.createChooser(shareIntent, "Поделиться логами через...")
                    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                } catch (e: Exception) {
                    Timber.e(e, "Error sharing log file")
                    errorMessage = "Не удалось поделиться файлом логов: ${e.message}"
                }
            } else {
                errorMessage = "Файл логов не найден."
            }
        }
    }
    // --- Конец функций для просмотра логов ---

    // --- Функции для удаления задач ---
    fun requestDeleteTask(task: Task) {
        showDeleteConfirmDialogForTask = task
        deleteTaskStatusMessage = null
        errorMessage = null
        Timber.d("Requested deletion for task: ${task.title} (ID: ${task.id})")
    }

    fun dismissDeleteTaskDialog() {
        showDeleteConfirmDialogForTask = null
        Timber.d("Delete task dialog dismissed.")
    }

    fun confirmDeleteTask() {
        if (users.isEmpty()) return
        val taskToDelete = showDeleteConfirmDialogForTask ?: return
        dismissDeleteTaskDialog()

        val user = users[currentUserIndex]
        deleteTaskStatusMessage = "Удаление задачи '${taskToDelete.title}'..."
        Timber.i("Confirming deletion for task ${taskToDelete.id} by user ${user.name}")

        val url = "${user.webhookUrl}tasks.task.delete?taskId=${taskToDelete.id}"
        val formBody = FormBody.Builder().build()

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
                            if (json.optBoolean("result", false)) {
                                Timber.i("Task ${taskToDelete.id} deleted successfully. Response: $responseBody")
                                deleteTaskStatusMessage = "Задача '${taskToDelete.title}' успешно удалена."
                                loadTasks()
                            } else if (json.has("error")) {
                                val errorDesc = json.optString("error_description", "Не удалось удалить задачу")
                                Timber.w("API error deleting task ${taskToDelete.id}: $errorDesc. Response: $responseBody")
                                deleteTaskStatusMessage = "Ошибка API: $errorDesc"
                            } else {
                                val resultObj = json.optJSONObject("result")
                                if (resultObj != null) {
                                    if (resultObj.optBoolean("success", false)) {
                                        Timber.i("Task ${taskToDelete.id} deleted successfully (via result.success). Response: $responseBody")
                                        deleteTaskStatusMessage = "Задача '${taskToDelete.title}' успешно удалена."
                                        loadTasks()
                                    } else if (resultObj.optBoolean("task", false)) {
                                        Timber.i("Task ${taskToDelete.id} deleted successfully (via result.task). Response: $responseBody")
                                        deleteTaskStatusMessage = "Задача '${taskToDelete.title}' успешно удалена."
                                        loadTasks()
                                    } else {
                                        Timber.w("Failed to delete task ${taskToDelete.id}, result object present but no known success field. Response: $responseBody")
                                        deleteTaskStatusMessage = "Не удалось удалить задачу: неизвестный формат ответа в 'result'."
                                    }
                                } else if (json.optBoolean("result", false)) {
                                     Timber.i("Task ${taskToDelete.id} deleted successfully (via top-level result:true). Response: $responseBody")
                                     deleteTaskStatusMessage = "Задача '${taskToDelete.title}' успешно удалена."
                                     loadTasks()
                                } else if (!json.has("error")) {
                                    Timber.i("Task ${taskToDelete.id} likely deleted (result is not a known success structure, but no error field). Response: $responseBody")
                                    deleteTaskStatusMessage = "Задача '${taskToDelete.title}' удалена (ответ сервера неоднозначен, но нет ошибки)."
                                    loadTasks()
                                } else {
                                    Timber.w("Failed to delete task ${taskToDelete.id}, unknown response structure. Response: $responseBody")
                                    deleteTaskStatusMessage = "Не удалось удалить задачу: неизвестный ответ сервера."
                                }
                            }
                        } catch (e: JSONException) {
                            Timber.e(e, "Error parsing delete task response (successful HTTP) for ${taskToDelete.id}. Response: $responseBody")
                            deleteTaskStatusMessage = "Ошибка обработки ответа (удаление): ${e.message}"
                        }
                    } else {
                        Timber.w("Failed to delete task ${taskToDelete.id}. HTTP Code: ${response.code}. Response: $responseBody")
                        var displayErrorMessage = "Ошибка ${response.code} (удаление задачи)"
                        var jsonParsedSuccessfully = false
                        val currentUserForErrorMessage = user.name

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
                                        combinedErrorText.contains("permission", ignoreCase = true) ||
                                        errorVal.contains("PERMISSIONS", ignoreCase = true) ||
                                        response.code == 403) {
                                        displayErrorMessage = "Нет прав (Ошибка ${response.code}): $combinedErrorText. Убедитесь, что пользователь '${currentUserForErrorMessage}' может удалять эту задачу."
                                    } else {
                                        displayErrorMessage += ": $combinedErrorText"
                                    }
                                } else {
                                    jsonParsedSuccessfully = false
                                }
                            } catch (e: JSONException) {
                                Timber.w(e, "Could not parse JSON from error response body for tasks.task.delete. Body: $responseBody")
                            }

                            if (!jsonParsedSuccessfully && responseBody.isNotBlank()) {
                                if (responseBody.length < 150 && !responseBody.trimStart().startsWith("<")) {
                                    val cleanedBody = responseBody.replace("\n", " ").replace("\r", "").trim()
                                    displayErrorMessage += ". Ответ: $cleanedBody"
                                }
                            }
                        }
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

        // Инициализация Timber для логирования в LogCat и в файл
        if (Timber.treeCount == 0) {
            Timber.plant(Timber.DebugTree())
            Timber.plant(FileLoggingTree(applicationContext))
            Timber.i("MainActivity onCreate: Timber DebugTree and FileLoggingTree planted.")
        } else {
            Timber.i("MainActivity onCreate: Timber already planted.")
        }

        setContent {
            val viewModel: MainViewModel = viewModel()
            LaunchedEffect(Unit) {
                viewModel.initViewModel(applicationContext)
            }

            val notificationPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
                onResult = { isGranted ->
                    if (isGranted) {
                        Timber.i("Notification permission granted.")
                        startTimerService()
                    } else {
                        Timber.w("Notification permission denied.")
                        startTimerService()
                    }
                }
            )

            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) ==
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        Timber.d("Notification permission already granted for Android 13+.")
                        startTimerService()
                    } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                        Timber.d("Showing rationale for notification permission.")
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    else {
                        Timber.d("Requesting notification permission for Android 13+.")
                        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                } else {
                    Timber.d("No need to request notification permission (SDK < 33).")
                    startTimerService()
                }
            }

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

            DisposableEffect(Unit) {
                Timber.d("MainActivity DisposableEffect: Binding to TimerService.")
                Intent(this@MainActivity, TimerService::class.java).also { intent ->
                    bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
                }
                onDispose {
                    Timber.d("MainActivity DisposableEffect: Unbinding from TimerService.")
                    try {
                         unbindService(serviceConnection)
                         viewModel.connectToTimerService(null)
                    } catch (e: IllegalArgumentException) {
                        Timber.w(e, "Error unbinding service. Already unbound or not bound?")
                    }
                }
            }

            Bitrix_appTheme {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    private fun startTimerService() {
        Timber.d("Attempting to start TimerService (Foreground).")
        val serviceIntent = Intent(this, TimerService::class.java).apply {
            action = TimerService.ACTION_START_FOREGROUND_SERVICE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.i("MainActivity onDestroy")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(viewModel: MainViewModel) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Просмотр логов") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.hideLogViewer() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadLogContent(context) }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Обновить")
                    }
                    IconButton(onClick = { viewModel.shareLogs(context) }) {
                        Icon(Icons.Filled.Share, contentDescription = "Поделиться")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 8.dp)
        ) {
            items(viewModel.logLines) { line ->
                Text(
                    text = line,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
                Divider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    var isSettingsExpanded by remember { mutableStateOf(false) }
    var isQuickTaskDropdownExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (viewModel.isLogViewerVisible) {
        LogViewerScreen(viewModel = viewModel)
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    viewModel.users.forEachIndexed { index, user ->
                        val isSelected = index == viewModel.currentUserIndex
                        val avatarSize = if (isSelected) 56 else 48
                        val elevation = if (isSelected) 6.dp else 2.dp
                        Box(
                            modifier = Modifier
                                .size(avatarSize.dp)
                                .shadow(elevation = elevation, shape = CircleShape, clip = false)
                                .clip(CircleShape)
                                .combinedClickable(
                                    onClick = {
                                        if (!isSelected) {
                                            viewModel.switchUser(index, context)
                                        }
                                    },
                                    onLongClick = {
                                        viewModel.requestRemoveUser(user)
                                    }
                                )
                                .padding(if (isSelected) 2.dp else 0.dp)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent,
                                    CircleShape
                                )

                        ) {
                            UserAvatar(user = user, size = avatarSize - (if (isSelected) 4 else 0))
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (viewModel.quickTaskDisplayMode == MainViewModel.QuickTaskDisplayMode.ICONS) {
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
                    } else {
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

                    Box {
                        IconButton(
                            onClick = { isSettingsExpanded = true },
                            modifier = Modifier.size(56.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Настройки",
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = isSettingsExpanded,
                            onDismissRequest = { isSettingsExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = if (viewModel.showCompletedTasks) "✓ " else "   ",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text("Показывать завершенные")
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
                            Divider()
                            DropdownMenuItem(
                                text = { Text("Очистить кэш и перезагрузить") },
                                onClick = {
                                    viewModel.forceReloadTasks()
                                    isSettingsExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Посмотреть логи") },
                                onClick = {
                                    viewModel.showLogViewer(context)
                                    isSettingsExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Добавить пользователя") },
                                onClick = {
                                    viewModel.prepareAddUserDialog()
                                    isSettingsExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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


            val serviceState = viewModel.timerServiceState

            if (serviceState?.activeTaskId != null) {
                val taskTitle = serviceState.activeTaskTitle ?: "Задача..."
                val cardColor = when {
                    serviceState.isUserPaused -> StatusYellow.copy(alpha = 0.8f)
                    else -> StatusBlue.copy(alpha = 0.8f)
                }
                val textColor = if (serviceState.isEffectivelyPaused) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary

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
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = taskTitle,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Text(
                            text = "${viewModel.formatTime(serviceState.timerSeconds)} / $timeEstimateFormatted",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = textColor,
                            maxLines = 1
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = { viewModel.stopAndSaveCurrentTimer(context) },
                            modifier = Modifier.size(40.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Save,
                                contentDescription = "Сохранить время и остановить",
                                tint = textColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (viewModel.isLoading) {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            viewModel.errorMessage?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            val taskCreationMessage = viewModel.quickTaskCreationStatus
            val textCommentMessage = viewModel.textCommentStatusMessage
            val deleteTaskMessage = viewModel.deleteTaskStatusMessage
            val pendingSyncMessage = viewModel.pendingSyncMessage

            val generalMessageToDisplay = pendingSyncMessage ?: deleteTaskMessage ?: textCommentMessage ?: taskCreationMessage
            if (generalMessageToDisplay != null) {
                val isGeneralError = viewModel.errorMessage != null ||
                                     generalMessageToDisplay.contains("Ошибка", ignoreCase = true) ||
                                     generalMessageToDisplay.contains("Failed", ignoreCase = true) ||
                                     generalMessageToDisplay.contains("не удалось", ignoreCase = true) ||
                                     (textCommentMessage != null && !textCommentMessage.contains("успешно", ignoreCase = true) && !textCommentMessage.startsWith("Отправка")) ||
                                     (deleteTaskMessage != null && !deleteTaskMessage.contains("успешно", ignoreCase = true) && !deleteTaskMessage.startsWith("Удаление"))


                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = if (isGeneralError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
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


            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(viewModel.tasks, key = { task -> task.id }) { task ->
                        val sState = viewModel.timerServiceState
                        val isTimerRunningForThisTask = sState?.activeTaskId == task.id && sState.isEffectivelyPaused == false
                        val isTimerUserPausedForThisTask = sState?.activeTaskId == task.id && sState.isUserPaused == true

                        TaskCard(
                            task = task,
                            onTimerToggle = { viewModel.toggleTimer(context, it) },
                            onCompleteTask = { viewModel.completeTask(context, it) },
                            onAddCommentClick = { viewModel.prepareForTextComment(it) },
                            onLongPress = { viewModel.requestDeleteTask(it) },
                            isTimerRunningForThisTask = isTimerRunningForThisTask,
                            isTimerUserPausedForThisTask = isTimerUserPausedForThisTask,
                            viewModel = viewModel,
                            context = context
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.background,
                                MaterialTheme.colorScheme.background.copy(alpha = 0.0f)
                            )
                        )
                    )
            )

            viewModel.showDeleteConfirmDialogForTask?.let { taskToDelete ->
                DeleteConfirmationDialog(
                    taskTitle = taskToDelete.title,
                    onConfirm = { viewModel.confirmDeleteTask() },
                    onDismiss = { viewModel.dismissDeleteTaskDialog() }
                )
            }

            if (viewModel.showAddUserDialog) {
                AddUserDialog(
                    viewModel = viewModel,
                    onConfirm = { viewModel.addUser(context) },
                    onDismiss = { viewModel.dismissAddUserDialog() }
                )
            }

            viewModel.showRemoveUserDialogFor?.let { userToRemove ->
                RemoveUserConfirmationDialog(
                    user = userToRemove,
                    onConfirm = { viewModel.confirmRemoveUser(context) },
                    onDismiss = { viewModel.dismissRemoveUserDialog() }
                )
            }

        }
    }
}

@Composable
private fun RenderUserAvatar(user: User, size: Int) {
    UserAvatar(user = user, size = size)
}

@Composable
fun UserAvatar(user: User, size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .shadow(elevation = 4.dp, shape = CircleShape)
            .clip(CircleShape)
            .background(AvatarBackground),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = user.avatar,
            fontSize = (size * 0.45).sp,
            fontWeight = FontWeight.Bold,
            color = LightOnPrimary,
            textAlign = TextAlign.Center
        )
    }
}

// WorkStatusIcon удален
// WorkDayControlButton удален

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskCard(
    task: Task,
    onTimerToggle: (Task) -> Unit,
    onCompleteTask: (Task) -> Unit,
    onAddCommentClick: (Task) -> Unit,
    onLongPress: (Task) -> Unit,
    isTimerRunningForThisTask: Boolean,
    isTimerUserPausedForThisTask: Boolean,
    viewModel: MainViewModel,
    context: Context
) {
    val hasDescription = task.description.isNotEmpty()
    val isExpanded = if (hasDescription) viewModel.expandedTaskIds.contains(task.id) else false
    Timber.d("TaskCard for task ${task.id} ('${task.title}'), hasDescription: $hasDescription, isExpanded = $isExpanded")

    LaunchedEffect(task.id, isExpanded, hasDescription) {
        if (isExpanded && hasDescription) {
            if (viewModel.checklistsMap[task.id].isNullOrEmpty() && viewModel.loadingChecklistMap[task.id] != true) {
                viewModel.fetchChecklistForTask(task.id)
            }
            // Логика загрузки деталей файлов удалена
        }
    }
    val scheme = MaterialTheme.colorScheme

    @OptIn(ExperimentalFoundationApi::class)
    val cardModifier = Modifier
        .fillMaxWidth()
        .combinedClickable(
            onClick = {
                if (hasDescription) {
                    viewModel.toggleTaskExpansion(task.id)
                }
            },
            onLongClick = { onLongPress(task) }
        )


    val cardContainerColor = remember(
        task.isCompleted,
        isTimerRunningForThisTask,
        isTimerUserPausedForThisTask,
        task.isOverdue,
        scheme.surfaceVariant,
        StatusGreen, StatusBlue, StatusYellow, StatusRed
    ) {
        when {
            task.isCompleted -> StatusGreen
            isTimerRunningForThisTask -> StatusBlue
            isTimerUserPausedForThisTask -> StatusYellow
            task.isOverdue -> StatusRed
            else -> scheme.surfaceVariant
        }
    }

    Card(
        modifier = cardModifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = cardContainerColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
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
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = if (isExpanded) Int.MAX_VALUE else 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (hasDescription) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (isExpanded) "Свернуть" else "Развернуть",
                            modifier = Modifier
                                .size(28.dp)
                                .padding(start = 8.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = progressIndicatorColor,
                trackColor = scheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "Время: ${task.formattedTime}",
                    fontSize = 14.sp,
                    color = scheme.onSurfaceVariant
                )
                val progressTextColor = remember(task.isOverdue, scheme.error, scheme.onSurfaceVariant) {
                    if (task.isOverdue) scheme.error else scheme.onSurfaceVariant
                }
                Text(
                    text = "${task.progressPercent}%",
                    fontSize = 14.sp,
                    color = progressTextColor
                )
            }

            task.deadline?.let { deadlineValue ->
                formatDeadline(deadlineValue)?.let { formattedDate ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Крайний срок: $formattedDate",
                        fontSize = 14.sp,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }


            if (isExpanded && hasDescription) {
                Spacer(modifier = Modifier.height(16.dp))
                Divider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (task.description.isNotEmpty()) {
                    Text(
                        text = "Описание:",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = task.description,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }

                val checklist = viewModel.checklistsMap[task.id]
                if (!checklist.isNullOrEmpty() && checklist.any { !it.isComplete }) {
                    Text(
                        text = "Чек-лист:",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    checklist.forEach { item ->
                        val onToggleItem = remember(task.id, item.id, item.isComplete) {
                            { viewModel.toggleChecklistItemStatus(task.id, item.id, item.isComplete) }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggleItem() }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = item.isComplete,
                                onCheckedChange = { _ -> onToggleItem() },
                                enabled = true,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = scheme.primary,
                                    uncheckedColor = scheme.onSurfaceVariant
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val checklistItemColor = remember(item.isComplete, scheme.onSurfaceVariant, scheme.onSurface) {
                                if (item.isComplete) scheme.onSurfaceVariant else scheme.onSurface
                            }
                            Text(
                                text = item.title,
                                fontSize = 16.sp,
                                color = checklistItemColor
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Секция прикрепленных файлов удалена
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val sErrorTimer = scheme.error
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
                        else -> sPrimaryTimer
                    },
                    contentColor = when {
                        isTimerRunningForThisTask -> sOnErrorTimer
                        isTimerUserPausedForThisTask -> sOnTertiaryTimer
                        else -> sOnPrimaryTimer
                    },
                    disabledContainerColor = sOnSurfaceTimer.copy(alpha = 0.12f),
                    disabledContentColor = sOnSurfaceTimer.copy(alpha = 0.38f)
                )
                val rememberedOnTimerToggle = remember(task) { { onTimerToggle(task) } }

                Button(
                    onClick = rememberedOnTimerToggle,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
                    colors = timerButtonColors
                ) {
                    val iconVector = when {
                        isTimerRunningForThisTask -> Icons.Filled.Pause
                        isTimerUserPausedForThisTask -> Icons.Filled.PlayArrow
                        else -> Icons.Filled.PlayArrow
                    }
                    val contentDescription = when {
                        isTimerRunningForThisTask -> "Приостановить таймер"
                        isTimerUserPausedForThisTask -> "Продолжить таймер"
                        else -> "Запустить таймер"
                    }
                    Icon(
                        imageVector = iconVector,
                        contentDescription = contentDescription,
                        modifier = Modifier.size(28.dp)
                    )
                }

                if (!task.isCompleted) {
                    val sOnPrimaryComplete = scheme.onPrimary
                    val rememberedCompleteButtonColors = ButtonDefaults.elevatedButtonColors(
                        containerColor = ProgressBarGreen,
                        contentColor = sOnPrimaryComplete
                    )
                    val rememberedOnCompleteTask = remember(task) { { onCompleteTask(task) } }
                    Button(
                        onClick = rememberedOnCompleteTask,
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
                        colors = rememberedCompleteButtonColors
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "Завершить", modifier = Modifier.size(28.dp))
                    }
                }

                if (!task.isCompleted) {
                    IconButton(
                        onClick = { onAddCommentClick(task) },
                        modifier = Modifier
                            .weight(0.6f)
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
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                maxLines = 10
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    if (currentComment.isNotBlank()) {
                        onConfirm(currentComment)
                    }
                },
                enabled = currentComment.isNotBlank()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddUserDialog(
    viewModel: MainViewModel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isConfirmEnabled = viewModel.newUserName.isNotBlank() &&
                           viewModel.newUserWebhookUrl.isNotBlank() &&
                           viewModel.newUserId.isNotBlank() &&
                           viewModel.newUserAvatar.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить нового пользователя") },
        text = {
            LazyColumn {
                item {
                    OutlinedTextField(
                        value = viewModel.newUserName,
                        onValueChange = { viewModel.newUserName = it },
                        label = { Text("Имя пользователя") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = viewModel.newUserWebhookUrl,
                        onValueChange = { viewModel.newUserWebhookUrl = it },
                        label = { Text("URL вебхука") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = viewModel.newUserId,
                        onValueChange = { viewModel.newUserId = it },
                        label = { Text("ID пользователя") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = viewModel.newUserAvatar,
                        onValueChange = { viewModel.newUserAvatar = it },
                        label = { Text("Инициалы для аватара") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = viewModel.newUserSupervisorId,
                        onValueChange = { viewModel.newUserSupervisorId = it },
                        label = { Text("ID руководителя (необязательно)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = isConfirmEnabled
            ) {
                Text("Добавить")
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
fun RemoveUserConfirmationDialog(
    user: User,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Удалить пользователя?") },
        text = { Text("Вы уверены, что хотите удалить пользователя \"${user.name}\"? Это действие нельзя будет отменить.") },
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
