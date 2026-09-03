package com.routina.app.ui

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.routina.app.engine.AlarmScheduler
import com.routina.app.engine.AlarmScheduler.SunLocation
import com.routina.app.model.Routine
import com.routina.app.model.RunLog
import com.routina.app.model.Trigger
import com.routina.app.model.geoCircle
import com.routina.app.ui.theme.RoutinaColors
import java.util.Calendar
import kotlin.math.roundToInt

/**
 * 卡片狀態晶片的資料與繪製（唯讀計算，不改動排程 / 執行邏輯）。
 *
 * 三類線索：下次執行（定時算時刻、事件型給觸發摘要）、上次結果（讀最近一筆 RunLog）、
 * 以及該例行程序所需但未授與的關鍵權限（點擊導向對應設定）。
 * 全部只做查詢與格式化，任何系統呼叫失敗都當作「未授與」顯示引導，不讓 App 崩潰。
 */

/**
 * 一次查好的權限狀態快照。
 *
 * 首頁已在 ON_RESUME 逐項查過權限，這裡把它們收成一份快照傳給每張卡片的
 * [missingPermissions]，避免在 LazyColumn 每次重繪時對每張卡片重複查詢系統。
 */
data class PermSnapshot(
    val canScheduleExact: Boolean,
    val notificationsEnabled: Boolean,
    val hasBackgroundLocation: Boolean,
    val canWriteSettings: Boolean,
    val canDrawOverlays: Boolean,
    val hasNotificationAccess: Boolean,
    val hasUsageAccess: Boolean,
    val hasCameraPermission: Boolean,
    val hasMicPermission: Boolean,
    val hasDndAccess: Boolean,
    val nfcAvailable: Boolean,
    val nfcEnabled: Boolean
)

/** 一個「所需但未授與」的權限：短標籤 + 導向對應設定頁的動作 */
data class PermIssue(
    val label: String,
    val open: (Context) -> Unit
)

// ---------- 下次執行 ----------

/**
 * 定時觸發的下次執行時刻摘要（「今天/明天/週X HH:mm」）；非定時觸發回 null。
 * 沿用 [AlarmScheduler] 既有的下一次觸發計算（含 mode 固定/日出日落 + offset + 星期）。
 */
fun nextRunSummary(
    routine: Routine,
    sunLocation: SunLocation?,
    now: Long = System.currentTimeMillis()
): String? {
    val trigger = routine.trigger as? Trigger.Time ?: return null
    val at = runCatching {
        AlarmScheduler.nextTriggerTime(
            trigger,
            sunLocation,
            Calendar.getInstance().apply { timeInMillis = now }
        )
    }.getOrNull() ?: return null
    return formatNextRun(at, now)
}

/** 把觸發時刻格式化為「今天 08:00」「明天 08:00」「週三 08:00」（純函式） */
fun formatNextRun(triggerAt: Long, now: Long = System.currentTimeMillis()): String {
    val at = Calendar.getInstance().apply { timeInMillis = triggerAt }
    val time = "%02d:%02d".format(at.get(Calendar.HOUR_OF_DAY), at.get(Calendar.MINUTE))
    return when (dayOffset(now, triggerAt)) {
        0 -> "今天 $time"
        1 -> "明天 $time"
        else -> "週${weekdayLabel(AlarmScheduler.isoDayOfWeek(at))} $time"
    }
}

/** 相差幾個「日曆天」（跨日算 1，不看時分）；四捨五入以吸收夏令時的小數誤差 */
private fun dayOffset(from: Long, to: Long): Int {
    val diff = startOfDay(to) - startOfDay(from)
    return (diff.toDouble() / 86_400_000.0).roundToInt()
}

