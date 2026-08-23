# Tasks: add-drag-reorder-actions

## 1. 依賴

- [x] 1.1 加入 sh.calvin.reorderable 最新穩定 2.x（驗證與 Compose BOM 2024.12.01 相容；不相容則退回手刻並於回報說明）；versionName 0.8.0 / versionCode 8

## 2. UI

- [x] 2.1 ActionBlock：右側控制由 ↑↓✕ 改為 ≡（拖曳握把）+ ✕；接受拖曳修飾符參數；點本體＝編輯、長按＝拿起
- [x] 2.2 EditScreen：動作清單改用 ReorderableColumn（帽子/幽靈積木在外）；onMove 重排 draft.actions；rememberSaveable 草稿機制不回退
- [x] 2.3 拖曳視覺：拿起浮起（shadow/scale + zIndex）、其餘讓位、拿起 haptic
- [x] 2.4 compact 唯讀預覽（首頁）不受影響，維持原樣

## 3. 驗證與提交

- [x] 3.1 assembleDebug + assembleRelease 綠燈；release APK 回報大小（≤ 8MB）
- [x] 3.2 README 更新（操作說明：拖曳排序）
- [x] 3.3 一個 commit（英文、無 co-author）
