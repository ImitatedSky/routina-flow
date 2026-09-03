# Tasks: add-lists-foreach

清單動作本身。逐項重複（`ForEachBegin` / `EndForEach`）在 `add-foreach-loop` 交付。

## 1. 模型

- [x] 1.1 五個清單動作：`ListCreate` / `ListSplit` / `ListAppend` / `ListGet` / `ListCount`（欄位皆有預設、舊 JSON 相容）

## 2. 執行

- [x] 2.1 `RoutineExecutor`：`parseList` / `listRaw`（先 `vars` 再 `globals`）與五個 `doListXxx`；名稱空白、編號非數字、超出範圍一律 `error(...)` 記為失敗
- [x] 2.2 dispatch `when`、`describe`、`resolveAction`（只解析 items／input／item／index，名稱不解析）

## 3. UI

- [x] 3.1 調色盤：新增「清單」組（五個動作）
- [x] 3.2 `ActionEditor`：五個編輯器（變數名稱用 `OutlinedTextField`、可含 token 的欄位用 `VariableTextField`）＋ `isActionValid`＋ `availableTokens`（清單變數列進「已設定的變數」）
- [x] 3.3 `UiLabels`（三個 `when`）、`Color`（清單＝變數家族色）

## 4. 驗證與提交

- [x] 4.1 `assembleDebug` 綠燈
- [x] 4.2 一個 commit（英文、無 co-author）
