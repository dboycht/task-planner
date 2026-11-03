package com.dboycht.taskplanner.ui.list

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.dboycht.taskplanner.model.Task
import com.dboycht.taskplanner.reminder.ReminderHealth
import com.dboycht.taskplanner.reminder.ReminderHealthSnapshot
import com.dboycht.taskplanner.reminder.backstageGuidanceBody
import com.dboycht.taskplanner.reminder.backstageGuidanceTitle
import com.dboycht.taskplanner.reminder.collectReminderTriggerTimes
import com.dboycht.taskplanner.reminder.isVendorManagedRom
import com.dboycht.taskplanner.reminder.openBackstageSettings
import com.dboycht.taskplanner.reminder.suspectMissedReminder

/**
 * 厂商「耗电行为控制」把提醒压掉时的引导横幅。
 *
 * 与 [ReminderPermissionCard] 的分工：那张卡管**权限**（通知 / 精确闹钟），有 API 可查；
 * 这张卡管**ROM 的耗电策略**，没有 API 可查，只能靠「闹钟到点后到底响没响」反推
 * （见 `BackstagePolicy` 与 ERROR.md E5）。两者都正常时整块不渲染。
 *
 * 用户点「去设置」后横幅先隐藏，回到应用再重新评估 —— 避免它一直杵在那里，
 * 也给用户一个「我处理过了」的确认反馈。
 */
@Composable
fun BackstageWarningCard(tasks: List<Task>, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    var dismissed by remember { mutableStateOf(false) }

    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // 从系统设置页回来：重新看一次「有没有按时响过」。
                dismissed = false
                refresh++
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }

    val show = remember(refresh, dismissed, tasks) {
        if (dismissed || tasks.isEmpty() || !isVendorManagedRom()) {
            false
        } else {
            suspectMissedReminder(
                ReminderHealthSnapshot(
                    dueTriggerTimes = collectReminderTriggerTimes(tasks),
                    lastDeliveredAt = ReminderHealth.lastDeliveredAt(context),
                    now = System.currentTimeMillis(),
                )
            ) != null
        }
    }
    if (!show) return

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Column(Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)) {
            Text(
                "⏰ " + backstageGuidanceTitle(),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                backstageGuidanceBody(),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { dismissed = true }) { Text("以后再说") }
                TextButton(onClick = {
                    openBackstageSettings(context)
                    dismissed = true
                }) { Text("去设置") }
            }
        }
    }
}
