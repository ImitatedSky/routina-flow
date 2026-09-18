package com.routina.app.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.routina.app.data.RoutineBackup
import com.routina.app.data.AppSettings
import com.routina.app.data.RoutineRepository
import com.routina.app.engine.RoutineManager
import com.routina.app.model.Routine
import java.util.UUID

/**
 * 把選到的程序加進現有清單。
 *
 * 一律配新 id、不比對也不覆蓋既有程序——匯入只會增加東西，永遠不會弄丟現有的流程。
 * 走 [RoutineManager.save] 而不是直接寫檔，鬧鐘／地理圍欄／監測服務才會一併重新註冊。
 *
 * [extras] 非 null 時一併還原 NFC 標籤、全域變數與偏好（格式 2 起的備份才有）。
 */
fun applyImport(context: Context, routines: List<Routine>, extras: RoutineBackup? = null) {
    for (routine in routines) {
        RoutineManager.save(
            context,
            routine.copy(
                id = UUID.randomUUID().toString(),
                createdAt = System.currentTimeMillis()
            )
        )
    }

    val backup = extras ?: return
    val repository = RoutineRepository.get(context)

    // NFC 標籤認 uid 而不是 id：同一張實體標籤重複匯入不該變成兩筆。
    // 沒有 uid 的（理論上不會有）就當成新的加進去。
    backup.nfcTags.forEach { record ->
        val sameTag = record.uid.takeIf { it.isNotBlank() }?.let { uid ->
            repository.nfcTags.value.firstOrNull { it.uid == uid }
        }
        repository.upsertNfc(if (sameTag == null) record else record.copy(id = sameTag.id))
    }

    // 全域變數同名就以備份裡的值為準——還原的用意就是把當時的狀態拿回來
    if (backup.globals.isNotEmpty()) {
        repository.applyGlobals(
            backup.globals.associate { it.name to it.value },
            blocking = false
        )
    }

    backup.settings?.let { settings ->
        AppSettings.setThemeMode(context, settings.themeMode)
        AppSettings.setRunToast(context, settings.runToast)
        AppSettings.setLogLimit(context, settings.logLimit)
    }
}

/**
 * 匯入前先讓使用者看清楚檔案裡有什麼、挑要哪幾支（預設全選）。
 * 直接整包匯入容易在還原舊備份時混進一堆不要的東西，多這一步成本很低。
 */
@Composable
fun ImportPickerDialog(
    backup: RoutineBackup,
    onDismiss: () -> Unit,
    onConfirm: (List<Routine>, Boolean) -> Unit
) {
    // 用索引記選取狀態：備份檔裡的 id 可能與現有程序重複，索引才是這份清單裡唯一可靠的鍵
    var selected by remember(backup) {
        mutableStateOf(backup.routines.indices.toSet())
    }
    // 程序以外的東西整組還原或整組不還原。拆成三個勾選框只是把簡單的事變複雜，
    // 而且要救資料的人幾乎都是全部都要
    var restoreExtras by remember(backup) { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("要匯入哪些？") },
        text = {
            Column {
                Text(
                    "備份裡有 ${backup.routines.size} 支例行程序。" +
                        "匯入會新增到現有清單，不會覆蓋或刪除任何現有的程序。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(backup.routines.indices.toList()) { index ->
                        val routine = backup.routines[index]
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (index in selected) {
                                        selected - index
                                    } else {
                                        selected + index
                                    }
                                }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = index in selected,
                                onCheckedChange = {
                                    selected = if (it) selected + index else selected - index
                                }
                            )
                            Text(
                                text = routine.name.ifBlank { "未命名" },
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // 格式 1 的舊備份沒有這些，就不用讓使用者看到一個永遠沒東西的選項
                if (backup.hasExtras) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { restoreExtras = !restoreExtras }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = restoreExtras,
                            onCheckedChange = { restoreExtras = it }
                        )
                        Text(
                            text = "一併還原 ${extrasSummary(backup)}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected.isNotEmpty() || (backup.hasExtras && restoreExtras),
                onClick = {
                    onConfirm(
                        selected.sorted().map { backup.routines[it] },
                        backup.hasExtras && restoreExtras
                    )
                }
            ) {
                Text("匯入")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 「NFC 標籤 3、全域變數 2、偏好設定」——只列真的有東西的項目 */
private fun extrasSummary(backup: RoutineBackup): String = buildList {
    if (backup.nfcTags.isNotEmpty()) add("NFC 標籤 ${backup.nfcTags.size}")
    if (backup.globals.isNotEmpty()) add("全域變數 ${backup.globals.size}")
    if (backup.settings != null) add("偏好設定")
}.joinToString("、")
