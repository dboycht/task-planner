package com.dboycht.taskplanner.ui.edit

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dboycht.taskplanner.model.ReminderPolicy
import com.dboycht.taskplanner.model.TaskStatus
import com.dboycht.taskplanner.model.isUsableFor
import com.dboycht.taskplanner.model.missingAnchorLabel
import com.dboycht.taskplanner.model.summary
import com.dboycht.taskplanner.reminder.hasOnlyPastTriggers
import com.dboycht.taskplanner.ui.anim.Appear
import com.dboycht.taskplanner.ui.anim.rememberAnimationsEnabled
import com.dboycht.taskplanner.ui.theme.priorityColor
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    taskKey: String,
    onBack: () -> Unit,
    onOpenPresets: () -> Unit,
) {
    val vm: TaskEditViewModel = viewModel(key = "edit-$taskKey")
    LaunchedEffect(taskKey) { vm.load(taskKey.toLongOrNull()) }
    val draft = vm.draft
    val presets by vm.presets.collectAsStateWithLifecycle()
    val isNew = draft.id == 0L
    val error = vm.error
    var showPresetSheet by remember { mutableStateOf(false) }
    val animOn = rememberAnimationsEnabled()

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
            Appear(0, animOn) {
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = vm::updateTitle,
                    label = { Text("标题") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Appear(1, animOn) {
                OutlinedTextField(
                    value = draft.description,
                    onValueChange = vm::updateDescription,
                    label = { Text("描述（可选）") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Appear(2, animOn) {
                PrioritySlider(priority = draft.priority, onSelected = vm::updatePriority)
            }

            Appear(3, animOn) {
                Column {
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
                }
            }

            Appear(4, animOn) {
                TimeField(label = "开始时间", value = draft.startTime, onSet = vm::setStart)
            }
            Appear(5, animOn) {
                TimeField(label = "截止时间", value = draft.dueTime, onSet = vm::setDue)
            }

            Appear(6, animOn) {
                OutlinedTextField(
                    value = draft.tags,
                    onValueChange = vm::updateTags,
                    label = { Text("标签（逗号分隔，可选）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // 提醒预设选择入口
            Appear(7, animOn) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showPresetSheet = true }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "提醒",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            draft.reminderPolicy.summary(),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        // 提醒点全部已过 = 保存后永远不响；这里是唯一能提前告知的位置。
                        if (draft.reminderPolicy.hasOnlyPastTriggers(
                                draft.startTime,
                                draft.dueTime,
                                System.currentTimeMillis(),
                            )
                        ) {
                            Text(
                                "所选提醒时间已过，保存后不会响",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    Text(
                        "选择 ›",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            if (error != null) {
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (showPresetSheet) {
        PresetPickerSheet(
            presets = presets,
            currentPolicy = draft.reminderPolicy,
            startTime = draft.startTime,
            dueTime = draft.dueTime,
            onSelect = {
                vm.selectPolicy(it)
                showPresetSheet = false
            },
            onManage = {
                showPresetSheet = false
                onOpenPresets()
            },
            onDismiss = { showPresetSheet = false },
        )
    }
}

/** 提醒预设选择：不提醒 + 内置/自建预设单选 + 管理入口；缺锚点时间的预设灰化不可选。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetPickerSheet(
    presets: List<com.dboycht.taskplanner.model.ReminderPreset>,
    currentPolicy: ReminderPolicy,
    startTime: Long?,
    dueTime: Long?,
    onSelect: (ReminderPolicy) -> Unit,
    onManage: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Text(
            "提醒预设",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        // 「不提醒」恒可用
        PresetRow(
            title = "不提醒",
            subtitle = "关闭任务提醒",
            selected = currentPolicy.points.isEmpty(),
            enabled = true,
            reason = null,
            onClick = { onSelect(ReminderPolicy(emptyList())) },
        )
        HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
        presets.forEach { preset ->
            PresetRow(
                title = preset.name,
                subtitle = preset.policy.summary(),
                selected = currentPolicy.points == preset.policy.points &&
                    preset.policy.isUsableFor(startTime, dueTime),
                enabled = preset.policy.isUsableFor(startTime, dueTime),
                reason = preset.policy.missingAnchorLabel(startTime, dueTime),
                onClick = { onSelect(preset.policy) },
            )
        }
        HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
        TextButton(
            onClick = onManage,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text("管理预设（新建 / 编辑 / 删除）")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PresetRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    reason: String?,
    onClick: () -> Unit,
) {
    val rowAlpha = if (enabled) 1f else 0.38f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.alpha(rowAlpha),
            )
            if (!enabled && reason != null) {
                // 不可用原因用强调色小字（灰化行内唯一的彩色信号）
                Text(
                    reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            } else {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.alpha(rowAlpha),
                )
            }
        }
        if (selected && enabled) {
            Text(
                "✓",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
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
                    // 常用时刻快捷档：点一下直接选定（要精调再用上方滚轮）
                    QuickTimeChips(
                        onPick = { hour, minute ->
                            val dateMillis = pickedDateMillis
                            if (dateMillis != null) {
                                val localDate = Instant.ofEpochMilli(dateMillis)
                                    .atZone(ZoneOffset.UTC).toLocalDate()
                                val millis = localDate
                                    .atTime(hour, minute)
                                    .atZone(zone).toInstant().toEpochMilli()
                                onSet(millis)
                            }
                            showTimePicker = false
                        },
                    )
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

/** 常用时刻快捷档（时:分）。点选即确定，免转滚轮。 */
private val QUICK_TIMES = listOf(
    8 to 0, 10 to 0, 12 to 0, 14 to 0, 16 to 0, 18 to 0, 20 to 0, 22 to 0,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickTimeChips(onPick: (hour: Int, minute: Int) -> Unit) {
    Column {
        Text(
            "常用时刻",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            QUICK_TIMES.forEach { (h, m) ->
                FilterChip(
                    selected = false,
                    onClick = { onPick(h, m) },
                    label = { Text("%02d:%02d".format(h, m)) },
                )
            }
        }
    }
}

/** 无极优先级滑块：整条渐变带（蓝→红）叠透明轨道 Slider，thumb 取当前值颜色；数值滚动切换。 */
@Composable
private fun PrioritySlider(priority: Int, onSelected: (Int) -> Unit) {
    // 颜色平滑过渡（thumb、数值、渐变位置联动）
    val animatedColor by animateColorAsState(
        targetValue = priorityColor(priority),
        animationSpec = tween(180),
        label = "priority-color",
    )
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "优先级",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            // 数值滚动切换（旧值上移淡出、新值下移淡入）
            AnimatedContent(
                targetState = priority,
                transitionSpec = {
                    (
                        slideInVertically(tween(180)) { it / 2 } + fadeIn(tween(180))
                        ) togetherWith (
                        slideOutVertically(tween(180)) { -it / 2 } + fadeOut(tween(140))
                        )
                },
                label = "priority-value",
            ) { value ->
                Text(
                    "$value",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = animatedColor,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            // 渐变色带（蓝 → 青/绿/黄/橙 → 红），与 Slider 轨道同高重合
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp),
            ) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = (0..100 step 10).map { priorityColor(it) },
                    ),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
            Slider(
                value = priority.toFloat(),
                onValueChange = { onSelected(it.roundToInt()) },
                valueRange = 0f..100f,
                colors = SliderDefaults.colors(
                    thumbColor = animatedColor,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            Text(
                "低",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Start,
            )
            Text(
                "高",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
    }
}

private val dateTimeFmt = DateTimeFormatter.ofPattern("MM-dd HH:mm")

private fun fmtDateTime(epochMillis: Long): String =
    dateTimeFmt.format(Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()))
