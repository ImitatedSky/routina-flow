# Tasks: add-share-action

## 1. 模型與執行

- [x] 1.1 Action.Share(text)（@SerialName("share")、舊 JSON 相容）
- [x] 1.2 RoutineExecutor：ACTION_SEND text/plain + createChooser；前景 startActivity、背景重用 launchOrNotify 通知降級；空文字記失敗；try/catch 不中斷

## 2. UI

- [x] 2.1 調色盤「通知與 App」藍組加「分享」積木（藍家族最淺階，自動深/白字）
- [x] 2.2 參數編輯器：多行文字欄；積木參數欄顯示分享文字（截斷）

## 3. 驗證與提交

- [x] 3.1 assembleDebug + assembleRelease 綠燈；mapping 檢查 Action$Share/serializer 保留；release APK 回報大小；versionName 0.11.0 / versionCode 11
- [x] 3.2 README 更新（分享動作）
- [x] 3.3 一個 commit（英文、無 co-author）
