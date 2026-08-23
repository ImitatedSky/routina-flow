# Change: add-clipboard-and-nfc-write

## Why

Wave 2 遺留的兩項使用者指定功能：
1. **剪貼簿動作**——iOS 捷徑的「拷貝至剪貼板」對等
2. **NDEF 寫入標籤**——Wave 2 的 NFC 觸發靠 TECH_DISCOVERED 分派，裝置上若有其他 NFC App
   會跳出選擇器；把專屬 URI 寫入標籤後，掃描即**直接**喚起 Routina，體驗與 iOS 捷徑的
   NFC 自動化一致

## What Changes

- **新動作「複製到剪貼簿」**：把設定的文字寫入系統剪貼簿
  - Android 10+ 對背景剪貼簿有系統限制（部分廠牌/版本會靜默忽略背景寫入）：
    手動執行（App 在前景）一律可用；背景觸發時盡力而為並誠實記錄
- **NFC 標籤寫入**：NFC 觸發參數編輯器新增「寫入標籤」——把 `routina://tag/{uid}` 的
  NDEF URI 寫入標籤（未格式化標籤自動格式化），並同步完成 UID 登錄
  - Manifest 新增 `routina` scheme 的 NDEF_DISCOVERED intent-filter → 掃描已寫入的標籤
    **不出現選擇器**，直接由 Routina 處理
  - 寫入失敗情境（唯讀標籤、容量不足、非 NDEF 且不可格式化）給明確錯誤訊息
- versionName 0.5.0 / versionCode 5

## Non-goals

- 讀取剪貼簿（觸發或變數）——Android 10+ 禁止背景讀取，做了也只是假功能
- 寫入其他 NDEF 內容（網址、文字、Wi-Fi 設定等）——Routina 不是通用 NFC 工具
- 標籤密碼保護 / 鎖定寫入

## Impact

- 受影響 specs：routine-actions（+1 剪貼簿）、routine-triggers（NFC 觸發 MODIFIED，加寫入情境）
- 受影響程式碼：model（+Action.Clipboard）、engine（RoutineExecutor +剪貼簿；NfcTagReader +寫入；
  NfcDispatchActivity +URI 解析）、ui（NFC 編輯器加寫入鈕與結果回饋、調色盤）、Manifest（+NDEF filter）
- 無新增依賴
