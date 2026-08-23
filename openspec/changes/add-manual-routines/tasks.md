# Tasks: add-manual-routines

## 1. 模型與引擎

- [x] 1.1 Trigger.Manual（@SerialName("manual") data object）；isConfigured→true；triggerSummary→「手動執行」
- [x] 1.2 新建空白 routine 預設觸發改為 Manual（範本各自觸發不變）
- [x] 1.3 確認 Manual 不進任何自動路徑：AlarmScheduler（不排程、nextTriggerTime null）、GeofenceManager、MonitorService.needsMonitor/hasMonitoredRoutines、Bt/Notification/AppUsage/Nfc、RoutineManager save/setEnabled/delete 對 Manual 皆 no-op

## 2. UI

- [x] 2.1 觸發調色盤最前加「手動」分組（「手動執行」中性色，說明只用 ▶ 執行）
- [x] 2.2 帽子積木 Manual：顯示「手動執行 · 點 ▶ 執行」中性色 #4E5560、無參數欄；點本體開觸發調色盤可切換
- [x] 2.3 HomeScreen 卡片：Manual 隱藏啟用開關、▶ 執行為主操作、狀態晶片顯示中性「手動」不顯示下次執行；非 Manual 維持原樣
- [x] 2.4 nextRunSummary(Manual)=null

## 3. 驗證與提交

- [x] 3.1 assembleDebug + assembleRelease 綠燈；mapping 檢查 Trigger$Manual/serializer 保留；release APK 回報大小；versionName 0.10.0 / versionCode 10
- [x] 3.2 README 更新（手動捷徑 vs 自動化說明）
- [x] 3.3 分階段 commit（model+engine / UI，英文、無 co-author）
