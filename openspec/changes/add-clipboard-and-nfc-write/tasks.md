# Tasks: add-clipboard-and-nfc-write

## 1. 剪貼簿動作

- [x] 1.1 Action.Clipboard(text)（@SerialName 新增、舊 JSON 相容）；RoutineExecutor 實作（try/catch、背景註記）
- [x] 1.2 UI：調色盤「流程」組加積木（灰家族 #757575）、多行文字參數編輯器、積木參數欄顯示文字截斷

## 2. NFC 寫入

- [x] 2.1 NfcTagReader 擴充寫入模式：Ndef/NdefFormatable 流程、isWritable/maxSize 檢查、IOException 重試提示、寫入同步登錄 UID
- [x] 2.2 Manifest：NfcDispatchActivity 加 NDEF_DISCOVERED + scheme routina/host tag filter
- [x] 2.3 NfcDispatchActivity：URI 有 uid 用 URI，否則讀 tag.id（同一比對路徑）
- [x] 2.4 UI：NFC 編輯器加「寫入標籤（選用）」段落與寫入對話框（狀態/結果回饋）

## 3. 驗證與提交

- [x] 3.1 assembleDebug + assembleRelease 綠燈；mapping 檢查 Clipboard serializer 保留；versionName 0.5.0 / versionCode 5
- [x] 3.2 release APK 回報大小（1.68 MB）；README 更新
- [x] 3.3 兩個 commit（剪貼簿 / NFC 寫入，英文、無 co-author）
