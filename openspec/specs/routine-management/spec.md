# routine-management Specification

## Purpose
TBD - created by archiving change add-routina-mvp. Update Purpose after archive.
## Requirements
### Requirement: 建立例行程序
系統 SHALL 允許使用者建立例行程序，內容包含：名稱、一個觸發條件、一至多個動作。

#### Scenario: 成功建立
- **WHEN** 使用者在編輯畫面輸入名稱、選擇觸發條件、加入至少一個動作並儲存
- **THEN** 例行程序被保存至本地儲存，並出現在清單畫面，預設為啟用狀態

#### Scenario: 缺少必要欄位
- **WHEN** 使用者未輸入名稱或未加入任何動作即嘗試儲存
- **THEN** 儲存按鈕不可用或顯示提示，不寫入任何資料

### Requirement: 清單顯示與顏色區隔
清單畫面 SHALL 以卡片列出所有例行程序，每張卡片以觸發類型專屬顏色作視覺區隔，並顯示名稱、觸發摘要、動作數量與啟用開關。

#### Scenario: 檢視清單
- **WHEN** 使用者開啟 App
- **THEN** 顯示所有例行程序卡片，卡片帶有觸發類型的顏色標記（色條或圖示底色），觸發摘要與動作以彩色標籤呈現

#### Scenario: 空清單
- **WHEN** 尚無任何例行程序
- **THEN** 顯示空狀態說明文字與建立引導

### Requirement: 啟用 / 停用
系統 SHALL 允許透過清單上的開關即時啟用或停用例行程序；停用者不得被任何觸發條件執行。

#### Scenario: 停用後不觸發
- **WHEN** 使用者將某例行程序切換為停用
- **THEN** 其排程鬧鐘被取消／監測條件被移除，觸發事件發生時不執行該程序

### Requirement: 編輯與刪除
系統 SHALL 允許編輯既有例行程序的所有欄位，以及刪除例行程序。

#### Scenario: 刪除
- **WHEN** 使用者在編輯畫面點擊刪除並確認
- **THEN** 該例行程序自儲存中移除、相關排程取消，返回清單畫面

### Requirement: 手動執行
系統 SHALL 在清單卡片上提供「立即執行」按鈕，不受啟用狀態與觸發條件限制，方便使用者測試。

#### Scenario: 手動執行
- **WHEN** 使用者點擊某例行程序的「立即執行」
- **THEN** 該程序的所有動作依序執行，並寫入一筆執行紀錄（來源標記為手動）

