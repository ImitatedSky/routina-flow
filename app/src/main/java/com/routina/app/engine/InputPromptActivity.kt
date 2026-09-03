package com.routina.app.engine

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.routina.app.ui.theme.RoutinaTheme

/**
 * 互動動作（詢問輸入 / 選單選擇）用的對話框 Activity。
 *
 * 系統只讓 Activity 呈現 UI，所以「詢問輸入 / 選單選擇」動作在背景服務裡跑到時，
 * 執行器登記一筆請求（[InputBridge]）後啟動這個透明、對話框樣式的 Activity 當收件人。
 * 使用者確定 / 選擇 / 取消後，透過 [InputBridge.deliver] 以 requestId 把結果交回執行器，
 * 執行器隨即續跑。本 Activity 只認 requestId、完全不碰 repository / ViewModel，維持低耦合。
 *
 * 主題 `Theme.Routina.Dialog` 為透明無標題（見 themes.xml）；Compose 的 [AlertDialog]
 * 自帶遮罩，因此看起來就是浮在原畫面之上的對話框。configChanges 讓旋轉 / 開鍵盤不重建，
 * 避免重建時誤把請求當成取消。
 */
class InputPromptActivity : ComponentActivity() {

    /** 已交回結果，避免 onDestroy 再交一次（deliver 本身也是冪等） */
    private var delivered = false

    private var requestId = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestId = intent.getLongExtra(EXTRA_REQUEST_ID, -1L)
        val kind = intent.getStringExtra(EXTRA_KIND) ?: KIND_INPUT
        val prompt = intent.getStringExtra(EXTRA_PROMPT).orEmpty()
        val default = intent.getStringExtra(EXTRA_DEFAULT).orEmpty()
        val options = intent.getStringArrayListExtra(EXTRA_OPTIONS) ?: arrayListOf()

        // 沒有有效的 requestId（極少見，例如被系統以空 intent 重建）→ 沒有可回應的對象，直接結束
        if (requestId < 0L) {
            finish()
            return
        }

        setContent {
            RoutinaTheme {
                if (kind == KIND_MENU) {
                    MenuDialog(
                        prompt = prompt,
                        options = options,
                        onPick = { finishWith(it) },
                        onCancel = { finishWith(null) }
                    )
                } else {
                    InputDialog(
                        prompt = prompt,
                        default = default,
                        onConfirm = { finishWith(it) },
                        onCancel = { finishWith(null) }
                    )
                }
            }
        }
    }

    /** 交回結果並關閉對話框 */
    private fun finishWith(value: String?) {
        delivered = true
        InputBridge.deliver(requestId, value)
        finish()
    }

    override fun onDestroy() {
        // 使用者用系統手勢直接關掉（沒按確定/取消）也要放行執行器：以取消交回。
        // 只在真的要關閉時才交回，設定變更（configChanges 已擋下重建，這裡是保險）不算取消。
        if (!delivered && isFinishing && requestId >= 0L) {
            InputBridge.deliver(requestId, null)
        }
        super.onDestroy()
    }

    companion object {
        const val KIND_INPUT = "input"
        const val KIND_MENU = "menu"

        private const val EXTRA_REQUEST_ID = "request_id"
        private const val EXTRA_KIND = "kind"
        private const val EXTRA_PROMPT = "prompt"
        private const val EXTRA_DEFAULT = "default"
        private const val EXTRA_OPTIONS = "options"
        private const val EXTRA_VARIABLE_NAME = "variable_name"

        /**
         * 建立啟動這個對話框的 Intent。由執行器（或背景降級時的通知 PendingIntent）使用。
         * [variableName] 只作紀錄用途夾帶，結果的對應完全靠 [requestId]。
         */
        fun intent(
            context: Context,
            requestId: Long,
            kind: String,
            prompt: String,
            default: String,
            options: List<String>,
            variableName: String
        ): Intent = Intent(context, InputPromptActivity::class.java).apply {
            putExtra(EXTRA_REQUEST_ID, requestId)
            putExtra(EXTRA_KIND, kind)
            putExtra(EXTRA_PROMPT, prompt)
            putExtra(EXTRA_DEFAULT, default)
            putStringArrayListExtra(EXTRA_OPTIONS, ArrayList(options))
            putExtra(EXTRA_VARIABLE_NAME, variableName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}

/** 詢問輸入：提示 + 文字輸入框（預填預設值）+ 確定 / 取消 */
@Composable
private fun InputDialog(
    prompt: String,
    default: String,
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit
) {
    var field by remember {
        mutableStateOf(TextFieldValue(default, TextRange(default.length)))
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    AlertDialog(
        onDismissRequest = onCancel,
        title = if (prompt.isBlank()) null else { { Text(prompt) } },
        text = {
            OutlinedTextField(
                value = field,
                onValueChange = { field = it },
                singleLine = false,
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(field.text) }) { Text("確定") }
        },
        dismissButton = {
            TextButton(onClick = onCancel) { Text("取消") }
        }
    )
}

/** 選單選擇：提示 + 每個選項一顆按鈕 + 取消 */
@Composable
private fun MenuDialog(
    prompt: String,
    options: List<String>,
    onPick: (String) -> Unit,
    onCancel: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = if (prompt.isBlank()) null else { { Text(prompt) } },
        text = {
            // 選項可能很多，內容過高時可捲動
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    TextButton(
                        onClick = { onPick(option) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(option) }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onCancel) { Text("取消") }
        }
    )
}
