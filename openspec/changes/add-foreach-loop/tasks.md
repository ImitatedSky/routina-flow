# Tasks: add-foreach-loop

## 1. 模型

- [x] 1.1 配對標記 `ForEachBegin(listSource, itemVariable = "item")`（`foreach_begin`）/ `EndForEach`（`end_foreach`）；欄位皆有預設、舊 JSON 相容

## 2. 執行

- [x] 2.1 `runProgram` 的 `ForEachBegin` 分支：`VariableResolver.resolve(listSource)` → `parseForEachItems`（換行或逗號切、去空白、略過空項）；逐項 `ctx.vars[itemVariable] = item`、`{{迴圈:次數}}` 設 1 起算位置，跑 body，受 `budget` 保護
- [x] 2.2 迴圈前後存回／還原：`itemVariable` 的舊值（比照 `restoreLoopIndex` 的巢狀還原）＋計數鍵；最外層則移除
- [x] 2.3 begin/end 集合補上新標記：`matchingEnd`、`nextBranchOrEnd`、孤立標記略過分支
- [x] 2.4 dispatch `when`（標記 no-op）、`describe`（顯示清單來源 → 項目變數）

## 3. UI

- [x] 3.1 調色盤「流程控制」組加「逐項重複」（結束標記由 `pairedEnd` 成對插入）
- [x] 3.2 `EditScreen` 三個 helper：`pairedEnd`、`matchingEndInList`、`indentDepths`
- [x] 3.3 `ActionEditor`：`ForEachBegin` 編輯器（`listSource` 用 `VariableTextField`、`itemVariable` 用 `OutlinedTextField`＋說明）、`EndForEach` 的 `ControlMarkerInfo`；`isActionValid`（`itemVariable` 非空）；`availableTokens` 把迴圈項目變數列進「已設定的變數」
- [x] 3.4 `UiLabels`（三個 `when`：類型名／積木標籤／參數欄顯示來源 → 項目變數）、`Color`（標記＝流程控制色）

## 4. 驗證與提交

- [x] 4.1 `assembleDebug` 綠燈
- [x] 4.2 一個 commit（英文、無 co-author）
