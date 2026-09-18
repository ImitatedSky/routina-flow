package com.routina.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.routina.app.data.AppSettings
import com.routina.app.data.RoutineBackup
import com.routina.app.data.RoutineBackupIo
import com.routina.app.data.RoutineRepository
import com.routina.app.data.ThemeMode
import com.routina.app.engine.NfcDispatch
import kotlinx.coroutines.launch

/**
 * 設定畫面：使用者「調一次就不用再管」的東西都收在這裡。
 *
 * 只放真的會被調整的偏好；程式自己記的帳（小工具綁哪支程序、離開時要還原的快照）
 * 不出現在這裡——那些不是使用者該手動改的東西。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: RoutineViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val routines by viewModel.routines.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val notify: (String) -> Unit = { message ->
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    // 匯出／匯入：檔案位置一律交給系統檔案選擇器（SAF），App 不碰共用儲存空間、不需要儲存權限
    var pendingImport by remember { mutableStateOf<RoutineBackup?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        RoutineBackupIo.write(context, uri, routines).fold(
            onSuccess = { notify("已匯出 $it 支例行程序") },
            onFailure = { notify(it.message ?: "匯出失敗") }
        )
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        RoutineBackupIo.read(context, uri).fold(
            onSuccess = { pendingImport = it },
            onFailure = { notify(it.message ?: "匯入失敗") }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("設定") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsSection("外觀") {
                SettingRow(
                    title = "深色模式",
                    description = "「跟隨系統」會照系統的深色設定切換。"
                ) {
                    val modes = listOf(
                        ThemeMode.SYSTEM to "跟隨系統",
                        ThemeMode.LIGHT to "淺色",
                        ThemeMode.DARK to "深色"
                    )
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        modes.forEachIndexed { index, (mode, label) ->
                            SegmentedButton(
                                selected = AppSettings.themeMode == mode,
                                onClick = { AppSettings.setThemeMode(context, mode) },
                                shape = SegmentedButtonDefaults.itemShape(index, modes.size)
                            ) {
                                Text(label, maxLines = 1)
                            }
                        }
                    }
                }
            }

            SettingsSection("執行") {
                SwitchRow(
                    title = "執行後顯示提示",
                    description = "從桌面小工具、桌面捷徑或快速設定磚執行時，跳出一則「執行：程序名稱」。" +
                        "關掉之後執行照舊，只是不再跳提示；找不到程序等錯誤仍然會提示。",
                    checked = AppSettings.runToast,
                    onCheckedChange = { AppSettings.setRunToast(context, it) }
                )
            }

            SettingsSection("NFC") {
                SwitchRow(
                    title = "回應 NFC 標籤",
                    description = "關掉之後，碰到標籤 Routina 不會被叫起來、NFC 觸發的程序也不會執行，" +
                        "也不再出現在「用哪個 App 開啟」的選擇器裡。" +
                        "系統的 NFC 開關不動，其他 App 照用；標籤庫的掃描與寫入標籤也照舊" +
                        "（那是畫面在前景時自己讀的）。",
                    checked = NfcDispatch.enabled,
                    onCheckedChange = { NfcDispatch.setEnabled(context, it) }
                )
            }

            SettingsSection("執行紀錄") {
                SettingRow(
                    title = "保留筆數",
                    description = "超過的舊紀錄會被丟掉。調小會立刻生效。"
                ) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        AppSettings.LOG_LIMIT_CHOICES.forEachIndexed { index, limit ->
                            SegmentedButton(
                                selected = AppSettings.logLimit == limit,
                                onClick = {
                                    AppSettings.setLogLimit(context, limit)
                                    RoutineRepository.get(context).trimLogs()
                                },
                                shape = SegmentedButtonDefaults.itemShape(
                                    index,
                                    AppSettings.LOG_LIMIT_CHOICES.size
                                )
                            ) {
                                Text("$limit", maxLines = 1)
                            }
                        }
                    }
                }
            }

            SettingsSection("備份") {
                ActionRow(
                    icon = Icons.Filled.FileUpload,
                    title = "匯出備份",
                    description = if (routines.isEmpty()) {
                        "目前沒有例行程序可以匯出。"
                    } else {
                        "把 ${routines.size} 支例行程序寫成一個 JSON 檔，位置由你在系統檔案選擇器裡決定。"
                    },
                    enabled = routines.isNotEmpty(),
                    onClick = { exportLauncher.launch(RoutineBackupIo.suggestedFileName()) }
                )
                RowDivider()
                ActionRow(
                    icon = Icons.Filled.FileDownload,
                    title = "匯入備份",
                    description = "可以挑要加回來哪幾支。匯入只會新增，不會覆蓋或刪掉現有的程序。",
                    // 備份檔的 MIME 由各家檔案 App 自行決定（json/octet-stream/text 都有），
                    // 收窄反而會讓使用者在選擇器裡看不到自己的檔案，因此不過濾
                    onClick = { importLauncher.launch(arrayOf("*/*")) }
                )
            }

            SettingsSection("說明") {
                InfoRow(
                    title = "那則關不掉的通知是什麼？",
                    body = "有些觸發（電量、Wi-Fi、App 使用情況等）必須有一個前景服務在跑，" +
                        "Android 規定前景服務一定要顯示通知。這則通知是最低重要度，不會響也不會跳出來。" +
                        "沒有用到這類觸發時就不會出現。"
                )
                RowDivider()
                InfoRow(
                    title = "NFC 需要常駐開啟掃描嗎？",
                    body = "不需要，也做不到。Routina 的 NFC 觸發是靠系統的標籤派送：" +
                        "手機碰到已登錄的標籤時，系統直接叫起對應的程序，" +
                        "Routina 平常不必在背景跑任何東西。" +
                        "（App 自己主動讀取標籤的「讀取模式」必須有畫面在前景，那才需要常駐。）" +
                        "不想讓 Routina 回應標籤時，用上面的「回應 NFC 標籤」關掉就好。"
                )
                RowDivider()
                InfoRow(
                    title = "版本",
                    body = appVersion(context)
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    pendingImport?.let { backup ->
        ImportPickerDialog(
            backup = backup,
            onDismiss = { pendingImport = null },
            onConfirm = { picked ->
                pendingImport = null
                applyImport(context, picked)
                notify("已匯入 ${picked.size} 支例行程序")
            }
        )
    }
}

/** 一個分組：小標題 + 一張卡片，卡片裡放這組的項目 */
@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp)
        )
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 4.dp)) { content() }
        }
    }
}

