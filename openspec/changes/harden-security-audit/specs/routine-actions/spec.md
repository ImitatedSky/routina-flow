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
