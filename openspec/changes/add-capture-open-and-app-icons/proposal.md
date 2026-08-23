# Change: add-capture-open-and-app-icons

## Why

使用者實測拍照可用後的兩個回饋：
1. 動作要更「有用」——拍照後希望**跳出通知顯示照片位置，點通知能直接開啟那張照片**
   （錄音同理，點了開啟音檔）
2. 「開啟 App」動作的 App 清單**前面放 App 圖示（logo）更好找**

## What Changes

- **擷取結果通知（可點擊開啟）**：拍照／連拍／錄音完成後，發出一則通知
  （「已拍照」「已連拍 N 張」「已錄音 N 秒」+ 檔名/位置），**點通知以系統檢視器開啟**
  該相片／音檔。每個擷取動作可開關此通知（預設開）
  - 需要可跨 App 讀取的 content URI：API 29+ 用 MediaStore 回傳的 URI；API ≤28 用 FileProvider
- **App 選擇器圖示**：「開啟 App」動作的選擇清單每列前面顯示 App 圖示，選定後
  **積木參數欄也顯示該 App 的小圖示**，更好辨識；通知觸發／App 觸發共用的 App 選擇器一併套用
- versionName 0.7.0 / versionCode 7

## Non-goals

- 通知內嵌相片縮圖大圖（BigPictureStyle）——先做點擊開啟，縮圖留待需要時再加
- 自訂通知動作按鈕（分享／刪除）
- 其他動作的通知化（維持只有擷取類發結果通知）

## Impact

- 受影響 specs：routine-actions（擷取結果通知、App 選擇器圖示）
- 受影響程式碼：model（3 擷取動作加 notify 欄位）、engine（擷取後發可點擊通知、
  FileProvider for API ≤28）、ui（App 選擇器與積木顯示圖示，含圖示載入/快取）、
  Manifest（FileProvider provider + file_paths）
- 無新增第三方依賴（FileProvider 在 androidx.core、圖示用 PackageManager + drawable toBitmap）
