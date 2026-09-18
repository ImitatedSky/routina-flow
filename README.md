<h1 align="center">Routina</h1>

<p align="center"><b>Android 版的極簡「捷徑 / 日常程式」</b></p>

<p align="center">
  一個觸發條件 × 一連串動作，把每天重複的手機操作自動化。<br />
  不像 Tasker、MacroDroid 把功能全攤開——只做一件事：三十秒內建立一個一眼看得懂的例行程序。
</p>

<p align="center">
  <img alt="Android" src="https://img.shields.io/badge/Android-8.0%2B-3ddc84?logo=android&logoColor=white" />
  <img alt="minSdk" src="https://img.shields.io/badge/minSdk-26-1e88e5" />
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.0-7f52ff?logo=kotlin&logoColor=white" />
  <img alt="Compose" src="https://img.shields.io/badge/Jetpack_Compose-Material_3-4285f4" />
  <img alt="APK" src="https://img.shields.io/badge/APK-~2.2_MB-43a047" />
</p>

<p align="center"><b>本地優先 · 無帳號 · 無後端 · 無廣告 · 無追蹤</b></p>

<p align="center">
  <a href="https://github.com/ImitatedSky/routina/releases/latest"><b>⬇ 下載 APK</b></a>
  &nbsp;·&nbsp;
  <a href="docs/GUIDE.md"><b>📖 使用說明</b></a>
</p>

---

## 亮點

- **捷徑與自動化並存** — 不設觸發就是純手動捷徑（點 ▶ 開網站 / App，不排程、不耗電）；設了觸發就自動執行，兩者隨時切換
- **17 種自動觸發 ＋ 手動 × 21 種動作** — 涵蓋 iOS 捷徑在 Android 上可行的自動化
- **圓角色塊介面** — 觸發與動作都是「彩色圓角塊 + 文字說明 + 白色參數欄」，功能分組配色、拖曳排序，看得懂就會用
- **範本啟動** — 早晨 / 就寢 / 到公司 / 回家 / 省電，一鍵建立可跑的例行程序
- **匯出 / 匯入備份** — 所有程序寫成一個 JSON 檔，換機或改壞了都能挑幾支還原（設定 → 備份）
- **輕量純本地** — Release APK 約 2.2 MB、Android 8.0+ 可裝、繁體中文、無帳號無後端無遙測

## 功能總覽

- **觸發**：定時（含日出日落）、充電、電量、Wi-Fi、藍牙、飛航 / 勿擾 / 省電、NFC 標籤、收到通知、App 開啟關閉、進出區域，或「手動執行」
- **動作**：通知、開 App / 網址、HTTP、設定鬧鐘、分享、音量 / 響鈴 / 朗讀 / 播放控制 / 播放音效、手電筒 / 震動 / 亮度 / 勿擾 / 藍牙、拍照 / 連拍 / 錄音、等待、複製到剪貼簿

> 完整功能清單、iOS 捷徑對照、權限說明、疑難排解與技術架構，見 **[使用說明 docs/GUIDE.md](docs/GUIDE.md)**。

## 下載與安裝

1. 到 [**Releases**](https://github.com/ImitatedSky/routina/releases) 下載某個版本的 `routina-vX.Y.Z.apk`（每個 `v*` 版本標籤都由 CI 自動建置成一個**永久保留的 Release**，可依版本下載）
2. 傳到手機、用檔案管理員點開
3. 系統提示「未知來源」→ 允許安裝
4. 開啟 Routina

> APK 為 **debug 簽章、僅供個人側載**，未上架 Google Play，不具發布者身分保證。
> 正式散佈的簽章做法見 [SECURITY.md](SECURITY.md)；權限與側載限制見[使用說明](docs/GUIDE.md#權限說明)。

### 自行建置

需要 JDK 17 與 Android SDK（platform 35、build-tools 35.0.0）。

```bash
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
./gradlew assembleRelease   # 產物：app/build/outputs/apk/release/app-release.apk
```

## 貢獻

本專案採 **spec-first** 流程（OpenSpec）：功能先在 `openspec/changes/<name>/` 寫提案（proposal / design / spec / tasks）驗證後才實作，`openspec/specs/` 為現行規格的單一事實來源。送 PR 前請跑 `./gradlew assembleDebug`，commit 訊息用英文。

## 免責聲明

Routina 與 Apple、Google、Samsung 無任何關聯，「捷徑 / 日常程式」僅為功能類比。部分功能受 Android 系統限制（背景相機 / 麥克風、背景剪貼簿、藍牙 / Wi-Fi 硬體開關等），Routina 一律**誠實降級並在執行紀錄註明**，不假裝成功。

## 授權

**尚未指定授權條款**——在加入 `LICENSE` 前依著作權預設保留所有權利。若要開源，建議挑一個標準授權（GPL-3.0 / Apache-2.0 / MIT）並在根目錄加上對應的 `LICENSE`。
