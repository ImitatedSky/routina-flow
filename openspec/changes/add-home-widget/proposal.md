# Change: add-home-widget

## Why

目前要執行一支例行程序，得開 App 進清單點 ▶，或先在編輯畫面把單一程序設成快速設定磚／
桌面捷徑。使用者想在桌面一眼看到所有程序、直接點哪支跑哪支時，這些入口都不夠。Apple 捷徑
與 Bixby Routines 都提供「桌面小工具列出捷徑、點一下就跑」的入口，這是這類 App 的基本期待。

## What Changes

新增一個桌面小工具（App Widget），把例行程序列成可捲動清單、點一列即以 MANUAL 執行該程序
（等同畫面上的 ▶：不受啟用狀態／觸發上限限制，也不計入觸發次數）：

- **RemoteViews 集合小工具**：`RoutinaWidgetProvider`（AppWidgetProvider）＋
  `RoutinaWidgetService`（RemoteViewsService）。清單資料讀 `RoutineRepository.routines.value`
  的當前快照，每列掛只帶該列 routineId 的 fill-in intent。
- **共用既有執行路徑**：點列的 PendingIntent 樣板指向既有的透明跳板 `RunRoutineActivity`
  （讀 `EXTRA_ROUTINE_ID` → 以 MANUAL 執行 → Toast 回饋 → 立刻 finish），與快速設定磚／
  桌面捷徑同一條路，不重造執行邏輯。
- **資料異動即刷新**：`RoutineManager` 的 save／delete／reorder 完成後呼叫
  `RoutinaWidgetProvider.refresh`，內部以 `notifyAppWidgetViewDataChanged` 讓清單重讀。
  沒有任何小工具時直接跳過，零成本。
- **亮／暗都可讀**：小工具自畫圓角底＋自訂文字色（values / values-night），對比不受啟動器
  主題影響。清單為空時由系統改顯示空狀態文字（setEmptyView）。

## Non-goals

- 不做每列額外操作（編輯／啟停），小工具只做「列出＋點擊執行」。
- 不新增 model 欄位或資料格式：清單直接讀既有 `routines.json` 快照。
- 不做 Compose Glance；小工具走傳統 RemoteViews / XML（Glance 會多帶一整套依賴，
  這個需求用不到）。

## Impact

- 受影響程式碼：engine（+RoutinaWidgetProvider、+RoutinaWidgetService；RoutineManager
  save/delete/reorder 各 +1 行刷新）、res（+widget 版面／item／provider info／背景 drawable、
  +widget 色與字串、+values-night 色）、AndroidManifest（+receiver、+service）。
- 權限：BIND_REMOTEVIEWS（小工具 service 的 android:permission，系統要求）。
- 無新增第三方依賴（AppWidget 全是平台 API）。無資料格式變更，既有 routines.json 照常讀入。
- 不觸碰 sealed Action/Trigger 的任何 when 分派點（未新增動作或觸發）。