private fun startOfDay(millis: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

/**
 * 事件型觸發的摘要字串（不預測時間，只描述「何時會觸發」）。
 * 定時觸發不走這裡（由 [nextRunSummary] 給出實際時刻）。
 */
fun triggerSummary(trigger: Trigger): String = when (trigger) {
    is Trigger.Manual -> "手動執行"
    is Trigger.Time -> timeModeLabel(trigger)
    is Trigger.PowerConnected -> "接上電源時"
    is Trigger.PowerDisconnected -> "拔除電源時"
    is Trigger.BatteryBelow -> "電量低於 ${trigger.threshold}%"
    is Trigger.BatteryAbove -> "電量高於 ${trigger.threshold}%"
    is Trigger.BatteryFull -> "充電完成時"
    is Trigger.LocationEnter -> "進入區域時"
    is Trigger.LocationExit -> "離開區域時"
    is Trigger.BtConnected -> "藍牙連接時"
    is Trigger.BtDisconnected -> "藍牙斷開時"
    is Trigger.WifiConnected -> "連上 Wi-Fi 時"
    is Trigger.WifiDisconnected -> "Wi-Fi 斷線時"
    is Trigger.AirplaneMode -> "飛航模式${onOffWhen(trigger.turnedOn)}"
    is Trigger.DndChanged -> "勿擾模式${onOffWhen(trigger.turnedOn)}"
    is Trigger.PowerSave -> "省電模式${onOffWhen(trigger.turnedOn)}"
    is Trigger.NfcTag -> "掃到標籤時"
    is Trigger.NotificationPosted -> "收到通知時"
    is Trigger.AppState -> "App ${if (trigger.onOpen) "開啟" else "關閉"}時"
    is Trigger.HeadsetPlugged -> "插入耳機時"
    is Trigger.HeadsetUnplugged -> "拔除耳機時"
    is Trigger.ScreenUnlocked -> "解鎖螢幕時"
    is Trigger.ScreenOn -> "螢幕開啟時"
    is Trigger.ScreenOff -> "螢幕關閉時"
    is Trigger.DeviceBoot -> "開機完成時"
}

private fun onOffWhen(on: Boolean): String = if (on) "開啟時" else "關閉時"

// ---------- 上次結果 ----------

/** 上次執行結果晶片的內容：文字 + 是否為（部分）失敗 */
data class LastRunSummary(val text: String, val failed: Boolean)

/**
 * 取該 routine 最近一筆 RunLog，判定全成功 / 部分失敗，附相對時間；無紀錄回 null。
 */
fun lastRunSummary(log: RunLog?, now: Long = System.currentTimeMillis()): LastRunSummary? {
    if (log == null || log.results.isEmpty()) return null
    val failed = !log.allSucceeded
    val label = if (failed) "上次部分失敗" else "上次成功"
    return LastRunSummary("$label · ${relativeTime(log.timestamp, now)}", failed)
}

/** 相對時間：剛剛 / N 分鐘前 / N 小時前 / N 天前 / MM-dd（純函式，不需依賴） */
fun relativeTime(then: Long, now: Long = System.currentTimeMillis()): String {
    val diff = now - then
    if (diff < 0) return "剛剛"
    val minutes = diff / 60_000L
    val hours = diff / 3_600_000L
    val days = diff / 86_400_000L
    return when {
        minutes < 1 -> "剛剛"
        minutes < 60 -> "$minutes 分鐘前"
        hours < 24 -> "$hours 小時前"
        days < 7 -> "$days 天前"
        else -> Calendar.getInstance().apply { timeInMillis = then }.let {
            "%02d-%02d".format(it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH))
        }
    }
}

// ---------- 需授權 ----------

/**
 * 依 routine 的觸發 / 動作，彙整出「所需但未授與」的權限清單（依關鍵程度排序）。
 *
 * 觸發相關（決定會不會被觸發）排在動作相關（決定動作會不會失敗）之前；
 * 卡片只顯示最前面的 1–2 個，點擊即導向對應設定頁。
 */
