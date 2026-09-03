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
 * 以程序清單為內容的小工具共同流程（清單 3x3／精簡清單 1x4／一排捷徑 4x1 共用）。
 *
 * 三種尺寸的差別只有主版面與每格樣式，取資料與點擊執行的路徑完全一樣，所以放在同一個基底。
 * 各尺寸的版面都用 widget_list / widget_empty 兩個 id，[onUpdate] 因此不需要分支。
 */
abstract class CollectionWidgetProvider : AppWidgetProvider() {

    /** 這個尺寸的主版面，必須含 widget_list（清單／格狀）與 widget_empty（空狀態） */
    protected abstract val layoutRes: Int

    /** 每一格的樣式，見 [RoutinaWidgetService.STYLE_ROW] / [RoutinaWidgetService.STYLE_TILE] */
    protected abstract val itemStyle: Int

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            val views = RemoteViews(context.packageName, layoutRes)

            // 清單的資料來源：指向 RemoteViewsService。data 塞入含 appWidgetId 與樣式的唯一 URI，
            // 避免多個小工具實例的 adapter intent 被視為相同而共用、不各自更新。
            val serviceIntent = Intent(context, RoutinaWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                putExtra(RoutinaWidgetService.EXTRA_ITEM_STYLE, itemStyle)
                data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
            }
            views.setRemoteAdapter(R.id.widget_list, serviceIntent)
            // 清單為空時由系統改顯示這個 view（空狀態文字）
            views.setEmptyView(R.id.widget_list, R.id.widget_empty)

            // 點擊的樣板 intent：實際的 routineId 由每一格的 fill-in intent 帶入後合併啟動。
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
}

/** 所有桌面小工具的共同進入點（目前只有「資料變了要重畫」這一件事）。 */
object RoutinaWidgets {

    /** 清單型的三種尺寸；之後要再加尺寸，補一個 provider 進來即可 */
    private val collectionProviders = listOf(
        RoutinaWidgetProvider::class.java,
        RoutinaRowWidgetProvider::class.java,
        RoutinaColumnWidgetProvider::class.java
    )

    /**
     * 例行程序有異動（新增／編輯／刪除／排序）後呼叫，由 [RoutineManager] 在存檔／刪除處觸發。
     * 清單型小工具重讀資料；單一捷徑小工具重畫（綁定的程序可能改了名稱、顏色或被刪掉）。
     * 沒有放置任何實例的尺寸會自動跳過。
     */
    fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        for (provider in collectionProviders) {
            val ids = manager.getAppWidgetIds(ComponentName(context, provider))
            if (ids.isNotEmpty()) {
                manager.notifyAppWidgetViewDataChanged(ids, R.id.widget_list)
            }
        }
        RoutinaShortcutWidgetProvider.refreshAll(context)
    }
}
