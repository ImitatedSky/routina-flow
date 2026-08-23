# Design: add-safer-delete-and-status

## 安全刪除

- EditScreen 頂部列（TopAppBar）：`actions` 由「垃圾桶 IconButton」改為「⋯ IconButton + DropdownMenu」
- DropdownMenu 項目：
  - 「刪除例行程序」`MenuItem`，文字與 leading icon 用 `error` 色 → 觸發既有刪除確認 AlertDialog
  - （選）「重新命名」聚焦名稱欄——若成本低可加；否則本次僅刪除
- 新建流程（routineId == null）不顯示 ⋯ 選單（無可刪對象），與現行一致
- 返回鍵維持左上；刪除離開返回鍵的相鄰區，杜絕誤觸

## 卡片狀態晶片

### 下次執行時間（唯讀計算，不改排程）

新增純函式 `nextRunSummary(routine): String?`：
- `Trigger.Time`：用既有 `AlarmScheduler` 的下一次觸發計算（含 mode 固定/日出日落 + offset + 星期），
  格式化為「今天 HH:mm」「明天 HH:mm」「週三 HH:mm」
- 事件型觸發（PowerConnected/Disconnected、BatteryBelow/Above、Location*、Bt*、Wifi*、
  AirplaneMode、Dnd、PowerSave、NfcTag、NotificationPosted、AppState）：不預測時間，
  回觸發摘要字串（沿用既有 `triggerSummary`），晶片以中性樣式呈現「進入區域時」等
- 停用中：不顯示下次執行（或顯示「已停用」淡樣式）

### 上次結果

- `RoutineRepository` 已存 RunLog（最近 50 筆）；取該 routine 最近一筆：
  - 全部動作成功 → 「✓ 上次成功」good 色
  - 有失敗 → 「✕ 上次部分失敗」warn/crit 色
  - 無紀錄 → 不顯示
- 相對時間：本地格式化「剛剛／N 分鐘前／N 小時前／N 天前／MM/dd」，純函式、不需依賴

### 需授權晶片

彙整函式 `missingPermissions(context, routine): List<PermIssue>`，依 routine 的觸發/動作檢查
既有各權限狀態，回傳未授與者：
- 背景定位（Location* 觸發）、通知存取（NotificationPosted）、使用情況（AppState）、
  精確鬧鐘（Time，API 31–32 未授時）、勿擾存取（Dnd 動作/響鈴）、相機（TakePhoto/BurstPhoto）、
  麥克風（RecordAudio）、通知（POST_NOTIFICATIONS 且有通知動作/擷取通知）、
  系統設定寫入（Brightness）、上層顯示（背景開 App/網址）
- 每項含 label + 導向 Intent（沿用 HomeScreen 既有 startFirstAvailable 引導）
- 卡片最多顯示 1–2 個最關鍵的權限晶片，避免爆版；點擊即引導
- 這部分與既有「首頁頂部引導卡」互補：頂部卡是全域彙總，卡片晶片是「這個 routine 缺什麼」

### 版面

```
┌ 卡片 ────────────────────────┐
│ 早晨                          │
│ ◷ 明天 08:00  ✓ 2 小時前  [開關] │ ← 狀態晶片列
│ [ 觸發積木 ]                   │
│ [ 動作積木 ×N / 更多 ]         │
│ ▶ 立即執行                     │
└──────────────────────────────┘
```
- 晶片列用 FlowRow（窄螢幕換行）；開關維持在列尾或原位（擇一，維持既有可用性）
- 停用中 routine：整體 40% alpha 維持；狀態晶片一併淡化

## 版本

versionName 0.9.0 / versionCode 9；無新依賴
