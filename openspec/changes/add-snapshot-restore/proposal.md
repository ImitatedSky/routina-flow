# Change: add-snapshot-restore

## Why

Samsung「情境模式」與 Tasker 的 exit task 會在條件結束時把設定自動還原。Routina 的模型是
一次觸發（trigger→actions），要做「條件結束時還原」若改動 routine 模型（加旗標、加反向觸發、
在各分派點掛還原）成本高、侵入大。改用**兩個動作**讓使用者自己用既有的進入／離開觸發把還原
組出來：進入區域的程序放「記住目前設定＋靜音」，離開區域的程序放「回復設定」。不動一次觸發的模型，
純加法、舊 routines.json 照常讀入。

## What Changes

新增「裝置」動作組的兩個動作（皆為 `data object`、無參數）：

- **記住目前設定**（`snapshot_settings`）：把當下可讀的設定拍成一張快照存進單一
  SharedPreferences slot——媒體／鈴聲／鬧鐘／通知音量（`AudioManager.getStreamVolume`，存成百分比）、
  響鈴模式（`AudioManager.ringerMode`）、勿擾／中斷過濾（`NotificationManager.currentInterruptionFilter`，
  只有取得勿擾模式存取權才記）、螢幕亮度（`Settings.System.SCREEN_BRIGHTNESS`，存成百分比）與亮度模式
  （手動／自動）。讀不到或沒授權的欄位以 null 表示「沒擷取」，並在紀錄註明略過了哪些。
- **回復設定**（`restore_settings`）：把快照重新套回去，逐項沿用既有設定動作的同一套機制與權限降級
  （`doMediaVolume` / `doRingerMode` / `doDnd` / `doBrightness`）。勿擾／亮度缺對應權限時略過該項並註明，
  絕不崩潰；尚未有任何快照時整個動作記為失敗。

- 讀寫快照的儲存集中在小工具物件 `engine/SettingsSnapshot`（read-all／write-all against SharedPreferences），
  兩個 `doXxx` handler 因此保持精簡。

## Non-goals

- 不改動 routine 模型、不新增反向觸發或「離開時自動還原」的常駐機制（那是另一條路）。
- 自動旋轉等未列入本次快照範圍的設定。
- 多張具名快照（單一 slot，後記蓋前記即可滿足「記住 → 回復」的直覺）。

## Impact

- 受影響 specs：routine-actions（+2 動作）
- 受影響程式碼：model（+2 Action `data object`）、engine（RoutineExecutor 新增
  doSnapshotSettings／doRestoreSettings＋describe 分派；新檔 SettingsSnapshot）、
  ui（ActionEditor 說明、UiLabels 標籤、theme/Color 裝置橘家族 +2 色、BlockPalette「裝置」組 +2 積木）
