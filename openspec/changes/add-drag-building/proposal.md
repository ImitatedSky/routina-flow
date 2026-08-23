# Change: add-drag-building

## Why

使用者要更 Scratch 的拖拉體驗，且「全部都要」：
1. 從積木清單把動作拖到想要的位置（決定插入位置）
2. 現有排序拖曳加強（整塊可拖、更順手）
3. 首頁 routine 卡片可拖曳排序

## What Changes

- **首頁卡片拖曳排序**：長按 routine 卡片拖曳調整先後，順序即時持久化；
  **僅在未搜尋（查詢為空）時可拖**（過濾清單上排序無意義）
- **編輯畫面整塊積木可拖**：動作積木除了 ≡ 握把，**長按整塊**也能拿起排序；
  拖曳浮起/讓位動畫沿用既有
- **決定插入位置**（Scratch 式「放到想要的位置」的手機務實版）：動作之間與清單首尾提供
  **「＋」插入點**，點某個插入點開調色盤 → 新動作插入該位置（不再只加到末尾）；
  搭配整塊可拖，等效於「拖到想要的位置」
  - 註：手機上「從 bottom sheet 面板真的拖到背後清單」互動脆弱、體驗差，故以
    「插入點 + 整塊可拖」達成同樣目的，更穩更順（見 design.md）

## Non-goals

- 真正的跨 bottom-sheet 拖放（技術脆弱，改以插入點達成同目的）
- C 型積木/巢狀（控制流程屬變數 Wave 3，另議）
- 首頁搜尋中的排序（順序在過濾清單無意義）

## Impact

- 受影響 specs：routine-management（首頁排序、編輯插入位置與整塊拖曳）
- 受影響程式碼：data/RoutineRepository + engine/RoutineManager（routine 順序持久化 + reorder）、
  ui/HomeScreen（卡片 reorderable，搜尋時停用）、ui/EditScreen + ui/blocks（整塊 longPress 拖曳、插入點 UI 與插入邏輯）
- 依賴：沿用既有 sh.calvin.reorderable；無新增；versionName 0.17.0 / versionCode 17
