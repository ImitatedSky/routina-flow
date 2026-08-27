# 觸發限制（次數 / 結束日期，達到後自動停用）

## 目的

讓每個程序可以設定「觸發的壽命」:

- **最多觸發幾次**(`maxRuns`)——例如「只在接下來 3 次插電時提醒」。
- **觸發到哪一天**(`expiresAt`)——例如「這個排程只跑到 8/15」。

達到任一上限就**自動停用**該程序(並取消它的排程/監測),不用手動關。**手動測試(執行一次)不計入次數、不受期限限制**。

## 設計

### 資料(`Routine`,皆有預設值→舊資料相容)
- `maxRuns: Int? = null`（null＝無限）
- `runCount: Int = 0`（由觸發實際執行累計；手動不計）
- `expiresAt: Long? = null`（epoch millis,當地日終;null＝無期限）

### 強制點:`RoutineExecutor.execute`(觸發執行的單一匯流點)
三個 execute 呼叫端中,`RoutineManager.runNow` 是 `MANUAL`,另兩個(`TriggerDispatch` / `ExecutionService`)是真實觸發;每次觸發只會走 execute 一次。因此在 execute 內以 `source != MANUAL` 為閘門:

1. **執行前**:已過期或已達次數 → `RoutineManager.setEnabled(false)`(連帶取消排程/監測)+ 記一筆說明的紀錄 + 跳過不執行。處理「排程還沒被拆掉又觸發」的情況。
2. **執行後**:`repository.incrementRunCount(id, blocking)`(背景執行同步寫檔,次數不漏記);若這次跑完達上限或已過期 → `setEnabled(false)` 自動停用。

`blocking` 沿用 `persistBlocking`:背景觸發後行程可能被回收,次數必須同步落地以免超額觸發。

### UI(`EditScreen` →「觸發限制(選用)」區塊)
- 開關「限制觸發次數」+ 數字欄 + 「已觸發 X / Y 次」+「重設次數」。
- 開關「設定結束日期」+ 日期選擇器(顯示「觸發到 YYYY/MM/DD(含當日)」)。
- 三個欄位都納入 `contentEquals`,改動會觸發「未儲存變更」提醒。

## 非目標
- 不提前依 `expiresAt` 裁掉時間觸發的下一次鬧鐘(靠執行前閘門處理即可;過期後最多一次無害的自我跳過)。
- 首頁卡片上的「剩 N 次 / 至 X 日」小標示留待後續。
