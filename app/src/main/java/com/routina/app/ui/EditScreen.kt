package com.routina.app.ui

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.ContextWrapper
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerLayoutType
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.routina.app.engine.BtAclReceiver
import com.routina.app.engine.GeofenceManager
import com.routina.app.engine.NfcTagReader
import com.routina.app.model.Action
import com.routina.app.model.AppTarget
import com.routina.app.model.BtDevice
import com.routina.app.model.Routine
import com.routina.app.model.TimeMode
import com.routina.app.model.Trigger
import com.routina.app.model.appTarget
import com.routina.app.model.batteryThreshold
import com.routina.app.model.btDevice
import com.routina.app.model.geoCircle
import com.routina.app.model.isConfigured
import com.routina.app.model.stateTurnedOn
import com.routina.app.model.withAppTarget
import com.routina.app.model.withBatteryThreshold
import com.routina.app.model.withBtDevice
import com.routina.app.model.withGeoCircle
import com.routina.app.model.withStateTurnedOn
import com.routina.app.ui.blocks.ActionBlock
import com.routina.app.ui.blocks.ActionPaletteSheet
import com.routina.app.ui.blocks.BlockStackSpacing
import com.routina.app.ui.blocks.GhostBlock
import com.routina.app.ui.blocks.InsertPoint
import com.routina.app.ui.blocks.TriggerBlock
import com.routina.app.ui.blocks.TriggerPaletteSheet
import com.routina.app.ui.blocks.TriggerParam
import com.routina.app.ui.theme.RoutinePalette
import kotlinx.serialization.json.Json
import sh.calvin.reorderable.ReorderableColumn
import kotlin.math.roundToInt

/**
 * 草稿保存：旋轉螢幕、切換深色模式等設定變更會重建 Activity，
 * Routine / Action 已是 @Serializable，直接以 JSON 字串存入 Bundle 即可保住草稿
 * （包含新增流程中隨機產生的 id）。
 */
private val draftJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}

private val RoutineSaver: Saver<Routine, String> = Saver(
    save = { runCatching { draftJson.encodeToString(Routine.serializer(), it) }.getOrNull() },
    restore = { runCatching { draftJson.decodeFromString(Routine.serializer(), it) }.getOrNull() }
)

/**
 * 正在編輯的動作。[isNew] 代表這塊積木是剛從調色盤加入的，
 * 取消編輯時要一併從堆疊移除。
 */
private data class IndexedAction(
    val index: Int,
    val action: Action,
    val isNew: Boolean = false
)

