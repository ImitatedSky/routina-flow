package com.routina.app.engine

import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import com.routina.app.R
import com.routina.app.data.RoutineRepository
import com.routina.app.model.Routine

/**
 * 供應 [RoutinaWidgetProvider] 清單資料的 RemoteViewsService。
 *
 * 每列讀取 [RoutineRepository] 目前的程序快照建一個 RemoteViews，並掛上只帶該列 routineId 的
 * fill-in intent；與 provider 設定的樣板合併後即可啟動對應程序。資料在 [onDataSetChanged]
 * 一次性讀入，provider 的 refresh 會觸發它重讀。
 */
class RoutinaWidgetService : RemoteViewsService() {

    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        RoutinaWidgetFactory(applicationContext)
}

private class RoutinaWidgetFactory(
    private val context: Context
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

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_item)
        // 邊界保護：資料在渲染之間變短時回空白列，不崩潰
        val routine = routines.getOrNull(position) ?: return views
        views.setTextViewText(R.id.widget_item_name, routine.name.ifBlank { "未命名" })
        // 只帶這一列的 routineId；RunRoutineActivity 讀 EXTRA_ROUTINE_ID 執行
        val fillIn = Intent().putExtra(RunRoutineActivity.EXTRA_ROUTINE_ID, routine.id)
        views.setOnClickFillInIntent(R.id.widget_item_row, fillIn)
        return views
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long =
        routines.getOrNull(position)?.id?.hashCode()?.toLong() ?: position.toLong()

    override fun hasStableIds(): Boolean = true
}
