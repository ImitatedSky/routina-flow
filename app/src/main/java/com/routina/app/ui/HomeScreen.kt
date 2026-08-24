package com.routina.app.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.routina.app.engine.AlarmScheduler
import com.routina.app.engine.RoutinaNotificationListener
import com.routina.app.model.Action
import com.routina.app.model.Routine
import com.routina.app.model.RunLog
import com.routina.app.model.TimeMode
import com.routina.app.model.Trigger
import com.routina.app.model.isLocation
import com.routina.app.ui.theme.actionColor
import com.routina.app.ui.theme.triggerColor
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: RoutineViewModel,
    onCreate: () -> Unit,
    onCreateFromTemplate: (String) -> Unit,
    onEdit: (String) -> Unit,
    onOpenLogs: () -> Unit
) {
    val context = LocalContext.current
    val routines by viewModel.routines.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    // 非空狀態下 FAB 帶出的「從範本建立 / 空白建立」sheet
    var showTemplateSheet by remember { mutableStateOf(false) }
    // 清單搜尋查詢（旋轉 / 程序重建後保留）
    var query by rememberSaveable { mutableStateOf("") }
    // 首頁呈現方式（清單／格狀），持久化於 SharedPreferences，重開 App 保留
    var viewMode by remember { mutableStateOf(loadHomeViewMode(context)) }

    // 權限狀態：從系統設定頁返回時（ON_RESUME）重新檢查，授權完成後引導卡要立即消失
    var canScheduleExact by remember { mutableStateOf(viewModel.canScheduleExactAlarms()) }
    var canDrawOverlays by remember { mutableStateOf(viewModel.canDrawOverlays()) }
    var notificationsEnabled by remember { mutableStateOf(viewModel.areNotificationsEnabled()) }
    var hasBackgroundLocation by remember { mutableStateOf(viewModel.hasBackgroundLocation()) }
    var hasForegroundLocation by remember { mutableStateOf(viewModel.hasForegroundLocation()) }
    var canWriteSettings by remember { mutableStateOf(viewModel.canWriteSettings()) }
    var hasNotificationAccess by remember { mutableStateOf(viewModel.hasNotificationAccess()) }
    var hasUsageAccess by remember { mutableStateOf(viewModel.hasUsageAccess()) }
    var hasCameraPermission by remember { mutableStateOf(context.hasCameraPermission()) }
    var hasMicPermission by remember { mutableStateOf(context.hasMicrophonePermission()) }
    var hasDndAccess by remember { mutableStateOf(viewModel.hasDndAccess()) }
    var nfcEnabled by remember { mutableStateOf(viewModel.isNfcEnabled()) }
    // GMS 可用性與 NFC 硬體的有無不會在 App 執行期間改變，查一次就好
    val playServicesAvailable = remember { viewModel.isPlayServicesAvailable() }
    val nfcAvailable = remember { viewModel.isNfcAvailable() }

    // Android 13+：首次進入請求通知權限（「顯示通知」動作與前景服務通知都需要）
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // 拒絕也不阻斷 App，改由下方的引導卡片接手
        notificationsEnabled = viewModel.areNotificationsEnabled()
    }

    LaunchedEffect(Unit) {
        val notGranted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        // 已授權就不再打擾；曾被拒絕時系統也只會忽略這次請求
        if (notGranted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // 相機／麥克風：拍照 / 連拍 / 錄音動作用的 runtime 權限，可一鍵請求
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { hasCameraPermission = context.hasCameraPermission() }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasMicPermission = it }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val exactNow = viewModel.canScheduleExactAlarms()
                val backgroundLocationNow = viewModel.hasBackgroundLocation()
                val usageAccessNow = viewModel.hasUsageAccess()
                // 剛授權完精確鬧鐘 → 把先前降級的時間窗排程升級回精確鬧鐘
                // 剛授權完背景位置 → 立刻補註冊地理圍欄
                // 剛授權完使用情況存取 → 讓監測服務立刻開始輪詢前景 App
                if ((exactNow && !canScheduleExact) ||
                    (backgroundLocationNow && !hasBackgroundLocation) ||
                    (usageAccessNow && !hasUsageAccess)
                ) {
                    viewModel.syncAll()
                }
                canScheduleExact = exactNow
                hasBackgroundLocation = backgroundLocationNow
                hasUsageAccess = usageAccessNow
                hasForegroundLocation = viewModel.hasForegroundLocation()
                canDrawOverlays = viewModel.canDrawOverlays()
                canWriteSettings = viewModel.canWriteSettings()
                notificationsEnabled = viewModel.areNotificationsEnabled()
                hasNotificationAccess = viewModel.hasNotificationAccess()
                hasCameraPermission = context.hasCameraPermission()
                hasMicPermission = context.hasMicrophonePermission()
                hasDndAccess = viewModel.hasDndAccess()
                nfcEnabled = viewModel.isNfcEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val hasTimeRoutine = routines.any { it.enabled && it.trigger is Trigger.Time }
    // 手動 routine 只在前景（▶）執行，不會從背景啟動 App / 網址 → 不列入上層顯示權限提示
    val hasBackgroundLaunchRoutine = routines.any { routine ->
        routine.enabled && routine.trigger !is Trigger.Manual &&
            routine.actions.any { it is Action.OpenApp || it is Action.OpenUrl }
    }
    val hasNotifyRoutine = routines.any { routine ->
        routine.actions.any { it is Action.Notify }
    }
    val hasLocationRoutine = routines.any { it.enabled && it.trigger.isLocation }
    val hasBrightnessRoutine = routines.any { routine ->
        routine.enabled && routine.actions.any { it is Action.Brightness }
    }
    // 指定了 SSID 的 Wi-Fi 觸發：讀 SSID 需要精確位置，未授權時這些程序不會觸發
    val hasNamedSsidRoutine = routines.any { routine ->
        routine.enabled && (routine.trigger as? Trigger.WifiConnected)?.ssid?.isNotBlank() == true
    }
    // 日出 / 日落模式但完全沒有位置資訊 → 以預設 06:00 / 18:00 運作
    val hasSunRoutineWithoutLocation = routines.any { routine ->
        val trigger = routine.trigger
        routine.enabled && trigger is Trigger.Time && trigger.mode != TimeMode.FIXED
    } && !viewModel.hasSunLocation()
    val hasNotificationTriggerRoutine =
        routines.any { it.enabled && it.trigger is Trigger.NotificationPosted }
    val hasAppStateRoutine = routines.any { it.enabled && it.trigger is Trigger.AppState }
    val hasNfcRoutine = routines.any { it.enabled && it.trigger is Trigger.NfcTag }
    val hasCameraRoutine = routines.any { routine ->
        routine.enabled &&
            routine.actions.any { it is Action.TakePhoto || it is Action.BurstPhoto }
    }
    val hasAudioRoutine = routines.any { routine ->
        routine.enabled && routine.actions.any { it is Action.RecordAudio }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Routina", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = {
                        if (viewMode != HomeViewMode.LIST) {
                            viewMode = HomeViewMode.LIST
                            saveHomeViewMode(context, HomeViewMode.LIST)
                        }
                    }) {
                        Icon(
                            Icons.Filled.ViewList,
                            contentDescription = "清單檢視",
                            tint = if (viewMode == HomeViewMode.LIST) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    IconButton(onClick = {
                        if (viewMode != HomeViewMode.GRID) {
                            viewMode = HomeViewMode.GRID
                            saveHomeViewMode(context, HomeViewMode.GRID)
                        }
                    }) {
                        Icon(
                            Icons.Filled.GridView,
                            contentDescription = "格狀檢視",
                            tint = if (viewMode == HomeViewMode.GRID) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                    IconButton(onClick = onOpenLogs) {
                        Icon(Icons.Filled.History, contentDescription = "執行紀錄")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showTemplateSheet = true }) {
                Icon(Icons.Filled.Add, contentDescription = "新增例行程序")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (hasTimeRoutine && !canScheduleExact) {
                PermissionWarningCard(
                    title = "未授權「鬧鐘與提醒」",
                    message = "定時程序目前以 ±10 分鐘的時間窗執行。點此前往設定授權。"
                ) {
                    context.openPermissionSettings(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM
                    )
                }
            }

            if (hasNotifyRoutine && !notificationsEnabled) {
                PermissionWarningCard(
                    title = "通知權限未授權",
                    message = "「顯示通知」動作會被記為失敗。點此前往系統通知設定開啟。"
                ) {
                    context.openNotificationSettings()
                }
            }

            if (hasLocationRoutine && !playServicesAvailable) {
                PermissionWarningCard(
                    title = "區域觸發不可用",
                    message = "這台裝置沒有 Google Play 服務，無法使用系統級地理圍欄，" +
                        "區域觸發的例行程序不會被執行。其他觸發類型不受影響。"
                )
            }

            if (hasLocationRoutine && playServicesAvailable && !hasBackgroundLocation) {
                PermissionWarningCard(
                    title = "未授權「一律允許」位置權限",
                    message = "區域觸發需要背景位置權限，目前不會註冊地理圍欄、也不會觸發。" +
                        "點此前往設定，在「權限 > 位置」選擇「一律允許」。",
                    hint = "Android 11 起系統不允許 App 直接請求背景位置，" +
                        "只能由你在設定頁選擇；授權後回到 Routina 即會自動註冊。"
                ) {
                    context.openAppDetailsSettings()
                }
            }

            if (hasNamedSsidRoutine && !hasForegroundLocation) {
                PermissionWarningCard(
                    title = "未授權位置權限（Wi-Fi 名稱）",
                    message = "Android 9 起讀取 Wi-Fi 名稱需要精確位置權限，" +
                        "指定了 SSID 的 Wi-Fi 程序目前不會觸發。點此前往設定授權。",
                    hint = "改成「任一網路」則不需要位置權限。"
                ) {
                    context.openAppDetailsSettings()
                }
            }

            if (hasSunRoutineWithoutLocation) {
                PermissionWarningCard(
                    title = "日出／日落缺少位置資訊",
                    message = "目前沒有任何位置可用於計算日出日落，這些程序會以預設的 " +
                        "06:00 / 18:00 運作。",
                    hint = "建立一個區域觸發並在地圖上選好地點後，日出日落即會改用實際時間。"
                )
            }

            if (hasBrightnessRoutine && !canWriteSettings) {
                PermissionWarningCard(
                    title = "未授權「修改系統設定」",
                    message = "「螢幕亮度」動作會被記為失敗。點此前往設定，允許 Routina 修改系統設定。",
                    hint = "側載安裝時開關可能被「受限制的設定」鎖住，處理方式與下方的疊加權限相同。"
                ) {
                    context.openPermissionSettings(Settings.ACTION_MANAGE_WRITE_SETTINGS)
                }
            }

            if (hasBackgroundLaunchRoutine && !canDrawOverlays) {
                PermissionWarningCard(
                    title = "未授權「顯示在其他應用程式上層」",
                    message = "背景觸發時無法直接開啟 App / 網址，會改發可點擊開啟的通知。" +
                        "點此前往設定授權，在清單中找到 Routina 並開啟允許。",
                    hint = RESTRICTED_SETTINGS_HINT
                ) {
                    context.openPermissionSettings(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION
                    )
                }
            }

            if (hasNotificationTriggerRoutine && !hasNotificationAccess) {
                PermissionWarningCard(
                    title = NOTIFICATION_ACCESS_TITLE,
                    message = "通知觸發需要讀取通知才能比對，這些程序目前不會觸發。" +
                        "點此前往設定，在清單中找到 Routina 並開啟允許。",
                    hint = RESTRICTED_SETTINGS_HINT
                ) {
                    context.openNotificationListenerSettings()
                }
            }

            if (hasAppStateRoutine && !hasUsageAccess) {
                PermissionWarningCard(
                    title = USAGE_ACCESS_TITLE,
                    message = "App 開啟／關閉觸發需要「使用情況存取權」才能知道哪個 App 在前景，" +
                        "這些程序目前不會觸發。點此前往設定，在清單中找到 Routina 並開啟允許。",
                    hint = "只在螢幕亮著且有這類程序啟用時才查詢，不會在背景持續耗電。"
                ) {
                    context.openPermissionSettings(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                }
            }

            if (hasNfcRoutine && nfcAvailable && !nfcEnabled) {
                PermissionWarningCard(
                    title = "NFC 已關閉",
                    message = "NFC 關著時掃描不到標籤，NFC 觸發的程序不會執行。" +
                        "點此前往 NFC 設定開啟。"
                ) {
                    context.openNfcSettings()
                }
            }

            if (hasCameraRoutine && !hasCameraPermission) {
                PermissionWarningCard(
                    title = "未授權「相機」權限",
                    message = "拍照／連拍動作會被記為失敗。點此授權相機。",
                    hint = "背景觸發時，Android 14+ 仍可能擋下背景啟動相機——" +
                        "手動「立即執行」一律可用。"
                ) {
                    cameraPermissionLauncher.launch(cameraPermissionArray())
                }
            }

            if (hasAudioRoutine && !hasMicPermission) {
                PermissionWarningCard(
                    title = "未授權「麥克風」權限",
                    message = "錄音動作會被記為失敗。點此授權麥克風。",
                    hint = "背景觸發時，Android 14+ 仍可能擋下背景啟動麥克風——" +
                        "手動「立即執行」一律可用。"
                ) {
                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }

            if (routines.isEmpty()) {
                // 範本空狀態只在「完全沒有 routine」時出現，搜尋無結果不走這裡
                EmptyState(
                    onPick = { onCreateFromTemplate(it.id) },
                    onBlank = onCreate
                )
            } else {
                SearchBar(
                    query = query,
                    onQueryChange = { query = it },
                    onClear = { query = "" }
                )

                val trimmed = query.trim()
                // 拖曳排序只在「清單」模式且未搜尋時可用（格狀為概覽、過濾清單排序無意義）
                val reorderEnabled = trimmed.isEmpty() && viewMode == HomeViewMode.LIST

                val filtered = if (trimmed.isEmpty()) {
                    routines
                } else {
                    routines.filter { routineSearchText(it).contains(trimmed, ignoreCase = true) }
                }

                if (filtered.isEmpty()) {
                    // 有查詢但無符合：與範本空狀態區分開，只給一句提示、不跳範本格
                    NoSearchResults(Modifier.weight(1f))
                    return@Column
                }

                // 一次查好權限狀態與日出日落座標，交給每張卡片彙整自己的狀態晶片，
                // 避免在清單每次重繪時對每張卡片重複查詢系統
                val permSnapshot = PermSnapshot(
                    canScheduleExact = canScheduleExact,
                    notificationsEnabled = notificationsEnabled,
                    hasBackgroundLocation = hasBackgroundLocation,
                    canWriteSettings = canWriteSettings,
                    canDrawOverlays = canDrawOverlays,
                    hasNotificationAccess = hasNotificationAccess,
                    hasUsageAccess = hasUsageAccess,
                    hasCameraPermission = hasCameraPermission,
                    hasMicPermission = hasMicPermission,
                    hasDndAccess = hasDndAccess,
                    nfcAvailable = nfcAvailable,
                    nfcEnabled = nfcEnabled
                )
                val sunLocation = remember(routines) { viewModel.sunLocation() }

                // 兩種呈現共用：立即執行並回報結果
                // （含等待 / 朗讀 / HTTP 的程序可能跑數十秒，執行完才回報結果）
                val runNow: (Routine) -> Unit = { routine ->
                    viewModel.runNow(routine.id) { log ->
                        val failures = log?.failureCount ?: 0
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                if (failures > 0) {
                                    "已執行「${routine.name}」（$failures 個動作失敗）"
                                } else {
                                    "已執行「${routine.name}」"
                                }
                            )
                        }
                    }
                }

                when (viewMode) {
                    HomeViewMode.LIST -> RoutineListView(
                        items = filtered,
                        logs = logs,
                        sunLocation = sunLocation,
                        permSnapshot = permSnapshot,
                        reorderEnabled = reorderEnabled,
                        onReorderCommit = { from, to -> viewModel.reorder(from, to) },
                        onEdit = onEdit,
                        onToggle = { id, enabled -> viewModel.setEnabled(id, enabled) },
                        onRunNow = runNow,
                        modifier = Modifier.weight(1f)
                    )

                    HomeViewMode.GRID -> RoutineGridView(
                        items = filtered,
                        sunLocation = sunLocation,
                        onEdit = onEdit,
                        onToggle = { id, enabled -> viewModel.setEnabled(id, enabled) },
                        onRunNow = runNow,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    // 非空狀態下 FAB 帶出的範本選擇 sheet（含「空白」＝空白新建）
    if (showTemplateSheet) {
        TemplateSheet(
            onPick = {
                showTemplateSheet = false
                onCreateFromTemplate(it.id)
            },
            onBlank = {
                showTemplateSheet = false
                onCreate()
            },
            onDismiss = { showTemplateSheet = false }
        )
    }
}

/** FAB 帶出的範本選擇 sheet：「從範本建立 / 空白建立」一鍵可達 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TemplateSheet(
    onPick: (RoutineTemplate) -> Unit,
    onBlank: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "從範本建立",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                "點一下就建好，之後再改。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TemplateGrid(onPick = onPick, onBlank = onBlank)
        }
    }
}

/**
 * 側載安裝時高風險權限的開關會被「受限制的設定」鎖住，解鎖步驟到處都一樣，
 * 因此各張引導卡共用同一份文案（已在 Pixel 10 實測可行）。
 */
internal const val RESTRICTED_SETTINGS_HINT =
    "若開關呈灰色無法開啟：請到 設定 > 應用程式 > Routina > 右上角 ⋮ > " +
        "允許受限制的設定，驗證身分後再回來開啟權限" +
        "（Android 13+ 對非商店安裝的 App 的安全機制）。"

internal const val NOTIFICATION_ACCESS_TITLE = "未授權「通知存取權」"
internal const val USAGE_ACCESS_TITLE = "未授權「使用情況存取權」"

/**
 * 通知存取權設定頁。Android 11 起可以直接開到本 App 的那一列開關；
 * 帶不動時退回通用的通知存取清單頁，再退回系統設定首頁。
 */
internal fun Context.openNotificationListenerSettings() {
    val component = ComponentName(this, RoutinaNotificationListener::class.java)
    val intents = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            add(
                Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
                    Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    component.flattenToString()
                )
            )
        }
        add(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        add(Intent(Settings.ACTION_SETTINGS))
    }
    startFirstAvailable(*intents.toTypedArray())
}

/** NFC 開關頁；部分機型沒有獨立的 NFC 頁，退回無線與網路設定 */
internal fun Context.openNfcSettings() {
    startFirstAvailable(
        Intent(Settings.ACTION_NFC_SETTINGS),
        Intent(Settings.ACTION_WIRELESS_SETTINGS),
        Intent(Settings.ACTION_SETTINGS)
    )
}

/**
 * 開啟權限設定頁。優先帶 package URI 直達本 App 的開關頁
 * （部分機型的通用清單頁很難找到自己的 App），該頁不存在時退回通用清單頁。
 */
internal fun Context.openPermissionSettings(action: String) {
    startFirstAvailable(
        Intent(action, Uri.parse("package:$packageName")),
        Intent(action)
    )
}

/**
 * 本 App 的設定頁：背景位置在 Android 11+ 只能由使用者在這裡選「一律允許」，
 * 沒有可直接請求的 runtime 對話框。
 */
internal fun Context.openAppDetailsSettings() {
    startFirstAvailable(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
        Intent(Settings.ACTION_SETTINGS)
    )
}

/** 系統通知設定頁：以 extra 指定本 App，失敗時退回通用通知設定頁 */
internal fun Context.openNotificationSettings() {
    startFirstAvailable(
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
        Intent(Settings.ACTION_SETTINGS)
    )
}

/** 是否已授權相機（拍照 / 連拍動作的前提） */
private fun Context.hasCameraPermission(): Boolean = runCatching {
    ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
        PackageManager.PERMISSION_GRANTED
}.getOrDefault(false)

/** 是否已授權麥克風（錄音動作的前提） */
private fun Context.hasMicrophonePermission(): Boolean = runCatching {
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
}.getOrDefault(false)

/** 拍照請求的權限：Android 9 以下另需 WRITE_EXTERNAL_STORAGE 存進公開相簿 */
private fun cameraPermissionArray(): Array<String> =
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
        arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    } else {
        arrayOf(Manifest.permission.CAMERA)
    }

/** 依序嘗試，第一個能開啟的就停止；全部失敗時安靜略過（不讓 App 崩潰） */
internal fun Context.startFirstAvailable(vararg intents: Intent) {
    for (intent in intents) {
        try {
            startActivity(intent)
            return
        } catch (_: Exception) {
            // ActivityNotFoundException（頁面不存在）或 OEM 的 SecurityException → 試下一個
        }
    }
}

/**
 * 權限未授權時的引導卡片：點擊直接跳到對應的系統設定頁。
 * [onGrant] 為 null 代表純告知（例如無 GMS，使用者無從處理）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PermissionWarningCard(
    title: String,
    message: String,
    hint: String? = null,
    onGrant: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp, 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            // 純告知的卡片（onGrant = null）不可點，但外觀必須維持警示色
            disabledContainerColor = MaterialTheme.colorScheme.errorContainer
        ),
        enabled = onGrant != null,
        onClick = { onGrant?.invoke() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                if (hint != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        hint,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

/**
 * 空狀態：對非技術使用者，空白畫面是最大門檻——與其給一張白紙，不如給幾個一鍵可用的
 * 起點。以範本選擇格作為主要起點（取代單一「建立第一個例行程序」按鈕），末格為「空白」。
 */
@Composable
private fun EmptyState(
    onPick: (RoutineTemplate) -> Unit,
    onBlank: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            "從一個範本開始",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "點一下就建好，之後再改。挑一個最接近你需求的，或選「空白」自己從頭組。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        TemplateGrid(onPick = onPick, onBlank = onBlank)
        Spacer(Modifier.height(24.dp))
    }
}

/** 清單頂部搜尋列：依名稱與觸發／動作摘要即時過濾，非空時右側顯示清除鈕 */
@Composable
private fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        placeholder = { Text("搜尋例行程序") },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.Clear, contentDescription = "清除搜尋")
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(14.dp)
    )
}

