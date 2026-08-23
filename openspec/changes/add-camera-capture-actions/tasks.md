# Tasks: add-camera-capture-actions

## 1. 依賴與宣告

- [x] 1.1 build.gradle.kts 加 CameraX（camera-core/camera-camera2/camera-lifecycle 最新穩定 1.4.x）；versionName 0.6.0 / versionCode 6
- [x] 1.2 Manifest：CAMERA、RECORD_AUDIO、FOREGROUND_SERVICE_CAMERA、FOREGROUND_SERVICE_MICROPHONE、SET_ALARM、WRITE_EXTERNAL_STORAGE(maxSdk 28)；ExecutionService foregroundServiceType 增列 camera|microphone

## 2. 資料模型

- [x] 2.1 Action 新增 TakePhoto/BurstPhoto/RecordAudio/PlaySound/SetAlarm（@SerialName 新增、舊 JSON 相容）

## 3. 引擎

- [x] 3.1 CameraCapture helper：CameraX ImageCapture 無預覽（服務內 LifecycleRegistry）、鏡頭選擇、存 MediaStore Pictures/Routina；連拍迴圈 + 間隔 delay；無硬體/失敗記錯
- [x] 3.2 ExecutionService：拍照/錄音前以 camera/microphone 類型 startForeground；背景啟動被擋 → 記失敗註明；權限檢查與引導
- [x] 3.3 錄音：MediaRecorder 錄 N 秒存 MediaStore/app 目錄
- [x] 3.4 播放音效：RingtoneManager；設定鬧鐘：AlarmClock intent（SKIP_UI、背景走既有 Activity 啟動處理）
- [x] 3.5 RoutineExecutor 串接上述 5 動作，失敗不中斷後續

## 4. UI

- [x] 4.1 色表：新增「媒體與擷取」紫組（拍照/連拍/錄音）、設定鬧鐘入藍組、播放音效入紅組
- [x] 4.2 調色盤新增分組與積木；參數編輯器（鏡頭 segmented、張數/間隔/秒數滑桿、音效選單、鬧鐘時間+標籤）
- [x] 4.3 權限：加入拍照/連拍請求 CAMERA、錄音請求 RECORD_AUDIO；HomeScreen 引導卡（相機/麥克風未授權且有此類動作時）
- [x] 4.4 積木參數欄摘要文案

## 5. 驗證與提交

- [x] 5.1 assembleDebug + assembleRelease 綠燈；mapping 檢查新 sealed 子類/serializer 保留、CameraX 若需 proguard 則補（先實測 release）
- [x] 5.2 release APK 回報大小（≤ 8MB）
- [x] 5.3 README 更新（新動作、權限表、對照矩陣）
- [x] 5.4 分階段 commit（依賴+model / 引擎 / UI，英文、無 co-author）
