# Tasks: add-drag-building

## 1. 首頁卡片拖曳排序（C）

- [ ] 1.1 RoutineRepository/RoutineManager：routine 順序持久化 + reorder(from,to)（沿用 list 順序，move 後儲存）
- [ ] 1.2 HomeScreen 清單改可拖曳排序（沿用 sh.calvin.reorderable，長按整卡拿起）；**查詢非空時停用拖曳**；拖曳浮起/讓位動畫
- [ ] 1.3 拖曳不影響既有卡片操作（開關、▶ 執行、點卡片進編輯）

## 2. 編輯畫面整塊拖曳（B）

- [ ] 2.1 動作積木除握把外，整塊 longPressDraggableHandle 可拖；點本體＝編輯、✕＝移除不受影響；帽子積木固定不參與

## 3. 插入位置（A 務實版）

- [ ] 3.1 動作之間與首尾的「＋」插入點 UI；點插入點開調色盤 → 新動作插入該索引（draft.actions 插入，非只 append）；沿用 rememberSaveable 草稿
- [ ] 3.2 既有末尾「＋ 加入動作」保留為「加到最後」

## 4. 驗證與提交

- [ ] 4.1 assembleDebug + assembleRelease 綠燈；versionName 0.17.0 / versionCode 17
- [ ] 4.2 舊資料相容（順序＝現有 list 順序）；不破壞搜尋、狀態晶片、拖曳排序
- [ ] 4.3 docs/GUIDE.md 更新（拖曳排序、插入位置）
- [ ] 4.4 分階段 commit（首頁排序 / 編輯拖曳+插入，英文、無 co-author），可 push
