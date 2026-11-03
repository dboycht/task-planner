package com.dboycht.taskplanner.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.dboycht.taskplanner.MainActivity
import com.dboycht.taskplanner.R

/**
 * 小组件刷新入口。全量重建 RemoteViews + 通知数据集变化，幂等；
 * 在应用启动 / maintenanceSync（任务增删改、勾完成、开机、改时间、闹钟到点）末尾调用。
 * updatePeriodMillis 30 分钟只是兜底，正常更新全靠主动触发。
 */
object WidgetUpdater {

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = manager.getAppWidgetIds(
            ComponentName(context, TaskWidgetProvider::class.java)
        )
        if (ids.isEmpty()) return
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val views = RemoteViews(context.packageName, R.layout.widget_layout).apply {
            setRemoteAdapter(R.id.widget_list, Intent(context, TaskWidgetService::class.java))
            setEmptyView(R.id.widget_list, R.id.widget_empty)
            // 行点击 = 打开 App 并直达任务（fillInIntent 带任务 id，见 TaskWidgetService.Factory）。
            setPendingIntentTemplate(R.id.widget_list, openIntent)
        }
        manager.updateAppWidget(ids, views)
        manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
    }
}
