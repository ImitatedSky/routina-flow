package com.routina.app.ui.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.routina.app.model.Action
import com.routina.app.model.Trigger
import com.routina.app.model.isLocation
import com.routina.app.ui.actionTypeName
import com.routina.app.ui.triggerTypeName
import com.routina.app.ui.theme.actionColor
import com.routina.app.ui.theme.blockContentColor
import com.routina.app.ui.theme.triggerColor

/** 調色盤上的一組同類積木 */
private data class PaletteGroup<T>(val title: String, val items: List<T>)

/**
 * 觸發類型選項（以預設值建立），依主題分組。
 * 型別數量已達 25 種，分組小標是讓使用者仍能一眼掃到目標的關鍵；
 * 每一組的積木色也同色相（色表見 RoutinaColors），組名與顏色互相印證。
 */
private val TRIGGER_GROUPS: List<PaletteGroup<Trigger>> = listOf(
    // 手動置於最前、獨立成組，讓使用者一眼看到「可以不設觸發」
    PaletteGroup("手動", listOf(Trigger.Manual)),
    PaletteGroup("時間", listOf(Trigger.Time())),
    PaletteGroup(
        "電源",
        listOf(
            Trigger.PowerConnected,
            Trigger.PowerDisconnected,
            Trigger.BatteryBelow(),
            Trigger.BatteryAbove(),
            Trigger.BatteryFull,
            Trigger.PowerSave()
        )
    ),
    PaletteGroup(
        "連線",
        listOf(
            Trigger.WifiConnected(),
            Trigger.WifiDisconnected,
            Trigger.BtConnected(),
            Trigger.BtDisconnected(),
            Trigger.HeadsetPlugged,
            Trigger.HeadsetUnplugged,
            Trigger.AirplaneMode(),
            Trigger.NfcTag()
        )
    ),
    PaletteGroup(
        "系統與應用",
        listOf(
            Trigger.ScreenUnlocked,
            Trigger.ScreenOn,
            Trigger.ScreenOff,
            Trigger.DeviceBoot,
            Trigger.DndChanged(),
            Trigger.NotificationPosted(),
            Trigger.AppState()
        )
    ),
    PaletteGroup("位置", listOf(Trigger.LocationEnter(), Trigger.LocationExit()))
)

/** 動作類型選項（以預設值建立），依主題分組 */
private val ACTION_GROUPS: List<PaletteGroup<Action>> = listOf(
    PaletteGroup(
        "通知與 App",
        listOf(
            Action.Notify(),
            Action.OpenApp(),
            Action.OpenUrl(),
            Action.Http(),
            Action.SetAlarm(),
            Action.Share(),
            Action.Dial(),
            Action.SendSms()
        )
    ),
    PaletteGroup(
        "聲音",
        listOf(
            Action.MediaVolume(),
            Action.RingerMode(),
            Action.Speak(),
            Action.MediaKey(),
            Action.PlaySound()
        )
    ),
    PaletteGroup(
        "裝置",
        listOf(
            Action.Flashlight(),
            Action.Vibrate(),
            Action.Brightness(),
            Action.Dnd(),
            Action.Bluetooth(),
            Action.WifiToggle(),
            Action.AutoRotate(),
            Action.ScreenTimeout(),
            Action.GetLocation(),
            Action.SnapshotSettings,
            Action.RestoreSettings
        )
    ),
    PaletteGroup(
        "媒體與擷取",
        listOf(Action.TakePhoto(), Action.BurstPhoto(), Action.RecordAudio())
    ),
    PaletteGroup("流程", listOf(Action.Wait(), Action.Clipboard())),
    // 運算式（Python 式一行「名稱 = 值」）取代原本拆散的「設定變數 / 計算」；
    // 舊型別仍可反序列化與執行，只是不再從調色盤新增。
    // 資料處理三塊（從 JSON 取值 / 文字處理 / 日期時間）也是「算出一個值存進具名變數」，
    // 與設定變數同源，歸在「變數」同一組。
    PaletteGroup(
        "變數",
        listOf(
            Action.Expression(),
            Action.Text(),
            Action.SetGlobalVariable(),
            Action.AskInput(),
            Action.ChooseMenu(),
            Action.JsonGet(),
            Action.TextTransform(),
            Action.DateFormat()
        )
    ),
    // 清單：一行一個項目的文字變數，配合「逐項重複」把每個項目跑一遍
    PaletteGroup(
        "清單",
        listOf(
            Action.ListCreate(),
            Action.ListSplit(),
            Action.ListAppend(),
            Action.ListGet(),
            Action.ListCount()
        )
    ),
    // 「結束」標記不列在調色盤：加入開始標記時由編輯畫面成對插入（見 EditScreen 的 pairedEnd）
    PaletteGroup(
        "流程控制",
        listOf(
            Action.IfBegin(),
            Action.ElseIf(),
            Action.Else,
            Action.WhileBegin(),
            Action.RepeatBegin(),
            Action.ForEachBegin(),
            Action.RunRoutine()
        )
    )
)

private const val NO_GMS_HINT =
    "這台裝置沒有 Google Play 服務，無法使用系統級地理圍欄，區域觸發不可用。"

private const val NO_NFC_HINT =
    "這台裝置沒有 NFC 硬體，無法讀取 NFC 標籤，這個觸發不可用。"

