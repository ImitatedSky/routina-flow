# 任務

- [x] `Routine` 新增 `maxRuns` / `runCount` / `expiresAt`（皆預設值，舊資料相容）
- [x] `RoutineRepository.incrementRunCount(id, blocking)`（背景執行同步落地）
- [x] `RoutineExecutor.execute`：執行前閘門（過期/超額 → 停用+跳過）、執行後計次+自動停用；手動（MANUAL）不計不擋
- [x] `EditScreen`：「觸發限制」區塊（次數開關+欄位+重設、結束日期開關+日期選擇器）
- [x] `contentEquals` 納入三個新欄位（未儲存變更提醒）
- [ ] 首頁卡片「剩 N 次 / 至 X 日」小標示（選用，後續）
- [ ] `docs/GUIDE.md` 補說明（V1 收尾時一起）
