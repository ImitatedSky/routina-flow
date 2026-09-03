# Change: add-interactive-actions

## Why

目前所有動作都「不問使用者、直接跑完」。要對齊 iOS 捷徑的「要求輸入」與「從選單選擇」，
需要能在執行途中暫停、跳出對話框問使用者，再把答案帶進後續動作（存成變數）。
例如手動捷徑「問我要去哪 → 存成 {{var:目的地}} → 開地圖導航」。

## What Changes

- **新增兩個互動動作**（歸「變數」家族，色用 `ActionSetVariable`）：
  - **詢問輸入 `AskInput`**（`ask_input`）：跳出含文字框的對話框，輸入的文字存進
    具名變數 `variableName`；`prompt`／`defaultValue` 可含 `{{...}}` token。
  - **選單選擇 `ChooseMenu`**（`choose_menu`）：跳出列出各選項按鈕的對話框，
    選中的文字存進 `variableName`；`prompt` 與每個選項都可含 token。
- **UI 需要，但動作可能在背景服務執行**：新增透明對話框樣式的 `InputPromptActivity` 當
  收件人，與執行器以行程內橋接 `InputBridge` 溝通——執行器登記請求、啟動 Activity、在
  `CompletableDeferred` 上掛起等待；Activity 回應後 `deliver` 交回結果，執行器續跑。
  低耦合：Activity 只認 requestId，完全不碰 repository／ViewModel。
- **誠實降級**：沿用「開啟 App／網址」的 `launchOrNotify`——前景直接跳對話框，背景無法
  直接啟動 Activity 時改發可點擊通知，點擊才開對話框。一律帶 120 秒等待上限，逾時／取消
  記為失敗（`使用者未回應／已取消`），流程不會永遠卡住。
- 調色盤「變數」組加兩塊積木；編輯器提供提示／變數名稱／預設值欄，選項以「一行一個」編輯；
  新變數可插入後續動作的「已設定的變數」清單。

## Non-goals

- 多欄位表單、輸入型別（數字／日期專用鍵盤）——先做純文字輸入與純文字選單
- 記住上次的回答、逾時後採用預設值——逾時一律記為失敗，語意清楚
- 在通知本身直接回覆（inline reply）——維持「點通知 → 開對話框」單一路徑

## Impact

- 受影響 specs：routine-actions（新增兩個互動動作）
- 受影響程式碼：model（`Action.AskInput`／`Action.ChooseMenu`）、engine（`InputBridge`、
  `InputPromptActivity`、`RoutineExecutor` 的 dispatch／describe／resolveAction／兩個
  `doXxx` 與等待流程）、ui（調色盤、ActionEditor 編輯器＋驗證＋可插入變數、UiLabels、Color）、
  AndroidManifest（註冊 Activity）、themes（對話框主題）、proguard（keep Activity）
- 序列化只新增、欄位皆有預設；舊 `routines.json` 照常讀入；無新依賴
