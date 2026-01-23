package com.example.bitrix_app.ui.screen

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bitrix_app.ui.component.AddTextCommentDialog
import com.example.bitrix_app.ui.component.AddUserDialog
import com.example.bitrix_app.ui.component.CreateTaskDialog
import com.example.bitrix_app.ui.component.DeleteConfirmationDialog
import com.example.bitrix_app.ui.component.RemoveUserConfirmationDialog
import com.example.bitrix_app.ui.component.StatusBlue
import com.example.bitrix_app.ui.component.StatusYellow
import com.example.bitrix_app.ui.component.TaskCard
import com.example.bitrix_app.ui.component.UserAvatar
import com.example.bitrix_app.ui.util.formatTime
import com.example.bitrix_app.ui.viewmodel.MainViewModel
import com.example.bitrix_app.LogExportHelper
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
fun TaskListScreen(viewModel: MainViewModel) {
    var isSettingsExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // --- Date Picker Dialog Logic ---
    val calendar = Calendar.getInstance()
    viewModel.deadlineFilterDate?.let {
        calendar.timeInMillis = it
    }
    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH)
    val day = calendar.get(Calendar.DAY_OF_MONTH)

    val datePickerDialog = DatePickerDialog(
        context,
        { _, selectedYear, selectedMonth, selectedDay ->
            val newCalendar = Calendar.getInstance().apply {
                set(selectedYear, selectedMonth, selectedDay)
            }
            viewModel.setDeadlineFilter(newCalendar.timeInMillis)
        }, year, month, day
    )

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showCreateTaskDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Создать задачу")
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // User Bar & Settings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // User avatars
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
                                            viewModel.switchUser(index)
                                        }
                                    },
                                    onLongClick = {
                                        viewModel.requestRemoveUser(user)
                                    }
                                )
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent,
                                    CircleShape
                                )
                                .padding(if (isSelected) 2.dp else 0.dp)

                        ) {
                            UserAvatar(user = user, size = avatarSize - (if (isSelected) 4 else 0))
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                // Right side controls
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Network Icon
                    val isOnline = viewModel.isOnline
                    Icon(
                        imageVector = if (isOnline) Icons.Default.Cloud else Icons.Default.CloudOff,
                        contentDescription = "Network Status",
                        tint = if (isOnline) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(end = 8.dp)
                    )

                    val deadlineFilter = viewModel.deadlineFilterDate
                    if (deadlineFilter != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            val formatter = remember { SimpleDateFormat("dd.MM.yy", Locale.getDefault()) }
                            Text(
                                text = formatter.format(Date(deadlineFilter)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            IconButton(
                                onClick = { viewModel.setDeadlineFilter(null) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Сбросить фильтр",
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    IconButton(
                        onClick = { datePickerDialog.show() },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Фильтр",
                            modifier = Modifier.size(32.dp)
                        )
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
                                text = { Text("Очистить кэш и перезагрузить") },
                                onClick = {
                                    viewModel.forceReloadTasks()
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
                            DropdownMenuItem(
                                text = { Text("Выбрать группу для задач (ID: ${viewModel.defaultGroupId})") },
                                onClick = {
                                    viewModel.openGroupSelectionDialog()
                                    isSettingsExpanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Экспорт логов для отладки") },
                                onClick = {
                                    LogExportHelper.shareLogFile(context)
                                    isSettingsExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Dialogs
             viewModel.showAddCommentDialogForTask?.let { task ->
                AddTextCommentDialog(
                    taskTitle = task.title,
                    currentComment = viewModel.commentTextInput,
                    onCommentChange = { viewModel.commentTextInput = it },
                    onConfirm = { comment -> viewModel.submitTextComment(task.id, comment) },
                    onDismiss = { viewModel.dismissAddCommentDialog() }
                )
            }

            // Active Timer Banner
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
                    val estimateSeconds = it.timeEstimate % 60
                    String.format("%d:%02d:%02d", estimateHours, estimateMinutes, estimateSeconds)
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
                            text = "${formatTime(serviceState.timerSeconds)} / $timeEstimateFormatted",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = textColor,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = { viewModel.stopAndSaveTaskTimer() },
                             modifier = Modifier.size(40.dp)
                        ) {
                             Icon(
                                imageVector = Icons.Filled.Save,
                                contentDescription = "Сохранить",
                                tint = textColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Loading / Error
            if (viewModel.isLoading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            viewModel.errorMessage?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(text = error, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            val pendingMsg = viewModel.pendingSyncMessage ?: viewModel.deleteTaskStatusMessage
            if (pendingMsg != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                     colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Text(text = pendingMsg, modifier = Modifier.padding(16.dp), textAlign = TextAlign.Center)
                }
            }

            // Optimized Timer State
            // We use derivedStateOf to only trigger recomposition if the ACTIVE TASK or PAUSE STATE changes.
            // This ignores "elapsed time" updates if they are part of the state object, preventing full list flickering.
            val activeTaskId by remember { derivedStateOf { viewModel.timerServiceState?.activeTaskId } }
            val isPaused by remember { derivedStateOf { viewModel.timerServiceState?.isEffectivelyPaused == true } }

            // Task List
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(viewModel.tasks, key = { task -> task.id }) { task ->
                        
                        // Now we use the stable derived states
                        val isTimerRunning = activeTaskId == task.id && !isPaused
                        val isTimerUserPaused = activeTaskId == task.id && isPaused

                        TaskCard(
                            task = task,
                            isExpanded = viewModel.expandedTaskIds.contains(task.id),
                            checklist = viewModel.checklistsMap[task.id],
                            isLoadingChecklist = viewModel.loadingChecklistMap[task.id] == true,
                            isTimerRunning = isTimerRunning,
                            isTimerUserPaused = isTimerUserPaused,
                            onExpandClick = { viewModel.toggleTaskExpansion(task.id) },
                            onTimerToggle = { viewModel.toggleTimer(it) },
                            onCompleteTask = { viewModel.completeTask(it) },
                            onAddCommentClick = { 
                                viewModel.showAddCommentDialogForTask = it; 
                                viewModel.commentTextInput = "" 
                            },
                            onLongPress = { viewModel.showDeleteConfirmDialogForTask = it },
                            onChecklistToggle = { itemId, isComplete -> 
                                viewModel.toggleChecklistItemStatus(task.id, itemId, isComplete) 
                            }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
                
                // Bottom Gradient
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.0f),
                                    MaterialTheme.colorScheme.background
                                )
                            )
                        )
                )
            }
            
            // Sync Error / Status Card - REMOVED as per request
            // if (viewModel.syncStatusMessage != null || viewModel.lastSyncError != null) { ... }

            // Other Dialogs
             viewModel.showDeleteConfirmDialogForTask?.let { taskToDelete ->
                DeleteConfirmationDialog(
                    taskTitle = taskToDelete.title,
                    onConfirm = { viewModel.confirmDeleteTask(taskToDelete) },
                    onDismiss = { viewModel.dismissDeleteTaskDialog() }
                )
            }

            if (viewModel.showAddUserDialog) {
                AddUserDialog(
                    viewModel = viewModel,
                    onConfirm = { /* handled inside dialog logic via VM methods? No, VM methods called direct. Confirm usually triggers VM logic. */
                        // In VM refactor: "confirm" usually means calling load or select.
                        // AddUserDialog in Dialogs.kt maps buttons to VM actions.
                        // But here we need to pass actions or just let Dialog call VM (if we pass VM).
                        // I passed `viewModel` to AddUserDialog.
                    },
                    onDismiss = { viewModel.dismissAddUserDialog() }
                )
            }

            viewModel.showRemoveUserDialogFor?.let { user ->
                RemoveUserConfirmationDialog(
                    user = user,
                    onConfirm = { viewModel.confirmRemoveUser() },
                    onDismiss = { viewModel.dismissRemoveUserDialog() }
                )
            }
            
            if (viewModel.showGroupSelectionDialog) {
                AlertDialog(
                    onDismissRequest = { viewModel.showGroupSelectionDialog = false },
                    title = { Text("Выберите группу") },
                    text = {
                        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                            items(viewModel.availableGroups) { (id, name) ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { viewModel.selectDefaultGroup(id) }.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(text = name)
                                    if (id == viewModel.defaultGroupId) {
                                        Spacer(Modifier.weight(1f))
                                        Icon(Icons.Default.Check, contentDescription = "Selected")
                                    }
                                }
                                androidx.compose.material3.HorizontalDivider()
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { viewModel.showGroupSelectionDialog = false }) { Text("Отмена") } }
                )
            }
            
            if (viewModel.showCreateTaskDialog) {
                CreateTaskDialog(
                    onDismiss = { viewModel.showCreateTaskDialog = false },
                    onConfirm = { t, m -> viewModel.createTask(t, m) },
                    isLoading = viewModel.isCreatingTask
                )
            }
        }
    }
}
