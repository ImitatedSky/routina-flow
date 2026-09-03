package com.routina.app.engine

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * 執行器 ↔ 互動對話框（[InputPromptActivity]）之間的行程內橋接。
 *
 * 「詢問輸入 / 選單選擇」動作需要 UI，但動作可能在背景服務裡執行。執行器在這裡登記一筆
 * 待回應請求、拿到一個 [CompletableDeferred]，接著啟動 InputPromptActivity 並在 deferred 上
 * 掛起等待；Activity 拿到使用者的答案（或取消）後呼叫 [deliver] 完成 deferred，執行器隨即續跑。
 *
 * 這是兩者唯一的耦合點：Activity 不碰 repository / ViewModel，只用 requestId 對應回傳結果。
 */
object InputBridge {

    /** 待回應的請求：requestId → 等待中的 deferred */
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<String?>>()

    /** 遞增的請求序號，保證同一行程內不重複 */
    private val seq = AtomicLong(0L)

    /** 產生下一個唯一的請求 id */
    fun nextRequestId(): Long = seq.incrementAndGet()

    /** 登記一筆待回應請求，回傳等待用的 deferred（答案字串；null＝取消／未回應） */
    fun open(requestId: Long): CompletableDeferred<String?> {
        val deferred = CompletableDeferred<String?>()
        pending[requestId] = deferred
        return deferred
    }

    /** Activity 交回結果：完成對應的 deferred 並移除登記（重複呼叫安全，第二次為 no-op） */
    fun deliver(requestId: Long, value: String?) {
        pending.remove(requestId)?.complete(value)
    }

    /** 放棄一筆請求（逾時／執行結束）：移除登記避免洩漏（deferred 已完成則無影響） */
    fun cancel(requestId: Long) {
        pending.remove(requestId)
    }
}
