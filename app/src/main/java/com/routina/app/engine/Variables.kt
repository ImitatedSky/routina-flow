package com.routina.app.engine

/**
 * 一次執行的情境：貫穿該次所有動作，讓動作讀到觸發資訊、前一個動作的輸出與自訂變數。
 *
 * 由執行路徑（[RoutineExecutor.execute]）在一次執行開始時建立一個，依序傳給每個動作：
 * 動作讀取情境代入文字參數，並可寫入輸出（[lastOutput]）或具名變數（[vars]）。
 */
class RunContext {
    /** 具名變數（設定變數動作寫入，`{{var:名稱}}` 讀取） */
    val vars = mutableMapOf<String, String>()

    /** 上一個有輸出的動作留下的輸出（`{{result}}` 讀取）；無輸出的動作不覆寫 */
    var lastOutput: String? = null

    /** 觸發情境值（時間/日期/星期/電量，及各觸發提供的通知內容、Wi-Fi 名稱等） */
    val trigger = mutableMapOf<String, String>()
}

/**
 * 文字參數的變數解析器。
 *
 * 掃描 `{{...}}` token 並代入實際值：
 * - `{{result}}`：上一個動作的輸出
 * - `{{var:名稱}}`：具名變數
 * - `{{觸發:key}}` 或直接 `{{key}}`：觸發情境值（如 `{{通知內容}}`、`{{時間}}`、`{{電量}}`）
 *
 * 查無對應值代入空字串；非合法 token 原樣保留；**不含 token 的字串原樣回傳**（舊資料零影響）。
 */
object VariableResolver {

    private val TOKEN = Regex("\\{\\{(.+?)\\}\\}")

    /**
     * 觸發可能提供、但當次觸發未提供時仍應代入空字串的 key。
     * 不在此列、也不在情境中的 `{{...}}` 視為使用者的一般文字，原樣保留。
     */
    val KNOWN_KEYS = setOf(
        "通知標題", "通知內容", "通知來源App",
        "電量", "Wi-Fi名稱", "藍牙裝置", "地點名稱", "標籤名稱",
        "時間", "日期", "星期"
    )

    fun resolve(template: String, ctx: RunContext): String {
        // 沒有 token 的字串（含所有舊資料）完全照舊，不做任何處理
        if (!template.contains("{{")) return template
        return TOKEN.replace(template) { match ->
            resolveKey(match.groupValues[1].trim(), ctx) ?: match.value
        }
    }

    /** @return 代入值（查無代空字串）；null 代表非合法 token，呼叫端原樣保留 */
    private fun resolveKey(key: String, ctx: RunContext): String? = when {
        key == "result" -> ctx.lastOutput ?: ""
        key.startsWith("var:") -> ctx.vars[key.removePrefix("var:").trim()] ?: ""
        key.startsWith("觸發:") -> ctx.trigger[key.removePrefix("觸發:").trim()] ?: ""
        ctx.trigger.containsKey(key) -> ctx.trigger[key]
        key in KNOWN_KEYS -> ""
        else -> null
    }
}
