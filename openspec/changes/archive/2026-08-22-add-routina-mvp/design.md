# Design: add-routina-mvp

## 技術選型

| 面向 | 選擇 | 理由 |
|------|------|------|
| 語言 | Kotlin 2.0.21 | Android 官方語言 |
| UI | Jetpack Compose + Material 3 | 宣告式 UI，彩色主題容易做；R8 後體積可接受 |
| 儲存 | kotlinx.serialization JSON（filesDir） | 資料量小（幾十筆 routine），不需要 Room/SQLite |
| 定時觸發 | AlarmManager | 系統級、可靠；WorkManager 週期任務不精確 |
| 充電/電量觸發 | 前景服務 + 動態 BroadcastReceiver | Android 8+ 禁止 manifest 註冊 POWER_CONNECTED 等隱式廣播 |
| DI / 架構框架 | 無（手寫單例 Repository + ViewModel） | 最小輕量原則，避免 Hilt 等額外依賴 |
| 後端 | 無 | 純本地 App，離線可用 |

## 模組結構（單一 app module）

```
app/src/main/java/com/routina/app/
├── MainActivity.kt              # 單 Activity + Compose Navigation
├── RoutinaApp.kt                # Application：建立 NotificationChannel
├── model/Models.kt              # Routine / Trigger / Action / RunLog（@Serializable sealed class）
├── data/RoutineRepository.kt    # JSON 檔案讀寫 + StateFlow，單例
├── engine/
│   ├── RoutineExecutor.kt       # 依序執行 actions
│   ├── AlarmScheduler.kt        # 排程 / 取消 AlarmManager
│   ├── AlarmReceiver.kt         # 定時觸發入口
│   ├── BootReceiver.kt          # BOOT_COMPLETED 恢復排程
│   └── MonitorService.kt        # 前景服務：充電 / 電量動態 receiver
└── ui/
    ├── theme/                   # 色彩系統（觸發/動作類型專屬色）
    ├── HomeScreen.kt            # 清單 + 開關 + 手動執行 + FAB
    ├── EditScreen.kt            # 編輯 routine（觸發選擇、動作清單）
    └── LogScreen.kt             # 執行紀錄
```

## 關鍵決策

### 1. 資料模型：sealed class + kotlinx.serialization
`Trigger` 與 `Action` 都是 `@Serializable` sealed class，JSON 多型序列化（`type` 欄位判別）。
新增觸發/動作類型時只需加子類 + UI 分支，不動儲存層。

### 2. 充電 / 電量觸發：前景監測服務
- 只有在「存在已啟用且觸發類型為充電/電量的 routine」時才啟動 `MonitorService`；
  全部停用時自動停止服務 → 平時零常駐、零耗電。
- Android 14+ 前景服務類型使用 `specialUse`（自動化 App 的標準做法）。
- 電量低於門檻：以 `ACTION_BATTERY_CHANGED` 監聽，**向下穿越門檻時觸發一次**，
  回升到門檻以上後重置（避免重複轟炸）。

### 3. 定時觸發：AlarmManager 精確鬧鐘
- Android 12+ 需要使用者授權「鬧鐘與提醒」；App 檢查 `canScheduleExactAlarms()`，
  未授權時引導到系統設定頁，並以 `setWindow`（±10 分鐘）降級運作。
- 觸發後立即排下一次符合星期幾條件的鬧鐘（single-shot chain，比 setRepeating 可靠）。

### 4. 響鈴模式動作的權限
切到靜音/震動需要「勿擾模式存取權」（`ACCESS_NOTIFICATION_POLICY`）。
執行時若無權限 → 發通知引導使用者到設定頁授權，動作記為失敗但不中斷後續動作。

### 5. 簽章與發佈
Release build 以 debug keystore 簽章（個人側載用途，零密鑰管理負擔），
`minifyEnabled + shrinkResources` 縮小體積。GitHub Actions push 時自動建置並上傳 APK artifact。
之後若要正式發佈再換正式 keystore（只改 signingConfig 一處）。

## 色彩系統（UI 顏色區隔）

| 類型 | 顏色 |
|------|------|
| 觸發：定時 | 藍 `#1E88E5` |
| 觸發：開始充電 | 橘 `#FB8C00` |
| 觸發：停止充電 | 深橘紅 `#E53935` |
| 觸發：電量低於 | 綠 `#43A047` |
| 動作：通知 | 紫 `#8E24AA` |
| 動作：開啟 App | 青 `#00897B` |
| 動作：開啟網址 | 靛 `#3949AB` |
| 動作：媒體音量 | 粉 `#D81B60` |
| 動作：響鈴模式 | 棕 `#6D4C41` |

清單卡片左側色條 = 觸發類型色；動作以彩色 chip 呈現，一眼可辨。
