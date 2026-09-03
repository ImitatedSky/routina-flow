# Change: add-run-from-outside

## Why

目前例行程序只能開 App、進到清單/編輯畫面點 ▶ 才能手動執行。對於「純捷徑型」的手動
程序（App 開著才有意義的其實是設定，執行本身應該一鍵可達），這一步太重。iOS 捷徑與
Bixby Routines 都能把單一捷徑放到系統 UI（快速設定磚、桌面圖示）直接觸發，這是使用者
對這類 App 的基本期待。

## What Changes

新增兩條「從 App 外執行指定程序」的路徑，兩者都以 MANUAL 執行（等同畫面上的 ▶：
不受啟用狀態/觸發上限限制，也不計入觸發次數）：

- **快速設定磚（TileService）**：一塊磚執行使用者指定的程序，磚標籤顯示該程序名稱
  （未指定退回「Routina」）。使用者在編輯畫面 ⋯ 選單的「設為快速設定磚」指定要跑哪個
  程序（id 存入 SharedPreferences，磚讀它）。未指定時點磚只跳 Toast 提示，不崩潰。
- **桌面釘選捷徑**：⋯ 選單的「加到桌面」呼叫 `ShortcutManagerCompat.requestPinShortcut`，
  建立一個標籤為程序名稱、圖示為 App 圖示的捷徑，點擊即執行該程序。桌面不支援釘選時
  提示使用者。

兩者共用一個透明跳板/共用邏輯 `RunFromOutside`：捷徑走透明的 `RunRoutineActivity`
（讀 routineId → 執行 → 立刻 finish），磚則直接執行（避開 TileService 啟動 Activity 在
Android 14 需改用 PendingIntent 版 `startActivityAndCollapse` 的版本差異）。因為跳板
Activity 立刻結束，執行掛在進程層級 `CoroutineScope(SupervisorJob()+Dispatchers.IO)`，
不用會被取消的 lifecycleScope。

## Non-goals

- 不做多塊磚 / 每塊磚各自綁不同程序（先支援單一使用者指定的磚）。
- 不做動態 App 捷徑（long-press App 圖示的清單）——只做使用者主動釘選的桌面捷徑。
- 不新增 model 欄位（磚指定的 id 存 SharedPreferences，不進 routines.json）。

## Impact

- 受影響程式碼：engine（+RunFromOutside、+RunRoutineActivity、+RunRoutineTileService）、
  ui/EditScreen（⋯ 選單 +2 項）、AndroidManifest（+磚 service、+跳板 activity）。
- 權限：BIND_QUICK_SETTINGS_TILE（磚 service 的 android:permission，系統要求）。
- 無新增第三方依賴（TileService 為平台 API；ShortcutManagerCompat 來自已在用的
  androidx.core）。無資料格式變更，既有 routines.json 照常讀入。
