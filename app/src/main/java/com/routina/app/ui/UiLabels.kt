package com.routina.app.ui

import com.routina.app.engine.RoutineExecutor
import com.routina.app.model.Action
import com.routina.app.model.AppTarget
import com.routina.app.model.BtDevice
import com.routina.app.model.CompareOp
import com.routina.app.model.Condition
import com.routina.app.model.MathOp
import com.routina.app.model.GeoCircle
import com.routina.app.model.RingerModeType
import com.routina.app.model.Routine
import com.routina.app.model.TimeMode
import com.routina.app.model.Trigger
import com.routina.app.model.TriggerSource
import com.routina.app.model.VolumeStream
import com.routina.app.model.usesRightOperand

/** 星期顯示：ISO-8601（1=週一 … 7=週日） */
val WEEKDAY_LABELS = listOf("一", "二", "三", "四", "五", "六", "日")

fun weekdayLabel(isoDay: Int): String = WEEKDAY_LABELS.getOrElse(isoDay - 1) { "?" }

/** 積木參數欄用的星期摘要：每天 / 週一至週五 / 週末 / 週一三五 */
fun weekdaysLabel(days: Set<Int>): String = when {
    days.isEmpty() || days.size == 7 -> "每天"
    days == setOf(1, 2, 3, 4, 5) -> "週一至週五"
    days == setOf(6, 7) -> "週末"
    else -> "週" + days.sorted().joinToString("") { weekdayLabel(it) }
}

