# routine-engine（執行引擎與紀錄）

## ADDED Requirements

### Requirement: 本地 JSON 持久化
系統 SHALL 將例行程序與執行紀錄以 JSON 格式存於 App 私有目錄（filesDir），不使用資料庫、不連網。資料損毀時以空清單啟動，不得崩潰。

#### Scenario: 檔案損毀
- **WHEN** routines.json 內容無法解析
- **THEN** App 以空清單正常啟動

### Requirement: 執行紀錄
系統 SHALL 記錄每次例行程序執行：時間、程序名稱、觸發來源（定時/充電/電量/手動）、每個動作的成敗；僅保留最近 50 筆。

#### Scenario: 檢視紀錄
- **WHEN** 使用者開啟執行紀錄畫面
- **THEN** 以新到舊列出紀錄，成功/失敗以顏色與圖示區隔

### Requirement: 輕量 APK 建置
系統 SHALL 以 R8 minify + resource shrinking 建置 release APK，體積目標 5MB 以下，minSdk 26（Android 8.0）以上可安裝。

#### Scenario: 建置 release APK
- **WHEN** 執行 gradlew assembleRelease
- **THEN** 產出可直接側載安裝的已簽章 APK

### Requirement: 持續整合建置
系統 SHALL 提供 GitHub Actions workflow：push 到主分支時自動建置並上傳 APK 為 artifact，作為 APK 下載來源。

#### Scenario: CI 建置
- **WHEN** 程式碼推送到主分支
- **THEN** CI 完成 assembleRelease 並上傳 APK artifact
