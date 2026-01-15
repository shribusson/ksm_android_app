package com.example.bitrix_app.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bitrix_app.data.local.preferences.EncryptedPreferences
import com.example.bitrix_app.domain.model.ChecklistItem
import com.example.bitrix_app.domain.model.Task
import com.example.bitrix_app.domain.model.User
import com.example.bitrix_app.domain.repository.UserRepository
import com.example.bitrix_app.domain.usecase.CompleteTaskUseCase
import com.example.bitrix_app.domain.usecase.CreateTaskUseCase
import com.example.bitrix_app.domain.usecase.DeleteTaskUseCase
import com.example.bitrix_app.domain.usecase.FetchChecklistUseCase
import com.example.bitrix_app.domain.usecase.GetGroupsUseCase
import com.example.bitrix_app.domain.usecase.GetTasksUseCase
import com.example.bitrix_app.domain.usecase.SyncTasksUseCase
import com.example.bitrix_app.domain.usecase.ToggleChecklistItemUseCase
import com.example.bitrix_app.domain.usecase.user.LoadUsersFromPortalUseCase
import com.example.bitrix_app.ui.util.formatTime
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    application: Application,
    private val getTasksUseCase: GetTasksUseCase,
    private val syncTasksUseCase: SyncTasksUseCase,
    private val createTaskUseCase: CreateTaskUseCase,
    private val completeTaskUseCase: CompleteTaskUseCase,
    private val deleteTaskUseCase: DeleteTaskUseCase,
    private val getGroupsUseCase: GetGroupsUseCase,
    private val loadUsersFromPortalUseCase: LoadUsersFromPortalUseCase,
    private val fetchChecklistUseCase: FetchChecklistUseCase,
    private val toggleChecklistItemUseCase: ToggleChecklistItemUseCase,
    private val addCommentUseCase: com.example.bitrix_app.domain.usecase.AddCommentUseCase,
    private val saveTaskTimeUseCase: com.example.bitrix_app.domain.usecase.SaveTaskTimeUseCase,
    private val userRepository: UserRepository,
    private val encryptedPrefs: EncryptedPreferences,
    private val taskRepository: com.example.bitrix_app.domain.repository.TaskRepository
) : AndroidViewModel(application) {

    // --- State ---
    var users by mutableStateOf<List<User>>(emptyList())
        private set
    var currentUserIndex by mutableStateOf(0)
        private set
    var tasks by mutableStateOf<List<Task>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    // Filtering
    var showCompletedTasks by mutableStateOf(true)
    var deadlineFilterDate by mutableStateOf<Long?>(null)
        private set

    // Expanded Tasks
    var expandedTaskIds by mutableStateOf<Set<String>>(emptySet())
        private set

    // Checklists
    var checklistsMap by mutableStateOf<Map<String, List<ChecklistItem>>>(emptyMap())
        private set
    var loadingChecklistMap by mutableStateOf<Map<String, Boolean>>(emptyMap())
        private set

    // Timer Service State (Bound from Activity)
    // We keep this for UI updates but logic should ideally be decoupled.
    // However, MainViewModel acts as bridge.
    // We define a simple state class or reuse TimerServiceState if accessible (It's in TimerService.kt)
    // Assuming TimerServiceState is accessible.
    // We need to access TimerService instance to control it.
    // Ideally this should be a UseCase "TimerUseCase" but TimerService is Android Service.
    // Dependencies on Service instance are tricky in DI.
    // Leaving existing pattern for now (conntected from Activity).
    var timerServiceState by mutableStateOf<com.example.bitrix_app.TimerServiceState?>(null)
        private set
    private var timerService: com.example.bitrix_app.TimerService? = null

    // Dialog States
    var showCreateTaskDialog by mutableStateOf(false)
    var isCreatingTask by mutableStateOf(false)
    var showAddUserDialog by mutableStateOf(false)
    var showRemoveUserDialogFor by mutableStateOf<User?>(null)
    var showGroupSelectionDialog by mutableStateOf(false)
    var availableGroups by mutableStateOf<List<Pair<String, String>>>(emptyList())
    var defaultGroupId by mutableStateOf("69")

    // UI Feedback
    var textCommentStatusMessage by mutableStateOf<String?>(null)
    var deleteTaskStatusMessage by mutableStateOf<String?>(null)
    var pendingSyncMessage by mutableStateOf<String?>(null) // TODO: Implement pending sync check usecase?

    // Add User Flow
    var setupStep by mutableStateOf(0)
    var currentPortalUrlInput by mutableStateOf("https://bitrix.tooksm.kz/rest/1/wj819u83f2ht207z/")
    var loadedUsersFromPortal by mutableStateOf<List<User>>(emptyList())
    var isPortalLoading by mutableStateOf(false)
    var selectedUsersToAdd by mutableStateOf<Set<User>>(emptySet())

    // Log Viewer
    var isLogViewerVisible by mutableStateOf(false)
    var logLines by mutableStateOf<List<String>>(emptyList())
    
    // Network
    var isOnline by mutableStateOf(true)
    private val networkObserver = com.example.bitrix_app.util.NetworkConnectivityObserver(application)

    // Comments
    var showAddCommentDialogForTask by mutableStateOf<Task?>(null)
    var commentTextInput by mutableStateOf("")

    // Initialization
    private var isInitialized = false
    private var observeTasksJob: Job? = null

    fun initViewModel() {
        if (isInitialized) return
        android.util.Log.e("BITRIX_DEBUG", "initViewModel called")
        viewModelScope.launch {
            // Start network observation
            launch {
                networkObserver.observe().collect { isOnlineStatus ->
                    val wasOffline = !isOnline
                    isOnline = isOnlineStatus
                    if (isOnlineStatus && wasOffline) {
                        android.util.Log.e("BITRIX_DEBUG", "Network restored, triggering sync...")
                        flushSyncQueue()
                    }
                }
            }
            
            android.util.Log.e("BITRIX_DEBUG", "DEBUG_INIT: Calling userRepository.getActiveUsers()")
            try {
                users = userRepository.getActiveUsers()
                android.util.Log.e("BITRIX_DEBUG", "DEBUG_INIT: Loaded ${users.size} users")
            } catch (e: Exception) {
                android.util.Log.e("BITRIX_DEBUG", "DEBUG_INIT: Failed to load users", e)
            }
            
            loadCurrentUserIndex()
            android.util.Log.e("BITRIX_DEBUG", "DEBUG_INIT: Current User Index: $currentUserIndex")
            
            val currentUser = users.getOrNull(currentUserIndex)
            if (currentUser != null) {
                android.util.Log.e("BITRIX_DEBUG", "DEBUG_URL: Current User ID: ${currentUser.userId}")
                android.util.Log.e("BITRIX_DEBUG", "DEBUG_URL: Webhook URL: '${currentUser.webhookUrl}'")
                android.util.Log.e("BITRIX_DEBUG", "DEBUG_URL: URL Length: ${currentUser.webhookUrl.length}")
                currentUser.webhookUrl.forEachIndexed { i, c ->
                    android.util.Log.e("BITRIX_DEBUG", "DEBUG_URL: Char[$i]: '$c' code=${c.code}")
                }
            } else {
                 android.util.Log.e("BITRIX_DEBUG", "DEBUG_URL: Current user is NULL. Users empty? ${users.isEmpty()}")
                 if (users.isNotEmpty()) {
                     android.util.Log.e("BITRIX_DEBUG", "DEBUG_URL: First user URL: '${users[0].webhookUrl}'")
                 }
            }

            defaultGroupId = encryptedPrefs.getDefaultGroupId()

            if (users.isNotEmpty()) {
                observeTasks()
                refreshTasks()
            }
            isInitialized = true
        }
    }

    private fun loadCurrentUserIndex() {
        // Simple shared prefs for index, can optimize later to verify user exists
         val prefs = getApplication<Application>().getSharedPreferences("MainViewModelPrefs", Context.MODE_PRIVATE)
         val idx = prefs.getInt("currentUserIndex", 0)
         currentUserIndex = if (idx in users.indices) idx else 0
    }
    
    private fun saveCurrentUserIndex(index: Int) {
        getApplication<Application>().getSharedPreferences("MainViewModelPrefs", Context.MODE_PRIVATE)
            .edit().putInt("currentUserIndex", index).apply()
    }

    fun switchUser(index: Int) {
        if (index !in users.indices) return
        currentUserIndex = index
        saveCurrentUserIndex(index)
        
        val user = users[index]
        timerService?.setCurrentUser(user.userId, user.name)
        
        tasks = emptyList() // clear old
        observeTasks()
        refreshTasks()
    }

    private fun observeTasks() {
        val user = users.getOrNull(currentUserIndex) ?: return
        observeTasksJob?.cancel()
        observeTasksJob = viewModelScope.launch {
            getTasksUseCase(user.userId).collect { rawTasks ->
                processAndSetTasks(rawTasks)
            }
        }
    }

    fun refreshTasks() {
        val user = users.getOrNull(currentUserIndex) ?: return
        isLoading = true
        errorMessage = null
        viewModelScope.launch {
            val result = syncTasksUseCase(user.userId, user.webhookUrl)
            isLoading = false
            if (result.isFailure) {
                // errorMessage = "Offline mode" // Optional
            }
        }
    }

    private suspend fun processAndSetTasks(rawTasks: List<Task>) {
        withContext(Dispatchers.Default) {
            // Apply Filters
             val filtered = rawTasks.filter { task ->
                 (if (showCompletedTasks) true else !task.isCompleted)
                 // Date filter logic (simplified for brevity, reuse utils if needed)
                 // TODO: restore heavy date filtering logic if needed
             }
             .sortedWith(
                compareBy<Task> { it.isCompleted } // Completed last
                    .thenByDescending { it.id.toLongOrNull() ?: 0L } // Newest first (by ID)
            )
            withContext(Dispatchers.Main) {
                tasks = filtered
            }
        }
    }

    // --- Task Actions ---

    fun createTask(title: String, minutes: Int) {
        val user = users.getOrNull(currentUserIndex) ?: return
        isCreatingTask = true
        viewModelScope.launch {
            val result = createTaskUseCase(title, user.userId, user.webhookUrl, minutes, defaultGroupId)
            isCreatingTask = false
            if (result.isSuccess) {
                showCreateTaskDialog = false
                refreshTasks()
            } else {
                errorMessage = "Failed to create task: ${result.exceptionOrNull()?.message}"
            }
        }
    }
    
    fun completeTask(task: Task) {
         val user = users.getOrNull(currentUserIndex) ?: return
         // Timer logic handled in UI/Service mostly, here just API/Repo
         // But existing logic stops timer first.
         // We'll trust UI to call stopTimer if needed, or do it here.
         // existing: completeTask logic checked timer status.
         // We can access timerService here.
         
         val service = timerService
         if (service?.serviceStateFlow?.value?.activeTaskId == task.id) {
             service.stopTaskTimer(user.userId)
             // Saving time logic?
         }

         viewModelScope.launch {
             completeTaskUseCase(task.id, user.userId, user.webhookUrl)
         }
    }

    fun requestDeleteTask(task: Task) {
         // Show dialog
         // logic handled in UI state
    }

    fun confirmDeleteTask(task: Task) {
        val user = users.getOrNull(currentUserIndex) ?: return
        deleteTaskStatusMessage = "Deleting..."
        viewModelScope.launch {
            val result = deleteTaskUseCase(task.id, user.userId, user.webhookUrl)
             if (result.isSuccess) {
                 deleteTaskStatusMessage = "Deleted"
                 refreshTasks() // Or relying on observe
             } else {
                 deleteTaskStatusMessage = "Error: ${result.exceptionOrNull()?.message}"
             }
             delay(3000)
             deleteTaskStatusMessage = null
        }
    }
    
    // --- Checklist ---
    
    fun toggleTaskExpansion(taskId: String) {
        expandedTaskIds = if (expandedTaskIds.contains(taskId)) expandedTaskIds - taskId else expandedTaskIds + taskId
        if (expandedTaskIds.contains(taskId)) {
            fetchChecklist(taskId)
        }
    }

    private fun fetchChecklist(taskId: String) {
        val user = users.getOrNull(currentUserIndex) ?: return
        if (checklistsMap.containsKey(taskId)) return // Already loaded?
        
        loadingChecklistMap = loadingChecklistMap + (taskId to true)
        viewModelScope.launch {
            val result = fetchChecklistUseCase(taskId, user.webhookUrl)
            loadingChecklistMap = loadingChecklistMap - taskId
            if (result.isSuccess) {
                checklistsMap = checklistsMap + (taskId to result.getOrNull()!!)
            }
        }
    }

    fun toggleChecklistItemStatus(taskId: String, itemId: String, currentIsComplete: Boolean) {
         val user = users.getOrNull(currentUserIndex) ?: return
         val action = if (currentIsComplete) "task.checklistitem.renew" else "task.checklistitem.complete"
         
         // Optimistic Update
         val currentList = checklistsMap[taskId] ?: return
         val updatedList = currentList.map { 
             if (it.id == itemId) it.copy(isComplete = !currentIsComplete) else it 
         }
         checklistsMap = checklistsMap + (taskId to updatedList)
         
         viewModelScope.launch {
             val result = toggleChecklistItemUseCase(taskId, itemId, if(currentIsComplete) "renew" else "complete", user.webhookUrl)
             if (result.isFailure) {
                 // Revert
                 checklistsMap = checklistsMap + (taskId to currentList)
                 Timber.e("Failed to toggle checklist item")
             }
         }
    }

    // --- User Management ---

    fun prepareAddUserDialog() {
        showAddUserDialog = true
        setupStep = 0
        currentPortalUrlInput = "https://bitrix.tooksm.kz/rest/1/wj819u83f2ht207z/"
        selectedUsersToAdd = emptySet()
    }
    
    fun loadUsersFromPortal() {
        isPortalLoading = true
        errorMessage = null
        viewModelScope.launch {
            val result = loadUsersFromPortalUseCase(currentPortalUrlInput)
            isPortalLoading = false
            if (result.isSuccess) {
                loadedUsersFromPortal = result.getOrNull() ?: emptyList()
                setupStep = 1
            } else {
                errorMessage = result.exceptionOrNull()?.message
            }
        }
    }

    fun toggleUserSelection(user: User) {
        selectedUsersToAdd = if (selectedUsersToAdd.contains(user)) {
            selectedUsersToAdd - user
        } else {
            selectedUsersToAdd + user
        }
    }

    fun addSelectedUsers() {
        val newUsers = selectedUsersToAdd.map { it.copy(webhookUrl = currentPortalUrlInput) }
        viewModelScope.launch {
            // Map existing users by ID for easy lookup
            val existingUsersMap = users.associateBy { it.userId }
            
            // New users list. If ID exists, we prefer the NEW one (to update URL/Name/Avatar)
            // But we might want to preserve some local fields if they existed? 
            // Currently User model is all remote config except maybe isActive/lastSelectedAt?
            // "isActive" defaults to true. "lastSelectedAt" also.
            
            // Actually, we should probably merge intelligently or just overwrite.
            // User requested: "Use the same hook". So overwrite is critical.
            
            val mergedUsers = (users + newUsers).reversed().distinctBy { it.userId }.reversed()
            // distinctBy keeps FIRST. To keep LAST (new), we reverse, distinct, reverse back.
            // Or simpler:
            // val newMap = existingUsersMap + newUsers.associateBy { it.userId }
            // val finalUsers = newMap.values.toList()
            
            val newMap = users.associateBy { it.userId }.toMutableMap()
            newUsers.forEach { newUser ->
                // If exists, we replace it. 
                // We might want to keep 'lastSelectedAt' if critical, but URL update is P0.
                newMap[newUser.userId] = newUser
            }
            
            val updated = newMap.values.toList()

            userRepository.saveUsers(updated)
            users = updated
            showAddUserDialog = false
            if (users.isNotEmpty()) switchUser(users.lastIndex)
        }
    }

    fun selectUserFromPortal(user: User) {
       // Deprecated single select? Or keep as "click adds one" if needed?
       // Let's keep it but ideally use the new batch method.
       // Actually user requested "select multiple".
       // So standard single click in list might toggle selection now?
       toggleUserSelection(user)
    }

    fun requestRemoveUser(user: User) {
         showRemoveUserDialogFor = user
    }
    
    fun confirmRemoveUser() {
        val user = showRemoveUserDialogFor ?: return
        viewModelScope.launch {
             val updated = users - user
             userRepository.saveUsers(updated)
             users = updated
             showRemoveUserDialogFor = null
             if (currentUserIndex >= users.size) {
                 currentUserIndex = 0.coerceAtLeast(0) // Switch to first
             }
             if (users.isNotEmpty()) switchUser(currentUserIndex)
        }
    }
    
    // --- Groups ---
    fun openGroupSelectionDialog() {
        val user = users.getOrNull(currentUserIndex) ?: return
        showGroupSelectionDialog = true
        viewModelScope.launch {
            val result = getGroupsUseCase(user.webhookUrl)
            if (result.isSuccess) {
                availableGroups = result.getOrNull() ?: emptyList()
            }
        }
    }
    
    fun selectDefaultGroup(groupId: String) {
        defaultGroupId = groupId
        encryptedPrefs.saveDefaultGroupId(groupId)
        showGroupSelectionDialog = false
    }

    // --- Timer Service Connection ---
    fun connectToTimerService(service: com.example.bitrix_app.TimerService?) {
        timerService = service
        if (service != null) {
            viewModelScope.launch {
                service.serviceStateFlow.collect { state ->
                    timerServiceState = state
                }
            }
            val user = users.getOrNull(currentUserIndex)
            if (user != null) {
                service.setCurrentUser(user.userId, user.name)
            }
        }
    }
    
    // Toggle Timer
    // Toggle Timer
    fun toggleTimer(task: Task) {
        val service = timerService ?: return
        val user = users.getOrNull(currentUserIndex) ?: return
        
        val currentState = timerServiceState
        
        // 1. If clicking on the currently active task
        if (currentState?.activeTaskId == task.id) {
             if (currentState.isEffectivelyPaused == false) {
                 service.userPauseTaskTimer(user.userId)
             } else {
                 service.userResumeTaskTimer(user.userId)
             }
             return
        }

        // 2. If there is a DIFFERENT active task -> STOP & SAVE IT
        if (currentState?.activeTaskId != null) {
             val previousTaskId = currentState.activeTaskId
             Timber.d("Switching tasks. Auto-saving previous task: $previousTaskId")
             
             // Get delta seconds
             val seconds = service.stopTaskTimer(user.userId)
             if (seconds > 0) {
                 viewModelScope.launch {
                     val result = saveTaskTimeUseCase(previousTaskId, user.userId, user.webhookUrl, seconds)
                     if (result.isFailure) {
                         errorMessage = "Failed to auto-save time: ${result.exceptionOrNull()?.message}"
                         Timber.e("Auto-save failed for $previousTaskId: ${result.exceptionOrNull()?.message}")
                     } else {
                         Timber.d("Auto-saved time for $previousTaskId")
                     }
                     // Force refresh to update the old task's time in UI
                     refreshTasks()
                 }
             }
        }

        // 3. Start new timer
        service.startTaskTimer(user.userId, user.name, task.id, task.title, task.timeSpent)
    }

    fun stopAndSaveTaskTimer() {
        val service = timerService ?: return
        val user = users.getOrNull(currentUserIndex) ?: return
        
        // Capture active task ID before stopping
        val activeTaskId = timerServiceState?.activeTaskId
        
        if (activeTaskId != null) {
             val seconds = service.stopTaskTimer(user.userId)
             if (seconds > 0) {
                 viewModelScope.launch {
                     val result = saveTaskTimeUseCase(activeTaskId, user.userId, user.webhookUrl, seconds)
                     if (result.isFailure) {
                        errorMessage = "Failed to save time: ${result.exceptionOrNull()?.message}"
                     }
                     delay(1000)
                     refreshTasks()
                 }
             }
        }
    }

    // Logging & Other
    fun hideLogViewer() { isLogViewerVisible = false }
    fun showLogViewer() { 
        // Load logs
        isLogViewerVisible = true 
    }
    fun loadLogContent(context: Context) {
        // ...
    }
    fun shareLogs(context: Context) {
        // ...
    }
    
    fun forceReloadTasks() { 
        android.util.Log.e("BITRIX_DEBUG", "Force reload tasks triggered")
        refreshTasks() 
    }
    
    fun dismissAddCommentDialog() { showAddCommentDialogForTask = null }
    fun submitTextComment(taskId: String, text: String) {
        val user = users.getOrNull(currentUserIndex) ?: return
        textCommentStatusMessage = "Sending..."
        viewModelScope.launch {
            val result = addCommentUseCase(taskId, user.userId, user.webhookUrl, text)
            if (result.isSuccess) {
                textCommentStatusMessage = "Sent!"
                showAddCommentDialogForTask = null // Dismiss dialog
                commentTextInput = ""
                delay(1000)
                textCommentStatusMessage = null
            } else {
                textCommentStatusMessage = "Error: ${result.exceptionOrNull()?.message}"
            }
        }
    }
    
    fun dismissAddUserDialog() { showAddUserDialog = false }
    fun dismissRemoveUserDialog() { showRemoveUserDialogFor = null }
    fun dismissDeleteTaskDialog() { showDeleteConfirmDialogForTask = null }
     var showDeleteConfirmDialogForTask by mutableStateOf<Task?>(null)

    fun setDeadlineFilter(deadline: Long?) {
        deadlineFilterDate = deadline
        // trigger filter update
        observeTasks() // or just update observable
    }
    
    fun toggleShowCompletedTasks() {
        showCompletedTasks = !showCompletedTasks
        observeTasks()
    }

    var syncStatusMessage by mutableStateOf<String?>(null)
    var lastSyncError by mutableStateOf<String?>(null)

    fun flushSyncQueue() {
        android.util.Log.e("BITRIX_DEBUG", "Manual Flush Sync Queue Triggered")
        syncStatusMessage = "Syncing..."
        
        // 1. Try immediate sync in foreground to report status
        viewModelScope.launch {
            try {
                val result = taskRepository.syncPendingOperations()
                if (result.isSuccess) {
                    syncStatusMessage = "Sync Complete"
                    lastSyncError = null
                    delay(3000)
                    syncStatusMessage = null
                } else {
                    val error = result.exceptionOrNull()?.message ?: "Unknown Error"
                    syncStatusMessage = "Sync Failed"
                    lastSyncError = error
                    android.util.Log.e("BITRIX_DEBUG", "Manual Sync Failed", result.exceptionOrNull())
                }
            } catch (e: Exception) {
                syncStatusMessage = "Sync Error"
                lastSyncError = e.message
                android.util.Log.e("BITRIX_DEBUG", "Manual Sync Exception", e)
            }
        }

        // 2. Also schedule WorkManager for robustness
        val syncRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.bitrix_app.worker.SyncWorker>()
            .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        
        androidx.work.WorkManager.getInstance(getApplication())
            .enqueueUniqueWork(
                "BitrixImmediateSync",
                androidx.work.ExistingWorkPolicy.REPLACE, // Use REPLACE to ensure fresh attempt
                syncRequest
            )
    }

    fun clearAllPendingQueue() {
        viewModelScope.launch {
            try {
                taskRepository.clearAllSyncQueue()
                syncStatusMessage = "Queue Cleared"
                lastSyncError = null
                delay(2000)
                syncStatusMessage = null
                android.util.Log.e("BITRIX_DEBUG", "Sync queue cleared")
            } catch (e: Exception) {
                syncStatusMessage = "Clear Failed"
                lastSyncError = e.message
                android.util.Log.e("BITRIX_DEBUG", "Clear queue failed", e)
            }
        }
    }

}
