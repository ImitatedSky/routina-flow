package com.routina.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AltRoute
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.routina.app.model.Action
import com.routina.app.model.CompareOp
import com.routina.app.model.Condition
import com.routina.app.model.RingerModeType
import com.routina.app.model.Routine
import com.routina.app.model.Trigger
import com.routina.app.model.VolumeStream
import com.routina.app.ui.theme.RoutinaColors

/**
 * 內建例行程序範本。
 *
 * 範本只是把既有的 [Trigger] / [Action] 預先組合起來，不新增任何能力，也不動序列化格式。
 * [build] 每次呼叫都產生一份**新 id、預設停用**的 Routine——待使用者在編輯畫面確認、
 * 補上參數並儲存後才生效，避免帶著未補的座標／SSID 就被啟用。
 */
data class RoutineTemplate(
    val id: String,
    val title: String,
    val description: String,
    /** 範本圖示底色（沿用觸發家族色系，與積木視覺一致） */
    val color: Color,
    val icon: ImageVector,
    /** 需要補參數（位置座標 / Wi-Fi 名稱）才算設定完成 */
    val needsSetup: Boolean,
    val build: () -> Routine
)

object RoutineTemplates {

    /** 六選一中的前五個實體範本；第六格「空白」由 UI 直接走空白新建流程 */
    val ALL: List<RoutineTemplate> = listOf(
        RoutineTemplate(
            id = "morning",
            title = "早晨",
            description = "08:00 提醒早安、把媒體音量開到 60%",
            color = RoutinaColors.TriggerTime,
            icon = Icons.Filled.WbSunny,
            needsSetup = false,
            build = {
                Routine(
                    name = "早晨",
                    enabled = false,
                    trigger = Trigger.Time(hour = 8, minute = 0),
                    actions = listOf(
                        Action.Notify(title = "早安"),
                        Action.MediaVolume(percent = 60, stream = VolumeStream.MEDIA)
                    )
                )
            }
        ),
        RoutineTemplate(
            id = "bedtime",
            title = "就寢",
            description = "23:00 開勿擾、把螢幕調暗、響鈴靜音",
            color = RoutinaColors.TriggerLocationExit,
            icon = Icons.Filled.Bedtime,
            needsSetup = false,
            build = {
                Routine(
                    name = "就寢",
                    enabled = false,
                    trigger = Trigger.Time(hour = 23, minute = 0),
                    actions = listOf(
                        Action.Dnd(on = true),
                        Action.Brightness(percent = 20),
                        Action.RingerMode(mode = RingerModeType.SILENT)
                    )
                )
            }
        ),
        RoutineTemplate(
            id = "office",
            title = "到公司",
            description = "進公司就靜音、提醒你已到（需選公司區域）",
            color = RoutinaColors.TriggerLocationEnter,
            icon = Icons.Filled.Work,
            needsSetup = true,
            build = {
                Routine(
                    name = "到公司",
                    enabled = false,
                    trigger = Trigger.LocationEnter(),
                    actions = listOf(
                        Action.RingerMode(mode = RingerModeType.SILENT),
                        Action.Notify(title = "已到公司")
                    )
                )
            }
        ),
        RoutineTemplate(
            id = "home",
            title = "回家",
            description = "連上家裡 Wi-Fi 就開藍牙、媒體音量 50%（需填 Wi-Fi）",
            color = RoutinaColors.ActionOpenApp,
            icon = Icons.Filled.Home,
            needsSetup = true,
            build = {
                Routine(
                    name = "回家",
                    enabled = false,
                    trigger = Trigger.WifiConnected(),
                    actions = listOf(
                        Action.Bluetooth(enable = true),
                        Action.MediaVolume(percent = 50, stream = VolumeStream.MEDIA)
                    )
                )
            }
        ),
        RoutineTemplate(
            id = "powersave",
            title = "省電",
            description = "電量低於 20% 就開勿擾、把螢幕調暗",
            color = RoutinaColors.TriggerBatteryBelow,
            icon = Icons.Filled.BatterySaver,
            needsSetup = false,
            build = {
                Routine(
                    name = "省電",
                    enabled = false,
                    trigger = Trigger.BatteryBelow(threshold = 20),
                    actions = listOf(
                        Action.Dnd(on = true),
                        Action.Brightness(percent = 20)
                    )
                )
            }
        ),
        // 以下兩個示範「變數」怎麼用：文字動作組出含 {{變數}} 的字串,後續動作再引用。
        RoutineTemplate(
            id = "report",
            title = "報時分享",
            description = "手動：組出「現在幾點＋電量」再分享（變數範例）",
            color = RoutinaColors.ActionShare,
            icon = Icons.Filled.Share,
            needsSetup = false,
            build = {
                Routine(
                    name = "報時分享",
                    enabled = false,
                    trigger = Trigger.Manual,
                    actions = listOf(
                        Action.Text(template = "現在 {{時間}}，電量還有 {{電量}}%"),
                        Action.Share(text = "{{result}}")
                    )
                )
            }
        ),
        RoutineTemplate(
            id = "forward-notif",
            title = "通知轉發",
            description = "收到通知就把來源＋標題＋內容複製起來（變數範例，需通知存取）",
            color = RoutinaColors.TriggerNotificationPosted,
            icon = Icons.Filled.Notifications,
            needsSetup = true,
            build = {
                Routine(
                    name = "通知轉發",
                    enabled = false,
                    trigger = Trigger.NotificationPosted(),
                    actions = listOf(
                        Action.Clipboard(text = "{{通知來源App}}｜{{通知標題}}：{{通知內容}}")
                    )
                )
            }
        ),
        // 示範 V1 流程控制：重複 N 次（含 {{迴圈:次數}}）+ 依電量的 如果／否則 分支。
        // 手動觸發，按「執行一次」即可看到效果，不需任何額外權限。
        RoutineTemplate(
            id = "flow-demo",
            title = "流程範例",
            description = "手動：重複 3 次通知，再依電量分支（if／迴圈範例）",
            color = RoutinaColors.ActionControl,
            icon = Icons.Filled.AltRoute,
            needsSetup = false,
            build = {
                Routine(
                    name = "流程範例",
                    enabled = false,
                    trigger = Trigger.Manual,
                    actions = listOf(
                        Action.RepeatBegin(count = 3),
                        Action.Notify(title = "第 {{迴圈:次數}} 次", message = "重複 N 次示範"),
                        Action.EndRepeat,
                        Action.IfBegin(
                            Condition(left = "{{電量}}", op = CompareOp.GREATER_EQUAL, right = "50")
                        ),
                        Action.Notify(title = "電量充足", message = "目前 {{電量}}%"),
                        Action.Else,
                        Action.Notify(title = "建議充電", message = "目前 {{電量}}%"),
                        Action.EndIf
                    )
                )
            }
        )
    )