private val IndexedActionSaver: Saver<IndexedAction?, String> = Saver(
    save = { value ->
        value?.let {
            runCatching {
                val json = draftJson.encodeToString(Action.serializer(), it.action)
                "${it.index}|${if (it.isNew) 1 else 0}|$json"
            }.getOrNull()
        }
    },
    restore = { stored ->
        runCatching {
            val first = stored.indexOf('|')
            val second = stored.indexOf('|', first + 1)
            if (first <= 0 || second < 0) {
                null
            } else {
                IndexedAction(
                    index = stored.substring(0, first).toInt(),
                    isNew = stored.substring(first + 1, second) == "1",
                    action = draftJson.decodeFromString(
                        Action.serializer(),
                        stored.substring(second + 1)
                    )
                )
            }
        }.getOrNull()
    }
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    viewModel: RoutineViewModel,
    routineId: String?,
    templateId: String? = null,
    onDone: () -> Unit
) {
    val existing = remember(routineId) { routineId?.let { viewModel.findById(it) } }
    // 套用範本＝新建模式並帶入該範本的草稿（新 id、預設停用）。
    // build() 只在首次進入時作為初始值跑一次，之後由 rememberSaveable 還原（含新 id），
    // 旋轉螢幕等設定變更不會重生一份新 id。
    var draft by rememberSaveable(stateSaver = RoutineSaver) {
        mutableStateOf(
            // 空白新建（無 existing、無範本）預設為手動執行——想做純捷徑時零負擔
            existing
                ?: templateId?.let { RoutineTemplates.byId(it)?.build() }
                ?: Routine(trigger = Trigger.Manual)
        )
    }

    var showTriggerPalette by rememberSaveable { mutableStateOf(false) }
    var showActionPalette by rememberSaveable { mutableStateOf(false) }
    // 開啟動作調色盤時要插入的位置；null＝加到最後（末尾幽靈積木）
    var insertIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var editingAction by rememberSaveable(stateSaver = IndexedActionSaver) {
        mutableStateOf<IndexedAction?>(null)
    }
    var showTimePicker by rememberSaveable { mutableStateOf(false) }
    var showTimeModePicker by rememberSaveable { mutableStateOf(false) }
    var showDayPicker by rememberSaveable { mutableStateOf(false) }
    var showThresholdPicker by rememberSaveable { mutableStateOf(false) }
    var showMapPicker by rememberSaveable { mutableStateOf(false) }
    var showBtPicker by rememberSaveable { mutableStateOf(false) }
    var showSsidInput by rememberSaveable { mutableStateOf(false) }
    var showStatePicker by rememberSaveable { mutableStateOf(false) }
    var showNfcScan by rememberSaveable { mutableStateOf(false) }
    var showNotificationFilter by rememberSaveable { mutableStateOf(false) }
    var showAppPicker by rememberSaveable { mutableStateOf(false) }
    var showDeleteConfirm by rememberSaveable { mutableStateOf(false) }
    // 刪除／複製／方塊顏色都收於右上「⋯」溢位選單，離開返回鍵的相鄰區，杜絕誤觸
    var showMenu by rememberSaveable { mutableStateOf(false) }
    var showColorPicker by rememberSaveable { mutableStateOf(false) }

    // 拖曳排序拿起 / 讓位時的觸覺回饋
    val haptic = LocalHapticFeedback.current
    // 特殊權限狀態：從系統設定頁返回時（ON_RESUME）重查，授權完成引導卡要立刻消失
    val context = LocalContext.current
    val playServicesAvailable = remember { viewModel.isPlayServicesAvailable() }
    // NFC 硬體的有無不會在執行期間改變，查一次就好（開關狀態則要重查）
    val nfcAvailable = remember { viewModel.isNfcAvailable() }
    var hasBackgroundLocation by remember { mutableStateOf(viewModel.hasBackgroundLocation()) }
    var hasForegroundLocation by remember { mutableStateOf(viewModel.hasForegroundLocation()) }
    var hasNotificationAccess by remember { mutableStateOf(viewModel.hasNotificationAccess()) }
    var hasUsageAccess by remember { mutableStateOf(viewModel.hasUsageAccess()) }
    // 日出日落沿用 App 內任一區域觸發的座標；沒有時退回預設 06:00 / 18:00
    val hasSunLocation = remember { viewModel.hasSunLocation() }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasBackgroundLocation = viewModel.hasBackgroundLocation()
                hasForegroundLocation = viewModel.hasForegroundLocation()
                hasNotificationAccess = viewModel.hasNotificationAccess()
                hasUsageAccess = viewModel.hasUsageAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // 選了區域觸發 → 先請求前景精確位置；不論授權與否都讓使用者接著在地圖上選點
    val mapLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { showMapPicker = true }

    // 指定 Wi-Fi SSID 也需要精確位置（Android 9+ 讀 SSID 的前提）
    val foregroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { hasForegroundLocation = viewModel.hasForegroundLocation() }

    // Android 10 可以用 runtime 對話框請求背景位置；11+ 只能導去設定頁（由引導卡負責）
    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasBackgroundLocation = viewModel.hasBackgroundLocation() }

    fun openMapPicker() {
        if (GeofenceManager.hasForegroundLocation(context)) {
            showMapPicker = true
        } else {
            mapLocationLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    // 由範本帶入且需補參數時（到公司＝未選座標、回家＝未填 SSID），一進畫面就把對應的
    // 選擇器帶出來引導使用者補上；每次進入只做一次（rememberSaveable 記住已引導過）。
    // 座標未選的區域觸發本來就過不了「未設定不可儲存」，補齊前無法儲存。
    var templatePromptShown by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!templatePromptShown && existing == null && templateId != null) {
            templatePromptShown = true
            val t = draft.trigger
            when {
                t.geoCircle?.isConfigured == false -> openMapPicker()
                t is Trigger.WifiConnected && t.ssid.isBlank() -> showSsidInput = true
            }
        }
    }

    val locationCircle = draft.trigger.geoCircle
    // 觸發參數沒填齊（沒選地點 / 沒掃標籤 / 沒選 App）就存不起來，
    // 否則會存出一個永遠不會觸發的程序
    val canSave = draft.name.isNotBlank() &&
        draft.actions.isNotEmpty() &&
        draft.trigger.isConfigured

    fun moveAction(from: Int, to: Int) {
        val actions = draft.actions
        if (from !in actions.indices || to !in actions.indices) return
        draft = draft.copy(
            actions = actions.toMutableList().apply { add(to, removeAt(from)) }
        )
    }

    fun removeAction(index: Int) {
        if (index !in draft.actions.indices) return
        draft = draft.copy(
            actions = draft.actions.toMutableList().apply { removeAt(index) }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "新增例行程序" else "編輯例行程序") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "更多選項")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("方塊顏色") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Palette, contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    showColorPicker = true
                                }
                            )
                            // 複製／刪除只在編輯既有程序時有意義（新建還沒有可複製 / 可刪的對象）
                            if (existing != null) {
                                DropdownMenuItem(
                                    text = { Text("複製此程序") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                                    },
                                    onClick = {
                                        showMenu = false
                                        // 複製已存的版本(新 id、名稱加「複製」、預設停用),回首頁
                                        viewModel.duplicate(draft.id)
                                        onDone()
                                    }
                                )
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            "刪除例行程序",
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            Icons.Filled.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        showDeleteConfirm = true
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = { draft = draft.copy(name = it) },
                label = { Text("名稱") },
                placeholder = { Text("例如：早晨通勤") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(20.dp))

            BlockCanvas {
                // 觸發帽子積木固定在最上方，不參與排序
                TriggerBlock(
                    trigger = draft.trigger,
                    onBodyClick = { showTriggerPalette = true },
                    onParamClick = { param ->
                        when (param) {
                            TriggerParam.TIME -> showTimeModePicker = true
                            TriggerParam.DAYS -> showDayPicker = true
                            TriggerParam.THRESHOLD -> showThresholdPicker = true
                            TriggerParam.LOCATION -> openMapPicker()
                            TriggerParam.BT_DEVICE -> showBtPicker = true
                            TriggerParam.SSID -> showSsidInput = true
                            TriggerParam.STATE -> showStatePicker = true
                            TriggerParam.NFC_TAG -> showNfcScan = true
                            TriggerParam.NOTIFICATION_FILTER -> showNotificationFilter = true
                            TriggerParam.APP -> showAppPicker = true
                        }
                    }
                )

                // 動作清單可拖曳排序：拖握把或長按積木本體拿起，放開（onSettle）即重排草稿。
                // 只有動作清單放進 ReorderableColumn；帽子積木與幽靈積木在其外，不參與排序。
                // 每塊動作前面帶一個插入點（「＋」），點它就把新動作插入該索引；
                // 第一塊前的插入點＝插到最前（清單開頭）。
                if (draft.actions.isNotEmpty()) {
                    ReorderableColumn(
                        list = draft.actions,
                        onSettle = { from, to -> moveAction(from, to) },
                        onMove = { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) },
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(BlockStackSpacing)
                    ) { index, action, isDragging ->
                        // 捕捉 ReorderableScope；包一層 Column 後 this 會變成 ColumnScope
                        val reorderScope = this
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            InsertPoint(onClick = {
                                insertIndex = index
                                showActionPalette = true
                            })
                            ActionBlock(
                                action = action,
                                showControls = true,
                                isDragging = isDragging,
                                reorderableScope = reorderScope,
                                onBodyClick = { editingAction = IndexedAction(index, action) },
                                onRemove = { removeAction(index) }
                            )
                        }
                    }
                }

                // 幽靈積木「＋加入動作」在排序容器外，不參與排序；作為清單末尾的插入點（加到最後）
                GhostBlock(onClick = {
                    insertIndex = null
                    showActionPalette = true
                })
            }

            // 缺背景位置權限時的引導：Android 10 可直接請求，11+ 只能導到 App 設定頁
            if (locationCircle != null && playServicesAvailable && !hasBackgroundLocation) {
                Spacer(Modifier.height(12.dp))
                PermissionWarningCard(
                    title = "需要「一律允許」位置權限",
                    message = if (GeofenceManager.canRequestBackgroundLocation()) {
                        "區域觸發要在背景才能監測，點此授權「一律允許」。"
                    } else {
                        "區域觸發要在背景才能監測。點此前往設定，在「權限 > 位置」選「一律允許」。"
                    },
                    hint = "未授權時這個程序不會註冊地理圍欄，也不會觸發。"
                ) {
                    if (GeofenceManager.canRequestBackgroundLocation()) {
                        backgroundLocationLauncher.launch(
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION
                        )
                    } else {
                        context.openAppDetailsSettings()
                    }
                }
            }

            if (draft.trigger is Trigger.NotificationPosted && !hasNotificationAccess) {
                Spacer(Modifier.height(12.dp))
                PermissionWarningCard(
                    title = NOTIFICATION_ACCESS_TITLE,
                    message = "通知觸發需要讀取通知才能比對來源與關鍵字。點此前往設定，" +
                        "在清單中找到 Routina 並開啟允許。",
                    hint = RESTRICTED_SETTINGS_HINT
                ) {
                    context.openNotificationListenerSettings()
                }
            }

            if (draft.trigger is Trigger.AppState && !hasUsageAccess) {
                Spacer(Modifier.height(12.dp))
                PermissionWarningCard(
                    title = USAGE_ACCESS_TITLE,
                    message = "App 開啟／關閉觸發需要「使用情況存取權」才能知道哪個 App 在前景。" +
                        "點此前往設定，在清單中找到 Routina 並開啟允許。",
                    hint = "只在螢幕亮著且有這類程序啟用時才查詢，不會在背景持續耗電。"
                ) {
                    context.openPermissionSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    viewModel.save(draft)
                    onDone()
                },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("儲存")
            }
            if (!canSave) {
                Spacer(Modifier.height(8.dp))
                Text(
                    saveHint(draft.trigger),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    // ---------- 調色盤 / 對話框 ----------

    if (showTriggerPalette) {
        TriggerPaletteSheet(
            current = draft.trigger,
            onPick = { trigger ->
                showTriggerPalette = false
                draft = draft.copy(trigger = trigger)
                // 剛換成需要先設定才會動的觸發 → 直接把對應的選擇器帶出來，
                // 使用者不必自己猜「還要點哪裡」
                when {
                    trigger.geoCircle?.isConfigured == false -> openMapPicker()
                    trigger is Trigger.NfcTag && trigger.uid.isBlank() -> showNfcScan = true
                    trigger is Trigger.AppState && trigger.packageName.isBlank() ->
                        showAppPicker = true
                }
            },
            onDismiss = { showTriggerPalette = false },
            locationAvailable = playServicesAvailable,
            nfcAvailable = nfcAvailable
        )
    }

    if (showActionPalette) {
        ActionPaletteSheet(
            onPick = { template ->
                showActionPalette = false
                // 插入到選定的位置（插入點指定的索引；幽靈積木為 null＝加到最後），
                // 再立即開啟參數編輯；取消時會移除這塊新積木
                val index = (insertIndex ?: draft.actions.size)
                    .coerceIn(0, draft.actions.size)
                insertIndex = null
                draft = draft.copy(
                    actions = draft.actions.toMutableList().apply { add(index, template) }
                )
                editingAction = IndexedAction(index, template, isNew = true)
            },
            onDismiss = {
                showActionPalette = false
                insertIndex = null
            }
        )
    }

    editingAction?.let { target ->
        ActionEditDialog(
            initial = target.action,
            trigger = draft.trigger,
            // 只有排在這個動作之前的動作才可能提供變數（設定變數）
            precedingActions = draft.actions.take(target.index),
            onConfirm = { updated ->
                if (target.index in draft.actions.indices) {
                    draft = draft.copy(
                        actions = draft.actions.toMutableList()
                            .apply { this[target.index] = updated }
                    )
                }
                editingAction = null
            },
            onDismiss = {
                if (target.isNew) removeAction(target.index)
                editingAction = null
            }
        )
    }

    if (showTimePicker) {
        val trigger = draft.trigger as? Trigger.Time
        if (trigger != null) {
            TimePickerDialog(
                initialHour = trigger.hour,
                initialMinute = trigger.minute,
                onConfirm = { hour, minute ->
                    draft = draft.copy(trigger = trigger.copy(hour = hour, minute = minute))
                    showTimePicker = false
                },
                onDismiss = { showTimePicker = false }
            )
        } else {
            showTimePicker = false
        }
    }

    if (showDayPicker) {
        val trigger = draft.trigger as? Trigger.Time
        if (trigger != null) {
            WeekdayPickerDialog(
                days = trigger.daysOfWeek,
                onChange = { draft = draft.copy(trigger = trigger.copy(daysOfWeek = it)) },
                onDismiss = { showDayPicker = false }
            )
        } else {
            showDayPicker = false
        }
    }

    if (showTimeModePicker) {
        val trigger = draft.trigger as? Trigger.Time
        if (trigger != null) {
            TimeModeDialog(
                trigger = trigger,
                hasSunLocation = hasSunLocation,
                onChange = { draft = draft.copy(trigger = it) },
                onPickTime = { showTimePicker = true },
                onDismiss = { showTimeModePicker = false }
            )
        } else {
            showTimeModePicker = false
        }
    }

    if (showThresholdPicker) {
        val threshold = draft.trigger.batteryThreshold
        if (threshold != null) {
            ThresholdPickerDialog(
                threshold = threshold,
                above = draft.trigger is Trigger.BatteryAbove,
                onChange = {
                    draft = draft.copy(trigger = draft.trigger.withBatteryThreshold(it))
                },
                onDismiss = { showThresholdPicker = false }
            )
        } else {
            showThresholdPicker = false
        }
    }

    if (showBtPicker) {
        val device = draft.trigger.btDevice
        if (device != null) {
            BtDevicePickerDialog(
                selected = device,
                onPick = {
                    draft = draft.copy(trigger = draft.trigger.withBtDevice(it))
                    showBtPicker = false
                },
                onDismiss = { showBtPicker = false }
            )
        } else {
            showBtPicker = false
        }
    }

    if (showSsidInput) {
        val trigger = draft.trigger as? Trigger.WifiConnected
        if (trigger != null) {
            SsidInputDialog(
                ssid = trigger.ssid,
                hasLocationPermission = hasForegroundLocation,
                onChange = { draft = draft.copy(trigger = trigger.copy(ssid = it)) },
                onRequestPermission = {
                    foregroundLocationLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                onDismiss = { showSsidInput = false }
            )
        } else {
            showSsidInput = false
        }
    }

    if (showStatePicker) {
        val turnedOn = draft.trigger.stateTurnedOn
        if (turnedOn != null) {
            StatePickerDialog(
                title = triggerTypeName(draft.trigger),
                turnedOn = turnedOn,
                onChange = {
                    draft = draft.copy(trigger = draft.trigger.withStateTurnedOn(it))
                },
                onDismiss = { showStatePicker = false }
            )
        } else {
            showStatePicker = false
        }
    }

    if (showNfcScan) {
        val trigger = draft.trigger as? Trigger.NfcTag
        if (trigger != null) {
            NfcTagDialog(
                trigger = trigger,
                onChange = { draft = draft.copy(trigger = it) },
                onDismiss = { showNfcScan = false }
            )
        } else {
            showNfcScan = false
        }
    }

    if (showNotificationFilter) {
        val trigger = draft.trigger as? Trigger.NotificationPosted
        if (trigger != null) {
            NotificationFilterDialog(
                trigger = trigger,
                onChange = { draft = draft.copy(trigger = it) },
                onDismiss = { showNotificationFilter = false }
            )
        } else {
            showNotificationFilter = false
        }
    }

    if (showAppPicker) {
        val target = draft.trigger.appTarget
        if (target != null) {
            AppPickerDialog(
                onPick = {
                    draft = draft.copy(trigger = draft.trigger.withAppTarget(it))
                    showAppPicker = false
                },
                onDismiss = { showAppPicker = false },
                selectedPackage = target.packageName
            )
        } else {
            showAppPicker = false
        }
    }

    if (showMapPicker) {
        val circle = draft.trigger.geoCircle
        if (circle != null) {
            MapPickerDialog(
                initial = circle,
                onConfirm = { picked ->
                    draft = draft.copy(trigger = draft.trigger.withGeoCircle(picked))
                    showMapPicker = false
                },
                onDismiss = { showMapPicker = false }
            )
        } else {
            showMapPicker = false
        }
    }

    if (showColorPicker) {
        AlertDialog(
            onDismissRequest = { showColorPicker = false },
            title = { Text("方塊顏色") },
            text = {
                ColorPickerRow(
                    selected = draft.color,
                    onSelect = { draft = draft.copy(color = it) }
                )
            },
            confirmButton = {
                TextButton(onClick = { showColorPicker = false }) { Text("完成") }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("刪除例行程序") },
            text = { Text("確定要刪除「${draft.name}」嗎？此操作無法復原。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(draft.id)
                    showDeleteConfirm = false
                    onDone()
                }) {
                    Text("刪除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("取消") }
            }
        )
    }
}

/** 方塊顏色挑選（放在 ⋯ 選單的對話框內）：「依觸發」預設 + 一排預選色，設定 [Routine.color] */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPickerRow(selected: Int?, onSelect: (Int?) -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("依觸發") }
        )
        RoutinePalette.forEach { c ->
            val argb = c.toArgb()
            ColorDot(color = c, selected = selected == argb, onClick = { onSelect(argb) })
        }
    }
}

