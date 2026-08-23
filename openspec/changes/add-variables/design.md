# Design: add-variables

## 架構總覽

變數系統分三層，本 change 做 Wave 1（前兩層 + 動態參數）：

1. **值與情境**：執行時攜帶一個 RunContext（變數表 + 觸發情境 + 上一個輸出）
2. **參數代入**：文字參數可含 token，執行時由 resolver 代入實際值
3. **控制流程**（後續波）：如果 / 重複——需要巢狀動作模型

## 值模型（runtime）

```kotlin
// Wave 1 以文字為主軸，數字/日期先以文字承載，Wave 2 再導入型別化 VarValue
// 每個動作可回傳一段輸出文字（null = 無輸出）
```

Wave 1 一律以 **String** 作為值的通用型別（iOS 的多型別 Wave 2 再引入 `sealed VarValue`）。
好處：與現有文字欄無縫、序列化零改動、解析單純。

## RunContext（執行情境）

```kotlin
class RunContext {
    val vars = mutableMapOf<String, String>()   // 具名變數
    var lastOutput: String? = null              // 魔術變數：上一個動作的輸出
    val trigger = mutableMapOf<String, String>() // 觸發情境值（見下）
}
```

- 由 `ExecutionService` / 手動執行路徑在一次執行開始時建立，貫穿該次所有動作
- 每個動作 `execute` 後，若有輸出就寫入 `lastOutput`
- `設定變數` 動作把（解析後的）文字寫進 `vars[name]`

### 觸發情境值（trigger map）

各觸發在啟動執行時，把可用資訊放進 `trigger`（key → 值），例如：

| 觸發 | 提供的 key |
|---|---|
| 通知 | `通知標題`、`通知內容`、`通知來源App` |
| 電量高於/低於、充電 | `電量` |
| Wi-Fi 連線 | `Wi-Fi名稱` |
| 藍牙連接/斷開 | `藍牙裝置` |
| 進入/離開區域 | `地點名稱` |
| NFC 標籤 | `標籤名稱` |
| 全部（含手動） | `時間`（HH:mm）、`日期`（yyyy-MM-dd）、`星期` |

沒有對應觸發時（例：定時 routine 引用 `通知內容`）該 token 代入空字串。

## Token 與解析（VariableResolver）

文字參數仍是 `String`，但可內嵌 token：

- `{{result}}` — 上一個動作的輸出（lastOutput）
- `{{var:名稱}}` — 具名變數
- `{{觸發:通知內容}}` 或直接 `{{通知內容}}` — 觸發情境值
- `{{時間}}`、`{{日期}}`、`{{電量}}`… — 常用捷徑

`VariableResolver.resolve(template, ctx)`：掃描 `{{...}}`、查 ctx 代入；查不到代空字串；
`{{` 若非合法 token 原樣保留。**不含 token 的字串原樣回傳** → 舊資料零影響。

序列化：文字欄型別不變（仍是 `@Serializable` String），token 只是字串內容，舊 JSON 完全相容。

## 動作改動

- 既有文字類動作（Notify title/text、Share、Http url/body、Speak、Clipboard、SetAlarm message…）
  執行前先把該欄位過一次 `resolver.resolve(...)`；其餘不變
- 有意義輸出的動作寫入 `lastOutput`：
  - Http → 回應內容（沿用現有讀取，但 Wave 1 起保留為輸出；注意仍遮罩進 log）
  - 拍照/錄音 → 檔案的 content URI 字串（供之後「分享 {{result}}」——實際分享檔案是 Wave 2，
    Wave 1 先讓 URI 可被引用）
  - Text 動作 → 其文字
  - 其餘動作無輸出（lastOutput 維持前值）

### 新動作（Wave 1）

- `Text(template: String)`：把 resolve 後的文字設為輸出（產生資料的起點）
- `SetVariable(name: String, template: String)`：`vars[name] = resolve(template)`

## UI

- 文字參數編輯器（多行/單行文字欄）加「**插入變數**」入口：開一個小清單，
  列出當下可插入的 token——分三類：**觸發提供**（依該 routine 觸發顯示對應項）、
  **上一個結果**（`{{result}}`）、**已設定的變數**（掃描此 routine 前面的 `設定變數` 動作名稱）、
  以及常用（時間/日期/電量…）。點選即把 token 插入游標處
- 調色盤新增「**變數**」分組（流程灰家族鄰近色或獨立色階）：`文字`、`設定變數`
- 積木參數欄顯示原始 template（含 token 文字），不需即時求值

## 執行路徑

`ExecutionService.runOne` 與手動 `RoutineManager.runNow` 建立 RunContext，
把觸發情境填入，依序執行動作（每個動作拿到 ctx：讀取代入、寫入輸出/變數），
成敗照舊寫 RunLog（log 內的敏感值沿用既有遮罩）。

## 後續路線（design 記錄，非本波）

- **Wave 2**：型別化 `VarValue`（文字/數字/日期/清單）、數字與數學、日期格式化、文字處理（取代/分開/合併）
- **Wave 3｜控制流程**：`如果 / 否則`、`重複 N 次`、`重複每一項`。需把 `Routine.actions: List<Action>`
  的扁平模型改為**可巢狀**——採 iOS 的「扁平清單 + 配對區塊標記」（`IfBegin`/`Else`/`EndIf`、
  `RepeatBegin`/`EndRepeat`）以最小衝擊現有序列化與拖曳排序；積木 UI 以縮排表現層級
- **Wave 4**：列表 / 辭典、更多來源（取得剪貼簿在前景、螢幕內容 OCR 等再評估）

## 版本

Wave 1：versionName 0.14.0 / versionCode 14