/**
 * 觸發調色盤。
 * 選到目前的類型時回傳原本的 trigger（保留已設定的參數），換類型才用預設值。
 *
 * 硬體不支援的積木以半透明呈現，但仍可點擊——點下去會就地說明原因，
 * 比讓使用者對著點不動的積木猜半天好：
 * [locationAvailable] 為 false（無 GMS）時是兩塊區域積木，
 * [nfcAvailable] 為 false（無 NFC 硬體）時是 NFC 標籤積木。
 */
@Composable
fun TriggerPaletteSheet(
    current: Trigger,
    onPick: (Trigger) -> Unit,
    onDismiss: () -> Unit,
    locationAvailable: Boolean = true,
    nfcAvailable: Boolean = true
) {
    var hint by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }

    PaletteSheet(title = "選擇觸發條件", onDismiss = onDismiss) {
        PaletteSearchField(query, { query = it })
        val shown = filterGroups(TRIGGER_GROUPS, query) { triggerTypeName(it) }
        shown.forEach { (group, items) ->
            GroupHeader(group.title)
            items.forEach { template ->
                val selected = current::class == template::class
                val unavailableHint = when {
                    !locationAvailable && template.isLocation -> NO_GMS_HINT
                    !nfcAvailable && template is Trigger.NfcTag -> NO_NFC_HINT
                    else -> null
                }
                PaletteBlock(
                    fill = triggerColor(template),
                    text = triggerTypeName(template),
                    selected = selected,
                    enabled = unavailableHint == null,
                    subtitle = if (template is Trigger.Manual) {
                        "只用 ▶ 執行，不自動觸發"
                    } else {
                        null
                    },
                    onClick = {
                        if (unavailableHint == null) {
                            onPick(if (selected) current else template)
                        } else {
                            hint = unavailableHint
                        }
                    }
                )
            }
        }
        if (shown.isEmpty()) NoPaletteResults()
        hint?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

/** 動作調色盤 */
@Composable
fun ActionPaletteSheet(
    onPick: (Action) -> Unit,
    onDismiss: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }

    PaletteSheet(title = "加入動作", onDismiss = onDismiss) {
        PaletteSearchField(query, { query = it })
        val shown = filterGroups(ACTION_GROUPS, query) { actionTypeName(it) }
        shown.forEach { (group, items) ->
            GroupHeader(group.title)
            items.forEach { template ->
                PaletteBlock(
                    fill = actionColor(template),
                    text = actionTypeName(template),
                    selected = false,
                    onClick = { onPick(template) }
                )
            }
        }
        if (shown.isEmpty()) NoPaletteResults()
    }
}

@Composable
private fun GroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 6.dp)
    )
}

/**
 * 依查詢過濾各分組：保留顯示名稱含查詢字串（不分大小寫）的積木；整組無符合就丟掉，
 * 讓那組小標一併隱藏。空查詢原樣回傳全部分組。
 */
private fun <T> filterGroups(
    groups: List<PaletteGroup<T>>,
    query: String,
    name: (T) -> String
): List<Pair<PaletteGroup<T>, List<T>>> {
    val q = query.trim()
    return groups.mapNotNull { group ->
        val items = if (q.isEmpty()) {
            group.items
        } else {
            group.items.filter { name(it).contains(q, ignoreCase = true) }
        }
        if (items.isEmpty()) null else group to items
    }
}

/** 調色盤頂部搜尋欄：依積木名稱即時過濾，非空時右側顯示清除鈕 */
@Composable
private fun PaletteSearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("搜尋") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "清除搜尋")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp)
    )
}

/** 調色盤搜尋全無符合時的提示 */
@Composable
private fun NoPaletteResults() {
    Text(
        text = "找不到符合的項目",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaletteSheet(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    // skipPartiallyExpanded：清單很長時直接展開到內容高度（上限為全螢幕），
    // 使用者不必先拖一次把 sheet 拉高、再拖第二次才開始捲。
    // 內容不長時 Expanded 仍只有內容的高度，短清單不會變成滿版。
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // 49 種動作 / 25 種觸發加上分組小標已遠超一個 sheet 的高度，內容必須可捲動，
        // 否則展開到全高後底部的積木完全搆不到。
        //
        // 用 verticalScroll 的 Column 而非 LazyColumn：ModalBottomSheet 已經為
        // Modifier.verticalScroll 接好 nested scroll（捲到頂再往下拖就收起 sheet），
        // 而 LazyColumn 在固有高度未定的 sheet 裡容易量不出高度；項目只有十幾個，
        // 也不需要 lazy 的回收機制。
        //
        // 捲動放在 padding 之外、insets 之內：底部的 navigationBarsPadding 與 20dp
        // 邊距都算進可捲動內容，捲到底時最後一塊積木一定能完全露出手勢列上方。
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            content()
        }
    }
}

/**
 * 調色盤上的一塊積木：形狀與編輯器內完全一致，點擊即選用。
 * [enabled] 為 false 時淡化顯示，但仍接受點擊（用來顯示不可用的原因）。
 */
@Composable
private fun PaletteBlock(
    fill: Color,
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    subtitle: String? = null
) {
    val content = blockContentColor(fill)
    BlockSurface(
        fill = fill,
        modifier = Modifier.alpha(if (enabled) 1f else 0.4f),
        onClick = onClick
    ) {
        if (subtitle == null) {
            BlockLabel(text, color = content)
        } else {
            // 手動積木附一行副說明，其餘積木維持單行標籤
            Column(modifier = Modifier.weight(1f, fill = false)) {
                BlockLabel(text, color = content)
                Text(
                    text = subtitle,
                    color = content.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
        if (selected) {
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "目前使用中",
                tint = content,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