    fun byId(id: String): RoutineTemplate? = ALL.firstOrNull { it.id == id }
}

/** 空白格的底色（灰，沿用流程類積木灰） */
private val BlankTemplateColor = RoutinaColors.ActionClipboard

/**
 * 2 欄範本選擇格：五個範本 + 末格「空白／自己從頭開始」。
 * 空狀態與 FAB 的「從範本建立」sheet 共用同一份格。
 */
@Composable
fun TemplateGrid(
    onPick: (RoutineTemplate) -> Unit,
    onBlank: () -> Unit,
    modifier: Modifier = Modifier
) {
    // null 代表末格的「空白」，與五個實體範本一起排成 2 欄
    val cells: List<RoutineTemplate?> = RoutineTemplates.ALL + listOf(null)
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        cells.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { template ->
                    TemplateCell(
                        template = template,
                        modifier = Modifier.weight(1f),
                        onClick = { if (template != null) onPick(template) else onBlank() }
                    )
                }
                // 補一個等寬空位，讓單數結尾那列的最後一格維持一半寬度、不撐滿
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/** 一張範本格：色塊圖示 + 標題 + 一行說明（[template] 為 null 時是「空白」格） */
@Composable
private fun TemplateCell(
    template: RoutineTemplate?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val color = template?.color ?: BlankTemplateColor
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = template?.icon ?: Icons.Filled.Add,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = template?.title ?: "空白",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = template?.description ?: "自己從頭開始",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
