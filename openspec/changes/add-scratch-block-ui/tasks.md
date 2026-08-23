# Tasks: add-scratch-block-ui

## 1. 積木元件

- [x] 1.1 BlockShapes.kt：卡榫幾何常數、hatBlockShape（圓弧頂+底凸榫）、stackBlockShape（頂凹槽+底凸榫）、幽靈積木虛線外框；深 25% 邊框繪製
- [x] 1.2 RoutineBlocks.kt：ParamField（白色圓角參數欄）、TriggerHatBlock、ActionBlock（含 ↑↓✕ 圖示槽）、GhostBlock、MoreActionsBlock（「還有 N 個動作」）
- [x] 1.3 BlockStack composable：-6dp 間距卡接堆疊、zIndex 排序、compact 縮小模式（34dp/12sp）
- [x] 1.4 BlockPalette.kt：觸發調色盤 sheet（4 帽子積木）、動作調色盤 sheet（5 動作積木）

## 2. 畫面改版

- [x] 2.1 EditScreen：點狀網格畫布 + 帽子積木 + 動作堆疊 + 幽靈積木；點擊行為（換觸發/編參數/加動作/排序/移除）全部接上；rememberSaveable 草稿機制不回退
- [x] 2.2 EditScreen：動作上移/下移功能（model 的 actions list 重排）
- [x] 2.3 HomeScreen：卡片改縮小版唯讀積木堆疊（≤3 塊 + 更多積木），移除左色條與文字摘要；停用 40% alpha；其餘行為不變
- [x] 2.4 深淺色模式檢查：畫布點陣、卡片底、參數欄對比

## 3. 驗證與提交

- [x] 3.1 assembleDebug + assembleRelease 綠燈，release APK 仍 ≤ 5MB
- [x] 3.2 分階段 commit（積木元件一個、畫面改版一個，英文 message、無 co-author）

## 4. 實測回饋修訂：簡化為圓角色塊（2026-08-22）

- [x] 4.1 所有積木改 RoundedCornerShape(12dp)（compact 10dp）；刪除帽子圓弧、凹槽、凸榫的
      自繪 path 與卡榫幾何常數，BlockShapes.kt 併入 RoutineBlocks.kt 後刪檔
- [x] 4.2 BlockStack 改正的 6dp 垂直間距，移除 zIndex 與負間距；清掉為卡榫預留的高度/padding
- [x] 4.3 顏色、白色參數欄、調色盤、↑↓✕ 與所有點擊行為不變；assembleDebug + assembleRelease 綠燈
