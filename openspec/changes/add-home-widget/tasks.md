# Tasks: add-home-widget

## 1. 版面與資源

- [x] 1.1 widget_routina.xml：標題列（App 圖示＋名稱）＋ ListView(@id/widget_list) ＋
      空狀態 TextView(@id/widget_empty)；自畫圓角底 widget_background.xml
- [x] 1.2 widget_item.xml：一列＝程序名稱(@id/widget_item_name) ＋ ▶ 提示，整列可點(@id/widget_item_row)
- [x] 1.3 亮／暗色：values/colors.xml + values-night/colors.xml（widget_bg／text／accent）、
      strings.xml（空狀態、▶ 提示）
- [x] 1.4 widget_routina_info.xml：appwidget-provider（~3x3、resizable、updatePeriodMillis=0、
      initialLayout/previewLayout）

## 2. 元件

- [x] 2.1 RoutinaWidgetService＋RemoteViewsFactory：讀 RoutineRepository.routines.value 快照，
      每列設 fill-in intent 帶 routineId；邊界保護不崩潰
- [x] 2.2 RoutinaWidgetProvider：onUpdate 綁 setRemoteAdapter＋setEmptyView＋
      指向 RunRoutineActivity 的 PendingIntent 樣板（FLAG_MUTABLE）；companion refresh()
      以 notifyAppWidgetViewDataChanged 通知重讀

## 3. 接線與宣告

- [x] 3.1 RoutineManager save/delete/reorder 完成後呼叫 RoutinaWidgetProvider.refresh
- [x] 3.2 Manifest：receiver（exported、APPWIDGET_UPDATE intent-filter、provider meta-data）＋
      service（exported=false、BIND_REMOTEVIEWS）

## 4. 驗證與提交

- [x] 4.1 assembleDebug 綠燈
- [x] 4.2 英文 commit、無 co-author
