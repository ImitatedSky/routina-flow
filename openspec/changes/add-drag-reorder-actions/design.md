# Design: add-drag-reorder-actions

## 技術選擇

drag-to-reorder 在 Compose 手刻容易踩雷（觸控 slop、位移換算、讓位動畫、捲動邊界），
依 AGENTS.md「優先採用成熟、維護良好的函式庫降低複雜度」，採用
**`sh.calvin.reorderable`**（現代、支援非 lazy 的 `ReorderableColumn`，與本專案編輯畫面的
可捲動 Column 結構相容，不需要把畫布改成 LazyColumn 而造成同向巢狀捲動問題）。

- 版本：最新穩定 2.x（例如 2.4.x），需與 Compose BOM 2024.12.01 / Kotlin 2.0.21 相容；
  實測 assembleRelease 綠燈為準
- **相容性風險與退路**：若該版與現有 BOM 衝突，退回手刻——純 Column、
  `pointerInput` + `detectDragGesturesAfterLongPress`、以各項實測高度（`onGloballyPositioned`）
  累積位移計算目標索引、`draft.actions` 重排。小清單（通常 1–8 塊）手刻可接受

## 結構

編輯畫面畫布（點狀網格）內：
```
[ 帽子積木 ]        ← 固定，不參與排序
[ ReorderableColumn 動作清單 ]
   每塊 ActionBlock：可拖曳（握把 + 長按本體）
[ 幽靈積木 ＋加入動作 ]
```

- 只有動作清單放進 `ReorderableColumn`；帽子積木、幽靈積木在其外
- `onMove(from, to)`：`draft = draft.copy(actions = draft.actions.toMutableList().apply { add(to, removeAt(from)) })`；
  沿用既有 rememberSaveable + Json Saver（不得回退）

## ActionBlock 互動

- 右側控制由「↑ ↓ ✕」改為「**≡（拖曳握把） ✕**」：
  - 握把用 `Modifier.draggableHandle()`（或 lib 對應 API），拖握把即開始排序
  - 整塊亦可 `longPressDraggableHandle()`：長按積木本體拿起（Scratch 感）
  - 觸覺回饋：拿起時 `HapticFeedback`（lib 內建或手動 `performHapticFeedback`）
- **點一下積木本體＝編輯參數**（與長按拖曳不衝突：tap vs long-press）
- **✕＝移除**（不變）
- 拖曳中：被拖積木 `shadow`/微放大＋提高 zIndex，其餘讓位（lib 提供）
- compact 唯讀預覽（首頁）不加任何拖曳，維持原樣

## 無障礙

移除 ↑↓ 後，為保留非拖曳的排序途徑，握把的 `contentDescription` 標示「拖曳排序」；
（本次不另做箭頭備援，若日後有無障礙需求再議——記於 Non-goals 精神下）

## 版本

versionName 0.8.0 / versionCode 8
