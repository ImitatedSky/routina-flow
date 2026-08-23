# Change: add-shortcuts-parity-wave2

## Why

1. **使用者回饋**：13 觸發 × 14 動作後顏色太亂——每個類型一個獨立顏色已超出可辨識範圍。
   改為「功能分組色系」：同組同色相、組內只差色階（例：通知與 App 全藍、聲音全紅），
   文字說明維持主要辨識（延續先前「不做拼圖造型」的回饋方向）。
2. **iOS 捷徑對等第二波**（Wave 1 遺留清單）：NFC 標籤觸發、通知觸發、App 開啟/關閉觸發——
   三者都是 Tasker/MacroDroid 級的招牌自動化能力。

## What Changes

### 色彩體系重整（全部積木與調色盤）
- 動作：通知與 App＝**藍家族**、聲音＝**紅家族**、裝置＝**橘家族**、流程＝**灰家族**
- 觸發：時間＝靛、電源＝綠、連線＝青、位置＝紫、系統/應用＝藍灰，各家族內以相近色階區分
- 積木文字顏色沿用亮度自動判定（淺色塊深字）

### 新增 3 種觸發
- **NFC 標籤**：在 App 內「登錄」實體 NFC 標籤（讀取 UID），掃描該標籤時觸發；
  需要 NFC 硬體，無硬體裝置標示不可用
- **通知觸發**：任一指定 App（或全部 App）發出通知時觸發，可加關鍵字過濾（比對標題與內容）；
  需要「通知存取權」（側載會被受限制的設定鎖住——沿用既有解鎖引導文案）
- **App 開啟/關閉觸發**：指定 App 進入前景或離開前景時觸發；
  需要「使用情況存取權」；僅螢幕亮著且有此類啟用中 routine 時輪詢（3 秒間隔），控制耗電

## Non-goals

- 無障礙服務方案（App 觸發用使用情況存取，不用無障礙）
- NFC 標籤寫入（本次僅讀 UID 比對；寫入 NDEF 留待需要時再議）
- 通知觸發的進階條件（正則、按鈕動作、回覆）——僅 App 來源 + 關鍵字包含

## Impact

- 受影響 specs：routine-triggers（+3 requirements）
- 受影響程式碼：theme 色表（重整）、model（+3 觸發型別）、engine（NotificationListenerService、
  UsageStats 輪詢入 MonitorService、NFC dispatch activity）、ui（登錄標籤流程、App 選擇器沿用、
  權限引導卡 ×2）、Manifest
- 無新增第三方依賴；versionName 0.4.0（versionCode 4）
