# routine-triggers — 對外元件加固

## ADDED Requirements

### Requirement: NFC 派送只接受真正的 NFC action
NFC 派送 Activity 為匯出元件,SHALL 只在 intent 的 action 屬於系統 NFC dispatch(TECH_DISCOVERED / NDEF_DISCOVERED / TAG_DISCOVERED)時才進入 UID 比對與派送;其餘 action 一律忽略。此為既有「必須帶真正 EXTRA_TAG」檢查之外的第二層。

#### Scenario: 任意 action 的顯式 intent 被忽略
- **WHEN** 其他 App 以非 NFC action 的顯式 intent 啟動該 Activity
- **THEN** 不做任何比對、不執行任何例行程序

### Requirement: 系統廣播接收器不對外匯出
只接收受保護系統廣播的靜態接收器(藍牙 ACL 連接/斷開、開機/套件更新/時間變更)SHALL 設為 `exported=false`。這些 action 是 protected-broadcast,系統仍會送達非匯出的靜態接收器,故功能不變,但杜絕外部 App 以顯式廣播假觸發的可能。

#### Scenario: 外部 App 無法假觸發
- **WHEN** 其他 App 嘗試對這些接收器發送對應 action 的廣播
- **THEN** 系統以 protected-broadcast 拒絕,接收器不會被外部觸發
