# 設計：全域變數與控制流程

## 第 1 層 全域變數（已實作）

### 資料與儲存
- `model.GlobalVar(name, value, updatedAt)`，以 `name` 為唯一鍵。
- `RoutineRepository`：`global_vars.json`、`globals: StateFlow<List<GlobalVar>>`，與 `nfc_tags.json` 同一套原子寫入模式。
- `globalsSnapshot(): Map<String,String>` 供執行載入；`applyGlobals(changed, blocking)` 只合併有異動的鍵（避免兩程序同時執行時互相覆蓋）；`setGlobal` / `deleteGlobal` 供管理畫面。

### 執行期
- `RunContext` 新增 `globals` 與 `dirtyGlobals`。`execute` 開始時 `globals.putAll(repository.globalsSnapshot())`。
- `VariableResolver`：新增 `{{全域:名稱}}`（與既有 `{{觸發:...}}` 對稱），查無代空字串。
- `Action.SetGlobalVariable(name, template)`：寫入 `ctx.globals` 並登記 `dirtyGlobals`；**執行結束時**才由 `execute` 把異動的鍵落地（背景執行用 blocking 寫入以防行程結束）。批次落地而非每次動作寫檔，較省 I/O 也天然照 `persistBlocking` 決定同步/非同步。

### 授權 UI
- 「插入變數」清單多一組「全域變數」，來源＝現有全域變數 + 本程序稍早的「設定全域變數」動作名稱。
- 首頁工具列 `{}`（DataObject）圖示 → 全域變數管理畫面（檢視 / 新增 / 改值 / 刪除）。

---

## 第 2、3 層 控制流程（待實作，設計鎖定）

採 iOS Shortcuts 的作法：**維持 `Routine.actions` 為扁平 `List<Action>`，用「配對的區塊標記」表達層級**。這對現有序列化、拖曳排序衝擊最小（沿用 add-variables/design.md 的建議）。

### 新增的標記型 Action（皆 @Serializable，附加相容）
| 型別 | SerialName | 參數 |
|---|---|---|
| `IfBegin` | `if_begin` | `condition: Condition` |
| `ElseIf` | `else_if` | `condition: Condition` |
| `Else` | `else` | 無（data object）|
| `EndIf` | `end_if` | 無 |
| `WhileBegin` | `while_begin` | `condition: Condition` |
| `EndWhile` | `end_while` | 無 |
| `RepeatBegin` | `repeat_begin` | `count: Int`, `countExpr: String`（可用變數，沿用 add-numeric-params 的 value/expr 慣例）|
| `EndRepeat` | `end_repeat` | 無 |

### 條件模型
```
Condition(left: String, op: CompareOp, right: String)
CompareOp = EQ, NEQ, GT, GTE, LT, LTE, CONTAINS, NOT_CONTAINS, IS_EMPTY, IS_NOT_EMPTY, IS_TRUE
```
- `left` / `right` 都先經 `VariableResolver.resolve`（可放 `{{全域:x}}`、`{{var:y}}`、字面值）。
- 求值：`GT/GTE/LT/LTE` → 兩邊能 parse 成數字才比數值，否則該條件為 false。`EQ/NEQ` → 先試數值、退回字串（trim 後比較）。`CONTAINS` → 字串包含。`IS_EMPTY/IS_NOT_EMPTY` → 看 `left` 是否空白（`right` 忽略，UI 隱藏）。`IS_TRUE` → `left` ∈ {true,1,yes,on,是,真}。
- 放在新檔 `engine/Conditions.kt`（`ConditionEvaluator.eval(cond, ctx): Boolean`），與 `VariableResolver` 平行、可單元測試。

### 執行引擎：從 mapIndexed 改為游標式直譯器
`RoutineExecutor.execute` 目前是 `routine.actions.mapIndexed { ... }`（每個動作剛好跑一次、結果 1:1）。改為：

1. **前置配對**：單次掃描用堆疊把每個 begin 標記配到它的 else/elif/end 位置，建成一張跳轉表。**不成對的標記視為無效**（多的 end→no-op；未關的 begin→視為在清單尾自動關閉），只記一筆說明、絕不崩潰。
2. **主迴圈** `while (i < actions.size)`：
   - 一般動作 → 跑（現有 `runAction`），`i++`。
   - `IfBegin` → 求值：真則進入其 body、跑到下一個 elif/else/end 前、再跳到 `EndIf` 之後；假則跳到下一個 `ElseIf`（再求值）/`Else`（進入）/`EndIf`。
   - `WhileBegin` → 求值：真則進入 body、遇 `EndWhile` 跳回該 `WhileBegin` 重評；假則跳過 `EndWhile`。
   - `RepeatBegin` → 解析次數（夾在 `0..REPEAT_MAX`），用**迴圈框堆疊**記「起點 + 已跑次數」；到 `EndRepeat` 決定回跳或離開；把目前次數以 `{{迴圈:次數}}` 放進 `ctx`（支援巢狀時用堆疊最內層）。
   - `Else / ElseIf / EndIf / EndWhile / EndRepeat` 被直接走到時多半是配對跳轉的落點，作用如上。
3. **安全上限**：`WHILE_MAX_ITERATIONS`、`REPEAT_MAX`，以及整體 `MAX_ACTIONS_PER_RUN`（即使邏輯壞掉也能收斂）；觸上限就中止並在紀錄留說明。
4. 結果：`RunLog.results` 不再與 actions 同長度（迴圈會重複、分支會略過）——這本來就只是一個 list，無妨。

### UI
- 調色盤新增「流程控制」組：`如果`、`重複 N 次`、`一直重複…當`。選「如果」時**成對插入** `IfBegin`+`EndIf`（body 空），使用者再用既有的 InsertPoint 往中間加動作；`IfBegin` 積木上提供「＋ 否則如果／否則」在它的 `EndIf` 前插標記。
- 顯示：走一次清單算每塊的縮排深度，區塊標記畫成細長的「如果 <條件> …／結束」頭尾塊，中間動作縮排一層。
- 排序：維持現有扁平 `ReorderableColumn`；直譯器對不成對標記穩健，必要時在配對不齊時顯示提示 chip。（不做複雜的配對整組拖曳。）
- 條件編輯器：`left` VariableTextField + `op` 下拉 + `right` VariableTextField（`IS_EMPTY/IS_NOT_EMPTY` 隱藏 right）。

### 每個 Action when 都要補（編譯器會強制）
新增子型別會讓所有 exhaustive `when(action)` 編譯失敗，需同步補：`RoutineExecutor`（dispatch / resolveAction / describe）、`ActionEditor`（編輯器 / isActionValid）、`UiLabels`（三處）、`Color.actionColor`、`BlockPalette`。標記型動作在 dispatch 不做事（由直譯器處理流程），描述文字給人看的層級提示。
