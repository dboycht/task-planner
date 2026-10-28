package com.dboycht.taskplanner.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.dboycht.taskplanner.MainActivity
import com.dboycht.taskplanner.R
import com.dboycht.taskplanner.model.Task
import java.time.ZoneId

/**
 * 通知渠道与通知构建。渠道属「任务提醒」，重要性 HIGH（弹出横幅 + 声音）。
 * 通知权限被拒时 notify 会被系统静默丢弃，调用前用 [canPost] 兜底并在 UI 引导。
 */
object NotificationHelper {

    const val CHANNEL_ID = "task_reminder"

    /** 幂等建渠道（重复调用不会重置用户改过的渠道设置）。Android 8.0+ 必须。 */
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "任务提醒",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "任务开始/截止前的提醒通知"
            enableVibration(true)
            setShowBadge(true)
        }
        manager.createNotificationChannel(channel)
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    fun show(context: Context, task: Task, reminder: DueReminder) {
        ensureChannel(context)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_TASK_ID, task.id)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            requestCode(task.id, "open"),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val doneIntent = Intent(context, ReminderReceiver::class.java).apply {
            action = ReminderReceiver.ACTION_MARK_DONE
            putExtra(ReminderReceiver.EXTRA_TASK_ID, task.id)
        }
        val donePending = PendingIntent.getBroadcast(
            context,
            requestCode(task.id, "done"),
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val body = reminderNotificationText(task, reminder, ZoneId.systemDefault())
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(task.title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(0, "标记完成", donePending)
            .build()

        if (!canPost(context)) return
        runCatching {
            NotificationManagerCompat.from(context).notify(requestCode(task.id, "notify"), notification)
        }
    }

    fun cancel(context: Context, taskId: Long) {
        NotificationManagerCompat.from(context).cancel(requestCode(taskId, "notify"))
    }

    /** 通知 id / 请求码都用 (taskId, 用途) 派生，保证同一任务的提醒只留一条。 */
    private fun requestCode(taskId: Long, kind: String): Int =
        ((taskId.toString() + ":" + kind).hashCode()) and 0x7fffffff
}
