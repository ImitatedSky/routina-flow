# Change: add-manual-routines

## Why

使用者回饋：不是每個例行程序都需要觸發條件。有時只是想「點一下 ▶ 就開一個網站或 App」——
一個純手動的捷徑。但目前每個 routine 都被綁死要選一個觸發條件。

這正是 iOS 捷徑的核心區分：**「捷徑」（手動執行）vs「自動化」（有觸發）**。
Routina 應該同時支援兩者，且手動應該是零負擔的預設。

## What Changes

- **新增「手動執行」觸發**（等於無自動觸發，只靠清單卡片與編輯畫面的 ▶ 執行）：
  - 加入觸發調色盤，置於最前（獨立「手動」分組），讓使用者一眼看到「可以不設觸發」
  - 手動 routine **不排程、不監測、不耗電**，永遠可用 ▶ 立即執行
- **新建空白 routine 預設為「手動執行」**：使用者想做純捷徑時完全不必碰觸發條件；
  要自動化再把手動帽子積木換成定時/區域/充電等
- **卡片與編輯的手動化調整**：
  - 手動 routine 的首頁卡片**隱藏啟用開關**（沒有東西要排程，開關無意義），並讓 ▶ 執行更明確
  - 手動帽子積木顯示「手動執行 · 點 ▶ 執行」，中性色，無參數欄
  - 狀態晶片：手動 routine 不顯示「下次執行」，顯示中性「手動」標記＋上次結果

## Non-goals

- 桌面捷徑圖示 / App 捷徑（long-press launcher shortcut）——另議
- 語音（Google Assistant）喚起——另議
- 移除既有觸發能力（手動只是多一個選項，其餘 17 種觸發不變）

## Impact

- 受影響 specs：routine-triggers（新增手動執行）、routine-management（手動卡片行為）
- 受影響程式碼：model（Trigger.Manual）、engine（各排程/監測/服務把 Manual 視為永不自動觸發、
  isConfigured 視為已設定）、ui（觸發調色盤、帽子積木、HomeScreen 卡片隱藏開關、
  nextRunSummary/triggerSummary、空白預設）
- 序列化只新增；舊資料相容；versionName 0.10.0 / versionCode 10；無新依賴
