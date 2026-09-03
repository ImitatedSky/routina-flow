# Change: add-device-event-triggers

## Why

常見的裝置事件（解鎖、螢幕開關、開機、插拔耳機、充電完成）是 Apple 捷徑 / Bixby
Routines 的招牌自動化情境，目前 Routina 缺這一批。它們都能沿用既有的廣播監測骨架，
不需新框架，補上即可讓「回家插耳機自動開播放清單」「開機恢復設定」這類日常用例成立。

## What Changes

新增 7 種觸發（皆無參數，data object）：

- **解鎖螢幕 / 螢幕開啟 / 螢幕關閉**：ACTION_USER_PRESENT / ACTION_SCREEN_ON /
  ACTION_SCREEN_OFF。這三個現代 Android 無法靜態註冊，由常駐的 MonitorService 動態註冊
  （沿用既有的 ScreenReceiver，本來就為 App 使用情況輪詢註冊 SCREEN_ON/OFF）。
- **開機完成**：ACTION_BOOT_COMPLETED。用既有的 manifest BootReceiver（已宣告
  RECEIVE_BOOT_COMPLETED 權限與 BOOT_COMPLETED intent-filter），開機當下除了恢復排程，
  另外分派此觸發。因此不需常駐服務，不列入 needsMonitor。
- **插入耳機 / 拔除耳機**：ACTION_HEADSET_PLUG（extra state 1/0）。此為 sticky 廣播，
  註冊時系統補送最後一次狀態，第一筆只記基準、不視為插拔，避免啟動即誤觸發。
- **充電完成**：達 100% / BatteryManager.BATTERY_STATUS_FULL。沿用電量門檻的
  邊緣 / 重置作法：在「滿」的上升邊緣觸發一次，拔除電源後重置，每次充電只觸發一次。

## Non-goals

- 不新增 TriggerSource：螢幕 / 耳機 / 開機記為 SYSTEM，充電完成記為 BATTERY。
- 不加參數（無「指定耳機類型」「特定開機模式」等）。
- 不改 AndroidManifest：螢幕 / 耳機 / 充電完成走動態註冊，開機沿用既有 BootReceiver。

## Impact

- 受影響程式碼：model（+7 觸發型別、needsMonitor）、theme 色表（+7 家族內色階）、
  ui（triggerTypeName / triggerSummary / TriggerBlock / 調色盤分組）、
  engine（MonitorService 動態註冊 + 比對 + 分派、BootReceiver 分派開機觸發）。
- 向後相容：新 sealed 變體為附加、舊 routines.json 照常讀入。
- 無新增第三方依賴；無新增權限。
