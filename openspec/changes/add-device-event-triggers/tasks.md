# Tasks: add-device-event-triggers

## 1. 資料模型

- [x] 1.1 Trigger 新增 ScreenUnlocked / ScreenOn / ScreenOff / DeviceBoot /
  HeadsetPlugged / HeadsetUnplugged / BatteryFull（皆 data object、@SerialName、舊 JSON 相容）
- [x] 1.2 needsMonitor 納入螢幕 ×3、耳機 ×2、充電完成（DeviceBoot 走 BootReceiver，不列入）

## 2. 引擎

- [x] 2.1 MonitorService：ScreenReceiver 加 ACTION_USER_PRESENT 並分派螢幕開/關/解鎖
- [x] 2.2 MonitorService：新增 HeadsetReceiver（ACTION_HEADSET_PLUG），吞 sticky 初值後才分派插拔
- [x] 2.3 MonitorService：handleBatteryChanged 加充電完成邊緣判定（滿觸發一次、拔電重置、啟動吞初值）
- [x] 2.4 BootReceiver：ACTION_BOOT_COMPLETED 另分派 DeviceBoot（goAsync 模式，來源 SYSTEM）

## 3. UI

- [x] 3.1 triggerTypeName / triggerSummary 補 7 種標籤文字
- [x] 3.2 triggerColor + 色表：螢幕/開機入系統與應用藍灰、充電完成入電源綠、耳機入連線青（組內色階）
- [x] 3.3 TriggerBlock 補 7 種純標籤渲染（無參數欄）
- [x] 3.4 調色盤 TRIGGER_GROUPS：充電完成入電源、耳機入連線、螢幕/開機入系統與應用

## 4. 驗證與提交

- [x] 4.1 assembleDebug 綠燈
- [x] 4.2 英文 commit、無 co-author
