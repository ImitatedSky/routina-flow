# Design: add-share-action

## 資料模型（向後相容，只新增）

```kotlin
@Serializable @SerialName("share") data class Share(val text: String = "") : Action()
```

## 執行（RoutineExecutor）

```kotlin
val send = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_TEXT, action.text)
}
val chooser = Intent.createChooser(send, "分享")
```

- **前景（source==MANUAL 或 context is Activity）**：`startActivity(chooser)`（chooser 需
  `FLAG_ACTIVITY_NEW_TASK` 視呼叫端而定）→ 系統分享選單出現，使用者選對象
- **背景觸發**：Android 10+ 禁止背景啟動 Activity → 重用既有 `launchOrNotify` 模式：
  發一則可點擊通知（CHANNEL_LAUNCH），contentIntent 掛 `getActivity(chooser)`（FLAG_IMMUTABLE），
  點通知才跳出分享選單；ActionResult 記成功並註明「已改以通知呈現，點擊分享」
- 文字為空：記失敗（無可分享內容），不崩潰
- 失敗（無可處理 App，理論上 chooser 一定有）：try/catch 記失敗，不中斷後續動作

## UI

- 調色盤「通知與 App」藍家族新增「分享」積木；色接續藍家族最淺階（如 `#64B5F6`，
  以既有對比機制自動決定深/白字）
- 參數編輯器：多行文字欄（提示「要分享的連結或文字」）
- 積木參數欄：顯示分享文字（截斷約 15 字）
- 文案繁中：積木標籤「分享」、參數「分享文字」

## 版本
versionName 0.11.0 / versionCode 11；無新依賴
