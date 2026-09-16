package com.routina.app.engine

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 等待中的通知回覆：記下「回覆進來時要跑哪一段動作」。
 *
 * 可回覆的通知不會卡住流程，所以主流程早就跑完了；使用者可能過幾分鐘才回，
 * 那時 App 行程很可能已經被系統回收。因此這份待辦必須落地成檔案，
 * 廣播進來時才有辦法把當時的情境還原出來、把區塊補跑完。
 *
 * 一併記下當下的變數與觸發情境快照，讓回覆區塊看到的 `{{var:...}}`
 * 與通知發出那一刻一致，而不是一個空白的新情境。
 */
@Serializable
data class PendingReply(
    val requestId: Long,
    val routineId: String,
    /** 回覆區塊的動作範圍（[bodyStart], [bodyEnd]）＝ OnReplyBegin 之後到 EndOnReply 之前 */
    val bodyStart: Int,
    val bodyEnd: Int,
    val variableName: String,
    /** 那則詢問通知的 id：回覆區塊跑完時再收一次，補上廣播當下清不掉的情況 */
    val notificationId: Int = -1,
    val vars: Map<String, String> = emptyMap(),
    val trigger: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis()
)

object PendingReplies {

    private const val FILE_NAME = "pending_replies.json"

    /** 過期門檻：超過這個時間沒回覆就不再執行，避免待辦無限累積成隱形的地雷 */
    private const val MAX_AGE_MS = 24L * 60 * 60 * 1000

    private val json = Json { ignoreUnknownKeys = true }

    private fun file(context: Context) = File(context.applicationContext.filesDir, FILE_NAME)

    @Synchronized
    fun put(context: Context, pending: PendingReply) {
        val kept = load(context).filter { it.requestId != pending.requestId }
        save(context, kept + pending)
    }

    /** 取出並移除一筆（回覆只該被處理一次）；找不到或已過期回 null */
    @Synchronized
    fun take(context: Context, requestId: Long): PendingReply? {
        val all = load(context)
        val hit = all.firstOrNull { it.requestId == requestId }
        save(context, all.filter { it.requestId != requestId })
        if (hit == null) return null
        return hit.takeIf { System.currentTimeMillis() - it.createdAt <= MAX_AGE_MS }
    }

    // 讀寫都不讓例外外溢：這是輔助資料，壞掉頂多是回覆不執行，不該讓通知或流程崩掉
    private fun load(context: Context): List<PendingReply> = runCatching {
        val f = file(context)
        if (!f.exists()) return emptyList()
        json.decodeFromString(ListSerializer(PendingReply.serializer()), f.readText())
            .filter { System.currentTimeMillis() - it.createdAt <= MAX_AGE_MS }
    }.getOrDefault(emptyList())

    private fun save(context: Context, list: List<PendingReply>) {
        runCatching {
            file(context).writeText(
                json.encodeToString(ListSerializer(PendingReply.serializer()), list)
            )
        }
    }
}
