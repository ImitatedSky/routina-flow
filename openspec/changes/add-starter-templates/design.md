# Design: add-starter-templates

## 範本定義（純資料，用既有 model 組成）

```kotlin
data class RoutineTemplate(
  val id: String,
  val title: String,       // 「早晨」
  val description: String, // 「08:00 響鈴＋今日提醒」
  val colorHex: Long,      // 範本圖示底色（用觸發家族色）
  val glyph: String,       // 簡單字元/或 vector 名
  val needsSetup: Boolean, // 需補參數（位置/SSID）
  val build: () -> Routine // 產生一份新 Routine（新 id、預設名稱、enabled=false）
)
```

- 集中於 `ui`（或 `model`）的 `RoutineTemplates.kt` 常數清單；`build()` 直接用現有
  `Trigger.*` / `Action.*` 組出，不新增任何能力
- 每次套用產生**新 id**（不可多次套用共用同一 id）；預設 `enabled=false`，
  待使用者於 EditScreen 確認並儲存後才生效——避免帶著未補的參數就啟用

### 六個範本

| 範本 | 觸發 | 動作 | needsSetup |
|---|---|---|---|
| 早晨 | Time 08:00 每天 | Notify「早安」、MediaVolume(MEDIA,60) | 否 |
| 就寢 | Time 23:00 每天 | Dnd(on)、Brightness(20)、RingerMode(SILENT) | 否 |
| 到公司 | LocationEnter（空座標） | RingerMode(SILENT)、Notify「已到公司」 | 是（選點） |
| 回家 | WifiConnected（空 SSID） | Bluetooth(on)、MediaVolume(MEDIA,50) | 是（SSID） |
| 省電 | BatteryBelow(20) | Dnd(on)、Brightness(20) | 否 |
| 空白 | 預設 Time | （無，直接進編輯） | — |

## 流程

1. 使用者點範本 → `template.build()` 產生 Routine → 直接進入 **EditScreen（新建模式，帶入該草稿）**
2. `needsSetup` 者：EditScreen 開啟時，若觸發參數未設定（座標 0,0 / SSID 空），
   以既有「未設定不可儲存」機制 + 提示引導補上（沿用區域選點、SSID 欄）
3. 使用者確認/微調 → 儲存（此時才寫入、才可啟用）→ 回清單
4. 全程沿用既有 rememberSaveable 草稿機制與儲存驗證

## UI

- **空狀態**：標題「從一個範本開始」+ 2 欄範本格（`tpl` 卡：色塊圖示 + 標題 + 一行說明），
  末格為「空白／自己從頭開始」
- **非空狀態入口**：FAB 點擊改為小選單「從範本建立 / 空白建立」，或長按 FAB 出範本；
  預設短按＝範本格 sheet，維持一鍵可達（擇一實作，維持既有新增可用性）
- 範本格色塊用觸發家族色（早晨靛、就寢紫、到公司深紫、回家藍、省電綠、空白灰），
  沿用既有色系，與積木視覺一致
- 文案繁中、口語（「點一下就建好，之後再改」）

## 版本

versionName 隨批次；若與 add-safer-delete-and-status 同批，versionCode 對齊；此 change 程式上獨立
