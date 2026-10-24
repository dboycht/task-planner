package com.dboycht.taskplanner.ui.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dboycht.taskplanner.model.Priority
import com.dboycht.taskplanner.model.Task
import com.dboycht.taskplanner.model.TaskStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(onEdit: (Long?) -> Unit) {
    val vm: TaskListViewModel = viewModel()
    val groups by vm.groups.collectAsStateWithLifecycle()
    var deleteTarget by remember { mutableStateOf<Task?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("任务计划") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { onEdit(null) }) {
                Text("＋", style = MaterialTheme.typography.titleLarge)
            }
        },
    ) { padding ->
        val total = groups.values.sumOf { it.size }
        if (total == 0) {
            EmptyList(Modifier.padding(padding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 96.dp),
            ) {
                groups.forEach { (group, tasks) ->
                    item(key = "header-${group.name}") {
                        GroupHeader("${group.label} · ${tasks.size}")
                    }
                    items(tasks, key = { it.id }) { task ->
                        TaskRow(
                            task = task,
                            onClick = { onEdit(task.id) },
                            onToggle = { vm.toggleDone(task) },
                            onLongPress = { deleteTarget = task },
                        )
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除任务") },
            text = { Text("确定删除「${target.title}」吗？该操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.delete(target)
                    deleteTarget = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun EmptyList(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("📝", style = MaterialTheme.typography.displaySmall)
            Spacer(Modifier.height(8.dp))
            Text("还没有任务", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "点右下角「＋」添加第一个任务",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun GroupHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskRow(
    task: Task,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
) {
    val done = task.status == TaskStatus.DONE
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToggleCircle(done = done, onClick = onToggle)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                task.title,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (done) TextDecoration.LineThrough else null,
            )
            timeLineText(task)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PriorityChip(task.priority)
                if (task.status == TaskStatus.DOING) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "· ${task.status.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                task.tags.forEach { tag ->
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "· $tag",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleCircle(done: Boolean, onClick: () -> Unit) {
    val borderColor =
        if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .border(1.5.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Text(
                "✓",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun PriorityChip(priority: Priority) {
    val color = when (priority) {
        Priority.HIGH -> MaterialTheme.colorScheme.error
        Priority.MEDIUM -> MaterialTheme.colorScheme.tertiary
        Priority.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.12f),
        contentColor = color,
    ) {
        Text(
            priority.label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

private val timeFmt = DateTimeFormatter.ofPattern("MM-dd HH:mm")

/** 副标题时间行；无任何时间返回 null。 */
internal fun timeLineText(task: Task): String? {
    val zone = ZoneId.systemDefault()
    val start = task.startTime?.let { timeFmt.format(Instant.ofEpochMilli(it).atZone(zone)) }
    val due = task.dueTime?.let { timeFmt.format(Instant.ofEpochMilli(it).atZone(zone)) }
    return when {
        start != null && due != null -> "$start ~ $due"
        due != null -> "截止 $due"
        start != null -> "开始 $start"
        else -> null
    }
}
