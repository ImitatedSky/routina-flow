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

    /**
     * 全域變數（跨程序、可持久化）：執行開始時由儲存載入，`{{全域:名稱}}` 讀取，
     * 「設定全域變數」動作寫入並登記到 [dirtyGlobals]，執行結束時只把有異動的鍵落地。
     */
    val globals = mutableMapOf<String, String>()

    /** 本次執行有寫入的全域變數名稱；執行結束時據此把異動落地（未動的不寫，避免無謂覆蓋） */
    val dirtyGlobals = mutableSetOf<String>()

    /** 目前正在執行中的程序 id 堆疊（含最外層）；「執行程序」動作據此擋循環呼叫 */
    val callStack = mutableSetOf<String>()

    /**
     * 前一個「可回覆的通知」留下的待回覆資訊，供緊接著的「收到回覆時」區塊取用。
     * 兩者位置相鄰但是分開的動作，用這裡交棒比讓積木互相知道彼此乾淨。
     * null＝前面沒有在等回覆的通知。
     */
    var pendingReply: PendingReplyHandle? = null
}

/** 一則正在等回覆的通知：等待用的請求 id、要存進哪個變數、等多久、通知本身的 id */
class PendingReplyHandle(
    val requestId: Long,
    val variableName: String,
    val waitMs: Long,
    val notificationId: Int
)

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

    // internal：編輯畫面要把 token 畫成膠囊，與這裡共用同一個樣式定義，
    // 免得兩邊對「什麼算一個 token」的認知不一致
    internal val TOKEN = Regex("\\{\\{(.+?)\\}\\}")

    /**
     * 觸發可能提供、但當次觸發未提供時仍應代入空字串的 key。
     * 不在此列、也不在情境中的 `{{...}}` 視為使用者的一般文字，原樣保留。
     */
    val KNOWN_KEYS = setOf(
        "通知標題", "通知內容", "通知來源App",
        "電量", "Wi-Fi名稱", "藍牙裝置", "地點名稱", "標籤名稱",
        "時間", "日期", "星期",
        "迴圈:次數"
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
        key.startsWith("全域:") -> ctx.globals[key.removePrefix("全域:").trim()] ?: ""
        key.startsWith("觸發:") -> ctx.trigger[key.removePrefix("觸發:").trim()] ?: ""
        ctx.trigger.containsKey(key) -> ctx.trigger[key]
        key in KNOWN_KEYS -> ""
        else -> null
    }

    /** 各 token 的範例值，只給編輯畫面的預覽用（執行時代入的是當下實際值）。 */
    private val SAMPLE = mapOf(
        "時間" to "21:45",
        "日期" to "8/24",
        "星期" to "週日",
        "電量" to "87",
        "通知標題" to "範例通知",
        "通知內容" to "這是通知內容",
        "通知來源App" to "訊息",
        "Wi-Fi名稱" to "MyWiFi",
        "藍牙裝置" to "我的耳機",
        "地點名稱" to "公司",
        "標籤名稱" to "床頭標籤",
        "迴圈:次數" to "2"
    )

    /**
     * 用範例值把 [template] 的變數 token 代換成看得懂的樣子，供編輯畫面預覽。
     * 不參與實際執行；未知 token 原樣保留（無 token 直接回傳，與 [resolve] 一致）。
     */
    fun previewResolve(template: String): String {
        if (!template.contains("{{")) return template
        return TOKEN.replace(template) { match ->
            sampleValue(match.groupValues[1].trim()) ?: match.value
        }
    }

    private fun sampleValue(key: String): String? = when {
        key == "result" -> "上一步的結果"
        key.startsWith("var:") -> "「${key.removePrefix("var:").trim()}」的值"
        key.startsWith("全域:") -> "「${key.removePrefix("全域:").trim()}」的全域值"
        key.startsWith("觸發:") -> sampleValue(key.removePrefix("觸發:").trim()) ?: "…"
        else -> SAMPLE[key]
    }
}
