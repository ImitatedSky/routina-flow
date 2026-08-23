# Tasks: add-shortcuts-parity-wave2

## 1. 色彩體系重整

- [x] 1.1 theme 色表改為功能分組色系（依 design.md 表；以調色盤實際分組對齊，組內相近色階）
- [x] 1.2 確認深淺字自動判定在新色表下正確（淺色塊深字）；深淺色模式檢查

## 2. 資料模型與宣告

- [x] 2.1 Trigger 新增 NfcTag(uid, label)、NotificationPosted(packageName, appName, keyword)、AppState(packageName, appName, onOpen)；@SerialName 新增、舊 JSON 相容
- [x] 2.2 Manifest：NFC 權限 + uses-feature(required=false)、NfcDispatchActivity（TECH/NDEF intent-filter + tech-list metadata、透明主題）、RoutinaNotificationListener（BIND_NOTIFICATION_LISTENER_SERVICE）、PACKAGE_USAGE_STATS（tools:ignore）

## 3. 引擎

- [x] 3.1 NfcDispatchActivity：讀 UID → 比對啟用中 NfcTag routine → ExecutionService → finish；未登錄標籤 Toast
- [x] 3.2 RoutinaNotificationListener：來源/關鍵字比對、排除自家與 ongoing/group summary、5 秒去重、命中走 ExecutionService
- [x] 3.3 MonitorService：UsageStats 輪詢 coroutine（3 秒、僅螢幕亮＋有啟用 AppState routine）、RESUMED/PAUSED 判定與去重；SCREEN_ON/OFF 控制啟停；服務啟動條件涵蓋 AppState
- [x] 3.4 TriggerSource 補 NFC/NOTIFICATION/APP 來源標記（RunLog 顯示）

## 4. UI

- [x] 4.1 調色盤：NFC 入連線組、通知觸發與 App 開啟入系統與應用組
- [x] 4.2 參數編輯器：NFC 掃描對話框（enableReaderMode + label 欄 + 無 NFC 停用態）、通知觸發（App 選擇器含「任一」+ 關鍵字欄）、App 觸發（App 選擇器 + 開啟/關閉）
- [x] 4.3 權限引導：通知存取引導卡（含受限制的設定文案）、使用情況存取引導卡；卡片警示（未授權時）
- [x] 4.4 versionName 0.4.0 / versionCode 4

## 5. 驗證與提交

- [x] 5.1 assembleDebug + assembleRelease 綠燈；mapping 檢查新 sealed 子類/serializer/listener/activity 保留
- [x] 5.2 release APK 回報大小（≤ 8MB）— 1.68 MB
- [x] 5.3 README 更新（新觸發、權限表、對照矩陣勾選 Wave 2 項目）
- [x] 5.4 分階段 commit（色彩重整 / model+manifest / 引擎 / UI，英文、無 co-author）
