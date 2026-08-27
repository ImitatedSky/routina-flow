# 任務

## 第 1 層 全域變數（已完成）
- [x] `model.GlobalVar` 與 `Action.SetGlobalVariable`
- [x] `RoutineRepository`：`global_vars.json`、`globals` flow、`globalsSnapshot` / `applyGlobals` / `setGlobal` / `deleteGlobal`
- [x] `RunContext.globals` / `dirtyGlobals`、`VariableResolver` 的 `{{全域:名稱}}`
- [x] `RoutineExecutor`：執行開始載入全域、`doSetGlobalVariable`、結束時落地異動、dispatch / resolveAction / describe
- [x] 授權 UI：`ActionEditor` 編輯器 + isActionValid + 「全域變數」插入清單；`BlockPalette`；`UiLabels` ×3；`Color`
- [x] 全域變數管理畫面 `GlobalVarsScreen` + 首頁 `{}` 入口 + 導覽路由
- [ ] 文件：`docs/GUIDE.md` 補全域變數用法（第 1 層收尾時）

## 第 2 層 條件（已完成）
- [x] `Condition` / `CompareOp`（含 為真/為假 布林運算子）；`engine/Conditions.kt` 的 `ConditionEvaluator`
- [x] `Action`：`IfBegin` / `ElseIf` / `Else` / `EndIf`
- [x] `RoutineExecutor`：mapIndexed → 遞迴游標式直譯器（`runProgram`）+ 配對掃描 + if/elif/else 分支
- [x] UI：流程控制調色盤組、成對插入（begin+end）、縮排渲染、條件編輯器（左值/運算子/右值）
- [x] 所有 exhaustive `when(action)`／`when(op)` 補齊

## 第 3 層 迴圈（已完成）
- [x] `Action`：`WhileBegin` / `EndWhile` / `RepeatBegin` / `EndRepeat`
- [x] 直譯器：while 回跳、repeat 迴圈、`{{迴圈:次數}}`（含巢狀還原）
- [x] 安全上限：`WHILE_MAX_ITERATIONS` / `REPEAT_COUNT_SAFE` / `MAX_ACTIONS_PER_RUN`
- [x] UI 與 when 補齊

## 布林旗標（本次追加）
- [x] `CompareOp.IS_TRUE / IS_FALSE`（設定變數存 true/1/yes/是 即可當旗標）
- [x] `usesRightOperand` 統一「只看左值」的運算子（為空/不為空/為真/為假）
