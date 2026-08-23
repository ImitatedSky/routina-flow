# Design: add-manual-routines

## 核心心智模型（彈性化）

一個例行程序 = **名稱 +（可選觸發）+ 動作**。

- **不設觸發** → 純手動捷徑，點 ▶ 執行（例：開網站、開 App）
- **設了觸發** → 自動化，同時仍可隨時點 ▶ 手動執行
- 觸發可**隨時切換**：把手動帽子積木換成定時/區域…即變自動化；換回「手動執行」即去自動化
- 手動執行對**所有** routine 永遠可用（不受啟用狀態影響）——這點目前已成立，維持

以「手動執行」作為一種觸發（sentinel），而非把 trigger 改為 nullable：對既有大量
`when(trigger)` 與非空假設破壞最小，且積木隱喻維持（永遠有一塊帽子積木，只是它說「手動」）。

## 資料模型（向後相容，只新增）

```kotlin
@Serializable @SerialName("manual") data object Manual : Trigger()
```

- `Trigger.Manual`：代表「無自動觸發」
- **新建空白 routine 的預設觸發改為 `Manual`**（原為 Time）——使用者要純捷徑時零負擔；
  範本（早晨/就寢…）維持各自的真實觸發不變
- `Trigger.isConfigured`：Manual → true（無參數要設）

## 引擎：Manual 視為永不自動觸發

逐一確認 Manual 不進入任何自動路徑（多數因 `when` 不匹配自然成立，仍明確處理）：
- `AlarmScheduler`：Manual 非 Time → 不排程、nextTriggerTime 回 null；schedule/cancel 對 Manual 為 no-op
- `GeofenceManager`：Manual 非 Location* → 不註冊
- `MonitorService.needsMonitor` / hasMonitoredRoutines：Manual 不列入 → 不啟動監測
- `BtAclReceiver` / `NotificationListener` / `AppUsageWatcher` / `NfcDispatch`：Manual 匹配不到 → 不觸發
- `RoutineManager.save/setEnabled/delete`：對 Manual，排程/監測同步呼叫皆 no-op（安全）

## UI

### 觸發調色盤
- 最前新增獨立「手動」分組，僅一項「手動執行」（中性色，說明「只用 ▶ 執行，不自動觸發」）
- 其餘 5 組（時間/電源/連線/系統與應用/位置）不變

### 帽子積木（Manual）
- 顯示「手動執行」＋次要說明「點 ▶ 執行」；中性色 `#4E5560`；無白色參數欄
- 點本體 → 開觸發調色盤（可切換成任何自動觸發，或維持手動）

### 首頁卡片（Manual）
- **隱藏啟用開關**（沒有可排程的東西）；改以明確的 ▶「執行」為主要操作
- 狀態晶片：不顯示「下次執行」；顯示中性「手動」晶片；有紀錄則顯示上次結果
- 非 Manual routine 卡片維持現狀（開關 + 狀態晶片）

### 編輯畫面
- canSave 不變（名稱 + 至少一動作；觸發永遠存在，Manual 也算）
- 手動 routine 儲存後即可用；啟用狀態對 Manual 無意義（存 enabled=true 即可，UI 不顯示開關）

## nextRunSummary / triggerSummary
- `triggerSummary(Manual)` = 「手動執行」
- `nextRunSummary(Manual)` = null（卡片不顯示排程晶片）

## 版本
versionName 0.10.0 / versionCode 10；無新依賴
