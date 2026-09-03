package com.routina.app.engine

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.compose.ui.graphics.toArgb
import com.routina.app.R
import com.routina.app.data.RoutineRepository
import com.routina.app.model.Routine
import com.routina.app.ui.theme.routineAccent

/**
 * 供應清單型小工具（見 [CollectionWidgetProvider]）資料的 RemoteViewsService。
 *
 * 每一格讀取 [RoutineRepository] 目前的程序快照建一個 RemoteViews，並掛上只帶該格 routineId 的
 * fill-in intent；與 provider 設定的樣板合併後即可啟動對應程序。資料在 [onDataSetChanged]
 * 一次性讀入，[RoutinaWidgets.refresh] 會觸發它重讀。
 */
class RoutinaWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        RoutinaWidgetFactory(
            applicationContext,
            intent.getIntExtra(EXTRA_ITEM_STYLE, STYLE_ROW)
        )

    companion object {
        const val EXTRA_ITEM_STYLE = "item_style"

        /** 整列：名稱 ＋ ▶，適合有寬度的清單版面 */
        const val STYLE_ROW = 0

        /** 色塊磚：程序色的圓角方塊 ＋ 置中名稱，適合格狀排列 */
        const val STYLE_TILE = 1
    }
}

private class RoutinaWidgetFactory(
    private val context: Context,
    private val itemStyle: Int
) : RemoteViewsService.RemoteViewsFactory {

    // getViewAt 在 binder 執行緒被呼叫；用一份不可變快照避免中途變動造成越界
    private var routines: List<Routine> = emptyList()

    override fun onCreate() {}

    override fun onDataSetChanged() {
        routines = RoutineRepository.get(context).routines.value
    }

    override fun onDestroy() {
        routines = emptyList()
    }

    override fun getCount(): Int = routines.size

    override fun getViewAt(position: Int): RemoteViews =
        if (itemStyle == RoutinaWidgetService.STYLE_TILE) tileAt(position) else rowAt(position)

    private fun rowAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_item)
        // 邊界保護：資料在渲染之間變短時回空白列，不崩潰
        val routine = routines.getOrNull(position) ?: return views
        views.setTextViewText(R.id.widget_item_name, routine.displayName)
        views.setOnClickFillInIntent(R.id.widget_item_row, fillIn(routine))
        return views
    }

    private fun tileAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_tile)
        val routine = routines.getOrNull(position) ?: return views
        views.setTextViewText(R.id.widget_tile_name, routine.displayName)
        views.setInt(R.id.widget_tile_bg, "setColorFilter", routineAccent(routine).toArgb())
        views.setOnClickFillInIntent(R.id.widget_tile_root, fillIn(routine))
        return views
    }

    /** 只帶這一格的 routineId；[RunRoutineActivity] 讀 EXTRA_ROUTINE_ID 執行 */
    private fun fillIn(routine: Routine): Intent =
        Intent().putExtra(RunRoutineActivity.EXTRA_ROUTINE_ID, routine.id)

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long =
        routines.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}

/** 小工具上顯示的名稱：沒取名的程序在桌面上仍要有東西可點 */
internal val Routine.displayName: String get() = name.ifBlank { "未命名" }
