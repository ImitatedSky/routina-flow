# Tasks: add-data-actions

## 1. 模型

- [x] 1.1 `Action.JsonGet(source, path, variableName)`（`@SerialName("json_get")`，欄位皆有預設、舊 JSON 相容）
- [x] 1.2 `Action.TextTransform(input, op, arg1, arg2, variableName)`（`@SerialName("text_transform")`）
- [x] 1.3 `Action.DateFormat(variableName, pattern, offsetMinutes, offsetDays)`（`@SerialName("date_format")`）
- [x] 1.4 `TextOp` 列舉（upper／lower／trim／replace／substring／length／regex_extract）與 `usesArgs`

## 2. 執行

- [x] 2.1 `engine/JsonPath`：點／中括號路徑解析與讀取（`data.items[0].name`，點分隔數字段亦當索引；org.json，取不到回 null）
- [x] 2.2 `doJsonGet`：陣列一行一個項目、物件輸出 JSON 文字；來源空／路徑不存在記為失敗
- [x] 2.3 `doTextTransform`：七種操作；擷取為 1-based 含頭含尾並夾回合法範圍；正規式不符得空字串
- [x] 2.4 `doDateFormat`：`LocalDateTime.now()` 加天／分偏移後格式化；格式不合法記為失敗
- [x] 2.5 dispatch `when`、`describe`、`resolveAction`（解析 source／path／input／arg1／arg2，不解析 variableName）、`textOpLabel`

## 3. UI

- [x] 3.1 調色盤：三塊加入既有「變數」組（不新增分組）
- [x] 3.2 Color：`actionColor` 三個分支沿用 `ActionSetVariable`、更新計數註解
- [x] 3.3 ActionEditor 三個編輯器（文字處理以 `ChipRow` 選操作、只在用得到時顯示 arg 欄）＋ `isActionValid`（變數名稱必填，JSON 取值另需路徑）＋ `availableTokens` 收錄新變數
- [x] 3.4 UiLabels：`actionTypeName`／`actionBlockLabel`／`actionParamText`／`textOpName`

## 4. 驗證與提交

- [x] 4.1 `assembleDebug` 綠燈
- [x] 4.2 一個 commit（英文、無 co-author）
