# Change: harden-security-audit

## Why

轉 public 前做了一輪對抗性安全稽核(CI/供應鏈、Android 對外元件/IPC、變數注入面)。
結論:**無 CRITICAL / HIGH 可直接遠端利用**(fork PR 預設拿不到 secret、無 script injection、
未用 pull_request_target;對外元件不是被 OS 的 protected-broadcast 擋住、就是已有防偽)。
以下把「一旦轉 public / 有協作者會咬人」的最小權限問題與幾個縱深防禦點修掉。

## What Changes

CI / 供應鏈(build-apk.yml):
- 頂層 `permissions: contents: read`;只有 tag 發版的 `release` job 提升 `contents: write`。
- 拆 `build`(push/PR/dispatch,unsigned 編譯檢查,**完全不碰簽章 secret**)與
  `release`(只在 `v*` tag,綁 `environment: release`,唯一動用 keystore 的地方)。
- release 缺 KEYSTORE_BASE64 時直接失敗,不再默默發 debug 簽章的 release。
- 第三方 Action 全部釘死 commit SHA(Dependabot 維護);加 `concurrency`。

Android 對外攻擊面:
- `BtAclReceiver`、`BootReceiver` 由 `exported="true"` 改 `false`(縱深防禦;
  兩者只收 protected-broadcast,系統仍會送達非匯出的靜態接收器,功能不變)。
- 「開啟網址」動作擋掉危險 scheme(file/content/javascript/data/intent/android-app),
  防止網址來自變數(如 `{{通知內容}}`,由其他 App 可控)時被當作繞道啟動/檔案外洩的跳板。
- NFC 派送 Activity 除了既有的 `EXTRA_TAG` 檢查,再加一層只接受真正 NFC dispatch action 的閘門。

## Non-goals(需使用者處理或另議)

- 擷取媒體(拍照/連拍/錄音)目前存到**公開**相簿/音樂目錄(其他 App 可讀)——屬產品/隱私取捨,另行決定。
- git 作者信箱 `yehforcode@gmail.com` 轉 public 會公開(使用者刻意選的 GitHub 身分,預設保留)。
- repo 設定類加固(Environment 必要審核者、分支/tag 保護、fork PR 需核准)須在 GitHub UI 設定。

## Impact

- specs:routine-actions(開啟網址 scheme 安全)、routine-triggers(NFC 派送閘門、接收器不匯出)
- 程式碼:.github/workflows/build-apk.yml、AndroidManifest.xml、engine/RoutineExecutor.kt、engine/NfcDispatchActivity.kt
- 併入 v0.18.0;無新依賴;boot/藍牙/NFC 觸發需實機再驗(改動不影響編譯與一般執行,已 BlueStacks 冒煙測試啟動正常)
