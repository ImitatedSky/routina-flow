# Change: add-shortcuts-parity-wave1

## Why

使用者要求：盤點 iOS 捷徑（Shortcuts）的自動化功能，把 Android 上可行的照抄進 Routina。
已依 Apple 官方文件完成功能盤點與可行性分析（見下方對照矩陣），本 change 實作第一波。

## iOS 捷徑功能對照矩陣

| iOS 捷徑功能 | Routina 狀態 |
|---|---|
| 觸發：特定時間 | ✅ 已有（定時） |
| 觸發：日出/日落 | 🔨 本次（擴充定時觸發，本地天文計算） |
| 觸發：充電開始/結束 | ✅ 已有 |
| 觸發：電量低於 | ✅ 已有 |
| 觸發：電量高於/等於 | 🔨 本次（高於門檻） |
| 觸發：到達/離開地點 | ✅ 已有（區域觸發） |
| 觸發：連接藍牙裝置 | 🔨 本次（可指定裝置，連接/斷開） |
| 觸發：連上 Wi-Fi | 🔨 本次（可指定 SSID，連線/斷線） |
| 觸發：飛航模式開/關 | 🔨 本次 |
| 觸發：勿擾（≈專注模式）開/關 | 🔨 本次 |
| 觸發：省電（≈低耗電模式）開/關 | 🔨 本次 |
| 觸發：NFC 標籤 | ⏭ Wave 2（需標籤登錄 UI） |
| 觸發：App 開啟/關閉 | ⏭ Wave 2（需使用情況存取權，耗電需評估） |
| 觸發：收到訊息/Email | ⏭ Wave 2（需通知存取權——順帶可做「任意 App 通知觸發」） |
| 觸發：鬧鐘停止、就寢/起床、CarPlay、交易 | ❌ Android 無對應公開 API |
| 動作：顯示通知 / 開啟 App / 開啟網址 | ✅ 已有 |
| 動作：音量 | ✅ 已有 → 🔨 擴充串流（媒體/鈴聲/鬧鐘/通知） |
| 動作：手電筒開/關 | 🔨 本次 |
| 動作：朗讀文字（Speak Text） | 🔨 本次（TTS） |
| 動作：震動 | 🔨 本次 |
| 動作：勿擾模式開/關 | 🔨 本次 |
| 動作：螢幕亮度 | 🔨 本次（WRITE_SETTINGS 引導） |
| 動作：取得 URL 內容（webhook） | 🔨 本次（HTTP GET/POST） |
| 動作：播放控制（播放/暫停/上下首） | 🔨 本次（媒體按鍵事件） |
| 動作：等待 N 秒 | 🔨 本次（1–30 秒） |
| 動作：Wi-Fi / 行動數據硬體開關 | ❌ Android 10+ 系統禁止第三方切換 |
| 動作：剪貼簿 | ⏭ Wave 2（背景寫入限制需驗證） |
| 動作：截圖、傳簡訊/撥號 | ⏭ Wave 2 / 需另議權限 |
| 巢狀流程（if / repeat / 變數） | ❌ Non-goal（Tasker 級，違反輕量定位） |

## What Changes

- **新增 8 種觸發情境**：藍牙裝置連接、藍牙裝置斷開、Wi-Fi 連線（可指定 SSID）、Wi-Fi 斷線、電量高於門檻、飛航模式切換、勿擾模式切換、省電模式切換；定時觸發擴充日出/日落模式
- **新增 8 種動作**：手電筒、朗讀文字、震動、勿擾模式、螢幕亮度、HTTP 請求、播放控制、等待；音量動作擴充串流選擇
- **執行架構升級**：新增短生命週期 ExecutionService——由觸發啟動的執行改在前景服務中進行，
  解除 BroadcastReceiver 10 秒限制（等待/TTS/HTTP 需要），執行完自動停止
- 積木 UI / 調色盤同步納入全部新類型（沿用圓角色塊 + 文字說明風格）
- versionName 0.3.0

## Non-goals

- 巢狀條件、迴圈、變數（維持「觸發 → 依序動作」的極簡模型）
- NFC、通知觸發、App 開啟觸發、剪貼簿（Wave 2）
- 任何需要 root / Shizuku / 無障礙服務的功能

## Impact

- 受影響 specs：routine-triggers（+4 requirements、定時 MODIFIED）、routine-actions（+7、音量 MODIFIED）、routine-engine（+1 ExecutionService）
- 受影響程式碼：model、engine（MonitorService 擴充、新 ExecutionService、靜態藍牙 receiver）、ui（積木、調色盤、參數編輯器）、Manifest
- 無新增第三方依賴（HTTP 用 HttpURLConnection、TTS/手電筒/震動用系統 API）