fun missingPermissions(routine: Routine, perm: PermSnapshot): List<PermIssue> {
    if (!routine.enabled) return emptyList()
    val issues = mutableListOf<PermIssue>()
    val trigger = routine.trigger
    val actions = routine.actions

    // --- 觸發相關（不授權就不會觸發） ---
    if (trigger.geoCircle != null && !perm.hasBackgroundLocation) {
        issues += PermIssue("需背景定位") { it.openAppDetailsSettings() }
    }
    if (trigger is Trigger.NotificationPosted && !perm.hasNotificationAccess) {
        issues += PermIssue("需通知存取") { it.openNotificationListenerSettings() }
    }
    if (trigger is Trigger.AppState && !perm.hasUsageAccess) {
        issues += PermIssue("需使用情況存取") {
            it.openPermissionSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        }
    }
    if (trigger is Trigger.NfcTag && perm.nfcAvailable && !perm.nfcEnabled) {
        issues += PermIssue("NFC 已關閉") { it.openNfcSettings() }
    }
    // 精確鬧鐘：API 31–32 未授時（33+ 由 USE_EXACT_ALARM 自動授予，canScheduleExact 為 true）
    if (trigger is Trigger.Time &&
        Build.VERSION.SDK_INT in Build.VERSION_CODES.S..Build.VERSION_CODES.S_V2 &&
        !perm.canScheduleExact
    ) {
        issues += PermIssue("需精確鬧鐘") {
            it.openPermissionSettings(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        }
    }

    // --- 動作相關（不授權該動作會失敗） ---
    if (actions.any { it is com.routina.app.model.Action.Notify } && !perm.notificationsEnabled) {
        issues += PermIssue("需通知權限") { it.openNotificationSettings() }
    }
    val needsDnd = actions.any {
        it is com.routina.app.model.Action.Dnd || it is com.routina.app.model.Action.RingerMode
    }
    if (needsDnd && !perm.hasDndAccess) {
        issues += PermIssue("需勿擾存取") {
            it.openPermissionSettings(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
        }
    }
    if (actions.any {
            it is com.routina.app.model.Action.TakePhoto ||
                it is com.routina.app.model.Action.BurstPhoto
        } && !perm.hasCameraPermission
    ) {
        issues += PermIssue("需相機") { it.openAppDetailsSettings() }
    }
    if (actions.any { it is com.routina.app.model.Action.RecordAudio } && !perm.hasMicPermission) {
        issues += PermIssue("需麥克風") { it.openAppDetailsSettings() }
    }
    if (actions.any { it is com.routina.app.model.Action.Brightness } && !perm.canWriteSettings) {
        issues += PermIssue("需寫入設定") {
            it.openPermissionSettings(Settings.ACTION_MANAGE_WRITE_SETTINGS)
        }
    }
    // 手動 routine 只在前景（▶）執行，永遠不會從背景啟動 App / 網址，因此不需要上層顯示權限
    if (trigger !is Trigger.Manual && actions.any {
            it is com.routina.app.model.Action.OpenApp || it is com.routina.app.model.Action.OpenUrl
        } && !perm.canDrawOverlays
    ) {
        issues += PermIssue("需上層顯示") {
            it.openPermissionSettings(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
        }
    }
    return issues
}

// ---------- 晶片繪製 ----------

/** 卡片名稱下方的狀態晶片列（下次執行 / 上次結果 / 最多 [maxPermIssues] 個權限晶片） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoutineStatusChips(
    routine: Routine,
    lastLog: RunLog?,
    sunLocation: SunLocation?,
    permIssues: List<PermIssue>,
    modifier: Modifier = Modifier,
    maxPermIssues: Int = 2
) {
    val context = LocalContext.current
    // 手動 routine 不顯示「下次執行」，改以中性「手動」晶片呈現（上次結果仍照常顯示）
    val isManual = routine.trigger is Trigger.Manual
    val scheduleText = when {
        isManual -> null
        !routine.enabled -> null
        routine.trigger is Trigger.Time -> nextRunSummary(routine, sunLocation)
        else -> triggerSummary(routine.trigger)
    }
    val lastRun = lastRunSummary(lastLog)
    val shownPerm = if (routine.enabled) permIssues.take(maxPermIssues) else emptyList()

    // 完全沒有任何線索就不佔用一列空間（手動晶片一律顯示，故手動卡片必留一列）
    if (!isManual && scheduleText == null && lastRun == null && shownPerm.isEmpty()) return

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (isManual) {
            StatusChip(
                text = "手動",
                icon = Icons.Filled.TouchApp,
                container = MaterialTheme.colorScheme.surfaceVariant,
                content = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (scheduleText != null) {
            StatusChip(
                text = scheduleText,
                icon = if (routine.trigger is Trigger.Time) {
                    Icons.Filled.Schedule
                } else {
                    Icons.Filled.Bolt
                },
                container = MaterialTheme.colorScheme.surfaceVariant,
                content = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (lastRun != null) {
            StatusChip(
                text = lastRun.text,
                icon = if (lastRun.failed) Icons.Filled.ErrorOutline else Icons.Filled.CheckCircle,
                container = (if (lastRun.failed) RoutinaColors.Warn else RoutinaColors.Good)
                    .copy(alpha = 0.16f),
                content = if (lastRun.failed) RoutinaColors.Warn else RoutinaColors.Good
            )
        }
        shownPerm.forEach { issue ->
            StatusChip(
                text = issue.label,
                icon = Icons.Filled.Warning,
                container = MaterialTheme.colorScheme.errorContainer,
                content = MaterialTheme.colorScheme.onErrorContainer,
                onClick = { issue.open(context) }
            )
        }
    }
}

/** 單一狀態晶片：圓角膠囊 + 小圖示 + 文字；[onClick] 非 null 時可點擊導向設定 */
@Composable
private fun StatusChip(
    text: String,
    icon: ImageVector,
    container: Color,
    content: Color,
    onClick: (() -> Unit)? = null
) {
    var chipModifier = Modifier
        .clip(RoundedCornerShape(50))
        .background(container)
    if (onClick != null) chipModifier = chipModifier.clickable(onClick = onClick)

    Row(
        modifier = chipModifier.padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(13.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            color = content,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
