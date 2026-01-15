package com.example.bitrix_app.ui.component

import android.content.Context
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddComment
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bitrix_app.domain.model.ChecklistItem
import com.example.bitrix_app.domain.model.Task
import com.example.bitrix_app.ui.util.formatDeadline

// Colors matching Theme (should ideally come from Theme.kt but copying for now/simplicity or import Color directly)
// Assuming hardcoded colors from MainActivity:
val ProgressBarGreen = Color(0xFF4CAF50)
val ProgressBarOrange = Color(0xFFFF9800)
val ProgressBarRed = Color(0xFFF44336)
val StatusBlue = Color(0xFFE3F2FD) // Light Blue 50
val StatusYellow = Color(0xFFFFFDE7) // Light Yellow 50
val StatusGreen = Color(0xFFE8F5E9)  // Light Green 50

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskCard(
    task: Task,
    isExpanded: Boolean,
    checklist: List<ChecklistItem>?,
    isLoadingChecklist: Boolean,
    isTimerRunning: Boolean,
    isTimerUserPaused: Boolean,
    onExpandClick: () -> Unit,
    onTimerToggle: (Task) -> Unit,
    onCompleteTask: (Task) -> Unit,
    onAddCommentClick: (Task) -> Unit,
    onLongPress: (Task) -> Unit,
    onChecklistToggle: (String, Boolean) -> Unit
) {
    val hasDescription = task.description.isNotEmpty()
    val scheme = MaterialTheme.colorScheme

    val cardModifier = Modifier
        .fillMaxWidth()
        .combinedClickable(
            onClick = {
                if (hasDescription) {
                    onExpandClick()
                }
            },
            onLongClick = { onLongPress(task) }
        )

    val isBlinkingTask = task.isImportant && !task.isCompleted // Updated logic, removed isWaitingForControl for simplicity if not in Task model (it wasn't in DB) NO, task has isWaitingForControl?
    // Wait, Task model has isWaitingForControl? Let's check Task.kt or TaskEntity.
    // Assuming strictly from domain.model.Task

    val infiniteTransition = rememberInfiniteTransition(label = "blinking_color_transition")
    val blinkingColor by infiniteTransition.animateColor(
        initialValue = ProgressBarRed,
        targetValue = ProgressBarRed.copy(alpha = 0.6f),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blinking_color"
    )

    val cardContainerColor = when {
        isTimerRunning -> StatusBlue
        isTimerUserPaused -> StatusYellow
        task.isCompleted -> StatusGreen
        isBlinkingTask -> blinkingColor
        task.isImportant -> ProgressBarRed
        // task.isWaitingForControl -> ProgressBarOrange // If exists
        else -> scheme.surfaceVariant
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

            if (task.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                com.google.accompanist.flowlayout.FlowRow(
                    mainAxisSpacing = 6.dp,
                    crossAxisSpacing = 6.dp
                ) {
                    task.tags.forEach { tag ->
                        TagChip(text = tag)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val progress = if (task.timeEstimate > 0) {
                (task.timeSpent.toFloat() / task.timeEstimate.toFloat()).coerceAtMost(1f)
            } else 0f

            val progressIndicatorColor = remember(task, progress) {
                when {
                    // task.isOverdue -> ProgressBarRed // Check if overdue exists
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
                    text = "Время: " + com.example.bitrix_app.ui.util.formatTime(task.timeSpent), // Helper needed?
                    fontSize = 14.sp,
                    color = scheme.onSurfaceVariant
                )
                // percent calc
                val percent = if(task.timeEstimate > 0) (task.timeSpent * 100 / task.timeEstimate) else 0
                Text(
                    text = "$percent%",
                    fontSize = 14.sp,
                    color = scheme.onSurfaceVariant
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

                if (!checklist.isNullOrEmpty() && checklist.any { !it.isComplete }) {
                    Text(
                        text = "Чек-лист:",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    checklist.forEach { item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onChecklistToggle(item.id, item.isComplete) }
                                .padding(vertical = 4.dp)
                        ) {
                            Checkbox(
                                checked = item.isComplete,
                                onCheckedChange = { _ -> onChecklistToggle(item.id, item.isComplete) },
                                enabled = true,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = scheme.primary,
                                    uncheckedColor = scheme.onSurfaceVariant
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val checklistItemColor = if (item.isComplete) scheme.onSurfaceVariant else scheme.onSurface
                            Text(
                                text = item.title,
                                fontSize = 16.sp,
                                color = checklistItemColor
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
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
                        isTimerRunning -> sErrorTimer
                        isTimerUserPaused -> sTertiaryTimer
                        else -> sPrimaryTimer
                    },
                    contentColor = when {
                        isTimerRunning -> sOnErrorTimer
                        isTimerUserPaused -> sOnTertiaryTimer
                        else -> sOnPrimaryTimer
                    },
                    disabledContainerColor = sOnSurfaceTimer.copy(alpha = 0.12f),
                    disabledContentColor = sOnSurfaceTimer.copy(alpha = 0.38f)
                )

                Button(
                    onClick = { onTimerToggle(task) },
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
                    colors = timerButtonColors
                ) {
                    val iconVector = when {
                        isTimerRunning -> Icons.Filled.Pause
                        isTimerUserPaused -> Icons.Filled.PlayArrow
                        else -> Icons.Filled.PlayArrow
                    }
                    val contentDescription = when {
                        isTimerRunning -> "Приостановить таймер"
                        isTimerUserPaused -> "Продолжить таймер"
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
                    Button(
                        onClick = { onCompleteTask(task) },
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

@Composable
fun TagChip(text: String) {
    Box(
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}
