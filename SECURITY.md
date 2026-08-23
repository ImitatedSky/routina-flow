# 安全性說明（Security）

Routina 是純本地執行、無帳號、無後端的 Android App。本文件說明本專案的資安加固措施、
APK 簽章的現況與風險，以及要對外正式散佈時該如何以「keystore 永不進版控」的方式簽章。

## 一、資安加固項目摘要

近期一輪資安稽核後套用的加固（對應原始碼變更）：

- **關閉備份外洩**：`android:allowBackup="false"`，並附上 `data_extraction_rules.xml`
  （API 31+ 的雲端備份與裝置轉移）與 `backup_rules.xml`（API ≤30 的 Auto Backup），
  兩者皆全面排除 App 資料。App 的 `routines.json` / `logs.json` 可能含 webhook token、
  剪貼簿／分享／朗讀全文、地理座標、藍牙 MAC、NFC UID，不應被雲端／adb 備份或裝置轉移帶出。
- **禁止明文流量**：新增 `network_security_config.xml`，`cleartextTrafficPermitted="false"`
  且信任錨點只含 system CA（不含 user CA），在所有 Android 版本（含 API 26/27）一致封鎖
  明文 HTTP。**HTTP 請求動作因此必須使用 https**；地圖圖磚（osmdroid MAPNIK）本就走 https。
  若需打區網 http 裝置（例如自架 webhook），請自行在該設定檔為該網域加上
  `<domain-config cleartextTrafficPermitted="true">` 例外。
- **NFC 抗偽造 intent**：NFC 派送一律以硬體 `EXTRA_TAG` 的 UID 為準，intent 沒有真實 Tag
  時不採信，擋掉本機其他 App 送出「只有 `routina://tag/{uid}` data URI、無真 NFC」的假 intent
  來觸發使用者的例行程序。
- **收斂 FileProvider 分享範圍**：`file_paths.xml` 只開放實際存檔的
  `Pictures/Routina/` 與 `Music/Routina/` 子目錄，而非整個 `Pictures/` / `Music/`。
- **RunLog 遮罩敏感值**：執行紀錄不再整段落地剪貼簿／分享／朗讀文字（截斷顯示），
  HTTP 動作只記去除 query string 後的網址（token 常藏在 query），降低 `logs.json` 明文敏感度。

## 二、APK 簽章現況與風險（重要）

- 本專案的 release APK **預設沿用 debug keystore 簽章**（當未提供正式 keystore 時的 fallback）。
- debug 簽章 **僅供個人側載（sideload）使用**，**不具備任何身分保證**：任何人都能用同一把
  公開的 debug key 簽出同名 App，**無法用簽章驗證發布者身分，也不適合上架商店或大規模散佈**。
- 側載安裝時 Android 13+ 會以「受限制的設定」鎖住部分高風險權限，屬正常機制（見 README 疑難排解）。

## 三、要正式對外散佈時：以 CI 簽章，keystore 永不進 repo

以下步驟讓你在**不把 keystore 或密碼放進版本控制**的前提下，用自己的正式金鑰簽章。
`keystore.properties`、`*.jks/*.keystore/*.p12/*.pfx/*.pem/*.key` 皆已在 `.gitignore` 排除。

### 1. 產生正式 keystore（本機，一次性）

```bash
keytool -genkeypair -v \
  -keystore routina-release.jks \
  -alias routina \
  -keyalg RSA -keysize 2048 -validity 10000
```

依提示設定 keystore 密碼、金鑰密碼與憑證資訊。**把 `routina-release.jks` 與密碼離線妥善保管，
切勿 commit 進 repo。**

### 2A. 本機簽章：用 keystore.properties（不進版控）

在專案根目錄建立 `keystore.properties`（已被 `.gitignore` 排除）：

```properties
storeFile=/絕對路徑/routina-release.jks
storePassword=你的keystore密碼
keyAlias=routina
keyPassword=你的金鑰密碼
```

`./gradlew assembleRelease` 便會自動改用此正式簽章；檔案不存在時仍 fallback 為 debug 簽章。

### 2B. CI 簽章：設定 GitHub Secrets（走 Actions）

把 keystore 轉成 base64 字串（避免二進位進 repo）：

```bash
# macOS / Linux
base64 -w0 routina-release.jks   # macOS 用 base64 -i routina-release.jks
```

到 GitHub 專案 **Settings → Secrets and variables → Actions** 新增：

| Secret 名稱 | 內容 |
|-------------|------|
| `KEYSTORE_BASE64` | 上一步輸出的 base64 字串 |
| `KEYSTORE_PASSWORD` | keystore 密碼 |
| `KEY_ALIAS` | 金鑰別名（例如 `routina`） |
| `KEY_PASSWORD` | 金鑰密碼 |

`.github/workflows/build-apk.yml` 會在偵測到 `KEYSTORE_BASE64` 時，把它解碼成暫存
keystore、以 env 傳給 Gradle 走正式簽章，並在 build 完成（或失敗）後刪除該檔；
**未設定這些 secret 時則照舊以 debug 簽章 build，CI 不會因此失敗**。

> keystore 本體與所有密碼都只存在於你的本機與 GitHub Secrets，**永遠不會進入 repo**。

## 四、回報安全問題

這是個人專案，若發現安全疑慮，請於本專案的 issue 說明（避免公開貼出可利用的完整細節）。
