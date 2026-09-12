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
import com.routina.app.engine.RoutineManager
import com.routina.app.model.Routine
import java.util.UUID

/**
 * 把選到的程序加進現有清單。
 *
 * 一律配新 id、不比對也不覆蓋既有程序——匯入只會增加東西，永遠不會弄丟現有的流程。
 * 走 [RoutineManager.save] 而不是直接寫檔，鬧鐘／地理圍欄／監測服務才會一併重新註冊。
 */
fun applyImport(context: Context, routines: List<Routine>) {
    for (routine in routines) {
        RoutineManager.save(
            context,
            routine.copy(
                id = UUID.randomUUID().toString(),
                createdAt = System.currentTimeMillis()
            )
        )
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
    onConfirm: (List<Routine>) -> Unit
) {
    // 用索引記選取狀態：備份檔裡的 id 可能與現有程序重複，索引才是這份清單裡唯一可靠的鍵
    var selected by remember(backup) {
        mutableStateOf(backup.routines.indices.toSet())
    }

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
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected.isNotEmpty(),
                onClick = { onConfirm(selected.sorted().map { backup.routines[it] }) }
            ) {
                Text("匯入 ${selected.size} 支")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
