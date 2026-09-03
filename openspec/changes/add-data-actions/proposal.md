# Change: add-data-actions

## Why

HTTP 請求已經能把回應存進 `{{result}}`，但那是一整包 JSON 或一長串文字，
要拿其中一個欄位、把大小寫統一、或補上一個時間戳，目前只能靠「運算式」硬拼，實務上做不到。
缺的是介於「取得資料」與「使用資料」之間的加工步驟。

## What Changes

- **新增三個資料處理動作**（各自把結果寫進一個具名變數，之後以 `{{var:名稱}}` 引用）：
  - **從 JSON 取值 `JsonGet`**（`json_get`）：`source`（JSON 文字，可含 token，通常是
    `{{result}}`）依 `path`（`data.items[0].name` 這類點／中括號路徑）取值。用 Android 內建的
    org.json，**不加新依賴**。取到陣列輸出「一行一個項目」，取到物件輸出原始 JSON 文字，
    路徑不存在記為失敗。
  - **文字處理 `TextTransform`**（`text_transform`）：對 `input` 做一次 `TextOp` 轉換——
    大寫／小寫／去空白／取代／擷取／長度／正規式擷取；`arg1`／`arg2` 的意義依操作而定，
    用不到的操作在編輯畫面不顯示參數欄。
  - **日期時間 `DateFormat`**（`date_format`）：把「現在 + 天數/分鐘偏移」依 java.time 的
    `pattern` 格式化（minSdk 26 可直接用 `java.time`），格式不合法記為失敗。
- **調色盤新增「資料處理」組**（排在「變數」之後），色表新增一個橄欖綠家族色
  `ActionDataProcess`，維持「一組一色相」。
- 路徑解析獨立成 `engine/JsonPath`（不塞進已經很長的 RoutineExecutor），
  取不到一律回 null，由動作決定使用者看到的訊息。

## Non-goals

- 完整的 JSONPath 語法（萬用字元、過濾、遞迴下降）——只做能對應到 UI 說明的點／索引路徑
- 寫入或組裝 JSON——這一波只做「讀出來」
- 解析既有字串的日期（parse）與時區換算——只做「現在 + 偏移」格式化

## Impact

- 受影響 specs：routine-actions（新增三個資料處理動作）
- 受影響程式碼：model（`Action.JsonGet` / `TextTransform` / `DateFormat`、`TextOp` 列舉與
  `usesArgs`）、engine（新檔 `JsonPath`、`RoutineExecutor` 的 dispatch／`resolveAction`／
  `describe`／三個 `doXxx` 與 `textOpLabel`）、ui（調色盤新組、ActionEditor 編輯器＋驗證＋
  可插入變數、UiLabels、Color）
- 清單變數沿用全 App 共同格式：一行一個項目（`\n` 分隔）
- 序列化只新增、欄位皆有預設；舊 `routines.json` 照常讀入；無新依賴
