package com.dboycht.taskplanner.widget

import android.appwidget.AppWidgetProvider

/** 桌面小组件入口（RemoteViews 列表）。数据与刷新见 WidgetUpdater / TaskWidgetService。 */
class TaskWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: android.content.Context,
        appWidgetManager: android.appwidget.AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        WidgetUpdater.updateAll(context)
    }
}
