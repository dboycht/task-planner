package com.dboycht.taskplanner.ui.list

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dboycht.taskplanner.TaskPlannerApp
import com.dboycht.taskplanner.calendar.CalendarSettings
import com.dboycht.taskplanner.calendar.CalendarSyncManager
import kotlinx.coroutines.launch

/**
 * 日历同步横幅（M2）。三种状态：
 * 1. 未授权 + 同步开关仍开 → 说明 + 授权按钮（拒绝后任务照存本地）；「不用了」关掉同步开关。
 * 2. 已授权 + 存量未回填 + 有未完成任务 → 提供一次性回填（用户已确认要回填方案）。
 * 3. 其余（已回填 / 已跳过 / 用户关了同步）→ 不渲染。
 * 从系统设置回来（ON_RESUME）重新检查，授权后横幅自动切换/消失。
 */
@Composable
fun CalendarPermissionCard(uncompletedCount: Int = 0) {
    val context = LocalContext.current
    val app = context.applicationContext as TaskPlannerApp
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    val granted = remember(refresh) { CalendarSyncManager.hasPermission(context) }
    val syncEnabled = remember(refresh) { CalendarSettings.syncEnabled(context) }
    val backfillDone = remember(refresh) { CalendarSettings.backfillDone(context) }
    val backfillDismissed = remember(refresh) { CalendarSettings.backfillDismissed(context) }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        refresh++
        // 授权成功 → 立即建专属日历并做一轮对齐（不用等下一次保存）。
        if (results.values.all { it }) {
            scope.launch { app.maintenanceSyncNow() }
        }
    }

    when {
        !granted && syncEnabled -> {
            BannerCard {
                Text("📅 日历同步还没开", style = MaterialTheme.typography.titleSmall)
                Text(
                    "授权后任务会自动写入系统日历（默认建一个专属日历「Task Planner」），并按提醒策略挂日历提醒作兜底。不授权也不影响任务本身。",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = {
                        CalendarSettings.setSyncEnabled(context, false)
                        refresh++
                    }) { Text("不用了") }
                    TextButton(onClick = {
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.READ_CALENDAR,
                                Manifest.permission.WRITE_CALENDAR,
                            )
                        )
                    }) { Text("授权日历") }
                }
            }
        }

        granted && !backfillDone && !backfillDismissed && uncompletedCount > 0 -> {
            BannerCard {
                Text("📅 把现有任务同步进日历？", style = MaterialTheme.typography.titleSmall)
                Text(
                    "检测到 $uncompletedCount 条未完成任务还没有日历日程。同步后可在系统日历里看到它们；任务在应用内的增删改会自动跟着日历走。",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = {
                        CalendarSettings.setBackfillDismissed(context)
                        refresh++
                    }) { Text("跳过") }
                    TextButton(onClick = {
                        CalendarSettings.setBackfillDone(context)
                        refresh++
                        scope.launch {
                            app.maintenanceSyncNow() // 先确保专属日历就位
                            CalendarSyncManager.backfillUncompleted(context, app.repository.allTasks())
                        }
                    }) { Text("同步 $uncompletedCount 条") }
                }
            }
        }
    }
}

@Composable
private fun BannerCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Column(Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            content()
        }
    }
}
