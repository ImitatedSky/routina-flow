package com.routina.app.engine

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.routina.app.R
import com.routina.app.data.RoutineRepository
import com.routina.app.ui.theme.routineAccent

/**
 * 單一捷徑小工具（預設 2x2）：整塊就是一支程序，點一下直接執行。
 *
 * 與清單型小工具不同，這個尺寸放不下清單，因此在放置時由 WidgetConfigActivity 選一支程序，
 * 「哪個小工具實例綁哪支程序」記在 SharedPreferences（key＝appWidgetId）。
 * 執行仍走同一條 [RunRoutineActivity] 跳板。
 */
class RoutinaShortcutWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            updateOne(context, appWidgetManager, id)
        }
    }

    /** 小工具被移除時一併刪掉綁定，不留孤兒設定 */
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val editor = prefs(context).edit()
        for (id in appWidgetIds) {
            editor.remove(id.toString())
        }
        editor.apply()
    }

    companion object {
        private const val PREFS_NAME = "widget_shortcut"

        private fun prefs(context: Context): SharedPreferences =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        /** 設定畫面選好程序後呼叫：記住綁定並立刻畫出來 */
        fun bind(context: Context, appWidgetId: Int, routineId: String) {
            prefs(context).edit().putString(appWidgetId.toString(), routineId).apply()
            updateOne(context, AppWidgetManager.getInstance(context), appWidgetId)
        }

        /** 程序資料有異動時重畫所有實例（名稱、顏色可能改了，或程序已被刪除） */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, RoutinaShortcutWidgetProvider::class.java)
            )
            for (id in ids) {
                updateOne(context, manager, id)
            }
        }

        private fun updateOne(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_shortcut)
            val routineId = prefs(context).getString(appWidgetId.toString(), null)
            val routine = routineId?.let { RoutineRepository.get(context).findById(it) }

            if (routine == null) {
                // 綁定的程序被刪掉了：誠實顯示狀態，點擊改成開 App（讓使用者自己處理）
                views.setTextViewText(
                    R.id.widget_shortcut_name,
                    context.getString(R.string.widget_shortcut_missing)
                )
                views.setOnClickPendingIntent(R.id.widget_shortcut_root, openApp(context, appWidgetId))
            } else {
                views.setTextViewText(R.id.widget_shortcut_name, routine.displayName)
                views.setInt(
                    R.id.widget_shortcut_bg,
                    "setColorFilter",
                    routineAccent(routine).toArgb()
                )
                views.setOnClickPendingIntent(
                    R.id.widget_shortcut_root,
                    runRoutine(context, appWidgetId, routine.id)
                )
            }
            manager.updateAppWidget(appWidgetId, views)
        }

        // requestCode 用 appWidgetId：同一支程序放兩個小工具時，兩個 PendingIntent 才不會互相覆蓋
        private fun runRoutine(context: Context, appWidgetId: Int, routineId: String) =
            PendingIntent.getActivity(
                context,
                appWidgetId,
                RunRoutineActivity.intent(context, routineId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

        private fun openApp(context: Context, appWidgetId: Int): PendingIntent? {
            val launch = context.packageManager
                .getLaunchIntentForPackage(context.packageName)
                ?: return null
            return PendingIntent.getActivity(
                context,
                appWidgetId,
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
