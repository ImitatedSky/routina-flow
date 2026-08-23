# Tasks: add-starter-templates

## 1. 範本定義

- [x] 1.1 RoutineTemplates.kt：6 個範本（早晨/就寢/到公司/回家/省電/空白），build() 用既有 Trigger/Action 組出新 Routine（新 id、enabled=false）、needsSetup 標記、家族色圖示
- [x] 1.2 套用＝進入 EditScreen 新建模式並帶入草稿；needsSetup 者靠既有「未設定不可儲存」+ 引導補參數（區域選點/SSID）

## 2. UI 入口

- [x] 2.1 空狀態：改為「從一個範本開始」+ 2 欄範本格（色塊圖示+標題+一行說明+空白格）
- [x] 2.2 非空狀態新增入口：FAB 提供「從範本建立 / 空白建立」（短按範本格 sheet 或小選單，維持一鍵可達）
- [x] 2.3 範本格色用觸發家族色，文案繁中口語

## 3. 驗證與提交

- [x] 3.1 assembleDebug + assembleRelease 綠燈；release APK 回報大小
- [x] 3.2 README 更新（範本說明）
- [x] 3.3 一個 commit（英文、無 co-author）