/** 搜尋無結果：與範本空狀態區分，只給一句提示 */
@Composable
private fun NoSearchResults(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "找不到符合的例行程序",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "換個關鍵字，或清除搜尋看全部。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/** 首頁的兩種呈現方式：清單（一列一張、精簡）／格狀（2 欄方塊、概覽） */
enum class HomeViewMode { LIST, GRID }

private const val UI_PREFS = "routina_ui"
private const val KEY_VIEW_MODE = "home_view_mode"

/** 讀取上次選的呈現方式；沒存過或值異常時預設「清單」 */
private fun loadHomeViewMode(context: Context): HomeViewMode = runCatching {
    context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
        .getString(KEY_VIEW_MODE, null)
        ?.let { HomeViewMode.valueOf(it) } ?: HomeViewMode.LIST
}.getOrDefault(HomeViewMode.LIST)

private fun saveHomeViewMode(context: Context, mode: HomeViewMode) {
    runCatching {
        context.getSharedPreferences(UI_PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_VIEW_MODE, mode.name).apply()
    }
}

/**
 * 清單模式：一列一張精簡卡（名稱 + 狀態晶片 + 執行／開關，**不顯示觸發／動作積木**），
 * 未搜尋時可長按整卡拖曳排序（沿用 v0.17.0，放開才落地持久化）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineListView(
    items: List<Routine>,
    logs: List<RunLog>,
    sunLocation: AlarmScheduler.SunLocation?,
    permSnapshot: PermSnapshot,
    reorderEnabled: Boolean,
    onReorderCommit: (Int, Int) -> Unit,
    onEdit: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onRunNow: (Routine) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    // 本地順序鏡像：拖曳期間即時重排讓動畫流暢，放開才落地
    var ordered by remember { mutableStateOf(items) }
    LaunchedEffect(items) { ordered = items }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        ordered = ordered.toMutableList().apply { add(to.index, removeAt(from.index)) }
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier,
        contentPadding = PaddingValues(12.dp, 6.dp, 12.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(ordered, key = { it.id }) { routine ->
            ReorderableItem(reorderableState, key = routine.id) { isDragging ->
                RoutineListRow(
                    routine = routine,
                    lastLog = logs.firstOrNull { it.routineId == routine.id },
                    sunLocation = sunLocation,
                    permIssues = missingPermissions(routine, permSnapshot),
                    isDragging = isDragging,
                    // 長按整卡拿起排序；tap＝進入編輯，兩手勢不衝突
                    modifier = Modifier.longPressDraggableHandle(
                        enabled = reorderEnabled,
                        onDragStarted = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDragStopped = {
                            val fromIdx = items.indexOfFirst { it.id == routine.id }
                            val toIdx = ordered.indexOfFirst { it.id == routine.id }
                            if (fromIdx >= 0 && toIdx >= 0 && fromIdx != toIdx) {
                                onReorderCommit(fromIdx, toIdx)
                            }
                        }
                    ),
                    onClick = { onEdit(routine.id) },
                    onToggle = { onToggle(routine.id, it) },
                    onRunNow = { onRunNow(routine) }
                )
            }
        }
    }
}

/** 清單模式的一列：家族色塊 + 名稱 + 狀態晶片 + 執行／開關（手動只有執行） */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineListRow(
    routine: Routine,
    lastLog: RunLog?,
    sunLocation: AlarmScheduler.SunLocation?,
    permIssues: List<PermIssue>,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRunNow: () -> Unit
) {
    val accent = triggerColor(routine.trigger)
    val elevation by animateDpAsState(if (isDragging) 8.dp else 2.dp, label = "listRowElevation")

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = elevation)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // 觸發家族色塊：清單模式也保有顏色分隔
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accent.copy(alpha = if (routine.enabled) 1f else 0.4f))
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    routine.name.ifBlank { "(未命名)" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (routine.enabled) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                RoutineStatusChips(
                    routine = routine,
                    lastLog = lastLog,
                    sunLocation = sunLocation,
                    permIssues = permIssues,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .alpha(if (routine.enabled) 1f else 0.5f)
                )
            }
            if (routine.trigger is Trigger.Manual) {
                FilledTonalButton(onClick = onRunNow) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("執行")
                }
            } else {
                IconButton(onClick = onRunNow) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "立即執行", tint = accent)
                }
                Switch(checked = routine.enabled, onCheckedChange = onToggle)
            }
        }
    }
}

