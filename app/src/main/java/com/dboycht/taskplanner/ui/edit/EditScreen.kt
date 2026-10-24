package com.dboycht.taskplanner.ui.edit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dboycht.taskplanner.model.Priority
import com.dboycht.taskplanner.model.ReminderAnchor
import com.dboycht.taskplanner.model.ReminderPolicy
import com.dboycht.taskplanner.model.TaskStatus
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(taskKey: String, onBack: () -> Unit) {
    val vm: TaskEditViewModel = viewModel(key = "edit-$taskKey")
    LaunchedEffect(taskKey) { vm.load(taskKey.toLongOrNull()) }
    val draft = vm.draft
    val isNew = draft.id == 0L
    val error = vm.error

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "新建任务" else "编辑任务") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("取消") }
                },
                actions = {
                    TextButton(
                        onClick = { vm.save(onBack) },
                        enabled = draft.loaded && error == null,
                    ) { Text("保存") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = draft.title,
                onValueChange = vm::updateTitle,
                label = { Text("标题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = draft.description,
                onValueChange = vm::updateDescription,
                label = { Text("描述（可选）") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel("优先级")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Priority.entries.forEachIndexed { index, p ->
                    SegmentedButton(
                        selected = draft.priority == p,
                        onClick = { vm.updatePriority(p) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = Priority.entries.size,
                        ),
                    ) { Text(p.label) }
                }
            }

            SectionLabel("状态")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TaskStatus.entries.forEachIndexed { index, s ->
                    SegmentedButton(
                        selected = draft.status == s,
                        onClick = { vm.updateStatus(s) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = TaskStatus.entries.size,
                        ),
                    ) { Text(s.label) }
                }
            }

            TimeField(label = "开始时间", value = draft.startTime, onSet = vm::setStart)
            TimeField(label = "截止时间", value = draft.dueTime, onSet = vm::setDue)

            OutlinedTextField(
                value = draft.tags,
                onValueChange = vm::updateTags,
                label = { Text("标签（逗号分隔，可选）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = "提醒：${policySummary(draft.reminderPolicy)}（自定义配置随提醒功能开放）",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (error != null) {
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** 时间行：点击弹「日期 → 时间」两级选择器；已设置时提供清除。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(label: String, value: Long?, onSet: (Long?) -> Unit) {
    val zone = ZoneId.systemDefault()
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var pickedDateMillis by remember { mutableStateOf<Long?>(null) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (value == null) "$label（未设置）" else "$label：${fmtDateTime(value)}",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable { showDatePicker = true }
                .padding(vertical = 10.dp),
        )
        if (value != null) {
            TextButton(onClick = { onSet(null) }) { Text("清除") }
        }
    }

    if (showDatePicker) {
        // DatePicker 以 UTC 当天 0 点表示选中日期；把本地日期换算过去，
        // 否则东八区的上午会被高亮成前一天。
        val initialUtcMillis = Instant.ofEpochMilli(value ?: System.currentTimeMillis())
            .atZone(zone).toLocalDate()
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialUtcMillis,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickedDateMillis = datePickerState.selectedDateMillis
                        showDatePicker = false
                        if (datePickerState.selectedDateMillis != null) showTimePicker = true
                    },
                ) { Text("下一步") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val base = ZonedDateTime.ofInstant(
            Instant.ofEpochMilli(value ?: System.currentTimeMillis()),
            zone,
        )
        val timePickerState = rememberTimePickerState(
            initialHour = base.hour,
            initialMinute = base.minute,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("选择时间") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    TimePicker(state = timePickerState)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val dateMillis = pickedDateMillis
                        if (dateMillis != null) {
                            val localDate = Instant.ofEpochMilli(dateMillis)
                                .atZone(ZoneOffset.UTC).toLocalDate()
                            val millis = localDate
                                .atTime(timePickerState.hour, timePickerState.minute)
                                .atZone(zone).toInstant().toEpochMilli()
                            onSet(millis)
                        }
                        showTimePicker = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("取消") }
            },
        )
    }
}

private val dateTimeFmt = DateTimeFormatter.ofPattern("MM-dd HH:mm")

private fun fmtDateTime(epochMillis: Long): String =
    dateTimeFmt.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))

/** 策略的人话摘要（M3 提供配置 UI，这里先只读展示）。 */
internal fun policySummary(policy: ReminderPolicy): String {
    if (policy.points.isEmpty()) return "关闭"
    return policy.points.joinToString("、") { p ->
        when (p.anchor) {
            ReminderAnchor.START ->
                if (p.offsetMinutes == 0) "开始时" else "开始前 ${p.offsetMinutes} 分钟"
            ReminderAnchor.DUE ->
                if (p.offsetMinutes == 0) "截止时" else "截止前 ${p.offsetMinutes} 分钟"
        }
    }
}
