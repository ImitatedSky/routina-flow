# Design: add-shortcuts-parity-wave1

## 觸發實作對應

| 觸發 | 機制 | 常駐成本 |
|---|---|---|
| 藍牙裝置連接/斷開 | `BluetoothDevice.ACTION_ACL_CONNECTED / DISCONNECTED`——屬於隱式廣播豁免清單，**manifest 靜態 receiver**，不需常駐服務 | 零 |
| Wi-Fi 連線/斷線 | `ConnectivityManager.registerNetworkCallback`（TRANSPORT_WIFI）於 MonitorService；SSID 取得需 FINE location（已有）；斷線 = onLost | MonitorService |
| 電量高於 | 併入既有 BATTERY_CHANGED 監聽，向上穿越觸發一次、回落重置（鏡像既有低於邏輯） | MonitorService |
| 飛航模式切換 | `Intent.ACTION_AIRPLANE_MODE_CHANGED` 動態 receiver（參數：開/關） | MonitorService |
| 勿擾模式切換 | `NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED` 動態 receiver（開 = filter != ALL） | MonitorService |
| 省電模式切換 | `PowerManager.ACTION_POWER_SAVE_MODE_CHANGED` 動態 receiver | MonitorService |
| 日出/日落 | 定時觸發加 `mode`（固定時間/日出/日落）+ 偏移分鐘；用 routine 內或最後已知位置以 NOAA 太陽方程式本地計算（±1–2 分鐘可接受），無網路、無新依賴；每次觸發後重算下一次 | 零（AlarmManager） |

- 藍牙觸發參數：裝置（從已配對清單 `BluetoothAdapter.bondedDevices` 選，或「任一裝置」）。
- Wi-Fi 觸發參數：SSID（文字輸入或「任一網路」）；Android 9+ 取 SSID 需定位權限與定位開啟，
  未授權時觸發降級為「任一網路」並在卡片提示。
- MonitorService 的啟動條件擴充為：存在任一「充電/電量/Wi-Fi/飛航/勿擾/省電」類啟用中 routine。

## 資料模型（向後相容，只新增）

```kotlin
// Trigger 新增
@SerialName("bt_connected")    data class BtConnected(val deviceAddress: String = "", val deviceName: String = "")   // 空 = 任一
@SerialName("bt_disconnected") data class BtDisconnected(val deviceAddress: String = "", val deviceName: String = "")
@SerialName("wifi_connected")  data class WifiConnected(val ssid: String = "")   // 空 = 任一
@SerialName("wifi_disconnected") data object WifiDisconnected
@SerialName("battery_above")   data class BatteryAbove(val threshold: Int = 80)
@SerialName("airplane_mode")   data class AirplaneMode(val turnedOn: Boolean = true)
@SerialName("dnd_changed")     data class DndChanged(val turnedOn: Boolean = true)
@SerialName("power_save")      data class PowerSave(val turnedOn: Boolean = true)
// Trigger.Time 新增欄位（有預設值，舊 JSON 相容）：
//   mode: TimeMode = FIXED (FIXED/SUNRISE/SUNSET)、offsetMinutes: Int = 0

// Action 新增
@SerialName("flashlight")  data class Flashlight(val on: Boolean = true)
@SerialName("speak")       data class Speak(val text: String = "")
@SerialName("vibrate")     data class Vibrate(val millis: Int = 500)      // 100–3000
@SerialName("dnd")         data class Dnd(val on: Boolean = true)
@SerialName("brightness")  data class Brightness(val percent: Int = 50)
@SerialName("http")        data class Http(val url: String = "", val method: String = "GET", val body: String = "")
@SerialName("media_key")   data class MediaKey(val key: String = "PLAY_PAUSE") // PLAY_PAUSE/NEXT/PREVIOUS
@SerialName("wait")        data class Wait(val seconds: Int = 3)          // 1–30
// Action.MediaVolume 新增欄位 stream: VolumeStream = MEDIA (MEDIA/RING/ALARM/NOTIFICATION)
```

## ExecutionService（執行架構升級）

