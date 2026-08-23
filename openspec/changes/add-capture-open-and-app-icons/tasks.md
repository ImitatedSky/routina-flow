# Tasks: add-capture-open-and-app-icons

## 1. 擷取結果通知

- [x] 1.1 TakePhoto/BurstPhoto/RecordAudio 加 notify:Boolean=true（@SerialName 欄位新增、舊 JSON 相容）
- [x] 1.2 CameraCapture/AudioRecorder 存檔函式回傳 顯示位置字串 + content URI（API 29+ MediaStore URI；API ≤28 FileProvider URI）
- [x] 1.3 Manifest：FileProvider provider（authority ${applicationId}.fileprovider）+ res/xml/file_paths.xml（Pictures/Music 外部路徑）
- [x] 1.4 擷取成功且 notify 時發可點擊通知（ACTION_VIEW + content URI + FLAG_GRANT_READ_URI_PERMISSION + FLAG_IMMUTABLE + autoCancel）；新「擷取結果」channel；未授權通知則略過；通知 ID 不互相覆蓋
- [x] 1.5 編輯器：三個擷取動作加「完成後發出通知」開關

## 2. App 圖示

- [x] 2.1 App 選擇器每列顯示 loadIcon（Drawable→ImageBitmap），IO 載入 + package→ImageBitmap 快取，不卡捲動；「任一 App」列處理
- [x] 2.2 ParamField 加 optional leadingIcon 參數；開啟 App／通知觸發／App 觸發積木參數欄顯示所選 App 小圖示；載不到只顯示名稱
- [x] 2.3 三處 App 選擇器共用同一元件獲得圖示

## 3. 驗證與提交

- [x] 3.1 assembleDebug + assembleRelease 綠燈；mapping 檢查（欄位變更不影響 serializer 保留）；versionName 0.7.0 / versionCode 7
- [x] 3.2 release APK 回報大小；README 更新
- [x] 3.3 兩個 commit（擷取通知+FileProvider / App 圖示，英文、無 co-author）
