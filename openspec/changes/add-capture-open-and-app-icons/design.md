# Design: add-capture-open-and-app-icons

## 1. 擷取結果通知（可點擊開啟）

### 資料模型（向後相容，加欄位）

```kotlin
// 三個擷取動作各加一個布林欄位（有預設值，舊 JSON 相容）
data class TakePhoto(val lensBack: Boolean = true, val notify: Boolean = true) : Action()
data class BurstPhoto(..., val notify: Boolean = true) : Action()
data class RecordAudio(val seconds: Int = 5, val notify: Boolean = true) : Action()
```

### content URI（點擊開啟的關鍵）

- **API 29+**：`MediaStore` insert 回傳的 `content://media/...` URI 直接可用於 `ACTION_VIEW`
- **API ≤28**：檔案在公開目錄，用 **FileProvider** 產生 `content://{app}.fileprovider/...` URI
- CameraCapture / AudioRecorder 的存檔函式回傳**顯示位置字串 + content URI**（供通知使用）

### 通知

- 完成且 `notify == true` 時，發一則通知：
  - 相片：標題「已拍照」/「已連拍 N 張」，內文檔名
  - 音檔：標題「已錄音 N 秒」，內文檔名
  - `contentIntent` = `PendingIntent.getActivity(ACTION_VIEW, uri, type=image/*|audio/*)` +
    `FLAG_GRANT_READ_URI_PERMISSION` + `FLAG_IMMUTABLE`；`setAutoCancel(true)`
  - 連拍用最後一張的 URI；相片 type `image/*`、音檔 `audio/*`
  - 新通知 channel「擷取結果」（或沿用既有 CHANNEL_LAUNCH 的可點擊模式——擇一，
    偏好新 channel 讓使用者可單獨控制）
  - 未授權通知（Android 13+）：靜默略過通知，但擷取動作本身仍記成功（檔案已存）
- 通知 ID 以時間或流水號避免互相覆蓋（連拍/多次擷取各一則）

### FileProvider

- Manifest `<provider>` `androidx.core.content.FileProvider`，authority `${applicationId}.fileprovider`，
  `file_paths.xml` 涵蓋外部公開目錄（Pictures、Music）
- 僅 API ≤28 路徑會用到；API 29+ 用 MediaStore URI

## 2. App 選擇器與積木圖示

- **圖示載入**：既有 App 選擇器已用 `queryIntentActivities(MAIN/LAUNCHER)` 取清單；
  每列加 `resolveInfo.loadIcon(pm)`（Drawable → `toBitmap().asImageBitmap()`），
  於 `LazyColumn` 列前顯示 24–32dp 圖示；圖示載入在 IO dispatcher + 簡單快取（package→ImageBitmap），
  避免捲動卡頓
- **積木參數欄圖示**：「開啟 App」動作的參數欄在 App 名稱前顯示 16–20dp 小圖示
  （`ParamField` 支援可選前置圖示 slot）；載不到圖示時只顯示名稱（fallback）
- **共用**：通知觸發、App 開啟/關閉觸發的 App 選擇器共用同一元件，一併獲得圖示
- 圖示快取生命週期隨畫面；不長駐、不寫檔

## UI 細節

- App 選擇器列：`Row { Image(icon, 32dp) + Spacer + Text(appName) }`，含「任一 App」列（給通知觸發，
  用泛用圖示或無圖示）
- ParamField 新增 optional `leadingIcon: ImageBitmap?` 參數，不影響既有呼叫

## 版本

versionName 0.7.0 / versionCode 7；無新依賴
