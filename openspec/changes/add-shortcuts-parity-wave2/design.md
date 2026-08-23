# Design: add-shortcuts-parity-wave2

## 1. 色彩體系重整（功能分組色系）

原則：**一組一色相，組內只差色階**（Material 色階 800→400 方向遞淺）；
文字為主要辨識，顏色輔助分組；`blockContentColor()` 亮度自動判定深/白字機制不變。
分組以**調色盤實際分組為準**（下表為建議值，實作時對齊現有分組標題，可微調色階讓相鄰積木可分辨）。

### 動作

| 組（色相） | 積木 → 色 |
|---|---|
| 通知與 App（藍） | 顯示通知 `#0D47A1`、開啟 App `#1565C0`、開啟網址 `#1976D2`、HTTP 請求 `#1E88E5` |
| 聲音（紅） | 音量 `#B71C1C`、響鈴模式 `#C62828`、朗讀文字 `#D32F2F`、播放控制 `#E53935` |
| 裝置（橘） | 手電筒 `#E65100`、震動 `#EF6C00`、螢幕亮度 `#F57C00`、勿擾模式 `#FB8C00`、藍牙 `#FFA000` |
| 流程（灰） | 等待 `#616161` |

### 觸發

| 組（色相） | 積木 → 色 |
|---|---|
| 時間（靛） | 定時 `#3949AB` |
| 電源（綠） | 開始充電 `#1B5E20`、停止充電 `#2E7D32`、電量低於 `#388E3C`、電量高於 `#43A047`、省電模式 `#66BB6A` |
| 連線（青） | Wi-Fi 連線 `#006064`、Wi-Fi 斷線 `#00838F`、藍牙連接 `#0097A7`、藍牙斷開 `#00ACC1`、飛航模式 `#26C6DA`、NFC 標籤 `#4DD0E1` |
| 位置（紫） | 進入區域 `#4527A0`、離開區域 `#5E35B1` |
| 系統與應用（藍灰） | 勿擾切換 `#37474F`、通知觸發 `#455A64`、App 開啟/關閉 `#546E7A` |

## 2. NFC 標籤觸發

- **登錄流程**：觸發參數編輯器開「掃描標籤」對話框 → `NfcAdapter.enableReaderMode`
  （FLAG_READER_NFC_A|B|F|V|NO_PLATFORM_SOUNDS）讀取 `tag.id` UID（hex）→ 存入
  `Trigger.NfcTag(uid, label)`，label 使用者自填（例「床頭標籤」）
- **背景觸發**：`NfcDispatchActivity`（透明主題、`ACTION_TECH_DISCOVERED` intent-filter +
  tech-list metadata、`ACTION_NDEF_DISCOVERED` 備援）→ 讀 UID → 比對所有啟用中 NfcTag routine
  → `ExecutionService.start()` → 立即 `finish()`（無 UI 閃爍）；無符合者顯示 Toast「未登錄的標籤」
- 裝置無 NFC（`NfcAdapter == null`）：調色盤該積木 40% alpha + 點擊提示（沿用無 GMS 模式）
- Manifest：`android.permission.NFC`、`uses-feature android.hardware.nfc required=false`

## 3. 通知觸發

- `RoutinaNotificationListener : NotificationListenerService`（manifest 宣告 +
  `BIND_NOTIFICATION_LISTENER_SERVICE` 權限 + intent-filter）
- `onNotificationPosted`：
  - 忽略自家 package（避免自觸發迴圈）、忽略 `FLAG_ONGOING_EVENT`／`FLAG_GROUP_SUMMARY`
  - 參數比對：`packageName`（空＝任一 App）+ `keyword`（空＝不過濾；否則 title/text 包含，不分大小寫）
  - 去重：同一 notification key 於 5 秒內不重複觸發
  - 命中 → `ExecutionService.start()`（listener 行程內不直接執行動作）
- **權限**：`NotificationManagerCompat.getEnabledListenerPackages` 檢查；引導卡導向
  `ACTION_NOTIFICATION_LISTENER_SETTINGS`；文案含「受限制的設定」解鎖步驟（沿用既有文案）
- 未授權時：含通知觸發的 routine 卡片警示 + 不觸發

## 4. App 開啟/關閉觸發

- `Trigger.AppState(packageName, appName, onOpen: Boolean)`
- `UsageStatsManager.queryEvents`（`ACTIVITY_RESUMED`/`ACTIVITY_PAUSED`，API 29+；
  舊版 `MOVE_TO_FOREGROUND`/`BACKGROUND`）由 MonitorService 內 coroutine 每 3 秒輪詢一次上一區間
- **耗電控制**：僅當（存在啟用中 AppState routine）且（螢幕亮著，SCREEN_ON/OFF receiver）才輪詢；
  兩者任一不成立即停輪詢。前景 App 判定去重：同 App 連續 RESUMED 只觸發一次，
  換到別的 App 再回來才會再觸發；「關閉」= 該 App PAUSED 且下一個 RESUMED 非同 App
- **權限**：`AppOpsManager.unsafeCheckOpNoThrow(OPSTR_GET_USAGE_STATS)` 檢查；引導卡導向
  `Settings.ACTION_USAGE_ACCESS_SETTINGS`（帶 package URI + fallback）；Manifest 宣告
  `PACKAGE_USAGE_STATS`（tools:ignore ProtectedPermissions）
- App 選擇器沿用既有「開啟 App」動作的 launcher App 清單元件

## 5. UI

- 觸發調色盤：NFC 入「連線」組、通知觸發與 App 開啟入「系統與應用」組（勿擾切換同組）
- 參數編輯器：NFC（掃描對話框 + label 欄）、通知（App 選擇器含「任一 App」+ 關鍵字欄）、
  App 開啟（App 選擇器 + 開啟/關閉 segmented）
- 引導卡 ×2（通知存取、使用情況存取），沿用 PermissionWarningCard + ON_RESUME 重查 +
  startFirstAvailable 模式

## 版本

versionName 0.4.0 / versionCode 4；無新依賴
