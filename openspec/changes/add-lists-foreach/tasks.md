# Tasks: add-lists-foreach

## 1. 模型

- [x] 1.1 五個清單動作：`ListCreate` / `ListSplit` / `ListAppend` / `ListGet` / `ListCount`（欄位皆有預設、舊 JSON 相容）
- [x] 1.2 配對標記 `ForEachBegin(listVariable)` / `EndForEach`

## 2. 執行

- [x] 2.1 `RoutineExecutor`：`parseList` / `listRaw`（先 `vars` 再 `globals`）與五個 `doListXxx`；名稱空白、編號非數字、超出範圍一律 `error(...)` 記為失敗
- [x] 2.2 `runProgram` 的 `ForEachBegin` 分支：逐項設定 `迴圈:項目` / `迴圈:次數`、跑 body、受 `budget` 保護，迴圈前後存回／還原兩個鍵（`restoreLoopIndex` + `restoreLoopItem`）
- [x] 2.3 begin/end 集合補上新標記：`matchingEnd`、`nextBranchOrEnd`、孤立標記略過分支
- [x] 2.4 dispatch `when`（標記 no-op）、`describe`、`resolveAction`（只解析 items／input／item／index，名稱不解析）
- [x] 2.5 `VariableResolver`：`KNOWN_KEYS` 與 `SAMPLE` 加入 `迴圈:項目`

## 3. UI

- [x] 3.1 調色盤：新增「清單」組（五個動作）；「流程控制」組加「逐項重複」（結束標記由 `pairedEnd` 成對插入）
- [x] 3.2 `EditScreen` 三個 helper：`pairedEnd`、`matchingEndInList`、`indentDepths`
- [x] 3.3 `ActionEditor`：六個編輯器（變數名稱用 `OutlinedTextField`、可含 token 的欄位用 `VariableTextField`）＋ `EndForEach` 的 `ControlMarkerInfo`＋ `isActionValid`＋ `availableTokens`（`{{迴圈:項目}}` 與清單變數）
- [x] 3.4 `UiLabels`（三個 `when`）、`Color`（清單＝變數家族色，標記＝流程控制色）

## 4. 驗證與提交

- [x] 4.1 `assembleDebug` 綠燈
- [x] 4.2 一個 commit（英文、無 co-author）
