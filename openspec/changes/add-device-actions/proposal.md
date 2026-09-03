# Change: add-device-actions

## Why

裝置家族目前只能調亮度、震動、手電筒、勿擾與藍牙，缺了兩個最常被用到的螢幕設定
（自動旋轉、螢幕逾時）；通訊面則完全空白——想做「到公司自動傳訊息說我到了」
只能繞路用開啟網址。另外流程常需要「現在人在哪」的座標當變數（填進 webhook、
拼進訊息），但目前只有區域觸發用得到位置，動作端拿不到。

## What Changes

- **裝置（橘）家族新增三塊積木**：
  - **自動旋轉 `AutoRotate`**（`auto_rotate`）：寫 `Settings.System.ACCELEROMETER_ROTATION`。
  - **螢幕逾時 `ScreenTimeout`**（`screen_timeout`）：寫 `Settings.System.SCREEN_OFF_TIMEOUT`，
    以秒輸入（`SCREEN_TIMEOUT_SAFE` 15–1800）換算成毫秒；沿用 `secondsExpr` 的值/運算式模式。
  - **取得目前位置 `GetLocation`**（`get_location`）：以 `FusedLocationProviderClient`
    取一次座標，依 `LocationFormat`（緯度,經度／緯度／經度）存進具名變數。
- **通知與 App（藍）家族新增兩塊積木**：
  - **撥號 `Dial`**（`dial`）：`ACTION_DIAL` + `tel:`，開撥號畫面帶入號碼，不自動撥出。
  - **傳簡訊 `SendSms`**（`send_sms`）：`ACTION_SENDTO` + `smsto:` + `sms_body`，
    開簡訊 App 預填收件人與內容，由使用者自己按送出。
- **權限沿用既有路徑，不新增任何 manifest 權限**：自動旋轉／螢幕逾時與螢幕亮度共用
  WRITE_SETTINGS 檢查與授權引導通知（編輯器的提示也抽成共用的 `WriteSettingsNotice`）；
  取得目前位置沿用 `GeofenceManager.hasForegroundLocation`。
- **誠實降級**：撥號／傳簡訊走既有的 `launchOrNotify`——前景直接開，背景被系統禁止啟動
  Activity 時改發可點擊通知；裝置沒有撥號／簡訊 App 時記為失敗。取得目前位置以
  `withTimeoutOrNull(15s)` 包住，逾時或定位關閉記為失敗，絕不拿舊快取位置冒充當下位置。

## Non-goals

- 直接以 `SEND_SMS` 發簡訊、以 `CALL_PHONE` 直接撥出——都需要 Play 政策嚴格審核的敏感權限，
  且不該替使用者無聲送出訊息／撥打電話
- 持續定位追蹤、地址反查（geocoding）——只取一次座標
- 更多螢幕設定（字型大小、深色模式等）：多為無公開 API 或需系統簽章

## Impact

- 受影響 specs：routine-actions（新增五個動作）
- 受影響程式碼：model（五個 `Action` 子類 + `LocationFormat` + `SCREEN_TIMEOUT_SAFE`）、
  engine（`RoutineExecutor` 的 dispatch／`describe`／`resolveAction`／五個 `doXxx`
  與 `Task` → coroutine 的位置橋接）、ui（調色盤、ActionEditor 編輯器＋驗證＋可插入變數、
  UiLabels、Color）
- 無新相依：`play-services-location` 已在專案中；沒有 `kotlinx-coroutines-play-services`，
  因此以 `suspendCancellableCoroutine` + `addOnSuccess/Failure/CanceledListener` 自行橋接
- 序列化只新增、欄位皆有預設；舊 `routines.json` 照常讀入
