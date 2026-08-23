# Change: add-share-action

## Why

使用者要求分享功能：「可以分享連結給某人」。對應 iOS 捷徑的「分享」動作——
執行時跳出系統分享選單，把設定好的連結／文字傳給使用者選定的對象或 App
（LINE、訊息、Email、記事…）。

與剛加入的「手動 routine」天然搭配：做一個手動捷徑「分享今日連結給群組」，點 ▶ 即分享。

## What Changes

- **新增「分享」動作**：把設定的文字（可為連結或任意文字）透過 Android 系統分享選單
  （`ACTION_SEND` + chooser）送出，由使用者當下選擇要分享到哪個 App／給誰
- 沿用既有背景啟動 Activity 的誠實降級：
  - **手動執行 / App 在前景**：直接跳出分享選單
  - **背景觸發**：系統禁止背景啟動 Activity → 改發可點擊通知，點通知才跳出分享選單，
    動作結果誠實註明（沿用「開啟 App／網址」既有的 launchOrNotify 模式）
- 歸「通知與 App」藍家族；積木參數欄顯示要分享的文字（過長截斷）
- versionName 0.11.0 / versionCode 11

## Non-goals

- 分享圖片／檔案（先做文字／連結；分享剛拍的照片可作為後續延伸——需接 content URI）
- 指定固定分享對象、直接送出不跳選單（Android 不保證可略過 chooser；維持使用者選擇）
- 分享例行程序本身給其他 Routina 使用者（routine 匯出/匯入為另一主題，若需要另議）

## Impact

- 受影響 specs：routine-actions（新增分享動作）
- 受影響程式碼：model（Action.Share）、engine\RoutineExecutor（ACTION_SEND + chooser +
  背景通知降級，重用既有 launchOrNotify）、ui（調色盤藍組、參數編輯多行文字、積木摘要）
- 序列化只新增；舊資料相容；無新依賴
- **相依順序**：與 add-manual-routines 同動 Models/調色盤/RoutineExecutor，故排在其後實作
