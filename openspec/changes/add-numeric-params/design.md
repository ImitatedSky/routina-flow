# Design: add-numeric-params

## 資料模型（向後相容，加選用字串欄）

各動作既有的數值 Int 欄保留，新增對應的選用「運算式」字串欄（預設空）：

```kotlin
BurstPhoto(lensBack, count: Int = 3, intervalMs: Int = 500, notify = true,
           countExpr: String = "", intervalExpr: String = "")
Wait(seconds: Int = 3, secondsExpr: String = "")
Vibrate(millis: Int = 500, millisExpr: String = "")
RecordAudio(seconds: Int = 5, notify = true, secondsExpr: String = "")
MediaVolume(stream, percent: Int = 50, percentExpr: String = "")
Brightness(percent: Int = 50, percentExpr: String = "")
```

- 新欄皆有預設值、純新增 → 舊 JSON 相容
- Expr 非空＝使用者用了數字輸入/變數；空＝沿用原 Int（舊行為）

## 執行時解析

```
fun resolveNum(expr: String, fallback: Int, range: IntRange, ctx): Int {
    if (expr.isBlank()) return fallback
    val resolved = VariableResolver.resolve(expr, ctx)   // 變數代入
    val n = resolved.trim().toIntOrNull() ?: fallback     // parse，失敗用 fallback
    return n.coerceIn(range)                               // clamp 安全範圍
}
```

- 先變數代入（沿用 Wave 1 的 resolver），再 parse 成 Int，失敗回 fallback（原 Int）
- clamp 各自安全範圍：連拍張數 1–999、間隔 0–60000ms、等待 1–3600s、震動 1–10000ms、
  錄音 1–3600s、音量/亮度 0–100
- RoutineExecutor 對應動作改用 resolveNum(expr, int, range, ctx) 取值

## UI（數值參數編輯器）

把「小範圍滑桿」改為 **NumericField**：

- 一個數字輸入欄（`keyboardType = Number`），可直接打任意整數
- 下方「＋ 插入變數」：插入數字類變數 token（沿用 Wave 1 的插入器；同樣列出上一個結果／已設定變數／常用如 `{{電量}}`）
- 可保留一個滑桿作為「快速調整」的輔助（在常用範圍內），但輸入欄才是主要、可超出滑桿範圍
- 欄位內容寫入對應的 Expr 字串；使用者清空則回退為原 Int 預設
- 顯示提示：超出安全上限會被夾到範圍（例「最多 999」）

積木參數欄：Expr 非空顯示 Expr（可能是數字或 `{{...}}`），否則顯示 Int。

## 相容與邊界

- 舊 routine：Expr 空 → 完全照舊
- 變數解析不到／parse 失敗 → 用原 Int fallback，不崩潰、不亂跳巨大值
- 連拍等大數值：clamp 上限保護（避免 135 打成 13500 之類誤植造成長時間佔用相機）

## 版本

versionName 0.15.0 / versionCode 15
