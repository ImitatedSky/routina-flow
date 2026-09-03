# Tasks: add-snapshot-restore

## 1. 模型

- [x] 1.1 Action.SnapshotSettings（@SerialName("snapshot_settings")、data object、無欄位）
- [x] 1.2 Action.RestoreSettings（@SerialName("restore_settings")、data object、無欄位）

## 2. 引擎

- [x] 2.1 engine/SettingsSnapshot：Snapshot 資料類 + capture（讀系統存進 SharedPreferences）+ load（讀回）
- [x] 2.2 RoutineExecutor.doSnapshotSettings：呼叫 SettingsSnapshot.capture，回報略過欄位
- [x] 2.3 RoutineExecutor.doRestoreSettings：逐項沿用 doMediaVolume／doRingerMode／doDnd／doBrightness，缺權限略過並註明；無快照時失敗
- [x] 2.4 runAction 分派 + describe 加兩個動作

## 3. UI

- [x] 3.1 UiLabels：actionTypeName／actionBlockLabel／actionParamText 加兩個動作
- [x] 3.2 theme/Color：裝置橘家族 +2 色、actionColor 加兩個動作
- [x] 3.3 BlockPalette「裝置」組加兩塊積木
- [x] 3.4 ActionEditor：兩個無參數動作顯示說明 Text；isActionValid 皆恆為 true

## 4. 驗證與提交

- [x] 4.1 assembleDebug 綠燈（BUILD SUCCESSFUL）
- [x] 4.2 一個 commit（英文、無 co-author）
