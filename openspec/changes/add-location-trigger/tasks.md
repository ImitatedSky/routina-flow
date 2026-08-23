# Tasks: add-location-trigger

## 1. 依賴與宣告

- [x] 1.1 build.gradle.kts 加 play-services-location 21.3.0、osmdroid-android 6.1.20；versionName 0.2.0
- [x] 1.2 Manifest：INTERNET、ACCESS_FINE_LOCATION、ACCESS_COARSE_LOCATION、ACCESS_BACKGROUND_LOCATION、BLUETOOTH_CONNECT、BLUETOOTH/BLUETOOTH_ADMIN（maxSdk 30）；GeofenceReceiver 宣告
- [x] 1.3 proguard：osmdroid / play-services 若需規則則補（先實測 release）
      → 實測 release 不需要額外規則：play-services 的 consumer proguard 規則（SafeParcelable /
      ReflectedParcelable / KeepName / DynamiteApi）由 AGP 自動套用；osmdroid 無反射需求，
      R8 以 vertical class merging 收斂 tilesource 繼承鏈，資源縮減也正確保留了它實際引用的
      sharp_add_black_36 / sharp_remove_black_36。只補了 GeofenceReceiver 的 -keep（比照既有
      AlarmReceiver / BootReceiver 的寫法）。

## 2. 資料模型

- [x] 2.1 Trigger 新增 LocationEnter / LocationExit（lat、lng、radiusM、label），@SerialName 沿用多型格式，舊 JSON 相容
- [x] 2.2 Action 新增 Bluetooth(enable)

## 3. 引擎

- [x] 3.1 GeofenceManager：GMS 可用性檢查、背景位置權限檢查、syncAll()（依啟用中 routine 註冊/移除 geofence，id = routine.id）
- [x] 3.2 GeofenceReceiver：解析 GeofencingEvent、ENTER/EXIT 對應觸發、RoutineExecutor 執行（persistBlocking = true）、觸發來源標記
- [x] 3.3 RoutineManager save/setEnabled/delete 與 BootReceiver 接上 GeofenceManager.syncAll
- [x] 3.4 RoutineExecutor 藍牙動作：API ≤32 直接切換 + 權限檢查；API 33+ 通知降級（開→ACTION_REQUEST_ENABLE、關→藍牙設定頁）
- [x] 3.5 RunLog 觸發來源新增區域類型

## 4. UI

- [x] 4.1 色表擴充：進入區域 #00ACC1、離開區域 #5E35B1、藍牙 #1565C0；帽子/動作積木標籤與參數欄
- [x] 4.2 調色盤：觸發 sheet 加 2 塊帽子積木、動作 sheet 加 1 塊藍牙積木
- [x] 4.3 MapPickerDialog：osmdroid MapView、中央準星、半徑滑桿 100–1000m 圓形預覽、使用目前位置鈕、回填參數欄
- [x] 4.4 權限流程：前景位置 runtime 請求 → 背景位置引導卡（ON_RESUME 重查、package URI 設定頁）；首頁警示卡（啟用中區域 routine 缺權限時）
- [x] 4.5 無 GMS 時的不可用標示（調色盤該積木禁用 + 說明）

## 5. 驗證與提交

- [x] 5.1 assembleDebug + assembleRelease 綠燈；確認 release 序列化/geofence/osmdroid 正常（mapping 檢查新 sealed 子類保留）
- [x] 5.2 release APK ≤ 8MB，回報實際大小 → **1.64 MB**（1,715,196 bytes）
- [x] 5.3 README 權限表與功能說明更新
- [x] 5.4 分階段 commit（英文、無 co-author）
