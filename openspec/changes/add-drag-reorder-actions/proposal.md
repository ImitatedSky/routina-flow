# Change: add-drag-reorder-actions

## Why

使用者希望在編輯畫面**用拖曳改變動作前後順序**，取代目前的上下箭頭按鈕。
這是最初 Scratch 積木風想要、但當時把 drag & drop 列為 non-goal 先以 ↑↓ 箭頭代替的功能，
現在正式補上——拖曳更直覺，也更符合積木堆疊的操作心智。

## What Changes

- **編輯畫面的動作積木改為可拖曳排序**：
  - 每塊動作積木右側改放**拖曳握把（≡）**取代原本的 ↑↓ 箭頭；握把可直接拖、
    長按積木本體亦可拖起
  - 拖曳過程中被拖的積木浮起（陰影/微放大），其餘積木讓位動畫，放開即套用新順序
  - 拿起時給觸覺回饋（haptic）
  - 觸發帽子積木固定在最上方不參與排序；✕ 移除鈕保留；點積木本體仍為編輯參數
- 清單畫面（唯讀預覽）不變
- versionName 0.8.0 / versionCode 8

## Non-goals

- 跨 routine 拖曳、把積木拖進/拖出堆疊（維持單一 routine 內排序）
- 拖曳觸發帽子積木（觸發永遠在最上，僅動作可排序）
- 首頁卡片預覽的拖曳（預覽為唯讀）

## Impact

- 受影響 specs：routine-management（積木編輯器排序方式 MODIFIED → 拖曳）
- 受影響程式碼：ui\EditScreen.kt（動作清單改可拖曳排序容器）、ui\blocks\RoutineBlocks.kt
  （ActionBlock 以拖曳握把取代 ↑↓，接受拖曳修飾符）
- 依賴：擬用維護良好的 Compose 拖曳排序函式庫（drag-reorder 自行手刻易出錯，
  符合「優先採用成熟函式庫降低複雜度」原則）；體積影響極小。若與現有 Compose BOM
  不相容則退回手刻（純 Column 小清單的位移交換）