/** 格狀模式：2 欄方塊卡概覽（色塊 + 名稱 + 一行狀態 + 執行／開關），為概覽故不排序 */
@Composable
private fun RoutineGridView(
    items: List<Routine>,
    sunLocation: AlarmScheduler.SunLocation?,
    onEdit: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onRunNow: (Routine) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(12.dp, 6.dp, 12.dp, 96.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        gridItems(items, key = { it.id }) { routine ->
            RoutineGridCell(
                routine = routine,
                sunLocation = sunLocation,
                onClick = { onEdit(routine.id) },
                onToggle = { onToggle(routine.id, it) },
                onRunNow = { onRunNow(routine) }
            )
        }
    }
}

/**
 * 格狀模式的一格方塊卡:白底方塊,右上執行／開關,中間用「無文字的彩色線條」呈現流程結構
 * （第一條＝觸發家族色,其後每個動作一條、縮排區分;一眼看出這程序有哪些彩色步驟,不塞看不清的字），
 * 底部名稱 + 一行狀態。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutineGridCell(
    routine: Routine,
    sunLocation: AlarmScheduler.SunLocation?,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onRunNow: () -> Unit
) {
    val accent = triggerColor(routine.trigger)
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (routine.trigger is Trigger.Manual) {
                    IconButton(onClick = onRunNow, modifier = Modifier.size(30.dp)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "執行", tint = accent)
                    }
                } else {
                    Switch(checked = routine.enabled, onCheckedChange = onToggle)
                }
            }
            Spacer(Modifier.weight(1f))
            RoutineBars(
                routine = routine,
                modifier = Modifier.alpha(if (routine.enabled) 1f else 0.4f)
            )
            Spacer(Modifier.weight(1f))
            Text(
                routine.name.ifBlank { "(未命名)" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (routine.enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Text(
                gridStatusLine(routine, sunLocation),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 無文字的彩色線條:第一條是觸發家族色,其後每個動作各一條（縮排）家族色,
 * 只表達「流程有哪些彩色步驟」的結構,不放會看不清的文字。
 */
@Composable
private fun RoutineBars(routine: Routine, modifier: Modifier = Modifier, maxActions: Int = 5) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        ColorBar(triggerColor(routine.trigger), indent = 0.dp)
        routine.actions.take(maxActions).forEach { action ->
            ColorBar(actionColor(action), indent = 14.dp)
        }
    }
}

/** 單一彩色線條:圓角色塊,依 [indent] 左縮排（觸發不縮、動作縮排以示層級） */
@Composable
private fun ColorBar(color: Color, indent: Dp) {
    Box(modifier = Modifier.fillMaxWidth().padding(start = indent)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(11.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
    }
}

/** 格狀卡的一行狀態摘要：手動／已停用／定時給下次時刻，其餘給觸發摘要 */
private fun gridStatusLine(routine: Routine, sunLocation: AlarmScheduler.SunLocation?): String = when {
    routine.trigger is Trigger.Manual -> "手動執行"
    !routine.enabled -> "已停用"
    routine.trigger is Trigger.Time ->
        nextRunSummary(routine, sunLocation) ?: triggerSummary(routine.trigger)

    else -> triggerSummary(routine.trigger)
}