fun timeLabel(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

/** 定時觸發的時刻參數欄：固定時間顯示 HH:mm，日出日落顯示「日落後 30 分」 */
fun timeModeLabel(trigger: Trigger.Time): String = when (trigger.mode) {
    TimeMode.FIXED -> timeLabel(trigger.hour, trigger.minute)
    TimeMode.SUNRISE -> sunLabel("日出", trigger.offsetMinutes)
    TimeMode.SUNSET -> sunLabel("日落", trigger.offsetMinutes)
}

fun timeModeName(mode: TimeMode): String = when (mode) {
    TimeMode.FIXED -> "固定時間"
    TimeMode.SUNRISE -> "日出"
    TimeMode.SUNSET -> "日落"
}

private fun sunLabel(event: String, offsetMinutes: Int): String = when {
    offsetMinutes > 0 -> "$event 後 $offsetMinutes 分"
    offsetMinutes < 0 -> "$event 前 ${-offsetMinutes} 分"
    else -> event
}

fun triggerTypeName(trigger: Trigger): String = when (trigger) {
    is Trigger.Manual -> "手動執行"
    is Trigger.Time -> "定時"
    is Trigger.PowerConnected -> "開始充電"
    is Trigger.PowerDisconnected -> "停止充電"
    is Trigger.BatteryBelow -> "電量低於"
    is Trigger.BatteryAbove -> "電量高於"
    is Trigger.LocationEnter -> "進入區域"
    is Trigger.LocationExit -> "離開區域"
    is Trigger.BtConnected -> "藍牙連接"
    is Trigger.BtDisconnected -> "藍牙斷開"
    is Trigger.WifiConnected -> "Wi-Fi 連線"
    is Trigger.WifiDisconnected -> "Wi-Fi 斷線"
    is Trigger.AirplaneMode -> "飛航模式"
    is Trigger.DndChanged -> "勿擾模式"
    is Trigger.PowerSave -> "省電模式"
    is Trigger.NfcTag -> "NFC 標籤"
    is Trigger.NotificationPosted -> "收到通知"
    is Trigger.AppState -> "App 開啟／關閉"
}

/** 區域參數欄的內容：有取名字就顯示名字，否則顯示「經度, 緯度 ±半徑m」 */
fun geoCircleLabel(circle: GeoCircle): String = when {
    circle.label.isNotBlank() -> truncate(circle.label, 16)
    !circle.isConfigured -> "選擇地點"
    else -> "%.4f, %.4f ±%dm".format(circle.lat, circle.lng, circle.radiusM)
}

/** 藍牙裝置參數欄：空位址＝任一裝置；有位址但沒名稱（缺權限）時退回顯示位址 */
fun btDeviceLabel(device: BtDevice): String = when {
    device.address.isBlank() -> "任一裝置"
    device.name.isNotBlank() -> truncate(device.name, 16)
    else -> device.address
}

fun ssidLabel(ssid: String): String =
    if (ssid.isBlank()) "任一網路" else truncate(ssid, 16)

fun onOffLabel(on: Boolean): String = if (on) "開啟時" else "關閉時"

/**
 * NFC 標籤參數欄：取過名字就顯示名字，只掃過標籤顯示 UID 尾段
 * （UID 全長對使用者沒有意義，尾段足以區分兩張標籤），都沒有則提示去掃描。
 */
fun nfcTagLabel(trigger: Trigger.NfcTag): String = when {
    trigger.label.isNotBlank() -> truncate(trigger.label, 16)
    trigger.uid.isNotBlank() -> "標籤 " + trigger.uid.takeLast(6)
    else -> "掃描標籤"
}

/** App 參數欄：空的 package 代表還沒選（或通知觸發的「任一 App」，由 [blankLabel] 決定文案） */
fun appTargetLabel(target: AppTarget, blankLabel: String): String = when {
    target.packageName.isBlank() -> blankLabel
    target.appName.isNotBlank() -> truncate(target.appName, 16)
    else -> truncate(target.packageName, 20)
}

/** 通知關鍵字參數欄：空的代表不過濾內容 */
fun keywordLabel(keyword: String): String =
    if (keyword.isBlank()) "不限內容" else "含「${truncate(keyword, 12)}」"

fun actionTypeName(action: Action): String = when (action) {
    is Action.Notify -> "顯示通知"
    is Action.OpenApp -> "開啟 App"
    is Action.OpenUrl -> "開啟網址"
    is Action.Share -> "分享"
    is Action.MediaVolume -> "音量"
    is Action.RingerMode -> "響鈴模式"
    is Action.Bluetooth -> "藍牙"
    is Action.Flashlight -> "手電筒"
    is Action.Speak -> "朗讀文字"
    is Action.Vibrate -> "震動"
    is Action.Dnd -> "勿擾模式"
    is Action.Brightness -> "螢幕亮度"
    is Action.Http -> "HTTP 請求"
    is Action.MediaKey -> "播放控制"
    is Action.Wait -> "等待"
    is Action.Clipboard -> "複製到剪貼簿"
    is Action.TakePhoto -> "拍照"
    is Action.BurstPhoto -> "連拍"
    is Action.RecordAudio -> "錄音"
    is Action.PlaySound -> "播放音效"
    is Action.SetAlarm -> "設定鬧鐘"
    is Action.Text -> "文字"
    is Action.SetVariable -> "設定變數"
    is Action.SetGlobalVariable -> "設定全域變數"
    is Action.Calculate -> "計算"
    is Action.Expression -> "運算式"
    is Action.AskInput -> "詢問輸入"
    is Action.ChooseMenu -> "選單選擇"
    is Action.IfBegin -> "如果"
    is Action.ElseIf -> "否則如果"
    is Action.Else -> "否則"
    is Action.EndIf -> "結束如果"
    is Action.WhileBegin -> "一直重複…當"
    is Action.EndWhile -> "結束重複"
    is Action.RepeatBegin -> "重複 N 次"
    is Action.EndRepeat -> "結束重複 N 次"
    is Action.RunRoutine -> "執行程序"
}

/** 動作積木上的標籤文字（參數欄前的敘述） */
fun actionBlockLabel(action: Action): String = when (action) {
    is Action.Notify -> "顯示通知"
    is Action.OpenApp -> "開啟"
    is Action.OpenUrl -> "開啟網址"
    is Action.Share -> "分享"
    is Action.MediaVolume -> "${volumeStreamName(action.stream)}音量設為"
    is Action.RingerMode -> "響鈴模式切為"
    is Action.Bluetooth -> "藍牙"
    is Action.Flashlight -> "手電筒"
    is Action.Speak -> "朗讀"
    is Action.Vibrate -> "震動"
    is Action.Dnd -> "勿擾模式"
    is Action.Brightness -> "螢幕亮度設為"
    is Action.Http -> "HTTP ${httpMethodName(action.method)}"
    is Action.MediaKey -> "播放控制"
    is Action.Wait -> "等待"
    is Action.Clipboard -> "複製到剪貼簿"
    is Action.TakePhoto -> "拍照"
    is Action.BurstPhoto -> "連拍"
    is Action.RecordAudio -> "錄音"
    is Action.PlaySound -> "播放"
    is Action.SetAlarm -> "設定鬧鐘"
    is Action.Text -> "文字"
    is Action.SetVariable -> "設定變數"
    is Action.SetGlobalVariable -> "設定全域變數"
    is Action.Calculate -> "計算"
    is Action.Expression -> "運算式"
    is Action.AskInput -> "詢問輸入"
    is Action.ChooseMenu -> "選單選擇"
    is Action.IfBegin -> "如果"
    is Action.ElseIf -> "否則如果"
    is Action.Else -> "否則"
    is Action.EndIf -> "結束如果"
    is Action.WhileBegin -> "當"
    is Action.EndWhile -> "結束重複"
    is Action.RepeatBegin -> "重複"
    is Action.EndRepeat -> "結束重複"
    is Action.RunRoutine -> "執行程序"
}

/** 動作積木參數欄的內容 */
fun actionParamText(action: Action): String = when (action) {
    is Action.Notify -> action.title.ifBlank { action.message.ifBlank { "未設定" } }
    is Action.OpenApp -> action.appLabel.ifBlank { action.packageName.ifBlank { "未選擇" } }
    is Action.OpenUrl -> truncate(action.url.ifBlank { "未設定" }, 20)
    // 多行分享文字在單行參數欄裡先攤平再截斷（沿用剪貼簿的處理）
    is Action.Share -> truncate(action.text.flattenLines().ifBlank { "未設定" }, 15)
    is Action.MediaVolume -> numParam(action.percentExpr, "${action.percent}%")
    is Action.RingerMode -> ringerModeName(action.mode)
    is Action.Bluetooth -> if (action.enable) "開啟" else "關閉"
    is Action.Flashlight -> if (action.on) "開啟" else "關閉"
    is Action.Speak -> truncate(action.text.ifBlank { "未設定" }, 20)
    is Action.Vibrate -> numParam(action.millisExpr, "${action.millis} 毫秒")
    is Action.Dnd -> if (action.on) "開啟" else "關閉"
    is Action.Brightness -> numParam(action.percentExpr, "${action.percent}%")
    is Action.Http -> truncate(action.url.ifBlank { "未設定" }, 20)
    is Action.MediaKey -> mediaKeyName(action.key)
    is Action.Wait -> numParam(action.secondsExpr, "${action.seconds} 秒")
    // 多行文字在單行參數欄裡只看得到第一行，先把換行攤平再截斷
    is Action.Clipboard -> truncate(action.text.flattenLines().ifBlank { "未設定" }, 15)
    is Action.TakePhoto -> lensName(action.lensBack)
    is Action.BurstPhoto -> numParam(action.countExpr, "${action.count} 張")
    is Action.RecordAudio -> numParam(action.secondsExpr, "${action.seconds} 秒")
    is Action.PlaySound -> soundTypeName(action.type)
    is Action.SetAlarm -> timeLabel(action.hour, action.minute)
    // 積木參數欄顯示原始 template（含 token 文字），不做即時求值
    is Action.Text -> truncate(action.template.flattenLines().ifBlank { "未設定" }, 20)
    is Action.SetVariable -> {
        val name = action.name.ifBlank { "未命名" }
        val value = action.template.flattenLines()
        if (value.isBlank()) name else truncate("$name = $value", 20)
    }

    is Action.SetGlobalVariable -> {
        val name = action.name.ifBlank { "未命名" }
        val value = action.template.flattenLines()
        if (value.isBlank()) name else truncate("$name = $value", 20)
    }

    is Action.Calculate -> truncate(
        "${action.name.ifBlank { "未命名" }} = ${action.left} ${mathOpLabel(action.op)} ${action.right}",
        20
    )

    is Action.Expression -> truncate(action.text.flattenLines().ifBlank { "未設定" }, 24)

    // 積木參數欄顯示提示文字（截斷）；未填提示時退回顯示變數名稱
    is Action.AskInput ->
        truncate(action.prompt.flattenLines().ifBlank { action.variableName.ifBlank { "未設定" } }, 20)

    is Action.ChooseMenu ->
        truncate(action.prompt.flattenLines().ifBlank { action.variableName.ifBlank { "未設定" } }, 20)

    is Action.IfBegin -> conditionSummary(action.condition)
    is Action.ElseIf -> conditionSummary(action.condition)
    is Action.WhileBegin -> conditionSummary(action.condition)
    is Action.RepeatBegin -> numParam(action.countExpr, "${action.count} 次")
    is Action.Else -> "其餘情況"
    is Action.EndIf, is Action.EndWhile, is Action.EndRepeat -> ""
    is Action.RunRoutine -> action.routineName.ifBlank { "選擇程序" }
}

/** 判斷式的積木參數摘要，例如「電量 > 20」 */
fun conditionSummary(c: Condition): String =
    if (c.op.usesRightOperand) {
        truncate("${c.left.ifBlank { "(空)" }} ${compareOpLabel(c.op)} ${c.right}", 20)
    } else {
        truncate("${c.left.ifBlank { "(空)" }} ${compareOpLabel(c.op)}", 20)
    }

/** 運算子的短標籤（積木參數用；下拉選單另有完整文字，見 ActionEditor） */
fun compareOpLabel(op: CompareOp): String = when (op) {
    CompareOp.EQUALS -> "="
    CompareOp.NOT_EQUALS -> "≠"
    CompareOp.GREATER -> ">"
    CompareOp.GREATER_EQUAL -> "≥"
    CompareOp.LESS -> "<"
    CompareOp.LESS_EQUAL -> "≤"
    CompareOp.CONTAINS -> "包含"
    CompareOp.NOT_CONTAINS -> "不包含"
    CompareOp.IS_EMPTY -> "為空"
    CompareOp.IS_NOT_EMPTY -> "不為空"
    CompareOp.IS_TRUE -> "為真"
    CompareOp.IS_FALSE -> "為假"
}

/** 算術運算子的短符號（積木參數用） */
fun mathOpLabel(op: MathOp): String = when (op) {
    MathOp.ADD -> "+"
    MathOp.SUBTRACT -> "−"
    MathOp.MULTIPLY -> "×"
    MathOp.DIVIDE -> "÷"
    MathOp.MODULO -> "餘"
}

/** 數值參數欄：Expr 非空顯示 Expr（數字或截斷後的 `{{...}}`），否則顯示原本的整數文字 */
private fun numParam(expr: String, intText: String): String =
    if (expr.isBlank()) intText else truncate(expr.trim(), 16)

/** 把多行文字攤成一行（換行改為空格），供單行參數欄顯示 */
private fun String.flattenLines(): String =
    trim().replace(Regex("\\s*\\R+\\s*"), " ")

private fun truncate(text: String, max: Int): String =
    if (text.length <= max) text else text.take(max) + "…"

/**
 * 首頁搜尋的比對字串：名稱 + 觸發摘要 + 每個動作的類型名與參數文字。
 * 沿用既有的 triggerSummary / actionTypeName / actionParamText，過濾時不分大小寫比對。
 */
fun routineSearchText(routine: Routine): String = buildString {
    append(routine.name)
    append(' ').append(triggerSummary(routine.trigger))
    routine.actions.forEach { action ->
        append(' ').append(actionTypeName(action))
        append(' ').append(actionParamText(action))
    }
}

fun ringerModeName(mode: RingerModeType): String = RoutineExecutor.ringerLabel(mode)

fun volumeStreamName(stream: VolumeStream): String = RoutineExecutor.volumeStreamLabel(stream)

fun mediaKeyName(key: String): String = RoutineExecutor.mediaKeyLabel(key)

fun lensName(lensBack: Boolean): String = RoutineExecutor.lensLabel(lensBack)

fun soundTypeName(type: String): String = RoutineExecutor.soundTypeLabel(type)

fun httpMethodName(method: String): String =
    if (method.equals(Action.METHOD_POST, ignoreCase = true)) Action.METHOD_POST else Action.METHOD_GET

fun triggerSourceName(source: TriggerSource): String = when (source) {
    TriggerSource.SCHEDULE -> "定時"
    TriggerSource.POWER -> "充電"
    TriggerSource.BATTERY -> "電量"
    TriggerSource.LOCATION -> "區域"
    TriggerSource.BLUETOOTH -> "藍牙"
    TriggerSource.WIFI -> "Wi-Fi"
    TriggerSource.SYSTEM -> "系統狀態"
    TriggerSource.NFC -> "NFC 標籤"
    TriggerSource.NOTIFICATION -> "通知"
    TriggerSource.APP -> "App"
    TriggerSource.MANUAL -> "手動"
}
