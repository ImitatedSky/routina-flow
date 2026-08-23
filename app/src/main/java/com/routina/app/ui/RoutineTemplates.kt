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
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Home
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
