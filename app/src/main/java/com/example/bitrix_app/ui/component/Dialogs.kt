package com.example.bitrix_app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.bitrix_app.domain.model.User
import com.example.bitrix_app.ui.viewmodel.MainViewModel

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
    val context = LocalContext.current // Not used directly logic-wise in viewmodel refactor (passed logic) but here UI

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (viewModel.setupStep == 0) "Настройка портала" else "Выберите сотрудника") },
        text = {
            Column {
                if (viewModel.setupStep == 0) {
                    Text("Введите Admin Webhook URL для загрузки списка сотрудников:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = viewModel.currentPortalUrlInput,
                        onValueChange = { viewModel.currentPortalUrlInput = it }, // This updates viewmodel state if exposed
                        label = { Text("Webhook URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("https://b24.../rest/1/key/") }
                    )
                    
                    if (viewModel.isPortalLoading) {
                        Spacer(modifier = Modifier.height(16.dp))
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                } else {
                    // Step 1: List
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(viewModel.loadedUsersFromPortal) { user ->
                            val isSelected = viewModel.selectedUsersToAdd.contains(user)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.toggleUserSelection(user) }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.Checkbox(
                                    checked = isSelected,
                                    onCheckedChange = { viewModel.toggleUserSelection(user) }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                UserAvatar(user = user, size = 40)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(user.name, style = MaterialTheme.typography.bodyLarge)
                            }
                            Divider()
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (viewModel.setupStep == 0) {
                Button(
                    onClick = { viewModel.loadUsersFromPortal() },
                    enabled = !viewModel.isPortalLoading
                ) {
                    Text("Загрузить")
                }
            } else {
                Button(
                    onClick = { viewModel.addSelectedUsers() },
                    enabled = viewModel.selectedUsersToAdd.isNotEmpty()
                ) {
                    Text("Добавить (${viewModel.selectedUsersToAdd.size})")
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit,
    isLoading: Boolean
) {
    var title by remember { mutableStateOf("") }
    var estimateMinutesStr by remember { mutableStateOf("60") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая задача") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название задачи") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = estimateMinutesStr,
                    onValueChange = { if (it.all { char -> char.isDigit() }) estimateMinutesStr = it },
                    label = { Text("Оценка времени (мин)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                if (isLoading) {
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val minutes = estimateMinutesStr.toIntOrNull() ?: 0
                    if (title.isNotBlank()) {
                        onConfirm(title, minutes)
                    }
                },
                enabled = !isLoading && title.isNotBlank()
            ) {
                Text("Создать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        }
    )
}
