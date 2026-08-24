# routine-actions — 開啟網址安全性

## ADDED Requirements

### Requirement: 開啟網址禁止危險 scheme
「開啟網址」動作以 ACTION_VIEW 啟動前 SHALL 擋掉危險 scheme(file、content、javascript、data、intent、android-app)。因為網址可能來自變數(如 `{{通知內容}}`,而通知內容是其他 App 可控的),此檢查避免它被當成繞道啟動元件或外洩本機檔案的跳板;一般 http/https 與正當的 App deep link(tel/mailto/geo/自訂 scheme)不受影響。

#### Scenario: 擋下 file scheme
- **WHEN** 開啟網址的實際值(變數代入後)為 `file:///...`
- **THEN** 動作記為失敗且不啟動任何檢視器

#### Scenario: 正常網址與 deep link 照常
- **WHEN** 值為 `https://example.com` 或 `myapp://x`
- **THEN** 照常開啟

### Requirement: 擷取媒體預設存 App 私有空間
拍照、連拍、錄音動作 SHALL 預設把檔案存到 App 私有外部目錄(其他 App 讀不到、不進相簿與雲端備份),並以 FileProvider 產生 content URI 供結果通知檢視。每個動作 SHALL 提供「存到公開相簿／音樂資料夾」開關,預設關;開啟時才另存到系統相簿／音樂目錄。

#### Scenario: 預設私有
- **WHEN** 使用者未開啟「存到公開相簿」就執行拍照
- **THEN** 相片存到 App 私有目錄,裝置上其他 App 無法讀取

#### Scenario: 明確選擇公開
- **WHEN** 使用者開啟該動作的「存到公開相簿」開關
- **THEN** 相片另存到系統相簿,可被相簿 App 與其他有媒體權限的 App 看到
