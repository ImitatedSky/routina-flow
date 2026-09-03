# 離開時還原（條件結束時把裝置設定放回去）

## 目的

Samsung「情境模式」與 Tasker「exit task」都有的行為：程序因為某個條件成立而改了裝置設定，
**條件結束時自動改回來**。例如「到公司 → 靜音＋亮度調低」，離開公司就該自己還原。

MVP 只做**快照 / 還原開關**（`restoreOnExit`），不做自訂的離開動作清單（留待後續）。

## 設計

### 反向觸發（`Trigger.opposite`，`model/Models.kt`）

「條件結束」＝反向觸發送達。沒有結束概念的觸發回 `null`，UI 就不提供這個選項。

| 觸發 | 反向 |
| --- | --- |
| 進入區域 ↔ 離開區域 | 同一組 lat/lng/半徑/名稱 |
| 連上 Wi-Fi ↔ Wi-Fi 斷線 | 斷線不帶參數，反向只能配到「連上任一 Wi-Fi」 |
| 藍牙連接 ↔ 藍牙斷開 | 同一台裝置（空位址＝任一台） |
| 接上電源 ↔ 拔除電源 | |
| 螢幕開啟 ↔ 螢幕關閉 | |
| 解鎖螢幕 → 螢幕關閉 | 解鎖沒有反向事件，以螢幕關閉當結束 |
| 插入耳機 ↔ 拔除耳機 | |
| 飛航／勿擾／省電（開） ↔ （關） | |
| App 開啟 ↔ App 關閉 | 同一個套件 |

其餘（定時、電量門檻、充電完成、NFC、通知、開機）→ `null`。

### 快照 / 還原（`engine/RestoreOnExit.kt`）

- **快照**：響鈴模式、勿擾開關、四個音量串流（媒體／鈴聲／鬧鐘／通知）的原始刻度、
  螢幕亮度、自動旋轉。**只記能還原的**：沒有勿擾模式存取權就不記勿擾與靜音／震動的響鈴模式，
  沒有「修改系統設定」權限就不記亮度與自動旋轉；讀不到的一律略過，不崩潰。
- **儲存**：`SharedPreferences("restore_on_exit")`，key＝routine id、value＝快照 JSON
  （kotlinx.serialization）。同步 `commit()` 落地，行程被回收或重開機都還在。
- **還原**：逐項套回 → 刪掉快照（一張快照只還原一次）→ 寫一筆 `RunLog`
  （note 為「離開時還原設定」、每個還原項目一筆 `ActionResult`）。
  寫入邏輯與動作共用 `RoutineExecutor` 的 `applyRingerMode` / `applyStreamVolume` /
  `applyDnd` / `applyBrightness`（由原本的 `doRingerMode` / `doMediaVolume` / `doDnd` /
  `doBrightness` 抽出），權限檢查、引導通知與失敗訊息只有一處。自動旋轉沒有對應動作，
  由 `RestoreOnExit` 自己寫回。
- **不是一次觸發**：還原不執行程序的動作、不經過 `RoutineExecutor.execute`，
  因此不計入 `maxRuns` / `runCount`，也不會因此自動停用程序。

### 掛載點

- **拍快照**：`RoutineExecutor.execute` 內、觸發上限閘門之後、動作開跑之前：
  `if (source != MANUAL && routine.restoreOnExit) RestoreOnExit.snapshot(...)`。
  手動測試不拍快照（不是真的進入條件）。
- **還原**：`RestoreOnExit.onEvent(context, matches)`（另有一個吃指定 routine 清單的版本）。
  各分派點只多一行，且**直接沿用它原本篩選 routine 用的比對**，差別只在比對對象換成
  `trigger.opposite`——不必為「條件結束」另寫一套比對規則。
  - `MonitorService.runMatching`（電源／Wi-Fi／螢幕／耳機／飛航／勿擾／省電，一處涵蓋全部）
  - `BtAclReceiver`（藍牙連接／斷開）
  - `AppUsageWatcher.fire`（App 開啟／關閉）
  - `GeofenceReceiver`（區域進入／離開）
  - 定時 / NFC / 通知 / 開機的觸發沒有反向事件，不必掛。

### 地理圍欄要註冊雙向

圍欄原本只註冊自己那一種轉換，「進入區域」的程序永遠收不到 EXIT。
`GeofenceManager.transitionMask()` 在 `restoreOnExit` 開啟時把反向轉換一起註冊，
反向事件由 `GeofenceReceiver` 分流成「只還原、不執行動作」。

### UI（`ui/EditScreen.kt`）

「觸發限制」對話框（⋯ 選單）底部新增開關「離開時還原設定」，
**只在 `draft.trigger.opposite != null` 時出現**，附一行說明。納入 `contentEquals`（未儲存變更提醒）。

## 非目標

- 自訂的「離開時要做什麼」動作清單（本次只做快照/還原）。
- 還原飛航模式、藍牙、Wi-Fi 開關等系統禁止第三方 App 直接寫入的設定。
- 程序因達觸發上限而被停用後仍要還原（`onEvent` 只看啟用中的程序）。
