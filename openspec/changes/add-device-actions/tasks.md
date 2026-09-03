# Tasks: add-device-actions

## 1. 模型

- [x] 1.1 `Action.AutoRotate(on)`（`auto_rotate`）、`Action.ScreenTimeout(seconds, secondsExpr)`（`screen_timeout`）
- [x] 1.2 `Action.Dial(number)`（`dial`）、`Action.SendSms(number, message)`（`send_sms`）
- [x] 1.3 `Action.GetLocation(variableName, format)`（`get_location`）＋ `LocationFormat` enum
- [x] 1.4 `Action.WifiToggle(on)`（`wifi_toggle`）
- [x] 1.5 companion 加 `SCREEN_TIMEOUT_SAFE = 15..1800`；欄位皆有預設，舊 JSON 相容

## 2. 執行

- [x] 2.1 `doAutoRotate` / `doScreenTimeout`：`canWriteSettings` 檢查 + `notifyWriteSettingsNeeded` 引導通知（同 `doBrightness`）；逾時秒數經 `resolveNum` 夾範圍後換算毫秒
- [x] 2.2 `doDial` / `doSendSms`：`ACTION_DIAL` / `ACTION_SENDTO` 經 `launchOrNotify` 啟動（背景改發可點擊通知）；號碼空白記為失敗
- [x] 2.3 `doGetLocation`：位置權限檢查 → `getCurrentLocation(PRIORITY_BALANCED_POWER_ACCURACY)` 經 `suspendCancellableCoroutine` 橋接 → `withTimeoutOrNull(15s)`；null／逾時 `error(...)`；6 位小數寫入 `ctx.vars`、回傳 `名稱 = 值`
- [x] 2.4 `doWifiToggle`：`Settings.Panel.ACTION_WIFI`（API 29+，之前退回 `ACTION_WIFI_SETTINGS`）經 `launchOrNotify` 啟動；回傳需自行切換的說明（比照 `doBluetooth` 誠實降級）
- [x] 2.5 dispatch `when`、`describe` 補六個分支；`resolveAction` 解析 `number`／`message`（`variableName`／`on` 為字面值不解析）

## 3. UI

- [x] 3.1 調色盤：自動旋轉／螢幕逾時／Wi-Fi／取得目前位置 → 「裝置」組；撥號／傳簡訊 → 「通知與 App」組
- [x] 3.2 Color：裝置橘家族加四階、通知藍家族加兩階，`actionColor` 補分支，數量註解更新為 38 種動作
- [x] 3.3 ActionEditor：六個編輯器分支（`OnOffChips`／`NumericVarField`／`VariableTextField`／`ChipRow`）、`isActionValid`（撥號與簡訊要號碼、取得位置要變數名、Wi-Fi 無必填）、`availableTokens` 收錄 `GetLocation` 的變數；「修改系統設定」提示抽成共用的 `WriteSettingsNotice`，新增 `LocationPermissionNotice`
- [x] 3.4 UiLabels：`actionTypeName`／`actionBlockLabel`／`actionParamText`（逾時用 `numParam`）＋ `locationFormatName`

## 4. 驗證與提交

- [x] 4.1 `assembleDebug` 綠燈
- [x] 4.2 一個 commit（英文、無 co-author）
