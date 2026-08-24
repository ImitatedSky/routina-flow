# Tasks: add-view-and-variable-ux

## 1. 首頁兩種呈現方式

- [x] 1.1 HomeViewMode（LIST/GRID）+ 以 SharedPreferences 持久化，重開 App 保留
- [x] 1.2 TopAppBar 切換鈕（清單／格狀兩顆圖示，當前模式高亮）
- [x] 1.3 清單模式：一列＝名稱 + 狀態晶片 + 執行／開關，**不含積木預覽**；維持長按拖曳排序（查詢中停用）
- [x] 1.4 格狀模式：2 欄方塊卡（色塊 + 名稱 + 一行狀態 + 執行／開關）；不排序
- [x] 1.5 搜尋、狀態晶片、手動 vs 自動卡片差異在兩種模式都正確

## 2. 變數即時預覽

- [x] 2.1 engine/Variables 加 previewResolve：用範例值代換 token（時間/日期/星期/電量/通知.../var:.../result）
- [x] 2.2 VariableTextField 在含 `{{` 時於欄下顯示「預覽：…」；不含變數不顯示
- [x] 2.3 純顯示層，不改序列化與執行；舊資料零影響

## 3. 變數範例範本

- [x] 3.1 RoutineTemplates 新增「報時分享」（手動：文字組出含變數字串 → 分享 {{result}}）
- [x] 3.2 RoutineTemplates 新增「通知轉發」（收到通知 → 用 {{通知標題}}/{{通知內容}} 組訊息，needsSetup）
- [x] 3.3 範本格排版在新增後仍正確（2 欄、單數結尾補空位）

## 4. 驗證與提交

- [x] 4.1 assembleDebug + assembleRelease 綠燈；versionName 0.18.0 / versionCode 18
- [x] 4.2 BlueStacks 實測：清單／格狀切換與持久化、變數預覽、套用變數範本
- [x] 4.3 docs/GUIDE.md 更新（呈現方式、變數預覽與範例）
- [ ] 4.4 分階段 commit（英文、無 co-author），可 push
