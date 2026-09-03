# Change: add-lists-foreach

## Why

變數目前只能存單一一段文字，沒辦法表達「一組東西」。因此像
「把一段文字切成幾項 → 每一項各做一件事」這種捷徑（iOS 捷徑的 List + Repeat with Each）
在 Routina 寫不出來，只能用 `重複 N 次` 硬湊。

## What Changes

- **清單格式（與並行開發的功能共用的約定）**：清單就是一段「一行一個項目」的純文字，
  存在既有的一般變數裡（`{{var:名稱}}` 照常引用）。讀取時 `lines()` → 去前後空白 → 略過空行；
  寫入時以 `\n` 接起來。不新增資料型別、不動序列化格式。
- **五個清單動作**（歸「變數」家族，色用 `ActionSetVariable`，調色盤新增「清單」組）：
  - **建立清單 `ListCreate`**（`list_create`）：多行文字正規化後存成清單變數。
  - **切割成清單 `ListSplit`**（`list_split`）：依分隔符號切開存成清單變數。
  - **加入清單項目 `ListAppend`**（`list_append`）：把項目接到清單最後（沒設過視為空清單）。
  - **取清單項目 `ListGet`**（`list_get`）：取第 N 項（1 起算，可用 `{{迴圈:次數}}`）存進變數；
    不是數字或超出範圍記為失敗。
  - **清單長度 `ListCount`**（`list_count`）：把項目數存進變數。
- **逐項重複 `ForEachBegin` / `EndForEach`**（`for_each_begin` / `end_for_each`）：
  沿用既有的配對標記直譯器，把清單的每個項目各跑一遍區塊內的動作。迴圈內以
  `{{迴圈:項目}}` 取得目前項目、`{{迴圈:次數}}` 取得第幾項；兩個鍵都比照 `重複 N 次`
  在迴圈前後存回／還原，巢狀迴圈的外層值不會被內層蓋掉。

## Non-goals

- 清單專用的資料型別、巢狀清單、排序 / 去重 / 過濾等清單運算——先做建立、切割、
  取用、長度、逐項這一組能端到端跑起來的最小集合
- 清單專用的編輯 UI（拖拉排序的項目編輯器）——先用「一行一個」的文字欄，與選單選擇一致
- 逐項重複的中斷（break / continue）——維持既有流程控制的單一路徑

## Impact

- 受影響 specs：routine-actions（五個清單動作）、routine-engine（逐項重複的直譯器語意）
- 受影響程式碼：model（七個新 `Action`）、engine（`RoutineExecutor` 的 `runProgram`
  逐項重複分支、`matchingEnd`／`nextBranchOrEnd`／dispatch／`describe`／`resolveAction`／
  五個 `doListXxx`，`Variables` 的 `迴圈:項目`）、ui（調色盤、ActionEditor 編輯器＋驗證＋
  可插入變數、UiLabels、Color、EditScreen 的 `pairedEnd`／`matchingEndInList`／`indentDepths`）
- 序列化只新增、欄位皆有預設；舊 `routines.json` 照常讀入；無新依賴
