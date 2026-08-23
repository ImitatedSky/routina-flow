# Tasks: add-shortcuts-parity-wave1

## 1. 資料模型

- [x] 1.1 Trigger 新增 8 型（BtConnected/BtDisconnected/WifiConnected/WifiDisconnected/BatteryAbove/AirplaneMode/DndChanged/PowerSave），Time 加 mode/offsetMinutes（預設值向後相容）
- [x] 1.2 Action 新增 8 型（Flashlight/Speak/Vibrate/Dnd/Brightness/Http/MediaKey/Wait），MediaVolume 加 stream（預設 MEDIA 向後相容）
- [x] 1.3 Manifest：BLUETOOTH_CONNECT 已有；加 VIBRATE、WRITE_SETTINGS（uses-permission）、靜態藍牙 ACL receiver、ExecutionService 宣告

## 2. 引擎

- [x] 2.1 ExecutionService：短生命週期前景服務（specialUse），佇列化執行 routine、完成自動停止；AlarmReceiver/GeofenceReceiver/MonitorService/藍牙 receiver 改走 ExecutionService.start()，啟動失敗降級（同步執行、跳過等待）
- [x] 2.2 BtAclReceiver（manifest 靜態）：ACL_CONNECTED/DISCONNECTED → 比對裝置位址（空=任一）→ 執行
- [x] 2.3 MonitorService 擴充：NetworkCallback（Wi-Fi 連/斷 + SSID 比對與定位權限檢查）、AIRPLANE_MODE / INTERRUPTION_FILTER / POWER_SAVE 動態 receiver、電量高於（向上穿越+重置，鏡像既有邏輯）；啟動條件涵蓋新類型
- [x] 2.4 SunCalc：NOAA 太陽方程式（純函式，日出/日落 ±偏移），AlarmScheduler 支援 Time.mode（無位置→預設 06:00/18:00）
- [x] 2.5 RoutineExecutor 新動作：手電筒、TTS（單例、逾時 30s）、震動、勿擾、亮度（canWrite 檢查+引導）、HTTP（HttpURLConnection、10s 逾時、記狀態碼）、播放控制（媒體按鍵）、等待（僅服務/手動路徑）；音量 stream 擴充

## 3. UI

- [x] 3.1 色表擴充（依 design.md；省電/亮度淺色塊用深色文字）；新積木標籤與參數欄文案
- [x] 3.2 調色盤加分類小標，納入全部新觸發/動作
- [x] 3.3 參數編輯器：配對藍牙裝置選單（BLUETOOTH_CONNECT runtime 請求）、SSID 輸入、開/關選擇、亮度/震動/等待滑桿、HTTP 表單（URL/method/body）、播放控制選單、音量串流選單、定時模式選擇（固定/日出/日落+偏移滑桿）
- [x] 3.4 卡片警示：指定 SSID 但無定位權限；HomeScreen 引導卡：WRITE_SETTINGS（存在亮度動作時）

## 4. 驗證與提交

- [x] 4.1 assembleDebug + assembleRelease 綠燈；mapping 檢查全部新 sealed 子類與 serializer 保留
- [x] 4.2 release APK 回報大小（目標仍 ≤ 8MB）；versionName 0.3.0 / versionCode 3 — 實測 1.67 MB
- [x] 4.3 README 功能表與權限表更新（含對照矩陣連結或摘要）
- [x] 4.4 分階段 commit（model/engine/UI，英文、無 co-author）
