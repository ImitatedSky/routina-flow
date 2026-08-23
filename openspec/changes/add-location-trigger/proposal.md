# Change: add-location-trigger

## Why

使用者回饋：想要地圖相關的自動化——「設定一個區域，到附近就觸發某些事」，
例如接近公司時跳訊息（既有的顯示通知動作）、回到家附近自動開藍牙。
這是 Bixby 日常程式 / 捷徑的招牌情境，也是目前觸發類型最大的缺口（MVP 時列為 non-goal，現在補上）。

## What Changes

- **新增 2 種觸發**：進入區域、離開區域——中心點（經緯度）+ 半徑（100m–1000m）
- **地圖選點**：內建 OpenStreetMap 地圖（osmdroid，免 API key）——點地圖設中心、
  滑桿調半徑（圓形範圍即時預覽）、「使用目前位置」快捷鈕
- **地理圍欄引擎**：Google Play Services `GeofencingClient`（系統級監測、低耗電，
  不用自己常駐輪詢 GPS）；裝置無 GMS 時該觸發標示為不可用
- **新增 1 種動作**：開啟／關閉藍牙
  - Android 12 以下：直接切換（BLUETOOTH_CONNECT / ADMIN）
  - Android 13+：系統已禁止 App 直接切換 → 發可點擊通知，帶出系統的藍牙開啟確認對話框
- **權限流程**：精確位置（runtime 對話框）→ 背景位置（Android 11+ 必須到設定選
  「一律允許」，App 內引導卡導向設定頁）
- 積木 UI 同步：2 塊新帽子積木、1 塊新動作積木、專屬顏色，調色盤自動納入

## Non-goals（本次不做）

- 自訂多邊形區域（只做圓形）
- 停留時間條件（dwell）
- 無 GMS 裝置的自建定位輪詢 fallback（耗電，違反輕量原則）
- Wi-Fi 開關（系統限制不變，仍做不到）

## Impact

- 受影響 specs：routine-triggers（新增區域觸發）、routine-actions（新增藍牙動作）
- 受影響程式碼：model/Models.kt（新 sealed 子類，JSON 向後相容——舊資料照常讀）、
  engine/（新增 GeofenceManager + GeofenceReceiver、RoutineManager 同步、藍牙執行）、
  ui/（地圖選點、調色盤與色表擴充）、Manifest（位置與藍牙權限）
- **新增依賴 2 個**：play-services-location、osmdroid——release APK 預估 +2~3MB，
  體積目標由 ≤5MB 放寬為 **≤8MB**
