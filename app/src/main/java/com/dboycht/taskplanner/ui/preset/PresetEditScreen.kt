package com.dboycht.taskplanner.ui.preset

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dboycht.taskplanner.model.ReminderAnchor
import com.dboycht.taskplanner.model.ReminderPoint
import com.dboycht.taskplanner.model.formatOffsetMinutes

/** 快捷提前量档（分钟）。 */
private val QUICK_OFFSETS = listOf(0, 10, 30, 60, 120, 1440, 2880, 4320, 10080)
private val CUSTOM_TAG = -1

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetEditScreen(
    presetKey: String,
    onBack: () -> Unit,
) {
    val vm: PresetEditViewModel = viewModel(key = "preset-edit-$presetKey")
    LaunchedEffect(presetKey) { vm.load(presetKey.toLongOrNull()) }
    val draft = vm.draft
    val isNew = draft.id == 0L
    val error = vm.error
    var showAddDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "新建预设" else "编辑预设") },
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
                value = draft.name,
                onValueChange = vm::updateName,
                label = { Text("预设名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                "提醒点（${draft.points.size} 个）",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            draft.points.forEachIndexed { index, point ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${anchorLabel(point.anchor)}前 ${formatOffsetMinutes(point.offsetMinutes)}"
                            .let { if (point.offsetMinutes == 0) anchorNow(point.anchor) else it },
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.removePoint(index) }) { Text("移除") }
                }
                HorizontalDivider()
            }
            OutlinedButton(onClick = { showAddDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text("＋ 添加提醒点")
            }

            if (error != null) {
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (!isNew) {
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text("删除此预设", color = MaterialTheme.colorScheme.error)
                }
            }
            Text(
                "提示：任务选用预设时按当时内容应用到任务；之后修改预设不影响已有任务。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showAddDialog) {
        AddPointDialog(
            onConfirm = {
                vm.addPoint(it)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("删除预设") },
            text = { Text("确定删除「${draft.name}」吗？已有任务的提醒不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    vm.delete(onBack)
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            },
        )
    }
}

/** 添加提醒点：锚点（开始/截止）+ 提前量（快捷档或自定义分钟）。 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun AddPointDialog(
    onConfirm: (ReminderPoint) -> Unit,
    onDismiss: () -> Unit,
) {
    var anchor by remember { mutableStateOf(ReminderAnchor.DUE) }
    var offsetMinutes by remember { mutableIntStateOf(60) }
    var customText by remember { mutableStateOf("") }
    val customSelected = offsetMinutes == CUSTOM_TAG
    val customValue = customText.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加提醒点") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ReminderAnchor.entries.forEachIndexed { index, a ->
                        SegmentedButton(
                            selected = anchor == a,
                            onClick = { anchor = a },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = ReminderAnchor.entries.size,
                            ),
                        ) { Text(if (a == ReminderAnchor.START) "开始时间" else "截止时间") }
                    }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    QUICK_OFFSETS.forEach { minutes ->
                        FilterChip(
                            selected = offsetMinutes == minutes,
                            onClick = { offsetMinutes = minutes },
                            label = {
                                Text(
                                    if (minutes == 0) "到点" else formatOffsetMinutes(minutes)
                                )
                            },
                        )
                    }
                    FilterChip(
                        selected = customSelected,
                        onClick = {
                            offsetMinutes = CUSTOM_TAG
                        },
                        label = { Text("自定义") },
                    )
                }
                if (customSelected) {
                    OutlinedTextField(
                        value = customText,
                        onValueChange = { customText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("提前多少分钟") },
                        singleLine = true,
                        isError = customSelected && customValue == null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val minutes = if (customSelected) customValue else offsetMinutes
                    if (minutes != null && minutes >= 0) {
                        onConfirm(ReminderPoint(anchor, minutes))
                    }
                },
                enabled = !customSelected || (customValue != null && customValue >= 0),
            ) { Text("添加") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}

private fun anchorLabel(anchor: ReminderAnchor): String =
    if (anchor == ReminderAnchor.START) "开始" else "截止"

private fun anchorNow(anchor: ReminderAnchor): String =
    if (anchor == ReminderAnchor.START) "开始时" else "截止时"
