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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.routina.app.engine.ExpressionEval
import com.routina.app.engine.GeofenceManager
import com.routina.app.engine.RoutineExecutor
import com.routina.app.engine.RunContext
import com.routina.app.engine.VariableResolver
import com.routina.app.model.Action
import com.routina.app.model.AppTarget
import com.routina.app.model.CompareOp
import com.routina.app.model.Condition
import com.routina.app.model.LocationFormat
import com.routina.app.model.MathOp
import com.routina.app.model.RingerModeType
import com.routina.app.model.usesRightOperand
import com.routina.app.model.Trigger
import com.routina.app.model.VolumeStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    onDismiss: () -> Unit,
    globalNames: List<String> = emptyList(),
    // 「執行程序」可選的其他程序（id 到名稱）；由呼叫端從 ViewModel 取得，保持本元件與資料層解耦
    routineChoices: List<Pair<String, String>> = emptyList()
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var showAppPicker by remember { mutableStateOf(false) }
    val tokenGroups = remember(trigger, precedingActions, globalNames) {
        availableTokens(trigger, precedingActions, globalNames)
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
                    NumericVarField(
                        label = "音量（%）",
                        expr = current.percentExpr,
                        fallback = current.percent,
                        unit = "%",
                        range = Action.PERCENT_SAFE,
                        tokenGroups = tokenGroups,
                        onExprChange = { draft = current.copy(percentExpr = it) }
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

                is Action.WifiToggle -> Column {
                    OnOffChips(current.on) { draft = current.copy(on = it) }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Android 10 起系統禁止 App 直接切換 Wi-Fi。" +
                            "執行時會開啟系統的 Wi-Fi 面板，由你自己切換開關或選擇網路。" +
                            "由背景觸發且系統禁止背景啟動時，會改發一則可點擊的通知。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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

                is Action.Vibrate -> NumericVarField(
                    label = "震動時間（毫秒）",
                    expr = current.millisExpr,
                    fallback = current.millis,
                    unit = " 毫秒",
                    range = Action.VIBRATE_MS_SAFE,
                    tokenGroups = tokenGroups,
                    onExprChange = { draft = current.copy(millisExpr = it) }
                )

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
                    NumericVarField(
                        label = "亮度（%）",
                        expr = current.percentExpr,
                        fallback = current.percent,
                        unit = "%",
                        range = Action.PERCENT_SAFE,
                        tokenGroups = tokenGroups,
                        onExprChange = { draft = current.copy(percentExpr = it) }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "只設定亮度值，不會關閉自動亮度——開著自動亮度時系統會在下次環境光變化後接手。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    WriteSettingsNotice()
                }

                is Action.AutoRotate -> Column {
                    OnOffChips(current.on) { draft = current.copy(on = it) }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "開啟＝畫面跟著手機轉向；關閉＝鎖定目前方向。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    WriteSettingsNotice()
                }

                is Action.ScreenTimeout -> Column {
                    NumericVarField(
                        label = "螢幕逾時（秒）",
                        expr = current.secondsExpr,
                        fallback = current.seconds,
                        unit = " 秒",
                        range = Action.SCREEN_TIMEOUT_SAFE,
                        tokenGroups = tokenGroups,
                        onExprChange = { draft = current.copy(secondsExpr = it) }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "多久沒有操作就自動關閉螢幕，可設 ${Action.SCREEN_TIMEOUT_SAFE.first}–" +
                            "${Action.SCREEN_TIMEOUT_SAFE.last} 秒。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    WriteSettingsNotice()
                }

                is Action.Dial -> Column {
                    VariableTextField(
                        value = current.number,
                        onValueChange = { draft = current.copy(number = it) },
                        label = "電話號碼",
                        tokenGroups = tokenGroups,
                        placeholder = "例如：0912345678",
                        singleLine = true,
                        keyboardType = KeyboardType.Phone
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "開啟系統撥號畫面並帶入號碼，由你自己按下通話鍵——不會自動撥出，" +
                            "也不需要通話權限。由背景觸發且系統禁止背景啟動時，" +
                            "會改發一則可點擊的通知。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is Action.SendSms -> Column {
                    VariableTextField(
                        value = current.number,
                        onValueChange = { draft = current.copy(number = it) },
                        label = "收件號碼",
                        tokenGroups = tokenGroups,
                        placeholder = "例如：0912345678",
                        singleLine = true,
                        keyboardType = KeyboardType.Phone
                    )
                    Spacer(Modifier.height(12.dp))
                    VariableTextField(
                        value = current.message,
                        onValueChange = { draft = current.copy(message = it) },
                        label = "訊息內容",
                        tokenGroups = tokenGroups,
                        placeholder = "可插入變數，例如：我大概 {{時間}} 到",
                        minLines = 2,
                        maxLines = 6
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "開啟簡訊 App 並預先填好收件人與內容，由你自己按送出——不會自動傳送，" +
                            "也不需要簡訊權限。由背景觸發且系統禁止背景啟動時，" +
                            "會改發一則可點擊的通知。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is Action.GetLocation -> Column {
                    OutlinedTextField(
                        value = current.variableName,
                        onValueChange = { draft = current.copy(variableName = it) },
                        label = { Text("存到變數") },
                        placeholder = { Text("例如：目前位置") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("格式", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    ChipRow(
                        options = LocationFormat.entries,
                        selected = current.format,
                        label = { locationFormatName(it) },
                        onSelect = { draft = current.copy(format = it) }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "取得一次目前座標（小數 6 位）存進變數，之後用 {{var:名稱}} 引用。" +
                            "需要位置權限；定位關閉或 15 秒內取不到位置時會記為失敗。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LocationPermissionNotice()
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
                    NumericVarField(
                        label = "等待秒數",
                        expr = current.secondsExpr,
                        fallback = current.seconds,
                        unit = " 秒",
                        range = Action.WAIT_SECONDS_SAFE,
                        tokenGroups = tokenGroups,
                        onExprChange = { draft = current.copy(secondsExpr = it) }
                    )
                    Spacer(Modifier.height(8.dp))
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
                    ShareToGallerySwitch(
                        checked = current.shareToGallery,
                        label = "存到公開相簿",
                        hint = "關閉（預設）＝只存 App 私有空間，其他 App 讀不到、也不進相簿與雲端備份；" +
                            "開啟＝另存到系統相簿，方便瀏覽但裝置上其他 App 看得到。"
                    ) { draft = current.copy(shareToGallery = it) }
                    CaptureBackgroundNotice()
                    CameraPermissionNotice()
                }

                is Action.BurstPhoto -> Column {
                    Text("鏡頭", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    LensChips(current.lensBack) { draft = current.copy(lensBack = it) }
                    Spacer(Modifier.height(12.dp))
                    NumericVarField(
                        label = "張數",
                        expr = current.countExpr,
                        fallback = current.count,
                        unit = " 張",
                        range = Action.BURST_COUNT_SAFE,
                        tokenGroups = tokenGroups,
                        onExprChange = { draft = current.copy(countExpr = it) }
                    )
                    Spacer(Modifier.height(12.dp))
                    NumericVarField(
                        label = "間隔（毫秒）",
                        expr = current.intervalExpr,
                        fallback = current.intervalMs,
                        unit = " ms",
                        range = Action.BURST_INTERVAL_SAFE,
                        tokenGroups = tokenGroups,
                        onExprChange = { draft = current.copy(intervalExpr = it) }
                    )
                    NotifyResultSwitch(current.notify) { draft = current.copy(notify = it) }
                    ShareToGallerySwitch(
                        checked = current.shareToGallery,
                        label = "存到公開相簿",
                        hint = "關閉（預設）＝只存 App 私有空間，其他 App 讀不到、也不進相簿與雲端備份；" +
                            "開啟＝另存到系統相簿，方便瀏覽但裝置上其他 App 看得到。"
                    ) { draft = current.copy(shareToGallery = it) }
                    CaptureBackgroundNotice()
                    CameraPermissionNotice()
                }

                is Action.RecordAudio -> Column {
                    NumericVarField(
                        label = "錄音秒數",
                        expr = current.secondsExpr,
                        fallback = current.seconds,
                        unit = " 秒",
                        range = Action.RECORD_SECONDS_SAFE,
                        tokenGroups = tokenGroups,
                        onExprChange = { draft = current.copy(secondsExpr = it) }
                    )
                    ShareToGallerySwitch(
                        checked = current.shareToGallery,
                        label = "存到公開音樂資料夾",
                        hint = "關閉（預設）＝只存 App 私有空間，其他 App 讀不到；" +
                            "開啟＝另存到系統音樂資料夾，裝置上其他 App 看得到。"
                    ) { draft = current.copy(shareToGallery = it) }
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

                is Action.SetGlobalVariable -> Column {
                    OutlinedTextField(
                        value = current.name,
                        onValueChange = { draft = current.copy(name = it) },
                        label = { Text("全域變數名稱") },
                        placeholder = { Text("例如：今日步數") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    VariableTextField(
                        value = current.template,
                        onValueChange = { draft = current.copy(template = it) },
                        label = "全域變數值",
                        tokenGroups = tokenGroups,
                        placeholder = "可插入變數，例如：{{時間}} 更新",
                        minLines = 2,
                        maxLines = 6
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "存成跨程序、可持久化的全域變數，任何程序都能以 {{全域:名稱}} 引用；" +
                            "值會保存到下次被覆寫。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is Action.Expression -> Column {
                    VariableTextField(
                        value = current.text,
                        onValueChange = { draft = current.copy(text = it) },
                        label = "運算式",
                        tokenGroups = tokenGroups,
                        placeholder = "例如：a = 3　或　count = count + 1",
                        minLines = 1,
                        maxLines = 3
                    )
                    // 即時預覽：以空情境試算（沒設過的變數當 0），讓使用者一眼看到結果
                    val preview = remember(current.text) { previewExpression(current.text) }
                    if (preview != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "預覽：$preview",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "一行寫「變數 = 值」。右邊有 + - * / % 就算數學（變數直接寫名字，例如 count + 1，" +
                            "沒設過的當 0）；否則整段存成文字。也能用 {{時間}} 這類變數；" +
                            "想強制當文字用引號 \"...\"。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is Action.Calculate -> Column {
                    OutlinedTextField(
                        value = current.name,
                        onValueChange = { draft = current.copy(name = it) },
                        label = { Text("存到變數") },
                        placeholder = { Text("例如：count") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    VariableTextField(
                        value = current.left,
                        onValueChange = { draft = current.copy(left = it) },
                        label = "左邊",
                        tokenGroups = numericTokenGroups(tokenGroups),
                        placeholder = "數字或變數，例如 {{var:count}}",
                        singleLine = true
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "運算",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    ChipRow(
                        options = MathOp.entries,
                        selected = current.op,
                        label = { mathOpFullLabel(it) },
                        onSelect = { draft = current.copy(op = it) }
                    )
                    Spacer(Modifier.height(10.dp))
                    VariableTextField(
                        value = current.right,
                        onValueChange = { draft = current.copy(right = it) },
                        label = "右邊",
                        tokenGroups = numericTokenGroups(tokenGroups),
                        placeholder = "數字或變數，例如 1",
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "把「左邊 運算 右邊」算完存進上面的變數，之後用 {{var:名稱}} 引用。" +
                            "整數不留小數；除以 0 會記為失敗。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is Action.AskInput -> Column {
                    VariableTextField(
                        value = current.prompt,
                        onValueChange = { draft = current.copy(prompt = it) },
                        label = "提示文字",
                        tokenGroups = tokenGroups,
                        placeholder = "例如：今天想去哪？",
                        minLines = 1,
                        maxLines = 3
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = current.variableName,
                        onValueChange = { draft = current.copy(variableName = it) },
                        label = { Text("存到變數") },
                        placeholder = { Text("例如：目的地") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    VariableTextField(
                        value = current.defaultValue,
                        onValueChange = { draft = current.copy(defaultValue = it) },
                        label = "預設值（選填）",
                        tokenGroups = tokenGroups,
                        placeholder = "預先填入輸入框的內容",
                        singleLine = true
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "執行到這裡會暫停並跳出對話框請你輸入文字，輸入的內容存進變數，" +
                            "之後用 {{var:名稱}} 引用。由背景觸發且無法直接跳出對話框時，" +
                            "會改發一則通知，點擊後才跳出對話框；逾時未回應會記為失敗。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is Action.ChooseMenu -> Column {
                    VariableTextField(
                        value = current.prompt,
                        onValueChange = { draft = current.copy(prompt = it) },
                        label = "提示文字",
                        tokenGroups = tokenGroups,
                        placeholder = "例如：選擇一個項目",
                        minLines = 1,
                        maxLines = 3
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = current.variableName,
                        onValueChange = { draft = current.copy(variableName = it) },
                        label = { Text("存到變數") },
                        placeholder = { Text("例如：選擇") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    // 選項以「一行一個」編輯：換行切開；空行在執行與驗證時忽略
                    VariableTextField(
                        value = current.options.joinToString("\n"),
                        onValueChange = { text ->
                            draft = current.copy(options = text.split("\n"))
                        },
                        label = "選項（一行一個）",
                        tokenGroups = tokenGroups,
                        placeholder = "早餐\n午餐\n晚餐",
                        minLines = 3,
                        maxLines = 8
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "執行到這裡會暫停並列出選項讓你選一個，選中的文字存進變數，" +
                            "之後用 {{var:名稱}} 引用。每個選項都可插入變數；由背景觸發且無法直接" +
                            "跳出對話框時，會改發一則通知，點擊後才跳出對話框；逾時未回應會記為失敗。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is Action.IfBegin -> ConditionEditor(
                    condition = current.condition,
                    tokenGroups = tokenGroups,
                    onChange = { draft = current.copy(condition = it) }
                )

                is Action.ElseIf -> ConditionEditor(
                    condition = current.condition,
                    tokenGroups = tokenGroups,
                    onChange = { draft = current.copy(condition = it) }
                )

                is Action.WhileBegin -> Column {
                    ConditionEditor(
                        condition = current.condition,
                        tokenGroups = tokenGroups,
                        onChange = { draft = current.copy(condition = it) }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "只要條件成立就重複執行到「結束重複」之間的動作" +
                            "（有 ${Action.WHILE_MAX_ITERATIONS} 次上限保護，避免無限迴圈）。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is Action.RepeatBegin -> Column {
                    VariableTextField(
                        value = current.countExpr.ifBlank { current.count.toString() },
                        onValueChange = { text ->
                            val n = text.trim().toIntOrNull()
                            draft = if (n != null) current.copy(count = n.coerceAtLeast(0), countExpr = "")
                            else current.copy(countExpr = text)
                        },
                        label = "重複次數",
                        tokenGroups = numericTokenGroups(tokenGroups),
                        placeholder = "例如：3，或插入變數",
                        singleLine = true,
                        keyboardType = KeyboardType.Number
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "重複執行到「結束重複」之間的動作這麼多次。動作中可用 {{迴圈:次數}} 取得目前第幾次。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is Action.Else ->
                    ControlMarkerInfo("前面條件都不成立時，執行到「結束如果」之間的動作。")

                is Action.EndIf -> ControlMarkerInfo("「如果」區塊的結尾。")
                is Action.EndWhile -> ControlMarkerInfo("「一直重複…當」區塊的結尾。")
                is Action.EndRepeat -> ControlMarkerInfo("「重複 N 次」區塊的結尾。")

                is Action.RunRoutine -> Column {
                    if (routineChoices.isEmpty()) {
                        Text(
                            "目前沒有其他程序可以執行。先建立別的程序，再回來這裡選。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        var expanded by remember { mutableStateOf(false) }
                        val selectedName = routineChoices.firstOrNull { it.first == current.routineId }?.second
                        Box {
                            TextButton(onClick = { expanded = true }) {
                                Text(selectedName ?: "選擇要執行的程序")
                                Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false }
                            ) {
                                routineChoices.forEach { (id, name) ->
                                    DropdownMenuItem(
                                        text = { Text(name) },
                                        onClick = {
                                            draft = current.copy(routineId = id, routineName = name)
                                            expanded = false
                                        }
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "跑到這塊時，會把所選程序的動作跑一遍（共用變數與結果）；會自動擋住循環呼叫。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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
fun availableTokens(
    trigger: Trigger,
    precedingActions: List<Action>,
    globalNames: List<String> = emptyList()
): List<VarTokenGroup> =
    buildList {
        triggerTokens(trigger).takeIf { it.isNotEmpty() }?.let {
            add(VarTokenGroup("觸發提供", it))
        }
        add(VarTokenGroup("上一個結果", listOf(VarToken("上一個動作的輸出", "{{result}}"))))
        // 前面用「設定變數 / 計算 / 運算式 / 詢問輸入 / 選單選擇 / 取得目前位置」設過的變數，
        // 都列進「已設定的變數」方便插入
        val setVarNames = precedingActions.filterIsInstance<Action.SetVariable>().map { it.name.trim() }
        val calcVarNames = precedingActions.filterIsInstance<Action.Calculate>().map { it.name.trim() }
        val exprVarNames = precedingActions.filterIsInstance<Action.Expression>().mapNotNull { expr ->
            val eq = expr.text.indexOf('=')
            if (eq > 0) expr.text.substring(0, eq).trim() else null
        }
        val askVarNames = precedingActions.filterIsInstance<Action.AskInput>().map { it.variableName.trim() }
        val menuVarNames = precedingActions.filterIsInstance<Action.ChooseMenu>().map { it.variableName.trim() }
        val locVarNames = precedingActions.filterIsInstance<Action.GetLocation>().map { it.variableName.trim() }
        val varNames = (setVarNames + calcVarNames + exprVarNames + askVarNames + menuVarNames + locVarNames)
            .filter { it.isNotBlank() }
            .distinct()
        if (varNames.isNotEmpty()) {
            add(VarTokenGroup("已設定的變數", varNames.map { VarToken(it, "{{var:$it}}") }))
        }
        // 全域變數：已存在的（globalNames）加上這個程序稍早才設定的，去重後列出
        val globalNamesAll = (globalNames + precedingActions.filterIsInstance<Action.SetGlobalVariable>()
            .map { it.name.trim() })
            .filter { it.isNotBlank() }
            .distinct()
        if (globalNamesAll.isNotEmpty()) {
            add(VarTokenGroup("全域變數", globalNamesAll.map { VarToken(it, "{{全域:$it}}") }))
        }
        add(
            VarTokenGroup(
                "常用",
                listOf(
                    VarToken("時間", "{{時間}}"),
                    VarToken("日期", "{{日期}}"),
                    VarToken("星期", "{{星期}}"),
                    VarToken("電量", "{{電量}}"),
                    VarToken("迴圈次數", "{{迴圈:次數}}")
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
        // 含變數時,用範例值即時預覽「執行後會變成什麼」,讓變數更直觀好懂
        if (field.text.contains("{{")) {
            Text(
                text = "預覽：${VariableResolver.previewResolve(field.text)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }
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

/**
 * 數值參數欄：可直接鍵入任意整數，或插入解析後為數字的變數 token。
 *
 * 內容寫入對應的 Expr 字串（[onExprChange]）；留空＝沿用原本的 [fallback]（舊資料 / 舊行為）。
 * 執行時由 RoutineExecutor.resolveNum 代入變數、parse 並夾在 [range] 內，超出上限會被夾到範圍。
 */
@Composable
private fun NumericVarField(
    label: String,
    expr: String,
    fallback: Int,
    unit: String,
    range: IntRange,
    tokenGroups: List<VarTokenGroup>,
    onExprChange: (String) -> Unit
) {
    val typed = expr.trim().toIntOrNull()
    val supporting = when {
        typed != null && typed > range.last -> "超過上限，執行時會夾到 ${range.last}$unit"
        typed != null && typed < range.first -> "低於下限，執行時會夾到 ${range.first}$unit"
        else -> "留空＝沿用 $fallback$unit，可直接輸入數字或插入變數"
    }
    VariableTextField(
        value = expr,
        onValueChange = onExprChange,
        label = label,
        tokenGroups = numericTokenGroups(tokenGroups),
        placeholder = fallback.toString(),
        singleLine = true,
        keyboardType = KeyboardType.Number,
        supportingText = supporting
    )
}

/**
 * 數值欄的「插入變數」清單：只留可能解析成數字的 token
 * （上一個結果、使用者設定的變數、常用裡的電量），略過時間/日期等純文字 token。
 */
private fun numericTokenGroups(groups: List<VarTokenGroup>): List<VarTokenGroup> =
    groups.mapNotNull { group ->
        when (group.title) {
            "上一個結果", "已設定的變數" -> group
            "常用" -> group.copy(tokens = group.tokens.filter { it.token == "{{電量}}" })
            else -> null
        }
    }.filter { it.tokens.isNotEmpty() }

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
 * 「存到公開相簿／音樂」的每動作開關。
 *
 * 預設關閉＝擷取的相片／音檔只存 App 私有空間（其他 App 讀不到、不進相簿與雲端備份）；
 * 開啟才另存到系統相簿／音樂目錄。把隱私設為預設，公開改為明確的每動作選擇。
 */
@Composable
private fun ShareToGallerySwitch(
    checked: Boolean,
    label: String,
    hint: String,
    onChange: (Boolean) -> Unit
) {
    Spacer(Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
    Text(
        hint,
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

/** 「修改系統設定」權限提示（螢幕亮度／自動旋轉／螢幕逾時共用） */
@Composable
private fun WriteSettingsNotice() {
    SpecialAccessNotice(
        granted = { RoutineExecutor.canWriteSettings(it) },
        message = "尚未取得「修改系統設定」權限，這個動作會被記為失敗。",
        onGrant = { it.openPermissionSettings(Settings.ACTION_MANAGE_WRITE_SETTINGS) }
    )
}

/** 位置權限的就地 runtime 請求（取得目前位置） */
@Composable
private fun LocationPermissionNotice() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(GeofenceManager.hasForegroundLocation(context)) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    if (granted) return
    Spacer(Modifier.height(12.dp))
    Text(
        "尚未取得「位置」權限，這個動作會被記為失敗。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
    TextButton(onClick = { launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) {
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

private fun isActionValid(action: Action): Boolean = when (action) {
    is Action.Notify -> action.title.isNotBlank() || action.message.isNotBlank()
    is Action.OpenApp -> action.packageName.isNotBlank()
    is Action.OpenUrl -> action.url.isNotBlank()
    is Action.Share -> action.text.isNotBlank()
    is Action.MediaVolume -> true
    is Action.RingerMode -> true
    is Action.Bluetooth -> true
    is Action.WifiToggle -> true
    is Action.Flashlight -> true
    is Action.Speak -> action.text.isNotBlank()
    is Action.Vibrate -> true
    is Action.Dnd -> true
    is Action.Brightness -> true
    is Action.AutoRotate -> true
    is Action.ScreenTimeout -> true
    // 撥號 / 傳簡訊至少要有號碼；取得目前位置一定要有存入的變數名稱
    is Action.Dial -> action.number.isNotBlank()
    is Action.SendSms -> action.number.isNotBlank()
    is Action.GetLocation -> action.variableName.isNotBlank()
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
    is Action.SetGlobalVariable -> action.name.isNotBlank()
    is Action.Calculate -> action.name.isNotBlank()
    is Action.Expression -> {
        val eq = action.text.indexOf('=')
        eq > 0 && action.text.substring(0, eq).isNotBlank()
    }
    // 互動動作：一定要有存入的變數名稱；選單另需至少一個非空選項
    is Action.AskInput -> action.variableName.isNotBlank()
    is Action.ChooseMenu -> action.variableName.isNotBlank() && action.options.any { it.isNotBlank() }
    // 流程控制標記沒有必填欄位（條件空＝恆成立）
    is Action.IfBegin, is Action.ElseIf, is Action.Else, is Action.EndIf,
    is Action.WhileBegin, is Action.EndWhile, is Action.RepeatBegin,
    is Action.EndRepeat -> true
    // 執行程序必須選定一個目標程序
    is Action.RunRoutine -> action.routineId.isNotBlank()
}

/** 判斷式編輯器：左值 + 運算子 + 右值（為空／不為空時隱藏右值） */
@Composable
private fun ConditionEditor(
    condition: Condition,
    tokenGroups: List<VarTokenGroup>,
    onChange: (Condition) -> Unit
) {
    val needsRight = condition.op.usesRightOperand
    Column {
        VariableTextField(
            value = condition.left,
            onValueChange = { onChange(condition.copy(left = it)) },
            label = "左值",
            tokenGroups = tokenGroups,
            placeholder = "例如：{{電量}} 或 {{全域:count}}",
            singleLine = true
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "條件",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        ChipRow(
            options = CompareOp.entries,
            selected = condition.op,
            label = { compareOpFullLabel(it) },
            onSelect = { onChange(condition.copy(op = it)) }
        )
        if (needsRight) {
            Spacer(Modifier.height(10.dp))
            VariableTextField(
                value = condition.right,
                onValueChange = { onChange(condition.copy(right = it)) },
                label = "右值",
                tokenGroups = tokenGroups,
                placeholder = "例如：20",
                singleLine = true
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "兩邊都是數字時比數值，否則比文字。左右值都能插入變數。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 運算式的即時預覽：以空情境試算（沒設過的變數當 0）並回傳結果；式子還不完整時回 null */
private fun previewExpression(text: String): String? =
    if (text.contains("=")) {
        runCatching { ExpressionEval.evaluate(text, RunContext()).second }.getOrNull()
    } else {
        null
    }

/** 算術運算子的選項文字 */
private fun mathOpFullLabel(op: MathOp): String = when (op) {
    MathOp.ADD -> "加 +"
    MathOp.SUBTRACT -> "減 −"
    MathOp.MULTIPLY -> "乘 ×"
    MathOp.DIVIDE -> "除 ÷"
    MathOp.MODULO -> "餘數"
}

/** 運算子下拉的完整文字（積木上的短標籤見 UiLabels.compareOpLabel） */
private fun compareOpFullLabel(op: CompareOp): String = when (op) {
    CompareOp.EQUALS -> "等於"
    CompareOp.NOT_EQUALS -> "不等於"
    CompareOp.GREATER -> "大於"
    CompareOp.GREATER_EQUAL -> "大於等於"
    CompareOp.LESS -> "小於"
    CompareOp.LESS_EQUAL -> "小於等於"
    CompareOp.CONTAINS -> "包含"
    CompareOp.NOT_CONTAINS -> "不包含"
    CompareOp.IS_EMPTY -> "為空"
    CompareOp.IS_NOT_EMPTY -> "不為空"
    CompareOp.IS_TRUE -> "為真 (true)"
    CompareOp.IS_FALSE -> "為假 (false)"
}

/** 無可編輯參數的流程標記（否則／各結束標記）的說明 */
@Composable
private fun ControlMarkerInfo(text: String) {
    Text(
        "$text\n\n這是流程標記，沒有可編輯的參數。",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
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
