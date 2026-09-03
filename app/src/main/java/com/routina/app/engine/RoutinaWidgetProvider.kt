package com.routina.app.engine

import com.routina.app.R

/**
 * 桌面小工具（預設 3x3）：把例行程序列成可捲動清單，點一列就執行那支程序。
 *
 * 清單資料由 [RoutinaWidgetService] 供應（RemoteViews 集合的標準做法）。點列走既有的
 * [RunRoutineActivity] 跳板（透明、以 MANUAL 執行、跑完顯示 Toast），與快速設定磚／桌面捷徑
 * 同一條執行路徑，不重造輪子。共同流程見 [CollectionWidgetProvider]。
 */
class RoutinaWidgetProvider : CollectionWidgetProvider() {
    override val layoutRes = R.layout.widget_routina
    override val itemStyle = RoutinaWidgetService.STYLE_ROW
}

/** 一排捷徑（預設 4x1）：改用色塊磚格狀排列，一眼看到常用程序、不必捲動。 */
class RoutinaRowWidgetProvider : CollectionWidgetProvider() {
    override val layoutRes = R.layout.widget_row
    override val itemStyle = RoutinaWidgetService.STYLE_TILE
}

/**
 * 直排捷徑（預設 1x4）：與一排捷徑同一個格狀版面，只有放置時的預設尺寸不同
 * （GridView 用 auto_fit，一格寬時自然排成單欄）。
 * 一格寬放不下文字列，所以這個尺寸走色塊磚、靠顏色辨識。
 */
class RoutinaColumnWidgetProvider : CollectionWidgetProvider() {
    override val layoutRes = R.layout.widget_row
    override val itemStyle = RoutinaWidgetService.STYLE_TILE
}
