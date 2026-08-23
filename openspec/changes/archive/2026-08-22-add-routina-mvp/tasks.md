# Tasks: add-routina-mvp

## 1. 專案骨架

- [x] 1.1 建立 Gradle 專案結構（settings.gradle.kts、build.gradle.kts、gradle wrapper、gradle.properties、.gitignore）
- [x] 1.2 app module：AGP 8.7、Kotlin 2.0、Compose BOM、kotlinx.serialization、compileSdk 35 / minSdk 26 / targetSdk 35
- [x] 1.3 AndroidManifest：權限（POST_NOTIFICATIONS、SCHEDULE_EXACT_ALARM、RECEIVE_BOOT_COMPLETED、FOREGROUND_SERVICE、FOREGROUND_SERVICE_SPECIAL_USE）、Activity、Service、Receivers 宣告
- [x] 1.4 release build：minifyEnabled + shrinkResources + debug 簽章（可直接側載）

## 2. 資料層

- [x] 2.1 Models.kt：Routine、Trigger（sealed：Time/PowerConnected/PowerDisconnected/BatteryBelow）、Action（sealed：Notify/OpenApp/OpenUrl/MediaVolume/RingerMode）、RunLog，全部 @Serializable
- [x] 2.2 RoutineRepository：JSON 讀寫（routines.json / logs.json）、StateFlow、CRUD、損毀容錯、紀錄上限 50 筆

## 3. 執行引擎

- [x] 3.1 RoutineExecutor：依序執行動作、單動作失敗不中斷、寫入 RunLog
- [x] 3.2 AlarmScheduler：計算下一次符合星期的觸發時間、canScheduleExactAlarms 檢查與降級、排程/取消
- [x] 3.3 AlarmReceiver：執行定時程序並鏈式排下一次
- [x] 3.4 MonitorService：前景服務（specialUse）、動態註冊充電/電量 receiver、門檻穿越去重、無監測需求時自動停止
- [x] 3.5 BootReceiver：開機恢復排程與監測服務
- [x] 3.6 啟用/停用/刪除時同步更新排程與監測服務

## 4. UI（Compose M3）

- [x] 4.1 主題與色彩系統：觸發/動作類型專屬色（依 design.md 色表）、深淺色模式
- [x] 4.2 HomeScreen：卡片清單（色條 + 觸發摘要 + 動作彩色 chip + 開關 + 立即執行）、空狀態、FAB、通知權限請求
- [x] 4.3 EditScreen：名稱、觸發類型選擇（彩色卡片）、觸發參數（時間選擇器/星期複選/電量滑桿）、動作清單（新增 bottom sheet、參數編輯、移除）、儲存/刪除
- [x] 4.4 LogScreen：執行紀錄列表（成敗顏色區隔）
- [x] 4.5 MainActivity + Navigation 串接三畫面

## 5. 建置驗證與發佈

- [x] 5.1 gradlew assembleDebug 成功
- [x] 5.2 gradlew assembleRelease 成功，APK ≤ 5MB
- [x] 5.3 GitHub Actions workflow（build-apk.yml）
- [x] 5.4 README.md（功能說明、APK 下載/安裝方式、權限說明）

## 6. Code review 修復

### 高嚴重度

- [x] H1 背景啟動 Activity 被系統靜默丟棄：宣告 SYSTEM_ALERT_WINDOW，執行前判斷可否直接啟動
  （手動執行 / Activity context / 已授權 overlay），否則改發可點擊開啟的高優先度通知
  並另發引導授權通知；HomeScreen 加對應引導卡
- [x] H2 MonitorService.startForegroundSafely 改回傳 Boolean，失敗時 stopSelf 並跳過註冊 receiver
- [x] H3 AlarmReceiver 先 runCatching 鏈式排程再執行動作，避免 execute 例外讓 routine 永久停擺

### 中嚴重度

- [x] M1 HomeScreen ON_RESUME 偵測精確鬧鐘權限由無到有時呼叫 syncAll，讓降級排程升級回精確
- [x] M2 保留 USE_EXACT_ALARM；spec 註明「未授權精確鬧鐘」僅適用 Android 12/12L 手動撤銷授權
- [x] M3 doNotify 先檢查 areNotificationsEnabled 否則記為失敗；通知統一改用 NotificationManagerCompat；
  HomeScreen 加「通知權限未授權」引導卡
- [x] M4 Repository 落地競態：persist 在 Mutex 鎖內才讀取狀態並編碼（含 blocking 路徑）
- [x] M5 EditScreen 草稿與對話框旗標改 rememberSaveable（Routine / Action 以 Json Saver 保存）
- [x] M6 星期 7 顆 FilterChip 改用 FlowRow，窄螢幕自動換行
- [x] M7 電量門檻改為含門檻（percent <= threshold，回升至門檻以上重置），spec 與 UI 文案同步
- [x] M8 MonitorService 兩處執行明確傳 persistBlocking = false
- [x] M9 BootReceiver 加收 TIMEZONE_CHANGED 與 TIME_SET，同樣重新排程

### 低嚴重度

- [x] L1 setWindow 改以 triggerAt 為中心（±10 分鐘）
- [x] L2 AlarmReceiver 執行前重新驗證今天是否符合 daysOfWeek，不符合仍排下一次
- [x] L3 doOpenUrl 以 Regex 判斷是否已帶 scheme
- [x] L4 通知 ID 由 routine.id + 動作序號推導，避免互相覆蓋並避開固定 ID
- [x] L5 只有切換靜音/震動才檢查勿擾模式存取權，正常模式直接嘗試並以 try/catch 兜底
- [x] L6 時間選擇 Dialog 內容可捲動，橫向時改用 TimePickerLayoutType.Horizontal
- [x] L7 EditScreen 捲動 Column 加 imePadding
- [x] L8 通知權限先檢查 checkSelfPermission，未授權才請求
- [x] L9 handleBatteryChanged 對停用中的 routine 不設 fired 旗標
- [x] L10 runNow 回傳 RunLog，Snackbar 依失敗數顯示結果
