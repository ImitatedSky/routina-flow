# Design: add-clipboard-and-nfc-write

## 剪貼簿動作

```kotlin
@SerialName("clipboard") data class Clipboard(val text: String = "") : Action()
```

- `ClipboardManager.setPrimaryClip(ClipData.newPlainText("Routina", text))`，try/catch
- **Android 10+ 背景限制的誠實處理**：系統對「非前景 App」的剪貼簿寫入在部分版本/廠牌會
  靜默忽略且無從偵測。策略：
  - 手動執行（App 前景）：一律成功
  - 背景觸發（ExecutionService）：照常呼叫並記成功，但 ActionResult 描述加註
    「背景寫入在部分裝置可能被系統忽略」；API 33+ 系統會顯示「已複製」浮層可自行確認
- 色彩：流程家族（灰）第二階 `#757575`；調色盤入「流程」組
- 參數編輯器：多行文字欄

## NFC 標籤寫入

### 寫入流程（NFC 觸發參數編輯器新增「寫入標籤」鈕）

1. 開啟寫入對話框 → `enableReaderMode`（沿用 NfcTagReader，本次不加 SKIP_NDEF_CHECK——寫入需要 NDEF）
2. 偵測到標籤：
   - 取 UID → 先完成/更新 UID 登錄（與既有掃描登錄相同）
   - `Ndef.get(tag)` 非空 → `connect()` → 檢查 `isWritable`（否→「標籤唯讀」）、
     `maxSize`（不足→「容量不足」）→ `writeNdefMessage(NdefMessage(NdefRecord.createUri("routina://tag/{uidHex}")))`
   - `Ndef.get(tag)` 為空 → `NdefFormatable.get(tag)` 非空 → `format(message)`；
     兩者皆空 →「此標籤不支援寫入」
3. 結果回饋：成功「已寫入，掃描此標籤將直接開啟 Routina」／失敗顯示具體原因；
   IOException（寫到一半移開）→「請保持標籤貼緊再試一次」

### 直接分派

- Manifest 的 `NfcDispatchActivity` 新增 intent-filter：
  `ACTION_NDEF_DISCOVERED` + `<data android:scheme="routina" android:host="tag"/>`
- 掃描已寫入標籤 → 系統以 URI 精準比對 → **僅 Routina 符合 → 不出現選擇器**
- `NfcDispatchActivity` 解析順序：intent data URI `routina://tag/{uid}` 有值就取其 uid，
  否則照舊讀 `tag.id`——兩路殊途同歸走同一個 UID 比對
- 既有 TECH_DISCOVERED / 通用 NDEF filter 保留（未寫入的標籤仍可用，行為不變）

## UI

- NFC 參數編輯器改為兩段：上段既有「掃描登錄」，下段「寫入標籤（選用）」說明 + 按鈕：
  「寫入後掃描會直接開啟 Routina，不再跳出 App 選擇視窗」
- 寫入對話框沿用掃描對話框樣式（等待動畫文字「將標籤貼近手機背面…」→ 結果狀態）

## 版本

versionName 0.5.0 / versionCode 5；無新依賴
