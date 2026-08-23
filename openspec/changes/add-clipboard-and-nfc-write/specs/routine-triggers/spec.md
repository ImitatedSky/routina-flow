# routine-triggers — NFC 標籤寫入

## ADDED Requirements

### Requirement: NFC 標籤寫入
系統 SHALL 提供「寫入標籤」功能：把本 App 專屬的 NDEF URI（含標籤 UID）寫入實體標籤並同步完成登錄；此後掃描該標籤 SHALL 直接由本 App 處理，不出現系統 App 選擇器。未寫入的已登錄標籤 SHALL 維持既有行為可正常觸發。寫入失敗（唯讀、容量不足、不支援 NDEF 且不可格式化、中途移開）SHALL 顯示具體原因且不崩潰。

#### Scenario: 寫入成功
- **WHEN** 使用者在 NFC 觸發編輯器點「寫入標籤」並貼近可寫入標籤
- **THEN** 標籤被寫入專屬 URI、UID 完成登錄，顯示成功訊息；此後掃描該標籤直接執行對應程序，無選擇器

#### Scenario: 唯讀標籤
- **WHEN** 寫入時標籤為唯讀
- **THEN** 顯示「標籤唯讀」錯誤，UID 登錄仍完成（仍可用一般方式觸發）

#### Scenario: 未格式化標籤
- **WHEN** 標籤不含 NDEF 但支援格式化
- **THEN** 自動格式化並寫入成功
