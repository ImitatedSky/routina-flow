# Tasks: add-numeric-params

## 1. 模型

- [ ] 1.1 各動作加選用運算式字串欄（預設空，向後相容）：BurstPhoto.countExpr/intervalExpr、Wait.secondsExpr、Vibrate.millisExpr、RecordAudio.secondsExpr、MediaVolume.percentExpr、Brightness.percentExpr

## 2. 引擎

- [ ] 2.1 resolveNum(expr, fallback, range, ctx)：expr 空→fallback；否則 VariableResolver.resolve → toIntOrNull ?: fallback → coerceIn(range)
- [ ] 2.2 RoutineExecutor 對應動作改用 resolveNum 取值；安全範圍：連拍張數 1–999、間隔 0–60000、等待 1–3600、震動 1–10000、錄音 1–3600、音量/亮度 0–100

## 3. UI

- [ ] 3.1 數值參數編輯器改 NumericField：數字輸入欄（Number 鍵盤，可超出原滑桿範圍）+「插入變數」（沿用 Wave 1 插入器）；可保留滑桿作快速調整；寫入對應 Expr；清空回退原 Int
- [ ] 3.2 積木參數欄：Expr 非空顯示 Expr，否則顯示 Int；超上限提示

## 4. 驗證與提交

- [ ] 4.1 assembleDebug + assembleRelease 綠燈；mapping 檢查（新欄不影響 serializer 保留）；versionName 0.15.0 / versionCode 15
- [ ] 4.2 舊 JSON 相容驗證（滑桿設定的舊動作行為不變）
- [ ] 4.3 docs/GUIDE.md 更新（數值參數可輸入/用變數）
- [ ] 4.4 分階段 commit（model+engine / UI，英文、無 co-author），可 push
