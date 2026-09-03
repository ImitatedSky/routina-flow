# Tasks: add-run-from-outside

## 1. 共用執行邏輯

- [x] 1.1 RunFromOutside：SharedPreferences 磚 id 的唯一存取點（set/get/name）、進程層級 scope
      + run(routineId)（MANUAL、Toast 回饋、null/已刪除只提示不崩潰）、pinShortcut 建立釘選捷徑

## 2. 入口

- [x] 2.1 RunRoutineActivity：透明跳板，讀 routineId extra → RunFromOutside.run → 立刻 finish；
      companion 提供帶 action 的捷徑 intent（requestPinShortcut 要求 action）
- [x] 2.2 RunRoutineTileService：onStartListening 更新標籤為程序名稱（退回 Routina）、
      onClick 直接執行（避開 startActivityAndCollapse 的版本差異）

## 3. UI 與宣告

- [x] 3.1 EditScreen ⋯ 選單新增「設為快速設定磚」（寫入 id + requestListeningState 更新標籤）
      與「加到桌面」（requestPinShortcut，不支援時 Toast）；僅編輯既有程序時顯示
- [x] 3.2 Manifest：RunRoutineTileService（BIND_QUICK_SETTINGS_TILE + QS_TILE intent-filter +
      label/icon）、RunRoutineActivity（exported、透明主題、noHistory/excludeFromRecents）

## 4. 驗證與提交

- [x] 4.1 assembleDebug 綠燈
- [x] 4.2 英文 commit、無 co-author
