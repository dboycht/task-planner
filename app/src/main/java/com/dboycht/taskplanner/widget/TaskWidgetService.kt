package com.dboycht.taskplanner.widget

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.dboycht.taskplanner.MainActivity
import com.dboycht.taskplanner.R
import com.dboycht.taskplanner.TaskPlannerApp
import com.dboycht.taskplanner.model.WidgetModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.time.ZoneId

/**
 * 小组件列表数据源（RemoteViewsFactory）。
 * onDataSetChanged 在 binder 线程同步读 Room（快照小、查询轻，runBlocking 可接受）。
 */
class TaskWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): Factory = Factory(applicationContext)

    class Factory(private val context: Context) : RemoteViewsFactory {

        private var rows: List<WidgetModel.Row> = emptyList()

        override fun onCreate() {}
        override fun onDestroy() {}
        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount(): Int = 2
        override fun hasStableIds(): Boolean = true

        override fun onDataSetChanged() {
            val app = context.applicationContext as TaskPlannerApp
            rows = runBlocking(Dispatchers.IO) {
                WidgetModel.buildRows(
                    app.repository.allTasks(),
                    System.currentTimeMillis(),
                    ZoneId.systemDefault(),
                )
            }
        }

        override fun getCount(): Int = rows.size

        override fun getItemId(position: Int): Long = when (val row = rows[position]) {
            is WidgetModel.Row.Header -> -(row.label.hashCode().toLong())
            is WidgetModel.Row.Item -> row.taskId
        }

        override fun getViewAt(position: Int): RemoteViews {
            return when (val row = rows[position]) {
                is WidgetModel.Row.Header ->
                    RemoteViews(context.packageName, R.layout.widget_row_header).apply {
                        setTextViewText(R.id.widget_header_text, row.label)
                    }

                is WidgetModel.Row.Item ->
                    RemoteViews(context.packageName, R.layout.widget_row_item).apply {
                        setTextViewText(R.id.widget_item_title, row.title)
                        if (row.timeText != null) {
                            setTextViewText(R.id.widget_item_time, row.timeText)
                        } else {
                            setViewVisibility(R.id.widget_item_time, android.view.View.GONE)
                        }
                        setInt(R.id.widget_dot, "setBackgroundColor", row.dotColor)
                        // 行点击直达任务编辑页（PendingIntentTemplate + fillIn 组合）。
                        setOnClickFillInIntent(
                            R.id.widget_row_item,
                            Intent().putExtra(MainActivity.EXTRA_TASK_ID, row.taskId),
                        )
                    }
            }
        }
    }
}
