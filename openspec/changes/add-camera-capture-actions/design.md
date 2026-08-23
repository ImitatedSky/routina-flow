# Design: add-camera-capture-actions

## 資料模型（向後相容，只新增）

```kotlin
@SerialName("take_photo")   data class TakePhoto(val lensBack: Boolean = true) : Action()
@SerialName("burst_photo")  data class BurstPhoto(val lensBack: Boolean = true, val count: Int = 3, val intervalMs: Int = 500) : Action()
@SerialName("record_audio") data class RecordAudio(val seconds: Int = 5) : Action()
@SerialName("play_sound")   data class PlaySound(val type: String = "NOTIFICATION") : Action() // NOTIFICATION/ALARM/RINGTONE
@SerialName("set_alarm")    data class SetAlarm(val hour: Int = 8, val minute: Int = 0, val label: String = "") : Action()
```

## 相機（CameraX，無預覽）

- 依賴：`androidx.camera:camera-core / camera-camera2 / camera-lifecycle`，最新穩定 1.4.x
- 擷取：`ImageCapture` use case 綁定一個服務內建立的 `LifecycleOwner`（`LifecycleRegistry`），
  `CameraSelector` 依 lensBack 選前/後鏡頭；`takePicture` 到 `MediaStore`（`Pictures/Routina`，
  API 29+ 免權限；API 28 需 `WRITE_EXTERNAL_STORAGE` maxSdk 28）
- **連拍**：同一次相機綁定內，迴圈 `takePicture` × count，每張間隔 intervalMs（在 ExecutionService
  的 coroutine 中 delay，故連拍/等待需要前景服務存活——本來就是）
- 擷取後解除綁定、釋放相機
- 無相機硬體 / 開啟失敗：記失敗（原因），不崩潰

## 錄音（MediaRecorder）

- `MediaRecorder`（AAC/mp4）錄 seconds 秒 → 存 `MediaStore` Music/Recordings（API 31+）或
  app 專屬外部目錄（附路徑於紀錄）；`RECORD_AUDIO` 權限
- 於 ExecutionService coroutine 內 `start` → `delay(seconds*1000)` → `stop/release`

## 前景服務類型（關鍵）

- ExecutionService 既有 `specialUse`；本波在 Manifest 增列 `camera` 與 `microphone` 類型
  （`android:foregroundServiceType="specialUse|camera|microphone"`）
- 權限：`CAMERA`、`RECORD_AUDIO`、`FOREGROUND_SERVICE_CAMERA`、`FOREGROUND_SERVICE_MICROPHONE`
  （API 34+）；`WRITE_EXTERNAL_STORAGE`（maxSdk 28）
- 執行拍照/錄音前，ExecutionService 以對應類型呼叫 `startForeground`（若尚未含該類型則補上）；
  背景啟動被 Android 14+ 擋下時 → 該動作記失敗註明「背景無法啟動相機/麥克風，請改用手動或前景觸發」
- 權限未授與時：動作記失敗 + 引導通知（相機/麥克風設定）；編輯器加入該動作時 runtime 請求

## 播放音效（RingtoneManager）

- `RingtoneManager.getRingtone(context, getDefaultUri(type))`.play()；type 對應
  TYPE_NOTIFICATION / TYPE_ALARM / TYPE_RINGTONE；無權限需求；歸「聲音」紅家族

## 設定鬧鐘（AlarmClock intent）

- `Intent(AlarmClock.ACTION_SET_ALARM)` 帶 EXTRA_HOUR/MINUTE/MESSAGE/SKIP_UI（SKIP_UI=true 免使用者
  確認，多數時鐘 App 支援）；`SET_ALARM` 權限（normal，自動授與）；沿用背景 Activity 啟動的
  既有處理（前景直接送、背景走通知 fallback），歸「通知與 App」藍家族

## 色彩（沿用功能分組色系）

| 組 | 新增積木 → 色 |
|---|---|
| 通知與 App（藍） | 設定鬧鐘 `#42A5F5`（HTTP #1E88E5 的下一淺階） |
| 聲音（紅） | 播放音效 `#EF5350`（播放控制 #E53935 的下一淺階） |
| 媒體與擷取（紫，**新組**） | 拍照 `#6A1B9A`、連拍 `#8E24AA`、錄音 `#AB47BC` |

（紫家族在動作命名空間尚未使用；觸發的位置組雖用紫，但觸發/動作為不同清單，不衝突。）

## UI

- 調色盤新增「媒體與擷取」分組（拍照/連拍/錄音）；設定鬧鐘入「通知與 App」、播放音效入「聲音」
- 參數編輯器：
  - 拍照：前/後鏡頭 segmented
  - 連拍：鏡頭 segmented + 張數滑桿(2–10) + 間隔滑桿(200–2000ms)
  - 錄音：秒數滑桿(1–60)
  - 播放音效：通知音/鬧鐘聲/鈴聲 選單
  - 設定鬧鐘：時間選擇器 + 標籤欄
- 積木參數欄摘要：「拍照 [後鏡頭]」「連拍 [3 張]」「錄音 [5 秒]」「播放 [通知音]」「鬧鐘 [08:00]」
- 加入拍照/連拍時請求 CAMERA、錄音時請求 RECORD_AUDIO；HomeScreen 對應引導卡（未授權且有此類動作時）

## 版本

versionName 0.6.0 / versionCode 6；新增 CameraX 依賴，其餘不變
