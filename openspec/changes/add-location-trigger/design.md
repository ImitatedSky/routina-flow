# Design: add-location-trigger

## 技術選型

| 面向 | 選擇 | 理由 |
|------|------|------|
| 地理圍欄 | Play Services `GeofencingClient` | 系統級、低耗電、Tasker/MacroDroid 同路線；自建 GPS 輪詢耗電且違反輕量原則 |
| 地圖 | osmdroid 6.1.x（OpenStreetMap） | 免 API key、可離線快取；Google Maps SDK 需要使用者自備金鑰，不適合 APK 直接下載散佈 |
| 藍牙 | API ≤32 `BluetoothAdapter.enable()/disable()`；API 33+ 通知帶 `ACTION_REQUEST_ENABLE` | Android 13 起系統禁止第三方直接切換，誠實降級 |

## 資料模型（向後相容）

```kotlin
// Trigger 新增（@SerialName 沿用既有多型格式，舊 JSON 不受影響）
@Serializable @SerialName("location_enter")
data class LocationEnter(val lat: Double, val lng: Double, val radiusM: Int, val label: String = "") : Trigger()
@Serializable @SerialName("location_exit")
data class LocationExit(val lat: Double, val lng: Double, val radiusM: Int, val label: String = "") : Trigger()

// Action 新增
@Serializable @SerialName("bluetooth")
data class Bluetooth(val enable: Boolean) : Action()
```

## 地理圍欄引擎

```
engine/GeofenceManager.kt   # 依啟用中的區域觸發 routine 註冊/移除 geofence
engine/GeofenceReceiver.kt  # PendingIntent broadcast 入口：解析 GeofencingEvent → RoutineExecutor
```

- Geofence id = routine.id；ENTER/EXIT transition 對應兩種觸發
- 註冊時機與 AlarmScheduler 相同步：save / setEnabled / delete / BootReceiver 時呼叫
  `GeofenceManager.syncAll()`（geofence 在重開機後會被系統清除，必須重註冊）
- `initialTrigger` 設 0（不做初始觸發，避免建立當下人在區域內就立刻執行）
- 無 GMS（`GoogleApiAvailability` 檢查失敗）或未授權背景位置：routine 卡片顯示警示標記，
  不註冊、不觸發
- responsiveness 用預設值；半徑下限 100m（Geofencing 官方建議最小值）

## 權限流程（EditScreen 選了區域觸發時逐步引導）

1. `ACCESS_FINE_LOCATION`：runtime 對話框（含 `ACCESS_COARSE_LOCATION` 一併宣告）
2. `ACCESS_BACKGROUND_LOCATION`：Android 10 可 runtime 請求；Android 11+ 只能導去
   App 設定頁選「一律允許」→ 用引導卡（比照 overlay 權限卡的既有模式，ON_RESUME 重查）
3. 首頁引導卡：存在已啟用的區域觸發 routine 但缺背景位置權限時顯示

## 地圖選點 UI

- EditScreen 點區域參數欄 → 全螢幕 `MapPickerDialog`：
  - osmdroid `MapView`（Mapnik 圖資、pinch 縮放）、中央固定準星 pin、
    半徑滑桿（100–1000m，步進 50m）、圓形範圍 overlay 即時重繪
  - 「使用目前位置」鈕（`FusedLocationProviderClient.lastLocation`，需已授權前景位置）
  - 確認後回填 lat/lng/radius，參數欄顯示「25.0330, 121.5654 ±300m」（有 label 則顯示 label）
- osmdroid 需要 INTERNET 權限（圖磚下載）與 tile cache 設定（用 app 私有目錄，
  `Configuration.getInstance().userAgentValue = BuildConfig.APPLICATION_ID`）

## 藍牙動作

- Manifest：`BLUETOOTH_CONNECT`（API 31+ runtime）、`BLUETOOTH_ADMIN`（≤30 install-time）、
  `BLUETOOTH`（≤30）
- API ≤32：`adapter.enable()` / `disable()`（缺 BLUETOOTH_CONNECT 時記失敗 + 引導通知）
- API 33+：開啟 → 高優先度通知帶 `ACTION_REQUEST_ENABLE` content intent（沿用 H1 的
  通知 fallback 模式與 CHANNEL_LAUNCH）；關閉 → 通知導向藍牙設定頁；
  ActionResult 描述註明「Android 13+ 需經系統確認」

## 色彩擴充（積木/色表）

| 類型 | 顏色 |
|------|------|
| 觸發：進入區域 | 青藍 `#00ACC1` |
| 觸發：離開區域 | 深紫 `#5E35B1` |
| 動作：藍牙 | 深藍 `#1565C0` |

## 版本

- play-services-location 21.3.0、osmdroid-android 6.1.20
- versionName 升到 0.2.0