/** 標題 + 說明 + 下方一排選項（分段按鈕）；選項橫排太擠，所以放在說明底下 */
@Composable
private fun SettingRow(
    title: String,
    description: String,
    control: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RowText(title, description)
        control()
    }
}

/** 標題 + 說明 + 右側開關 */
@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        RowText(title, description, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** 點下去會做一件事的項目（匯出／匯入）；停用時整列變淡且不可點 */
@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    val tint = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        RowText(
            title = title,
            description = description,
            modifier = Modifier.weight(1f),
            titleColor = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

/** 純說明：不能點、只是把「為什麼會這樣」寫清楚，省得使用者以為是壞掉 */
@Composable
private fun InfoRow(title: String, body: String) {
    RowText(
        title = title,
        description = body,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

/** 標題 + 說明兩行；自己包成一個 Column，外層的間距才不會把這兩行也撐開 */
@Composable
private fun RowText(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Column(modifier = modifier) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = titleColor
        )
        Spacer(Modifier.height(2.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    )
}

/** 側載安裝看不到商店版本號，設定裡給一個，回報問題時才講得清楚是哪一版 */
private fun appVersion(context: android.content.Context): String = runCatching {
    val info = context.packageManager.getPackageInfo(context.packageName, 0)
    "Routina ${info.versionName}"
}.getOrDefault("Routina")
