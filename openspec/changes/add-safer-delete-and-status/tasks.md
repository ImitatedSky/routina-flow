# Tasks: add-safer-delete-and-status

## 1. 安全刪除

- [x] 1.1 EditScreen 頂部列：移除垃圾桶 IconButton，改為 ⋯ IconButton + DropdownMenu；「刪除例行程序」紅字項觸發既有確認對話框；新建流程不顯示
- [x] 1.2 確認刪除流程與排程/服務清理（沿用既有 RoutineManager.delete）不變

## 2. 卡片狀態

- [x] 2.1 nextRunSummary(routine)：定時（含日出日落 mode/offset/星期）用 AlarmScheduler 計算並格式化「今天/明天/週X HH:mm」；事件型回觸發摘要；停用不顯示
- [x] 2.2 lastRunSummary：取該 routine 最近一筆 RunLog，判定成功/部分失敗 + 相對時間格式化（純函式）
- [x] 2.3 missingPermissions(context, routine)：依觸發/動作彙整未授與權限（背景定位/通知存取/使用情況/精確鬧鐘/勿擾/相機/麥克風/通知/寫入設定/上層顯示），各含 label + 導向 Intent
- [x] 2.4 HomeScreen 卡片：名稱下 FlowRow 狀態晶片列（下次執行、上次結果、最多 1–2 權限晶片，點擊引導）；語意色；停用淡化；不爆版

## 3. 驗證與提交

- [x] 3.1 assembleDebug + assembleRelease 綠燈；release APK 回報大小；versionName 0.9.0 / versionCode 9
- [x] 3.2 README 更新
- [x] 3.3 兩個 commit（安全刪除 / 卡片狀態，英文、無 co-author）
