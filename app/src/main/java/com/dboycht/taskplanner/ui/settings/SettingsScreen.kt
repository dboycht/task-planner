package com.dboycht.taskplanner.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dboycht.taskplanner.calendar.CalendarSettings

/**
 * 设置页（M2）：日历同步开关、权限状态、目标日历选择、存量回填。
 * 提醒相关引导仍在列表页横幅（权限卡），此处不重复。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val vm: SettingsViewModel = viewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { vm.refresh() }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refresh()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { vm.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("返回") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel("日历同步")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("同步任务到系统日历", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "任务保存后自动写入日历事件；提醒策略同时挂到日历提醒作兜底。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = state.syncEnabled,
                    onCheckedChange = {
                        vm.setSyncEnabled(it)
                    },
                    enabled = state.granted,
                )
            }

            HorizontalDivider()

            when {
                !state.granted -> {
                    Text(
                        "日历权限未授权，任务只保存在本应用内。",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_CALENDAR,
                                Manifest.permission.WRITE_CALENDAR,
                            )
                        )
                    }) { Text("授权日历（读 / 写）") }
                }

                else -> {
                    if (!state.syncEnabled) {
                        Text(
                            "同步已关闭；重新打开后新任务才会写入日历。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text("写入哪个日历？", style = MaterialTheme.typography.bodyLarge)
                    if (state.calendars.isEmpty()) {
                        Text(
                            "没有可写日历（本机日历存储不可用或被 ROM 限制）。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        state.calendars.forEach { cal ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { vm.selectCalendar(cal.id) }
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(
                                    selected = state.resolvedCalendarId == cal.id,
                                    onClick = { vm.selectCalendar(cal.id) },
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        if (cal.isOurs) "${cal.displayName}（本应用专属）" else cal.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        cal.accountName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        "看不到「Task Planner」专属日历？部分系统（如 ColorOS）默认隐藏本机日历，" +
                            "可在系统日历 App 的「设置 → 要显示的日历」里勾选，或改选上面其他日历。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    HorizontalDivider()

                    Text("存量任务回填", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (state.backfillDone) "回填已做过（跳过过也算）。新建/修改的任务会自动同步，无需重复回填。"
                        else "还有 ${state.uncompletedWithoutEvent} 条未完成任务没有日历日程。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(
                        onClick = { vm.backfill() },
                        enabled = !state.busy,
                    ) {
                        Text(if (state.busy) "同步中…" else "把未完成任务同步到日历")
                    }
                }
            }

            HorizontalDivider()
            SectionLabel("说明")
            Text(
                "· 日历只是任务的「投影」：真数据始终在本应用（Room）里，删掉日历事件不会丢任务，应用会自动重建。\n" +
                    "· 事件标题格式：[优先级] 标题；已完成任务保留事件并加 ✓ 前缀。\n" +
                    "· 时区/改时间：应用开机与系统时间变化时会自动重排提醒。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold,
    )
}
