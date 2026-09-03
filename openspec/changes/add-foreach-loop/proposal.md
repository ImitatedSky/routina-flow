# Change: add-foreach-loop

## Why

有了清單（一行一個項目的文字變數）之後，還缺一個把「清單的每個項目各做一件事」
直接寫出來的積木（iOS 捷徑的 Repeat with Each、Bixby 的 For each）。目前只能用
`重複 N 次` 搭配「取清單項目」硬湊，步驟多又容易錯。

## What Changes

- **逐項重複 `ForEachBegin` / `EndForEach`**（`foreach_begin` / `end_foreach`），
  沿用既有的配對標記直譯器（比照 `重複 N 次` 的 `RepeatBegin` / `EndRepeat`）：
  - `ForEachBegin(listSource, itemVariable = "item")`。`listSource` 是一段清單值，
    可含 `{{...}}` token（如 `{{result}}`、`{{var:待辦}}`），執行時先 `VariableResolver.resolve`
    再切割。切割規則：**換行或逗號都切**、去每項前後空白、略過空項。
  - 每一項：把值存進具名變數 `itemVariable`（迴圈內以 `{{var:名稱}}` 取得），
    並把 1 起算的位置寫進 `{{迴圈:次數}}`（沿用重複迴圈的計數鍵，for-each 內也讀得到），
    再跑一遍區塊到 `EndForEach`。
  - 迴圈前後存回／還原 `itemVariable` 的舊值與計數鍵，巢狀時外層值不會被內層蓋掉。
  - 受既有的單次執行動作預算（`MAX_ACTIONS_PER_RUN`）保護。
- **配對插入與縮排**：調色盤「流程控制」組新增「逐項重複」，結束標記由 `pairedEnd`
  成對插入；`matchingEndInList` / `indentDepths` 把新標記算進層級。
- **編輯器**：`listSource` 用可插入 token 的 `VariableTextField`、`itemVariable` 用
  `OutlinedTextField`；`EndForEach` 是唯讀的 `ControlMarkerInfo`。迴圈內的動作把
  `itemVariable` 列進「已設定的變數」，`{{var:item}}` 可從插入選單點選。

## Non-goals

- 逐項重複的中斷（break / continue）——維持既有流程控制的單一路徑
- 清單專用的資料型別或索引物件——項目就是文字，位置用既有的 `{{迴圈:次數}}`

## Impact

- 受影響 specs：routine-engine（逐項重複的直譯器語意）
- 受影響程式碼：model（`ForEachBegin` / `EndForEach` 兩個 `Action`）、engine
  （`RoutineExecutor` 的 `runProgram` 逐項分支、`matchingEnd` / `nextBranchOrEnd` /
  dispatch no-op / `describe`、`parseForEachItems`）、ui（調色盤、`ActionEditor` 編輯器＋
  `isActionValid`＋`availableTokens`、`UiLabels`、`Color`、`EditScreen` 的 `pairedEnd` /
  `matchingEndInList` / `indentDepths`）
- 序列化只新增、欄位皆有預設；舊 `routines.json` 照常讀入；無新依賴
