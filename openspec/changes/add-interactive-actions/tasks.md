# Tasks: add-interactive-actions

## 1. 模型

- [x] 1.1 `Action.AskInput(prompt, variableName, defaultValue)`（`@SerialName("ask_input")`，欄位皆有預設、舊 JSON 相容）
- [x] 1.2 `Action.ChooseMenu(prompt, options, variableName)`（`@SerialName("choose_menu")`）

## 2. 執行與橋接

- [x] 2.1 `InputBridge`：`nextRequestId` / `open(requestId)` / `deliver(requestId, value)` / `cancel`（行程內、ConcurrentHashMap）
- [x] 2.2 `InputPromptActivity`：透明對話框主題、noHistory + excludeFromRecents + configChanges；輸入／選單兩種 Material3 對話框；確定／選擇／取消／關閉都經 `deliver` 交回（冪等）
- [x] 2.3 `RoutineExecutor`：`doAskInput` / `doChooseMenu`（suspend）——resolveAction 解析 prompt/options/default（variableName 不解析）；登記請求、`launchOrNotify` 前景啟動或背景通知降級；`withTimeoutOrNull(120s)` 等待；成功寫入 `ctx.vars`、回傳 `名稱 = 答案`；逾時／取消 `error(...)`
- [x] 2.4 dispatch `when`、`describe` 補上兩個分支
- [x] 2.5 AndroidManifest 註冊 Activity、themes 加對話框主題、proguard keep Activity

## 3. UI

- [x] 3.1 調色盤「變數」組加「詢問輸入」「選單選擇」積木
- [x] 3.2 ActionEditor 編輯器（提示 + 變數名稱 + 預設值；選單以「一行一個」選項）＋ `isActionValid`（變數名稱必填、選單需至少一非空選項）＋ `availableTokens` 收錄新變數
- [x] 3.3 UiLabels（`actionTypeName`／`actionBlockLabel`／`actionParamText` 顯示提示截斷）、Color（`actionColor` 用 `ActionSetVariable`）

## 4. 驗證與提交

- [x] 4.1 `assembleDebug` 綠燈
- [x] 4.2 一個 commit（英文、無 co-author）
