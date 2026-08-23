# Tasks: add-variables（Wave 1）

## 1. 情境與解析

- [x] 1.1 RunContext（vars / lastOutput / trigger map），一次執行建立一個、貫穿所有動作
- [x] 1.2 VariableResolver.resolve(template, ctx)：解析 `{{result}}` / `{{var:名稱}}` / `{{觸發key}}` / 常用（時間/日期/星期/電量…）；查無代空字串；非合法 token 原樣保留；無 token 原樣回傳
- [x] 1.3 各觸發啟動執行時填入 trigger map（通知標題/內容/來源、電量、Wi-Fi名稱、地點名稱、NFC標籤名稱；全部含時間/日期/星期）

## 2. 動作與執行

- [x] 2.1 model 新增 Action.Text(template)、Action.SetVariable(name, template)（@SerialName 新增、舊 JSON 相容）
- [x] 2.2 RoutineExecutor：執行前對文字類參數（Notify/Share/Http/Speak/Clipboard/SetAlarm 訊息等）套 resolve；有輸出的動作（Http 回應、拍照/錄音 URI、Text）寫入 lastOutput；SetVariable 寫 vars
- [x] 2.3 ExecutionService.runOne 與手動 runNow 建立並傳遞 RunContext；RunLog 敏感遮罩維持

## 3. UI

- [x] 3.1 文字參數編輯器加「插入變數」：分類列出可用 token（觸發提供／上一個結果／已設定變數／常用），點選插入游標處
- [x] 3.2 調色盤新增「變數」分組，含「文字」「設定變數」積木；積木參數欄顯示原始 template

## 4. 驗證與提交

- [x] 4.1 assembleDebug + assembleRelease 綠燈；mapping 檢查新動作/serializer 保留；versionName 0.14.0 / versionCode 14
- [x] 4.2 舊 JSON 相容驗證（純文字動作行為不變）
- [x] 4.3 README / docs 更新（變數用法）
- [x] 4.4 分階段 commit（model+engine / UI，英文、無 co-author）
