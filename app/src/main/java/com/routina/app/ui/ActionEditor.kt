package com.routina.app.ui

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.routina.app.engine.RoutineExecutor
import com.routina.app.model.Action
import com.routina.app.model.AppTarget
import com.routina.app.model.RingerModeType
import com.routina.app.model.Trigger
import com.routina.app.model.VolumeStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * 編輯單一動作的參數。confirm 時回傳更新後的 Action。
 *
 * [trigger] 與 [precedingActions] 用來算出「插入變數」清單可用的 token
 * （觸發提供的、此動作之前設定過的變數）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionEditDialog(
    initial: Action,
    trigger: Trigger,
    precedingActions: List<Action>,
    onConfirm: (Action) -> Unit,
    onDismiss: () -> Unit
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var showAppPicker by remember { mutableStateOf(false) }
    val tokenGroups = remember(trigger, precedingActions) {
        availableTokens(trigger, precedingActions)
    }

    if (showAppPicker) {
        AppPickerDialog(
            onPick = { target ->
                draft = Action.OpenApp(
                    packageName = target.packageName,
                    appLabel = target.appName
                )
                showAppPicker = false
            },
            onDismiss = { showAppPicker = false }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(actionTypeName(initial)) },
        text = {
            when (val current = draft) {
                is Action.Notify -> Column {
                    VariableTextField(
                        value = current.title,
                        onValueChange = { draft = current.copy(title = it) },
                        label = "標題",
                        tokenGroups = tokenGroups,
                        singleLine = true
                    )
                    Spacer(Modifier.height(12.dp))
                    VariableTextField(
                        value = current.message,
                        onValueChange = { draft = current.copy(message = it) },
                        label = "內容",
                        tokenGroups = tokenGroups
                    )
                }

                is Action.OpenApp -> Column {
                    Text(
                        if (current.packageName.isBlank()) {
                            "尚未選擇 App"
                        } else {
                            "已選擇：${current.appLabel.ifBlank { current.packageName }}"
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { showAppPicker = true }) {
                        Text(if (current.packageName.isBlank()) "選擇 App" else "更換 App")
                    }
                }

                is Action.OpenUrl -> VariableTextField(
                    value = current.url,
                    onValueChange = { draft = current.copy(url = it) },
                    label = "網址",
                    tokenGroups = tokenGroups,
                    placeholder = "example.com",
                    singleLine = true,
                    keyboardType = KeyboardType.Uri,
                    supportingText = "未輸入 scheme 時會自動補上 https://"
                )

                is Action.Share -> Column {
                    VariableTextField(
                        value = current.text,
                        onValueChange = { draft = current.copy(text = it) },
                        label = "分享文字",
                        tokenGroups = tokenGroups,
                        placeholder = "要分享的連結或文字",
                        minLines = 3,
                        maxLines = 6
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "執行時跳出系統分享選單，由你當下選擇要分享到哪個 App／給誰。" +
                            "手動「立即執行」一律直接跳出；由背景觸發時若系統禁止背景啟動，" +
                            "會改發一則可點擊的通知，點擊後才跳出分享選單。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is Action.MediaVolume -> Column {
                    Text("串流", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    ChipRow(
                        options = VolumeStream.entries,
                        selected = current.stream,
                        label = { volumeStreamName(it) },
                        onSelect = { draft = current.copy(stream = it) }
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("音量：${current.percent}%", style = MaterialTheme.typography.bodyLarge)
                    Slider(
                        value = current.percent.toFloat(),
                        onValueChange = {
                            draft = current.copy(percent = it.roundToInt().coerceIn(0, 100))
                        },
                        valueRange = 0f..100f,
                        steps = 19
                    )
                    if (current.stream == VolumeStream.RING ||
                        current.stream == VolumeStream.NOTIFICATION
                    ) {
                        Text(
                            "系統處於勿擾模式時，調整鈴聲／通知音量需要「勿擾模式存取權」。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is Action.RingerMode -> ChipRow(
                    options = RingerModeType.entries,
                    selected = current.mode,
                    label = { ringerModeName(it) },
                    onSelect = { draft = current.copy(mode = it) }
                )

                is Action.Bluetooth -> Column {
                    OnOffChips(current.enable) { draft = current.copy(enable = it) }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Android 13 起系統禁止 App 直接切換藍牙。" +
                                "執行時會改發一則通知，點擊後由系統確認開啟或前往藍牙設定。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    BluetoothPermissionNotice()
                }

                is Action.Flashlight -> Column {
                    OnOffChips(current.on) { draft = current.copy(on = it) }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "使用裝置的閃光燈。沒有閃光燈硬體時這個動作會被記為失敗。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is Action.Speak -> Column {
                    VariableTextField(
                        value = current.text,
                        onValueChange = { draft = current.copy(text = it) },
                        label = "朗讀內容",
                        tokenGroups = tokenGroups,
                        placeholder = "例如：該出門了"
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "以系統語音朗讀，語言跟隨系統設定；朗讀完成（或逾時 30 秒）才進行下一個動作。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is Action.Vibrate -> Column {
                    Text(
                        "震動 ${current.millis} 毫秒",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Slider(
                        value = current.millis.toFloat(),
                        onValueChange = {
                            draft = current.copy(millis = roundTo(it, 100, 100, 3000))
                        },
                        valueRange = Action.MIN_VIBRATE_MS.toFloat()..Action.MAX_VIBRATE_MS.toFloat(),
                        steps = 28
                    )
                }

                is Action.Dnd -> Column {
                    OnOffChips(current.on) { draft = current.copy(on = it) }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "開啟＝只允許優先通知；關閉＝全部通知。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SpecialAccessNotice(
                        granted = { hasDndAccess(it) },
                        message = "尚未取得「勿擾模式存取權」，這個動作會被記為失敗。",
                        onGrant = {
                            it.startFirstAvailable(
                                Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                            )
                        }
                    )
                }

                is Action.Brightness -> Column {
                    Text("亮度：${current.percent}%", style = MaterialTheme.typography.bodyLarge)
                    Slider(
                        value = current.percent.toFloat(),
                        onValueChange = {
                            draft = current.copy(percent = it.roundToInt().coerceIn(0, 100))
                        },
                        valueRange = 0f..100f,
                        steps = 19
                    )
                    Text(
                        "只設定亮度值，不會關閉自動亮度——開著自動亮度時系統會在下次環境光變化後接手。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SpecialAccessNotice(
                        granted = { RoutineExecutor.canWriteSettings(it) },
                        message = "尚未取得「修改系統設定」權限，這個動作會被記為失敗。",
                        onGrant = {
                            it.openPermissionSettings(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                        }
                    )
                }

                is Action.Http -> Column {
                    ChipRow(
                        options = Action.HTTP_METHODS,
                        selected = httpMethodName(current.method),
                        label = { it },
                        onSelect = { draft = current.copy(method = it) }
                    )
                    Spacer(Modifier.height(12.dp))
                    VariableTextField(
                        value = current.url,
                        onValueChange = { draft = current.copy(url = it) },
                        label = "網址",
                        tokenGroups = tokenGroups,
                        placeholder = "example.com/hook",
                        singleLine = true,
                        keyboardType = KeyboardType.Uri
                    )
                    if (httpMethodName(current.method) == Action.METHOD_POST) {
                        Spacer(Modifier.height(12.dp))
                        VariableTextField(
                            value = current.body,
                            onValueChange = { draft = current.copy(body = it) },
                            label = "內容（純文字，可留空）",
                            tokenGroups = tokenGroups
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "逾時 10 秒；回應狀態碼 2xx 視為成功，狀態碼會記入執行紀錄。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is Action.MediaKey -> ChipRow(
                    options = Action.MEDIA_KEYS,
                    selected = current.key,
                    label = { mediaKeyName(it) },
                    onSelect = { draft = current.copy(key = it) }
                )

                is Action.Wait -> Column {
                    Text("等待 ${current.seconds} 秒", style = MaterialTheme.typography.bodyLarge)
                    Slider(
                        value = current.seconds.toFloat(),
                        onValueChange = {
                            draft = current.copy(seconds = it.roundToInt().coerceIn(1, 30))
                        },
                        valueRange = Action.MIN_WAIT_SECONDS.toFloat()..
                            Action.MAX_WAIT_SECONDS.toFloat(),
                        steps = 28
                    )
                    Text(
                        "等待在前景執行服務中進行。極端情況下服務起不來時會跳過等待並在紀錄註明。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is Action.Clipboard -> Column {
                    VariableTextField(
                        value = current.text,
                        onValueChange = { draft = current.copy(text = it) },
                        label = "要複製的文字",
                        tokenGroups = tokenGroups,
                        placeholder = "例如：會議室 Wi-Fi 密碼",
                        minLines = 3,
                        maxLines = 6
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "可輸入多行文字。手動「立即執行」一律寫入成功；" +
                            "由背景觸發時仍會照常寫入，但 Android 10 起系統對背景剪貼簿有限制，" +
                            "部分裝置會靜默忽略——執行紀錄會誠實註明這一點。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is Action.TakePhoto -> Column {
                    Text("鏡頭", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    LensChips(current.lensBack) { draft = current.copy(lensBack = it) }
                    NotifyResultSwitch(current.notify) { draft = current.copy(notify = it) }
                    CaptureStorageNotice()
                    CaptureBackgroundNotice()
                    CameraPermissionNotice()
                }

                is Action.BurstPhoto -> Column {
                    Text("鏡頭", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    LensChips(current.lensBack) { draft = current.copy(lensBack = it) }
                    Spacer(Modifier.height(12.dp))
                    Text("張數：${current.count} 張", style = MaterialTheme.typography.bodyLarge)
                    Slider(
                        value = current.count.toFloat(),
                        onValueChange = {
                            draft = current.copy(
                                count = it.roundToInt()
                                    .coerceIn(Action.MIN_BURST_COUNT, Action.MAX_BURST_COUNT)
                            )
                        },
                        valueRange = Action.MIN_BURST_COUNT.toFloat()..
                            Action.MAX_BURST_COUNT.toFloat(),
                        steps = Action.MAX_BURST_COUNT - Action.MIN_BURST_COUNT - 1
                    )
                    Text(
                        "間隔：${current.intervalMs} ms",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Slider(
                        value = current.intervalMs.toFloat(),
                        onValueChange = {
                            draft = current.copy(
                                intervalMs = roundTo(
                                    it, 100,
                                    Action.MIN_BURST_INTERVAL_MS, Action.MAX_BURST_INTERVAL_MS
                                )
                            )
                        },
                        valueRange = Action.MIN_BURST_INTERVAL_MS.toFloat()..
                            Action.MAX_BURST_INTERVAL_MS.toFloat(),
                        steps = (Action.MAX_BURST_INTERVAL_MS - Action.MIN_BURST_INTERVAL_MS)
                            / 100 - 1
                    )
                    NotifyResultSwitch(current.notify) { draft = current.copy(notify = it) }
                    CaptureStorageNotice()
                    CaptureBackgroundNotice()
                    CameraPermissionNotice()
                }

                is Action.RecordAudio -> Column {
                    Text("錄音 ${current.seconds} 秒", style = MaterialTheme.typography.bodyLarge)
                    Slider(
                        value = current.seconds.toFloat(),
                        onValueChange = {
                            draft = current.copy(
                                seconds = it.roundToInt()
                                    .coerceIn(Action.MIN_RECORD_SECONDS, Action.MAX_RECORD_SECONDS)
                            )
                        },
                        valueRange = Action.MIN_RECORD_SECONDS.toFloat()..
                            Action.MAX_RECORD_SECONDS.toFloat(),
                        steps = 0
                    )
                    Text(
                        "存入音樂資料夾（音樂/Routina）；執行紀錄會附上存檔位置。" +
                            "在 Android 10 以上為公開音樂目錄，裝置上的其他 App 也看得到。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    NotifyResultSwitch(current.notify) { draft = current.copy(notify = it) }
                    CaptureBackgroundNotice(microphone = true)
                    MicrophonePermissionNotice()
                }

                is Action.PlaySound -> Column {
                    ChipRow(
                        options = Action.SOUND_TYPES,
                        selected = current.type,
                        label = { soundTypeName(it) },
                        onSelect = { draft = current.copy(type = it) }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "播放系統目前設定的預設音效，無需額外權限。" +
                            "音量跟隨系統的鈴聲／鬧鐘／通知音量設定。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is Action.SetAlarm -> {
                    val timeState = rememberTimePickerState(
                        initialHour = current.hour,
                        initialMinute = current.minute,
                        is24Hour = true
                    )
                    // TimeInput 只改自身 state，這裡把時:分同步回草稿
                    LaunchedEffect(timeState.hour, timeState.minute) {
                        draft = current.copy(hour = timeState.hour, minute = timeState.minute)
                    }
                    Column {
                        TimeInput(state = timeState)
                        VariableTextField(
                            value = current.label,
                            onValueChange = { draft = current.copy(label = it) },
                            label = "標籤（選填）",
                            tokenGroups = tokenGroups,
                            placeholder = "例如：起床",
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "以系統時鐘 App 建立一個鬧鐘（免確認直接新增）。" +
                                "沒有可處理的時鐘 App 時這個動作會被記為失敗。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                is Action.Text -> Column {
                    VariableTextField(
                        value = current.template,
                        onValueChange = { draft = current.copy(template = it) },
                        label = "文字內容",
                        tokenGroups = tokenGroups,
                        placeholder = "可插入變數，例如：現在 {{時間}}",
                        minLines = 2,
                        maxLines = 6
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "產生一段文字作為輸出，後續動作可用 {{result}} 引用" +
                            "（例如拼好一段訊息再分享出去）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is Action.SetVariable -> Column {
                    OutlinedTextField(
                        value = current.name,
                        onValueChange = { draft = current.copy(name = it) },
                        label = { Text("變數名稱") },
                        placeholder = { Text("例如：問候語") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    VariableTextField(
                        value = current.template,
                        onValueChange = { draft = current.copy(template = it) },
                        label = "變數值",
                        tokenGroups = tokenGroups,
                        placeholder = "可插入變數，例如：早安 {{時間}}",
                        minLines = 2,
                        maxLines = 6
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "把這段（可含變數的）文字存成變數，之後以 {{var:名稱}} 引用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            val valid = isActionValid(draft)
            TextButton(onClick = { onConfirm(draft) }, enabled = valid) { Text("確定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 可插入的一個變數 token（顯示標籤 + 實際插入的字串） */
data class VarToken(val label: String, val token: String)

/** 「插入變數」清單的一個分類 */
data class VarTokenGroup(val title: String, val tokens: List<VarToken>)

/**
 * 算出此動作可插入的變數 token，分成四類：
 * 觸發提供的、上一個結果、此動作之前設定過的變數、常用（時間/日期/星期/電量）。
 */
fun availableTokens(trigger: Trigger, precedingActions: List<Action>): List<VarTokenGroup> =
    buildList {
        triggerTokens(trigger).takeIf { it.isNotEmpty() }?.let {
            add(VarTokenGroup("觸發提供", it))
        }
        add(VarTokenGroup("上一個結果", listOf(VarToken("上一個動作的輸出", "{{result}}"))))
        val varNames = precedingActions.filterIsInstance<Action.SetVariable>()
            .map { it.name.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (varNames.isNotEmpty()) {
            add(VarTokenGroup("已設定的變數", varNames.map { VarToken(it, "{{var:$it}}") }))
        }
        add(
            VarTokenGroup(
                "常用",
                listOf(
                    VarToken("時間", "{{時間}}"),
                    VarToken("日期", "{{日期}}"),
                    VarToken("星期", "{{星期}}"),
                    VarToken("電量", "{{電量}}")
                )
            )
        )
    }

/** 依觸發類型列出它會放進情境的 token */
private fun triggerTokens(trigger: Trigger): List<VarToken> = when (trigger) {
    is Trigger.NotificationPosted -> listOf(
        VarToken("通知標題", "{{通知標題}}"),
        VarToken("通知內容", "{{通知內容}}"),
        VarToken("通知來源App", "{{通知來源App}}")
    )

    is Trigger.LocationEnter, is Trigger.LocationExit ->
        listOf(VarToken("地點名稱", "{{地點名稱}}"))

    is Trigger.NfcTag -> listOf(VarToken("標籤名稱", "{{標籤名稱}}"))
    is Trigger.WifiConnected -> listOf(VarToken("Wi-Fi名稱", "{{Wi-Fi名稱}}"))
    is Trigger.BtConnected, is Trigger.BtDisconnected ->
        listOf(VarToken("藍牙裝置", "{{藍牙裝置}}"))

    else -> emptyList()
}

/**
 * 文字參數欄 + 「插入變數」入口。
 *
 * 內部以 [TextFieldValue] 追蹤游標位置，點選清單中的 token 即插入游標處。
 * 對外仍以純字串回報（[onValueChange]），序列化與既有資料完全不變。
 */
@Composable
fun VariableTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    tokenGroups: List<VarTokenGroup>,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    keyboardType: KeyboardType = KeyboardType.Text,
    supportingText: String? = null
) {
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    // 外部值被程式改動（非本欄輸入）時同步回來；一般輸入時 value 已等於 field.text，不觸發
    LaunchedEffect(value) {
        if (value != field.text) field = TextFieldValue(value, TextRange(value.length))
    }
    var menuOpen by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = field,
            onValueChange = {
                field = it
                onValueChange(it.text)
            },
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            supportingText = supportingText?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth()
        )
        Box {
            TextButton(onClick = { menuOpen = true }) { Text("＋ 插入變數") }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                tokenGroups.forEach { group ->
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                    group.tokens.forEach { token ->
                        DropdownMenuItem(
                            text = { Text("${token.label}　${token.token}") },
                            onClick = {
                                field = insertToken(field, token.token)
                                onValueChange(field.text)
                                menuOpen = false
                            }
                        )
                    }
                }
            }
        }
    }
}

/** 把 [token] 插入目前游標處（或取代選取範圍），並把游標移到插入內容之後 */
private fun insertToken(field: TextFieldValue, token: String): TextFieldValue {
    val text = field.text
    val start = field.selection.start.coerceIn(0, text.length)
    val end = field.selection.end.coerceIn(0, text.length)
    val newText = text.replaceRange(start, end, token)
    return field.copy(text = newText, selection = TextRange(start + token.length))
}

/**
 * Android 12 起切換藍牙、甚至只是帶出系統的開啟確認對話框，都需要 BLUETOOTH_CONNECT
 * （「附近的裝置」）。未授權時就地提供 runtime 請求，免得使用者得自己去設定頁找。
 */
@Composable
private fun BluetoothPermissionNotice() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasBluetoothConnect(context)) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    if (granted) return
    Spacer(Modifier.height(12.dp))
    Text(
        "尚未取得「附近的裝置」權限，這個動作會被記為失敗。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
    TextButton(onClick = { launcher.launch(Manifest.permission.BLUETOOTH_CONNECT) }) {
        Text("授權")
    }
}

private fun hasBluetoothConnect(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
    return runCatching {
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)
}

/**
 * 擷取動作的「完成後發出通知」開關。
 * 開啟時，擷取成功後會發一則可點擊開啟該相片 / 音檔的通知。
 */
@Composable
private fun NotifyResultSwitch(checked: Boolean, onChange: (Boolean) -> Unit) {
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("完成後發出通知", style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
    Text(
        "擷取成功後發一則通知顯示結果，點擊可用系統檢視器開啟該相片 / 音檔。" +
            "未授權通知時會略過，動作仍記為成功。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** 前／後鏡頭二選一 */
@Composable
private fun LensChips(lensBack: Boolean, onSelect: (Boolean) -> Unit) {
    ChipRow(
        options = listOf(true, false),
        selected = lensBack,
        label = { if (it) "後鏡頭" else "前鏡頭" },
        onSelect = onSelect
    )
}

/**
 * 擷取結果的存放位置與可見性告知（拍照 / 連拍用）。
 *
 * 相片會存進裝置的公開相簿，**裝置上的其他 App 也看得到**——提醒使用者留意隱私。
 * 錄音的位置與可見性直接寫在錄音編輯器的說明文字裡（含 Android 版本差異）。
 */
@Composable
private fun CaptureStorageNotice() {
    Spacer(Modifier.height(12.dp))
    Text(
        "拍攝的相片會存入裝置的公開相簿（相片/Routina），裝置上的其他 App 也看得到。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * 相機／麥克風的背景限制說明。
 *
 * Android 9 起禁止背景存取相機麥克風、Android 14 起限制背景啟動該類前景服務，
 * 因此手動執行一律可用、背景觸發只能盡力而為（被擋下時記為失敗並誠實註明）。
 */
@Composable
private fun CaptureBackgroundNotice(microphone: Boolean = false) {
    val hardware = if (microphone) "麥克風" else "相機"
    Spacer(Modifier.height(12.dp))
    Text(
        "手動「立即執行」一律可用。由背景觸發時，若系統擋下背景啟動$hardware" +
            "（Android 14+ 的限制），這個動作會被記為失敗並在紀錄註明，不影響其他動作。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * 相機權限的就地 runtime 請求（拍照／連拍）。
 * Android 9 以下另需 WRITE_EXTERNAL_STORAGE 才能把相片存進公開相簿。
 */
@Composable
private fun CameraPermissionNotice() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasCameraPermission(context)) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted = hasCameraPermission(context) }

    if (granted) return
    Spacer(Modifier.height(12.dp))
    Text(
        "尚未取得「相機」權限，這個動作會被記為失敗。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
    TextButton(onClick = { launcher.launch(cameraPermissions()) }) {
        Text("授權")
    }
}

/** 麥克風權限的就地 runtime 請求（錄音） */
@Composable
private fun MicrophonePermissionNotice() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasRecordAudioPermission(context)) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    if (granted) return
    Spacer(Modifier.height(12.dp))
    Text(
        "尚未取得「麥克風」權限，這個動作會被記為失敗。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
    TextButton(onClick = { launcher.launch(Manifest.permission.RECORD_AUDIO) }) {
        Text("授權")
    }
}

private fun cameraPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
        arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    } else {
        arrayOf(Manifest.permission.CAMERA)
    }

private fun hasCameraPermission(context: Context): Boolean = runCatching {
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
}.getOrDefault(false)

private fun hasRecordAudioPermission(context: Context): Boolean = runCatching {
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
}.getOrDefault(false)

private fun hasDndAccess(context: Context): Boolean = runCatching {
    context.getSystemService(NotificationManager::class.java)
        ?.isNotificationPolicyAccessGranted == true
}.getOrDefault(false)

/**
 * 需要使用者到系統設定頁手動開啟的特殊權限提示。
 * 從設定頁返回時（ON_RESUME）重查，授權完成提示要立刻消失。
 *
 * [onGrant] 負責開啟對應的設定頁——各種特殊權限的入口差異很大
 * （有的要帶 package URI、有的要先試 App 專屬頁再退回通用頁），
 * 交給呼叫端決定比在這裡塞開關參數清楚。
 */
@Composable
private fun SpecialAccessNotice(
    granted: (Context) -> Boolean,
    message: String,
    onGrant: (Context) -> Unit
) {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(granted(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) hasAccess = granted(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (hasAccess) return
    Spacer(Modifier.height(12.dp))
    Text(
        message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
    TextButton(onClick = { onGrant(context) }) {
        Text("前往設定")
    }
}

/** 開啟／關閉二選一 */
@Composable
private fun OnOffChips(on: Boolean, onSelect: (Boolean) -> Unit) {
    ChipRow(
        options = listOf(true, false),
        selected = on,
        label = { if (it) "開啟" else "關閉" },
        onSelect = onSelect
    )
}

/** 一列單選 chip（選項少時比下拉選單更快選到） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { option ->
            FilterChip(
                selected = selected == option,
                onClick = { onSelect(option) },
                label = { Text(label(option)) }
            )
        }
    }
}

/** 滑桿值對齊到 [step] 的倍數並限制在範圍內 */
private fun roundTo(value: Float, step: Int, min: Int, max: Int): Int =
    ((value / step).roundToInt() * step).coerceIn(min, max)

private fun isActionValid(action: Action): Boolean = when (action) {
    is Action.Notify -> action.title.isNotBlank() || action.message.isNotBlank()
    is Action.OpenApp -> action.packageName.isNotBlank()
    is Action.OpenUrl -> action.url.isNotBlank()
    is Action.Share -> action.text.isNotBlank()
    is Action.MediaVolume -> true
    is Action.RingerMode -> true
    is Action.Bluetooth -> true
    is Action.Flashlight -> true
    is Action.Speak -> action.text.isNotBlank()
    is Action.Vibrate -> true
    is Action.Dnd -> true
    is Action.Brightness -> true
    is Action.Http -> action.url.isNotBlank()
    is Action.MediaKey -> true
    is Action.Wait -> true
    is Action.Clipboard -> action.text.isNotBlank()
    is Action.TakePhoto -> true
    is Action.BurstPhoto -> true
    is Action.RecordAudio -> true
    is Action.PlaySound -> true
    is Action.SetAlarm -> true
    is Action.Text -> action.template.isNotBlank()
    is Action.SetVariable -> action.name.isNotBlank()
}

/** 已安裝且可啟動的 App 清單 */
private data class LaunchableApp(val packageName: String, val label: String)

/**
 * 已安裝 App 的選擇器（「開啟 App」動作、通知觸發、App 開啟關閉觸發共用）。
 *
 * [anyAppLabel] 非 null 時在最上面多一列「任一 App」，選它會回傳空的 package
 * （通知觸發用來表示不限來源）。
 */
@Composable
internal fun AppPickerDialog(
    onPick: (AppTarget) -> Unit,
    onDismiss: () -> Unit,
    anyAppLabel: String? = null,
    selectedPackage: String = ""
) {
    val context = LocalContext.current
    var apps by remember { mutableStateOf<List<LaunchableApp>?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            runCatching {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                pm.queryIntentActivities(intent, 0)
                    .map {
                        LaunchableApp(
                            packageName = it.activityInfo.packageName,
                            label = it.loadLabel(pm).toString()
                        )
                    }
                    .distinctBy { it.packageName }
                    .sortedBy { it.label.lowercase() }
            }.getOrDefault(emptyList())
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("選擇 App") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("搜尋") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (anyAppLabel != null) {
                    PickerRow(
                        label = anyAppLabel,
                        detail = "不限來源 App",
                        selected = selectedPackage.isBlank(),
                        onClick = { onPick(AppTarget("", "")) },
                        leading = { AnyAppIcon(APP_PICKER_ICON_SIZE) }
                    )
                }
                val list = apps
                when {
                    list == null -> Text("讀取中…")
                    list.isEmpty() -> Text("找不到可啟動的 App")
                    else -> {
                        val filtered = if (query.isBlank()) {
                            list
                        } else {
                            list.filter { it.label.contains(query, ignoreCase = true) }
                        }
                        LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                            items(filtered, key = { it.packageName }) { app ->
                                val icon = rememberAppIcon(app.packageName)
                                PickerRow(
                                    label = app.label,
                                    detail = app.packageName,
                                    selected = app.packageName == selectedPackage,
                                    onClick = {
                                        onPick(AppTarget(app.packageName, app.label))
                                    },
                                    leading = { AppRowIcon(icon, APP_PICKER_ICON_SIZE) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/**
 * 選擇清單的一列：（可選）前置圖示 + 主標 + 副標，選中的那列前面加勾。
 * App 選擇器（帶 App 圖示）與藍牙裝置選擇器（無圖示）共用。
 */
@Composable
internal fun PickerRow(
    label: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
    leading: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (leading != null) leading()
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (selected) "✓ $label" else label,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** App 選擇清單每列的圖示大小 */
private val APP_PICKER_ICON_SIZE = 32.dp

/** 清單列的 App 圖示；載入中或載不到時以泛用占位圖示保持列高與對齊一致 */
@Composable
private fun AppRowIcon(icon: ImageBitmap?, size: Dp) {
    if (icon != null) {
        Image(
            bitmap = icon,
            contentDescription = null,
            modifier = Modifier.size(size).clip(RoundedCornerShape(6.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Apps,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.7f)
            )
        }
    }
}

/** 「任一 App」列的泛用圖示 */
@Composable
private fun AnyAppIcon(size: Dp) {
    Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Filled.Apps,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(size * 0.8f)
        )
    }
}
