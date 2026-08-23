# Change: add-routina-mvp

## Why

市面上的 Android 自動化工具（Tasker、MacroDroid）功能龐大但學習曲線陡、介面複雜。
本專案要做一個 Android 版的「Apple 捷徑 / Samsung Bixby 日常程式」：
**極簡、輕量、一眼看懂**的例行程序自動化 App，以 APK 直接下載安裝。

## What Changes

建立 Routina App 的 MVP（v0.1.0），端到端可運行：

- **例行程序（Routine）管理**：建立 / 編輯 / 刪除 / 啟用開關 / 手動執行
- **觸發條件（4 種）**：定時（每日 + 星期幾）、開始充電、停止充電、電量低於門檻
- **動作（5 種）**：顯示通知、開啟 App、開啟網址、設定媒體音量、切換響鈴模式
- **執行引擎**：AlarmManager 定時觸發、前景監測服務（充電/電量）、開機後自動恢復排程、執行紀錄（最近 50 筆）
- **UI**：Jetpack Compose Material 3，三個畫面（清單 / 編輯 / 紀錄），觸發類型與動作類型以顏色區隔
- **建置與發佈**：R8 縮小的 release APK、git 版控、GitHub Actions 自動建置 APK artifact

## Non-goals（本次不做）

- 雲端同步、帳號系統、後端伺服器
- Wi-Fi / 藍牙開關動作（Android 10+ 系統已禁止第三方 App 切換）
- 位置觸發（需要常駐定位權限，違反輕量原則，留待後續版本）
- 巢狀條件 / 變數 / 流程控制（Tasker 級功能）
- 上架 Google Play

## Impact

- 全新專案，無既有程式碼受影響
- 新增 capabilities：routine-management、routine-triggers、routine-actions、routine-engine