/** 一顆可點的顏色圓點；選中時加深色外框 + 白色勾 */
@Composable
private fun ColorDot(color: Color, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(color)
            .then(
                if (selected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "已選",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** 積木畫布：點狀網格背景，Scratch 編輯區質感 */
@Composable
private fun BlockCanvas(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val dotColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind {
                val step = 20.dp.toPx()
                val radius = 1.dp.toPx()
                var y = step / 2f
                while (y < size.height) {
                    var x = step / 2f
                    while (x < size.width) {
                        drawCircle(color = dotColor, radius = radius, center = Offset(x, y))
                        x += step
                    }
                    y += step
                }
            }
            .padding(horizontal = 12.dp, vertical = 16.dp),
        // 帽子積木 / 排序容器 / 幽靈積木之間維持與堆疊相同的等距間隔
        verticalArrangement = Arrangement.spacedBy(BlockStackSpacing),
        content = content
    )
}

/** 星期複選：改動即時套用到草稿，關閉即完成 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WeekdayPickerDialog(
    days: Set<Int>,
    onChange: (Set<Int>) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重複") },
        text = {
            Column {
                // 7 顆 chip 在 360dp 寬的裝置上放不進單一 Row，需要自動換行
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    (1..7).forEach { day ->
                        val selected = day in days
                        FilterChip(
                            selected = selected,
                            onClick = {
                                onChange(
                                    days.toMutableSet().apply {
                                        if (selected) remove(day) else add(day)
                                    }
                                )
                            },
                            label = { Text(weekdayLabel(day)) }
                        )
                    }
                }
                if (days.isEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "未選擇任何星期時視為每天執行。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}

/** 電量門檻滑桿：改動即時套用到草稿，關閉即完成 */
@Composable
private fun ThresholdPickerDialog(
    threshold: Int,
    above: Boolean,
    onChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("電量門檻") },
        text = {
            Column {
                Text(
                    if (above) "電量高於 $threshold%" else "電量低於 $threshold%",
                    style = MaterialTheme.typography.titleMedium
                )
                Slider(
                    value = threshold.toFloat(),
                    onValueChange = { onChange(it.roundToInt().coerceIn(5, 95)) },
                    valueRange = 5f..95f,
                    steps = 17
                )
                Text(
                    if (above) {
                        "電量升至門檻（含）以上時觸發一次，回落至門檻以下後才會重置。"
                    } else {
                        "電量降至門檻（含）以下時觸發一次，回升至門檻以上後才會重置。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}

/**
 * 定時模式：固定時間 / 日出 / 日落（＋偏移分鐘）。
 * 固定時間模式下由「選擇時間」帶出時鐘選擇器。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TimeModeDialog(
    trigger: Trigger.Time,
    hasSunLocation: Boolean,
    onChange: (Trigger.Time) -> Unit,
    onPickTime: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("觸發時刻") },
        text = {
            Column {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TimeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = trigger.mode == mode,
                            onClick = { onChange(trigger.copy(mode = mode)) },
                            label = { Text(timeModeName(mode)) }
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))

                if (trigger.mode == TimeMode.FIXED) {
                    Text(
                        "時間：${timeLabel(trigger.hour, trigger.minute)}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    TextButton(onClick = onPickTime) { Text("選擇時間") }
                } else {
                    Text(timeModeLabel(trigger), style = MaterialTheme.typography.titleMedium)
                    Slider(
                        value = trigger.offsetMinutes.toFloat(),
                        onValueChange = {
                            onChange(
                                trigger.copy(
                                    offsetMinutes = (it.roundToInt() / 5 * 5).coerceIn(
                                        Trigger.MIN_SUN_OFFSET_MIN,
                                        Trigger.MAX_SUN_OFFSET_MIN
                                    )
                                )
                            )
                        },
                        valueRange = Trigger.MIN_SUN_OFFSET_MIN.toFloat()..
                            Trigger.MAX_SUN_OFFSET_MIN.toFloat(),
                        steps = 47
                    )
                    Text(
                        if (hasSunLocation) {
                            "日出日落以裝置所在地本地計算（沿用你設定過的區域地點），誤差約 ±2 分鐘。"
                        } else {
                            "目前沒有任何位置資訊，將以預設的日出 06:00 / 日落 18:00 運作。" +
                                "建立一個區域觸發並選好地點後即會改用實際日出日落時間。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (hasSunLocation) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}

/** 已配對藍牙裝置選單（含「任一裝置」） */
@Composable
private fun BtDevicePickerDialog(
    selected: BtDevice,
    onPick: (BtDevice) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(BtAclReceiver.canReadDeviceName(context)) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }

    val devices = remember(granted) { bondedDevices(context) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("選擇藍牙裝置") },
        text = {
            Column {
                PickerRow(
                    label = "任一裝置",
                    detail = "任何藍牙裝置連接／斷開時都觸發",
                    selected = selected.address.isBlank(),
                    onClick = { onPick(BtDevice("", "")) }
                )
                if (!granted) {
                    Text(
                        "未取得「附近的裝置」權限，無法讀取已配對裝置的名稱與清單。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(
                        onClick = { launcher.launch(Manifest.permission.BLUETOOTH_CONNECT) }
                    ) {
                        Text("授權")
                    }
                } else if (devices.isEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "找不到已配對的藍牙裝置。請先到系統設定完成配對。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                        items(devices, key = { it.address }) { device ->
                            PickerRow(
                                label = device.name.ifBlank { device.address },
                                detail = device.address,
                                selected = selected.address.equals(
                                    device.address,
                                    ignoreCase = true
                                ),
                                onClick = { onPick(device) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** 已配對裝置清單。無 BLUETOOTH_CONNECT 權限時系統會擋下，回傳空清單。 */
private fun bondedDevices(context: Context): List<BtDevice> = runCatching {
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        ?: return emptyList()
    adapter.bondedDevices
        .map { BtDevice(it.address ?: "", runCatching { it.name }.getOrNull() ?: "") }
        .filter { it.address.isNotBlank() }
        .sortedBy { it.name.lowercase() }
}.getOrDefault(emptyList())

/** Wi-Fi SSID 輸入（留空＝任一網路） */
@Composable
private fun SsidInputDialog(
    ssid: String,
    hasLocationPermission: Boolean,
    onChange: (String) -> Unit,
    onRequestPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Wi-Fi 網路") },
        text = {
            Column {
                OutlinedTextField(
                    value = ssid,
                    onValueChange = onChange,
                    label = { Text("網路名稱（SSID）") },
                    placeholder = { Text("留空＝任一網路") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "請輸入與系統顯示完全相同的名稱（區分大小寫）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (ssid.isNotBlank() && !hasLocationPermission) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Android 9 起讀取 Wi-Fi 名稱需要精確位置權限。" +
                            "未授權時指定 SSID 的程序不會觸發。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    TextButton(onClick = onRequestPermission) { Text("授權位置") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}

/**
 * NFC 標籤登錄：對話框開著的期間啟用 reader mode，掃到標籤就把 UID 填進觸發參數。
 *
 * reader mode 只在這個對話框存在時啟用（[NfcReaderModeEffect] 收尾即解除）——
 * 一方面不影響 App 其他畫面的 NFC 行為，另一方面登錄中的掃描不會被
 * NfcDispatchActivity 搶去當成「觸發」。
 *
 * 下段的「寫入標籤（選用）」帶出 [NfcWriteDialog]。同一個 Activity 同時只能有一種
 * reader mode，所以寫入對話框開著時這裡的掃描模式必須讓位（`active = !showWrite`），
 * 關閉後再自動接回來。
 */
@Composable
private fun NfcTagDialog(
    trigger: Trigger.NfcTag,
    onChange: (Trigger.NfcTag) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val available = remember(context) { NfcTagReader.isAvailable(context) }
    var enabled by remember { mutableStateOf(NfcTagReader.isEnabled(context)) }
    var showWrite by remember { mutableStateOf(false) }

    // rememberUpdatedState：reader mode 註冊一次就好，但回呼必須看得到最新的 trigger，
    // 否則掃描前輸入的標籤名稱會被舊值蓋掉
    val handleUid by rememberUpdatedState(
        newValue = { uid: String -> onChange(trigger.copy(uid = uid)) }
    )

    NfcReaderModeEffect(
        activity = activity,
        active = !showWrite,
        onNfcEnabledChange = { enabled = it }
    ) { NfcTagReader.enableReaderMode(it) { uid -> handleUid(uid) } }

    if (showWrite) {
        NfcWriteDialog(
            activity = activity,
            onUid = { handleUid(it) },
            onDismiss = { showWrite = false }
        )
    }

    val ready = available && activity != null && enabled

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("NFC 標籤") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                when {
                    !available || activity == null -> Text(
                        "這台裝置沒有 NFC 硬體，無法使用 NFC 標籤觸發。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )

                    !enabled -> {
                        Text(
                            "NFC 目前是關閉的，開啟後才能掃描標籤。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = { context.openNfcSettings() }) {
                            Text("前往 NFC 設定")
                        }
                    }

                    trigger.uid.isBlank() -> Text(
                        "把 NFC 標籤靠到手機背面（多數機型在鏡頭附近），讀到就會自動填入。",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    else -> Text(
                        "已讀到標籤：${trigger.uid}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = trigger.label,
                    onValueChange = { onChange(trigger.copy(label = it)) },
                    label = { Text("標籤名稱") },
                    placeholder = { Text("例如：床頭標籤") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "登錄只讀取標籤的識別碼，不會改動標籤內容；" +
                        "再靠一次新的標籤即可換成別張。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(20.dp))
                Text("寫入標籤（選用）", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "寫入後掃描會直接開啟 Routina，不再跳出 App 選擇視窗。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { showWrite = true }, enabled = ready) {
                    Text("寫入標籤")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}

/**
 * 寫入標籤：把 `routina://tag/{uid}` 寫進標籤，此後掃描該標籤會直接開啟 Routina。
 *
 * 寫入前先完成／更新 UID 登錄——即使寫入失敗（唯讀、容量不足），標籤仍然可以用
 * 一般的掃描方式觸發，登錄不該跟著失敗一起被丟掉。
 * 有結果之後就關掉寫入模式，手還沒離開手機也不會被重複寫第二次。
 */
@Composable
private fun NfcWriteDialog(
    activity: Activity?,
    onUid: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(NfcTagReader.isEnabled(context)) }
    var outcome by remember { mutableStateOf<NfcTagReader.WriteOutcome?>(null) }

    val handleOutcome by rememberUpdatedState(
        newValue = { result: NfcTagReader.WriteOutcome ->
            result.uid?.let { onUid(it) }
            outcome = result
        }
    )

    NfcReaderModeEffect(
        activity = activity,
        active = outcome == null,
        onNfcEnabledChange = { enabled = it }
    ) { NfcTagReader.enableWriteMode(it) { result -> handleOutcome(result) } }

    val result = outcome
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("寫入標籤") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                when {
                    activity == null -> Text(
                        "這台裝置沒有 NFC 硬體，無法寫入標籤。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )

                    !enabled -> {
                        Text(
                            "NFC 目前是關閉的，開啟後才能寫入標籤。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        TextButton(onClick = { context.openNfcSettings() }) {
                            Text("前往 NFC 設定")
                        }
                    }

                    result == null -> {
                        Text(
                            "將標籤貼近手機背面…",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "多數機型的 NFC 天線在鏡頭附近。寫入期間請保持標籤貼緊、不要移開。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    result.success -> Text(
                        "已寫入，掃描此標籤將直接開啟 Routina",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    else -> {
                        Text(
                            result.error ?: "寫入失敗",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        if (result.uid != null) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "標籤識別碼已登錄完成，這張標籤仍可用一般方式掃描觸發" +
                                    "（掃描時可能出現系統的 App 選擇視窗）。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    "寫入的內容只是一段 Routina 專屬的識別網址，不會改動標籤的識別碼；" +
                        "未格式化的空白標籤會自動格式化。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (result == null) "取消" else "完成")
            }
        },
        dismissButton = if (result != null && !result.success) {
            { TextButton(onClick = { outcome = null }) { Text("再試一次") } }
        } else {
            null
        }
    )
}

/**
 * 把 NFC reader mode 綁在 Activity 的 resumed 狀態上（系統要求），並回報 NFC 開關狀態。
 *
 * 去 NFC 設定頁再回來要重新啟用，離開畫面就解除；觀察者加入時會補送目前狀態的事件，
 * 因此對話框一開就會啟用一次。
 *
 * 同一個 Activity 同時只能有一種 reader mode（後啟用的會取代前一個），
 * 所以掃描與寫入對話框以 [active] 互相讓位——關掉的那一邊會先解除，
 * 接手的那一邊才啟用。
 *
 * @param enable 實際要啟用的模式（掃描或寫入）
 */
@Composable
private fun NfcReaderModeEffect(
    activity: Activity?,
    active: Boolean,
    onNfcEnabledChange: (Boolean) -> Unit,
    enable: (Activity) -> Unit
) {
    val context = LocalContext.current
    val currentEnable by rememberUpdatedState(enable)
    val currentOnChange by rememberUpdatedState(onNfcEnabledChange)

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, activity, active) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    val nfcOn = NfcTagReader.isEnabled(context)
                    currentOnChange(nfcOn)
                    if (activity != null && active && nfcOn) currentEnable(activity)
                }

                Lifecycle.Event.ON_PAUSE ->
                    activity?.let { NfcTagReader.disableReaderMode(it) }

                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            activity?.let { NfcTagReader.disableReaderMode(it) }
        }
    }
}

/** 通知觸發的來源 App（可選「任一 App」）與關鍵字 */
@Composable
private fun NotificationFilterDialog(
    trigger: Trigger.NotificationPosted,
    onChange: (Trigger.NotificationPosted) -> Unit,
    onDismiss: () -> Unit
) {
    var showAppPicker by remember { mutableStateOf(false) }

    if (showAppPicker) {
        AppPickerDialog(
            onPick = {
                onChange(trigger.copy(packageName = it.packageName, appName = it.appName))
                showAppPicker = false
            },
            onDismiss = { showAppPicker = false },
            anyAppLabel = "任一 App",
            selectedPackage = trigger.packageName
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("通知條件") },
        text = {
            Column {
                Text(
                    "來源：" + appTargetLabel(
                        AppTarget(trigger.packageName, trigger.appName),
                        blankLabel = "任一 App"
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
                TextButton(onClick = { showAppPicker = true }) { Text("選擇 App") }

                Spacer(Modifier.height(4.dp))
                OutlinedTextField(
                    value = trigger.keyword,
                    onValueChange = { onChange(trigger.copy(keyword = it)) },
                    label = { Text("關鍵字") },
                    placeholder = { Text("留空＝不限內容") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "比對通知的標題與內容，不分大小寫；同一則通知 5 秒內只觸發一次。" +
                        "Routina 自己發出的通知不會觸發（避免無限迴圈）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}

/** 儲存按鈕停用時的說明：缺什麼就說什麼 */
private fun saveHint(trigger: Trigger): String = when {
    trigger.isConfigured -> "請輸入名稱並至少加入一個動作。"
    trigger is Trigger.NfcTag -> "請輸入名稱、掃描一張 NFC 標籤，並至少加入一個動作。"
    trigger is Trigger.AppState -> "請輸入名稱、選擇一個 App，並至少加入一個動作。"
    else -> "請輸入名稱、在地圖上選好區域，並至少加入一個動作。"
}

/** 從 Compose 的 Context 找出宿主 Activity（NFC reader mode 必須綁定 Activity） */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** 系統狀態切換：開啟時 / 關閉時 */
@Composable
private fun StatePickerDialog(
    title: String,
    turnedOn: Boolean,
    onChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(true, false).forEach { on ->
                    FilterChip(
                        selected = turnedOn == on,
                        onClick = { onChange(on) },
                        label = { Text(onOffLabel(on)) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )
    // 橫向時改用水平版面，並讓內容可捲動，避免在矮螢幕上被裁切
    val configuration = LocalConfiguration.current
    val layoutType = if (configuration.screenWidthDp > configuration.screenHeightDp) {
        TimePickerLayoutType.Horizontal
    } else {
        TimePickerLayoutType.Vertical
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("選擇時間") },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center
            ) {
                TimePicker(state = state, layoutType = layoutType)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(state.hour, state.minute) }) { Text("確定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
