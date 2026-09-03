package com.routina.app.engine

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.routina.app.R

/**
 * 桌面小工具：把例行程序列成可捲動清單，點一列就執行那支程序。
 *
 * 清單資料由 [RoutinaWidgetService] 供應（RemoteViews 集合的標準做法）。點列走既有的
 * [RunRoutineActivity] 跳板（透明、以 MANUAL 執行、跑完顯示 Toast），與快速設定磚／桌面捷徑
 * 同一條執行路徑，不重造輪子。
 */
class RoutinaWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, R.layout.widget_routina)

            // 清單的資料來源：指向 RemoteViewsService。data 塞入含 appWidgetId 的唯一 URI，
            // 避免多個小工具實例的 adapter intent 被視為相同而共用、不各自更新。
            val serviceIntent = Intent(context, RoutinaWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_list, serviceIntent)
            // 清單為空時由系統改顯示這個 view（空狀態文字）
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)

            // 點列的樣板 intent：實際的 routineId 由每列的 fill-in intent 帶入後合併啟動。
            // FLAG_MUTABLE 是 Android 12+ 讓 fill-in 能改寫樣板的必要條件。
            val template = Intent(context, RunRoutineActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
            val pending = PendingIntent.getActivity(
                context,
                id,
                template,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            views.setPendingIntentTemplate(R.id.widget_list, pending)

            appWidgetManager.updateAppWidget(id, views)
        }
    }

    companion object {
        /**
         * 例行程序有異動（新增／編輯／刪除／排序）後呼叫：通知所有小工具實例的清單重新讀資料，
         * 由 [RoutineManager] 在存檔／刪除處觸發。沒有任何小工具時直接跳過。
         */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, RoutinaWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
        }
    }
}
