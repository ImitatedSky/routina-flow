package com.routina.app.ui

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.routina.app.model.GlobalVar

/**
 * 全域變數管理畫面：列出目前所有跨程序、可持久化的全域變數，可新增／編輯值／刪除。
 *
 * 全域變數也會被「設定全域變數」動作自動寫入；這裡是給使用者檢視與手動調整的地方
 * （例如重設一個卡住的值）。任何程序都能以 `{{全域:名稱}}` 引用。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalVarsScreen(
    viewModel: RoutineViewModel,
    onBack: () -> Unit
) {
    val globals by viewModel.globals.collectAsState()

    // 非 null＝正在新增/編輯的對話框內容；deleteTarget 非 null＝正在確認刪除
    var dialog by remember { mutableStateOf<GlobalDialogState?>(null) }
    var deleteTarget by remember { mutableStateOf<GlobalVar?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("全域變數", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { dialog = GlobalDialogState("", "", isNew = true) }) {
                Icon(Icons.Filled.Add, contentDescription = "新增全域變數")
            }
        }
    ) { padding ->
        if (globals.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "還沒有全域變數。",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "全域變數是跨程序、會保存下來的值。用「設定全域變數」動作寫入，" +
                        "或按右下角 ＋ 手動新增；之後任何程序都能以 {{全域:名稱}} 引用。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(globals, key = { it.name }) { g ->
                    GlobalVarCard(
                        item = g,
                        onEdit = { dialog = GlobalDialogState(g.name, g.value, isNew = false) },
                        onDelete = { deleteTarget = g }
                    )
                }
            }
        }
    }

    dialog?.let { state ->
        GlobalEditDialog(
            state = state,
            existingNames = globals.map { it.name },
            onConfirm = { name, value ->
                viewModel.setGlobal(name, value)
                dialog = null
            },
            onDismiss = { dialog = null }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("刪除全域變數") },
            text = { Text("確定要刪除「${target.name}」嗎？引用它的程序之後會讀到空字串。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteGlobal(target.name)
                    deleteTarget = null
                }) { Text("刪除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun GlobalVarCard(
    item: GlobalVar,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    item.value.ifBlank { "（空字串）" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "更新於 ${DateUtils.getRelativeTimeSpanString(item.updatedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "編輯")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "刪除")
            }
        }
    }
}

/** 新增／編輯對話框的內容；[isNew] 為 true 時名稱可編輯，否則名稱是既有鍵、只能改值 */
private data class GlobalDialogState(val name: String, val value: String, val isNew: Boolean)

@Composable
private fun GlobalEditDialog(
    state: GlobalDialogState,
    existingNames: List<String>,
    onConfirm: (name: String, value: String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(state.name) }
    var value by remember { mutableStateOf(state.value) }

    val trimmedName = name.trim()
    // 新增時名稱不可空、不可與現有重名；編輯既有變數時名稱固定，一律有效
    val nameValid = if (state.isNew) {
        trimmedName.isNotEmpty() && existingNames.none { it == trimmedName }
    } else {
        true
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (state.isNew) "新增全域變數" else "編輯全域變數") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名稱") },
                    placeholder = { Text("例如：今日步數") },
                    singleLine = true,
                    enabled = state.isNew,
                    isError = state.isNew && trimmedName.isNotEmpty() && !nameValid,
                    modifier = Modifier.fillMaxWidth()
                )
                if (state.isNew && trimmedName.isNotEmpty() && !nameValid) {
                    Text(
                        "已經有同名的全域變數了。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text("值") },
                    minLines = 1,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "以 {{全域:${trimmedName.ifBlank { "名稱" }}}} 在任何程序引用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmedName, value) },
                enabled = nameValid
            ) { Text("儲存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
