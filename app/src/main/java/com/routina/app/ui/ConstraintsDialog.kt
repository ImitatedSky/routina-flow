package com.routina.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.routina.app.model.Condition
import com.routina.app.model.Trigger

/**
 * 程序的「執行條件」：觸發發生時，這些條件全部成立才真的執行。
 *
 * 條件的編輯沿用「如果」積木的 [ConditionEditor]，兩處的操作方式一致，也不必維護兩套。
 * 可用的變數 token 不含「上一個結果」——條件在任何動作之前就判斷，那時還沒有結果。
 */
@Composable
fun ConstraintsDialog(
    trigger: Trigger,
    constraints: List<Condition>,
    globalNames: List<String>,
    onChange: (List<Condition>) -> Unit,
    onDismiss: () -> Unit
) {
    val tokenGroups = availableTokens(
        trigger = trigger,
        precedingActions = emptyList(),
        globalNames = globalNames,
        includeLastResult = false
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("執行條件") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    if (constraints.isEmpty()) {
                        "沒有條件＝觸發到就執行。加上條件後，只有全部成立時才會執行" +
                            "（例如：插耳機時播放音樂，但只在平日）。手動執行不受條件限制，隨時可以測試。"
                    } else {
                        "以下 ${constraints.size} 個條件全部成立時才會執行。" +
                            "手動執行不受條件限制，隨時可以測試。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Column(
                    modifier = Modifier.heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    constraints.forEachIndexed { index, condition ->
                        Spacer(Modifier.height(8.dp))
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        // 第二個之後標上「且」，讓「全部成立」這件事在畫面上看得出來
                                        if (index == 0) "條件 1" else "且　條件 ${index + 1}",
                                        style = MaterialTheme.typography.labelLarge,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(onClick = {
                                        onChange(constraints.filterIndexed { i, _ -> i != index })
                                    }) {
                                        Icon(Icons.Filled.Close, contentDescription = "移除這個條件")
                                    }
                                }
                                ConditionEditor(
                                    condition = condition,
                                    tokenGroups = tokenGroups,
                                    onChange = { updated ->
                                        onChange(
                                            constraints.mapIndexed { i, c ->
                                                if (i == index) updated else c
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { onChange(constraints + Condition()) }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("加一個條件")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}
