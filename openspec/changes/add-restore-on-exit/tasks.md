# 任務

- [x] `Routine.restoreOnExit`（預設 false，舊資料相容）
- [x] `Trigger.opposite`：反向觸發對照表（沒有結束概念的回 null）
- [x] `engine/RestoreOnExit.kt`：快照（響鈴／勿擾／四個音量／亮度／自動旋轉）、還原、
      SharedPreferences 落地、`onEvent` 統一入口
- [x] `RoutineExecutor`：抽出 `applyRingerMode` / `applyStreamVolume` / `applyDnd` /
      `applyBrightness` 供動作與還原共用
- [x] `RoutineExecutor.execute`：非手動觸發且開了 `restoreOnExit` → 動作開跑前拍快照
- [x] 分派點各加一行 `RestoreOnExit.onEvent`：`MonitorService.runMatching`、`BtAclReceiver`、
      `AppUsageWatcher.fire`、`GeofenceReceiver`
- [x] `GeofenceManager`：開了還原的區域程序一併註冊反向轉換
- [x] `EditScreen`：「觸發限制」對話框的「離開時還原設定」開關（只在有反向觸發時顯示）
- [x] `contentEquals` 納入 `restoreOnExit`
- [ ] 自訂「離開時的動作清單」（後續）
- [ ] `docs/GUIDE.md` 補說明
