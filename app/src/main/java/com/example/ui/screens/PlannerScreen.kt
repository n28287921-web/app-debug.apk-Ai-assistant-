package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.data.model.AssistantTask
import com.example.data.model.TaskCategory
import com.example.data.model.TaskPriority
import com.example.data.model.UiState
import com.example.ui.components.GlassHudCard
import com.example.ui.components.StatusPill
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.DeepCobalt
import com.example.ui.theme.DeepHudNavy
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.GlassNavySurface
import com.example.ui.theme.HudOutline
import com.example.ui.theme.HudTextSecondary
import com.example.ui.theme.IceCyan
import com.example.ui.theme.VoidNavy

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlannerScreen(
    tasksState: UiState<List<AssistantTask>>,
    onAddTask: (String, String, TaskCategory, String, String, TaskPriority) -> Unit,
    onToggleTask: (AssistantTask) -> Unit,
    onDeleteTask: (String) -> Unit,
    onSetSystemAlarm: (Int, Int, String) -> Unit,
    onExportToCalendar: (String, String) -> Unit
) {
    var selectedCategory by remember { mutableStateOf<TaskCategory?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var showAlarmDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = ElectricCyan,
                contentColor = VoidNavy,
                modifier = Modifier.testTag("add_planner_item_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add task or reminder")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(VoidNavy, DeepHudNavy, VoidNavy)))
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Column(
                    modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "SMART PLANNER",
                                style = MaterialTheme.typography.headlineMedium,
                                color = ElectricCyan
                            )
                            Text(
                                text = "Tasks • Reminders • Schedule • Birthdays",
                                style = MaterialTheme.typography.bodyMedium,
                                color = HudTextSecondary
                            )
                        }
                        OutlinedButton(
                            onClick = { showAlarmDialog = true },
                            border = BorderStroke(1.dp, ElectricCyan),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricCyan)
                        ) {
                            Icon(Icons.Default.Alarm, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Alarm")
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = { Text("All") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricCyan,
                                selectedLabelColor = VoidNavy
                            )
                        )
                        TaskCategory.entries.forEach { cat ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(cat.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricCyan,
                                    selectedLabelColor = VoidNavy
                                )
                            )
                        }
                    }
                }
            }

            when (tasksState) {
                is UiState.Loading -> {
                    item {
                        CircularProgressIndicator(color = ElectricCyan, modifier = Modifier.padding(24.dp))
                    }
                }

                is UiState.Error -> {
                    item {
                        Text(tasksState.message, color = CrimsonAlert)
                    }
                }

                is UiState.Success -> {
                    val filtered = tasksState.data.filter { task ->
                        selectedCategory == null || TaskCategory.fromRaw(task.category) == selectedCategory
                    }
                    if (filtered.isEmpty()) {
                        item {
                            GlassHudCard(modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth()) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.EventNote,
                                        contentDescription = null,
                                        tint = ElectricCyan,
                                        modifier = Modifier.size(40.dp)
                                    )
                                    Text("No items in this category yet", style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        text = "Tap + or say \"Remind me to call Dad at 7 PM\" or \"Add birthday reminder\".",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = HudTextSecondary
                                    )
                                }
                            }
                        }
                    } else {
                        items(filtered, key = { it.id }) { task ->
                            TaskItemCard(
                                task = task,
                                onToggle = { onToggleTask(task) },
                                onDelete = { onDeleteTask(task.id) },
                                onCalendar = { onExportToCalendar(task.title, task.notes) },
                                modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddPlannerDialog(
            onDismiss = { showAddDialog = false },
            onSave = { title, notes, category, date, time, priority ->
                onAddTask(title, notes, category, date, time, priority)
                showAddDialog = false
            }
        )
    }

    if (showAlarmDialog) {
        QuickAlarmDialog(
            onDismiss = { showAlarmDialog = false },
            onSetAlarm = { h, m, label ->
                onSetSystemAlarm(h, m, label)
                showAlarmDialog = false
            }
        )
    }
}

@Composable
private fun TaskItemCard(
    task: AssistantTask,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onCalendar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val category = TaskCategory.fromRaw(task.category)
    val catIcon = when (category) {
        TaskCategory.BIRTHDAY -> Icons.Default.Cake
        TaskCategory.REMINDER -> Icons.Default.NotificationsActive
        TaskCategory.SCHEDULE -> Icons.Default.CalendarMonth
        TaskCategory.TASK -> Icons.Default.EventNote
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = GlassNavySurface.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, if (task.completed) HudOutline else ElectricCyan.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (task.completed) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = "Toggle completion",
                    tint = if (task.completed) EmeraldOnline else ElectricCyan
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(catIcon, contentDescription = null, tint = IceCyan, modifier = Modifier.size(16.dp))
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None
                        )
                    )
                }
                if (task.notes.isNotBlank()) {
                    Text(
                        text = task.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = HudTextSecondary
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill(label = category.label.uppercase(), accentColor = ElectricCyan)
                    if (task.dueDateLabel.isNotBlank() || task.dueTimeLabel.isNotBlank()) {
                        StatusPill(
                            label = listOf(task.dueDateLabel, task.dueTimeLabel).filter { it.isNotBlank() }.joinToString(" "),
                            accentColor = AmberWarning
                        )
                    }
                }
            }
            IconButton(onClick = onCalendar) {
                Icon(Icons.Default.CalendarMonth, contentDescription = "Add to Calendar", tint = IceCyan)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete item", tint = CrimsonAlert)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddPlannerDialog(
    onDismiss: () -> Unit,
    onSave: (String, String, TaskCategory, String, String, TaskPriority) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var dateLabel by remember { mutableStateOf("") }
    var timeLabel by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(TaskCategory.TASK) }
    var priority by remember { mutableStateOf(TaskPriority.NORMAL) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeepHudNavy,
        title = { Text("New Planner Entry", color = ElectricCyan) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title (e.g., Mom's Birthday / Team Call)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("planner_title_input")
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dateLabel,
                        onValueChange = { dateLabel = it },
                        label = { Text("Date") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = timeLabel,
                        onValueChange = { timeLabel = it },
                        label = { Text("Time") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }
                Text("Category", style = MaterialTheme.typography.labelLarge, color = IceCyan)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TaskCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = category == cat,
                            onClick = { category = cat },
                            label = { Text(cat.label) }
                        )
                    }
                }
                Text("Priority", style = MaterialTheme.typography.labelLarge, color = IceCyan)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TaskPriority.entries.forEach { p ->
                        FilterChip(
                            selected = priority == p,
                            onClick = { priority = p },
                            label = { Text(p.label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(title, notes, category, dateLabel, timeLabel, priority)
                    }
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = VoidNavy)
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun QuickAlarmDialog(
    onDismiss: () -> Unit,
    onSetAlarm: (Int, Int, String) -> Unit
) {
    var hour by remember { mutableIntStateOf(7) }
    var minute by remember { mutableIntStateOf(0) }
    var label by remember { mutableStateOf("Jarvis Morning Briefing") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DeepHudNavy,
        title = { Text("Set Device Alarm", color = ElectricCyan) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        OutlinedButton(onClick = { hour = (hour + 1) % 24 }) { Text("+") }
                        Text(String.format("%02d", hour), style = MaterialTheme.typography.displaySmall, color = IceCyan)
                        OutlinedButton(onClick = { hour = (hour + 23) % 24 }) { Text("-") }
                    }
                    Text(":", style = MaterialTheme.typography.displaySmall, color = ElectricCyan)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        OutlinedButton(onClick = { minute = (minute + 5) % 60 }) { Text("+") }
                        Text(String.format("%02d", minute), style = MaterialTheme.typography.displaySmall, color = IceCyan)
                        OutlinedButton(onClick = { minute = (minute + 55) % 60 }) { Text("-") }
                    }
                }
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("Alarm Label") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSetAlarm(hour, minute, label) },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = VoidNavy)
            ) { Text("Set Alarm") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
