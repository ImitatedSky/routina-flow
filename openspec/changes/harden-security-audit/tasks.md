# Tasks: harden-security-audit

## 1. CI / 供應鏈
- [x] 1.1 頂層 permissions: contents: read;release job 才 contents: write
- [x] 1.2 拆 build(unsigned,無 secret)/ release(v* tag,environment: release,唯一用 keystore)
- [x] 1.3 release 缺 KEYSTORE_BASE64 直接 exit 1(不發未簽章 release)
- [x] 1.4 第三方 Action 釘死 commit SHA;加 concurrency

## 2. Android 對外攻擊面
- [x] 2.1 BtAclReceiver / BootReceiver 改 exported=false
- [x] 2.2 「開啟網址」擋危險 scheme(file/content/javascript/data/intent/android-app)
- [x] 2.3 NfcDispatchActivity 加 NFC action 閘門(EXTRA_TAG 檢查之外再一層)
- [x] 2.4 擷取媒體(M1)改隱私預設:拍照/連拍/錄音預設存 App 私有外部目錄(FileProvider 供檢視),
  新增每動作「存到公開相簿/音樂」開關(預設關);model 加 shareToGallery=false(舊資料相容,
  行為變更為更私密);file_paths.xml 加私有 Pictures 路徑

## 3. 驗證
- [x] 3.1 assembleRelease 綠燈;BlueStacks 冒煙測試啟動正常、資料保留
- [ ] 3.2 實機(Pixel)再驗:開機自啟、藍牙連接/斷開觸發、NFC 觸發仍正常

## 4. 待使用者處理(GitHub UI / 決策,程式碼碰不到)
- [ ] 4.1 建立 Environment `release` + 必要審核者 + 部署 tag 限 v*;KEYSTORE_* 移到該 Environment
- [ ] 4.2 main 分支保護、v* tag 保護、fork PR 需核准、預設 workflow 權限設 read
- [x] 4.3 擷取媒體儲存位置決策 → 使用者選「App 私有 + 每動作可選公開」,已實作(見 2.4)
- [ ] 4.4 是否重寫 git 歷史換掉作者信箱(破壞性,需明確同意 + 備份)