- 短生命週期前景服務（specialUse，沿用 MonitorService 的通知 channel 模式）：
  `AlarmReceiver / GeofenceReceiver / MonitorService / 靜態藍牙 receiver` 一律改為
  `ExecutionService.start(context, routineId, source)`，服務內依序執行動作後 `stopSelf()`。
- 解除 receiver 10 秒限制：等待（最長 30s×多個）、TTS 初始化、HTTP 都安全。
- 服務內仍沿用 RoutineExecutor（改為 suspend 友善），`persistBlocking` 機制可簡化——
  服務存活期間用非阻塞寫入即可。
- 手動執行（UI）維持現行路徑不變。
- 背景啟動前景服務的豁免：BOOT_COMPLETED 與各 exempt broadcast 已涵蓋；
  啟動失敗（Android 12+ 背景限制極端情況）fallback：直接在 receiver 內同步執行但跳過等待動作並記錄。

## 動作實作要點

| 動作 | API | 權限/降級 |
|---|---|---|
| 手電筒 | `CameraManager.setTorchMode`（取第一個有 flash 的鏡頭） | 無權限；無手電筒硬體 → 記失敗 |
| 朗讀文字 | `TextToSpeech`（單例惰性初始化，語言跟系統） | 無 |
| 震動 | `VibratorManager`(31+)/`Vibrator`，`VibrationEffect.createOneShot` | 無 |
| 勿擾 | `setInterruptionFilter(PRIORITY 或 ALL)` | 沿用既有 DND access 檢查+引導 |
| 螢幕亮度 | `Settings.System.putInt(SCREEN_BRIGHTNESS, 0–255)`；先關自動亮度旗標？——不動自動亮度，僅寫亮度值 | `Settings.System.canWrite` 檢查，未授權 → 引導通知到 `ACTION_MANAGE_WRITE_SETTINGS`（沿用引導卡模式） |
| HTTP 請求 | `HttpURLConnection`，GET/POST，body 為 text/plain 或 JSON 原文，逾時 10s，回應丟棄只記狀態碼 | INTERNET 已有；失敗記錄狀態碼/例外 |
| 播放控制 | `AudioManager.dispatchMediaKeyEvent`（down+up 成對） | 無 |
| 等待 | `delay(seconds * 1000L)`，僅於 ExecutionService/手動路徑有效 | 無 |
| 音量（擴充） | `AudioManager.setStreamVolume(stream, ...)`；RING/NOTIFICATION 在 DND 時可能需 DND access → 失敗走既有引導 | 沿用 |

## 色彩（新積木；文字說明為主、顏色輔助）

觸發：藍牙連接 `#0277BD`、藍牙斷開 `#546E7A`、Wi-Fi 連線 `#00838F`、Wi-Fi 斷線 `#90A4AE`、
電量高於 `#7CB342`、飛航模式 `#A1887F`、勿擾切換 `#7E57C2`、省電模式 `#F9A825`（深色文字）
動作：手電筒 `#F57F17`、朗讀 `#00897B` 系（沿用青綠家族 `#00796B`）、震動 `#5E35B1`、
勿擾 `#455A64`、亮度 `#FBC02D`（深色文字）、HTTP `#3949AB` 系（`#283593`）、
播放控制 `#D81B60` 系（`#AD1457`）、等待 `#757575`

淺色系積木（省電/亮度）文字用 `#333`，其餘白字；一切以文字標籤為主要辨識（使用者回饋）。

## UI

- 調色盤變長：觸發/動作 sheet 均加分類小標（「電源」「連線」「裝置」「媒體」…），維持圓角色塊
- 參數編輯器新增：配對藍牙裝置選單、SSID 輸入、開/關 segmented、亮度/震動/等待滑桿、
  HTTP（URL + method + body）、播放控制選單、音量串流選單、定時模式（固定/日出/日落 + 偏移）
- HomeScreen 權限引導卡新增：WRITE_SETTINGS（存在亮度動作時）

## 版本

versionName 0.3.0（versionCode 3）
