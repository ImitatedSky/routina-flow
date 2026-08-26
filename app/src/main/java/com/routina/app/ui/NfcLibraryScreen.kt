package com.routina.app.ui

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.routina.app.engine.NfcTagReader
import com.routina.app.model.NfcRecord

/**
 * NFC 標籤庫：掃描一張標籤把 UID 與 NDEF 內容存起來，之後可以把內容複製到別張標籤、
 * 設成觸發，或重新命名 / 刪除。
 *
 * 複製只能複製 NDEF 資料內容，複製不了 UID 或加密卡片——這是預期的。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcLibraryScreen(
    viewModel: RoutineViewModel,
    onUseAsTrigger: (uid: String, name: String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val records by viewModel.nfcRecords.collectAsState()
    // NFC 硬體的有無不會在執行期間改變，查一次就好；開關狀態則要在 ON_RESUME 重查
    val nfcAvailable = remember { viewModel.isNfcAvailable() }
    var nfcEnabled by remember { mutableStateOf(viewModel.isNfcEnabled()) }

    var showScanAdd by remember { mutableStateOf(false) }
    var writeTarget by remember { mutableStateOf<NfcRecord?>(null) }
    var renameTarget by remember { mutableStateOf<NfcRecord?>(null) }
    var deleteTarget by remember { mutableStateOf<NfcRecord?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                nfcEnabled = viewModel.isNfcEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NFC 標籤庫") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            // 沒有 NFC 硬體時不提供掃描
            if (nfcAvailable) {
                FloatingActionButton(onClick = { showScanAdd = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "掃描新增")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (!nfcAvailable) {
                PermissionWarningCard(
                    title = "這台裝置沒有 NFC",
                    message = "沒有 NFC 硬體，無法掃描或寫入標籤。"
                )
            } else if (!nfcEnabled) {
                PermissionWarningCard(
                    title = "NFC 已關閉",
                    message = "NFC 關著時掃描不到標籤。點此前往 NFC 設定開啟。"
                ) {
                    context.openNfcSettings()
                }
            }

            if (records.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "還沒有記錄的標籤。點右下＋掃描一張 NFC 標籤存起來，" +
                            "之後可以複製到別張，或設成觸發。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(records, key = { it.id }) { record ->
                        NfcRecordCard(
                            record = record,
                            canWrite = nfcAvailable && record.ndefBase64.isNotBlank(),
                            onWrite = { writeTarget = record },
                            onUseAsTrigger = { onUseAsTrigger(record.uid, record.name) },
                            onRename = { renameTarget = record },
                            onDelete = { deleteTarget = record }
                        )
                    }
                }
            }
        }
    }

    if (showScanAdd) {
        NfcScanAddDialog(
            onSave = { record ->
                viewModel.saveNfcRecord(record)
                showScanAdd = false
            },
            onDismiss = { showScanAdd = false }
        )
    }

    writeTarget?.let { record ->
        NfcWriteRecordDialog(
            record = record,
            onDismiss = { writeTarget = null }
        )
    }

    renameTarget?.let { record ->
        NfcRenameDialog(
            record = record,
            onConfirm = {
                viewModel.saveNfcRecord(record.copy(name = it))
                renameTarget = null
            },
            onDismiss = { renameTarget = null }
        )
    }

    deleteTarget?.let { record ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("刪除標籤記錄") },
            text = { Text("確定要刪除「${record.name.ifBlank { "(未命名)" }}」嗎？此操作無法復原。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteNfcRecord(record.id)
                    deleteTarget = null
                }) {
                    Text("刪除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

/** 標籤庫的一張卡：名稱 + UID（等寬）+ 內容摘要，右上「⋯」溢位選單放各項操作 */
@Composable
private fun NfcRecordCard(
    record: NfcRecord,
    canWrite: Boolean,
    onWrite: () -> Unit,
    onUseAsTrigger: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    record.name.ifBlank { "(未命名)" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (record.uid.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        record.uid,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (record.summary.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        record.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "更多選項")
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("寫到新標籤") },
                        enabled = canWrite,
                        onClick = {
                            showMenu = false
                            onWrite()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("設為觸發") },
                        onClick = {
                            showMenu = false
                            onUseAsTrigger()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("重新命名") },
                        onClick = {
                            showMenu = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("刪除", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

/**
 * 掃描新增：對話框開著的期間啟用完整讀取模式，讀到標籤就顯示 UID 與內容摘要，
 * 讓使用者填名稱後儲存。reader mode 只在對話框存在時啟用（[NfcReaderModeEffect] 收尾即解除）。
 */
@Composable
private fun NfcScanAddDialog(
    onSave: (NfcRecord) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val available = remember(context) { NfcTagReader.isAvailable(context) }
    var enabled by remember { mutableStateOf(NfcTagReader.isEnabled(context)) }
    var result by remember { mutableStateOf<NfcTagReader.ReadResult?>(null) }
    var name by remember { mutableStateOf("") }

    // reader mode 註冊一次就好，回呼透過 rememberUpdatedState 看到最新的 setter
    val handleRead by rememberUpdatedState(
        newValue = { r: NfcTagReader.ReadResult -> result = r }
    )

    NfcReaderModeEffect(
        activity = activity,
        active = result == null,
        onNfcEnabledChange = { enabled = it }
    ) { NfcTagReader.enableReadFullMode(it) { r -> handleRead(r) } }

    val current = result
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("掃描新增") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                when {
                    !available || activity == null -> Text(
                        "這台裝置沒有 NFC 硬體，無法掃描標籤。",
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

                    current == null -> Text(
                        "把 NFC 標籤靠到手機背面（多數機型在鏡頭附近），讀到就會顯示內容。",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    else -> {
                        Text(
                            "UID：${current.uid ?: "（讀不到）"}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            if (current.ndefBase64.isBlank()) {
                                "這張標籤只有 UID、沒有可複製的 NDEF 內容。"
                            } else {
                                "內容：${current.summary}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名稱") },
                    placeholder = { Text("例如：家門口標籤") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    current?.let {
                        onSave(
                            NfcRecord(
                                name = name,
                                uid = it.uid.orEmpty(),
                                ndefBase64 = it.ndefBase64,
                                summary = it.summary
                            )
                        )
                    }
                },
                enabled = current != null
            ) {
                Text("儲存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 寫到新標籤：把這筆記錄的 NDEF 內容原樣寫進另一張可寫標籤（＝複製資料）。
 * 只複製 NDEF 內容，複製不了原標籤的 UID。內容為空時不提供寫入。
 */
@Composable
private fun NfcWriteRecordDialog(
    record: NfcRecord,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val hasContent = record.ndefBase64.isNotBlank()
    var enabled by remember { mutableStateOf(NfcTagReader.isEnabled(context)) }
    var outcome by remember { mutableStateOf<NfcTagReader.WriteOutcome?>(null) }

    val handleOutcome by rememberUpdatedState(
        newValue = { r: NfcTagReader.WriteOutcome -> outcome = r }
    )

    NfcReaderModeEffect(
        activity = activity,
        active = hasContent && outcome == null,
        onNfcEnabledChange = { enabled = it }
    ) { NfcTagReader.enableWriteNdefMode(it, record.ndefBase64) { r -> handleOutcome(r) } }

    val result = outcome
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("寫到新標籤") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                when {
                    !hasContent -> Text(
                        "這張記錄沒有可寫入的 NDEF 內容(只有 UID)。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )

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
                            "將空白標籤貼近手機背面…",
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
                        "已寫入，這張標籤現在帶有相同的 NDEF 內容",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    else -> Text(
                        result.error ?: "寫入失敗",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (hasContent) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "只會複製 NDEF 資料內容，複製不了原標籤的 UID 或加密卡片；" +
                            "未格式化的空白標籤會自動格式化。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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

/** 重新命名：改記錄的顯示名稱 */
@Composable
private fun NfcRenameDialog(
    record: NfcRecord,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(record.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重新命名") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("名稱") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) { Text("完成") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
