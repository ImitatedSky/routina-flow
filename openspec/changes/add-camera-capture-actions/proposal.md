# Change: add-camera-capture-actions

## Why

使用者要求更多可用動作，點名相機拍照、連拍。盤點 Android 上「自動化可實作」的擷取／媒體動作後，
本波新增 5 種，其中相機/麥克風類受 Android 前景限制影響，採既有的誠實降級策略。

## 關鍵系統限制（決定設計）

Android 9（API 28）起禁止**背景**存取相機與麥克風；只有前景服務掛上對應
foregroundServiceType 才能存取。Android 14（API 34）又限制「從背景啟動 camera/microphone
類前景服務」。因此：
- **手動執行**（App 在前景，while-in-use）：拍照/錄音一律可用
- **背景觸發**（ExecutionService 由鬧鐘/事件啟動）：盡力嘗試；若被系統擋下，
  動作記為失敗並在執行紀錄註明原因（沿用剪貼簿/背景 Activity 的誠實策略）

## What Changes

新增「媒體與擷取」動作組：

- **拍照**：用選定鏡頭（後/前）拍一張，存入相簿（相片/Routina）
- **連拍**：連續拍 N 張（2–10）、間隔可調（200–2000ms）
- **錄音**：錄製 N 秒（1–60）音訊存檔
- **播放音效**：播放系統音效（通知音/鬧鐘聲/鈴聲）——歸「聲音」紅家族
- **設定鬧鐘**：以系統時鐘 App 設定一個鬧鐘（時:分 + 標籤）——歸「通知與 App」藍家族

- 新增依賴 CameraX（無預覽 ImageCapture 擷取，比 Camera2 大幅降低複雜度）
- versionName 0.6.0 / versionCode 6；APK 目標維持 ≤ 8MB

## Non-goals

- 錄影（比錄音重、FGS 時間更長，暫緩）
- 螢幕截圖（需 MediaProjection 每次彈出使用者同意框，不適合自動化）
- 傳簡訊／撥號（敏感權限，另議）
- 剪貼簿讀取（Android 10+ 禁止背景讀取）

## Impact

- 受影響 specs：routine-actions（+5 動作）
- 受影響程式碼：build.gradle.kts（+CameraX）、model（+5 Action）、engine（RoutineExecutor 拍照/
  連拍/錄音/播放音效/鬧鐘；ExecutionService 掛 camera/microphone FGS 類型）、ui（參數編輯器、
  調色盤新組、權限引導 CAMERA/RECORD_AUDIO）、Manifest（權限 + FGS 類型）
