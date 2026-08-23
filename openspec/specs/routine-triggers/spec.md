# routine-triggers Specification

## Purpose
TBD - created by archiving change add-routina-mvp. Update Purpose after archive.
## Requirements
### Requirement: 定時觸發
系統 SHALL 支援「每日定時」觸發：使用者設定時間（HH:mm）與星期幾（可複選，預設每天），到達指定時間且當日符合星期條件時執行。

#### Scenario: 準時觸發
- **WHEN** 已啟用的定時例行程序到達設定時間，且今日在所選星期內
- **THEN** 該程序的動作被執行，並自動排程下一次符合條件的時間

#### Scenario: 未授權精確鬧鐘
- **WHEN** Android 12+ 上使用者未授權「鬧鐘與提醒」
- **THEN** App 顯示引導前往系統設定的提示，並以非精確排程（時間窗）降級運作
- **NOTE** App 已宣告 `USE_EXACT_ALARM`（鬧鐘是核心功能，Android 13+ 安裝時即自動授予），
  因此本情境僅適用於 Android 12/12L（API 31–32）使用者手動撤銷「鬧鐘與提醒」授權時

### Requirement: 充電狀態觸發
系統 SHALL 支援「開始充電」與「停止充電」兩種觸發：裝置接上或拔除電源時執行對應程序。

#### Scenario: 接上電源
- **WHEN** 存在已啟用的「開始充電」例行程序，且裝置接上電源
- **THEN** 該程序的動作被執行

#### Scenario: 監測服務生命週期
- **WHEN** 至少一個充電或電量類觸發的例行程序處於啟用狀態
- **THEN** 前景監測服務保持運行；當全部停用或刪除時，服務自動停止

### Requirement: 電量門檻觸發
系統 SHALL 支援「電量低於 X%」觸發：電量降至門檻（含）以下時執行一次，回升至門檻以上後重置、可再次觸發。

#### Scenario: 穿越門檻只觸發一次
- **WHEN** 電量從 21% 降至 20%（門檻 20%），隨後續降至 15%
- **THEN** 程序僅在穿越當下執行一次，15% 時不重複執行

### Requirement: 開機恢復排程
系統 SHALL 在裝置重新開機後，自動恢復所有已啟用例行程序的定時排程與監測服務。

#### Scenario: 重開機
- **WHEN** 裝置重新開機完成（BOOT_COMPLETED）
- **THEN** 所有已啟用的定時程序重新排入鬧鐘；若有充電/電量觸發程序，監測服務重新啟動

