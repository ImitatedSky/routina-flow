# routine-actions — 藍牙動作

## ADDED Requirements

### Requirement: 藍牙動作
系統 SHALL 支援「藍牙」動作：設定為開啟或關閉。Android 12（含）以下直接切換；Android 13 起系統禁止第三方 App 直接切換，SHALL 改以可點擊通知帶出系統確認對話框（開啟）或導向藍牙設定頁（關閉），並在動作結果描述中註明，不得記為假成功或崩潰。

#### Scenario: Android 12 以下直接切換
- **WHEN** 動作在 Android 12（含）以下執行且已具備藍牙權限
- **THEN** 藍牙被直接開啟或關閉，動作記為成功

#### Scenario: Android 13 以上降級
- **WHEN** 動作在 Android 13 以上執行
- **THEN** 發出高優先度通知：開啟時點擊帶出系統藍牙開啟確認對話框、關閉時導向藍牙設定頁；動作結果描述註明需經系統確認

#### Scenario: 缺少藍牙權限
- **WHEN** Android 12/12L 上未授權 BLUETOOTH_CONNECT
- **THEN** 動作記為失敗並發出引導授權的通知，後續動作照常執行
