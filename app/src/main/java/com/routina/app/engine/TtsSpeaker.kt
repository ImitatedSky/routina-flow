package com.routina.app.engine

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicLong

/**
 * 系統 TTS 的單例包裝。
 *
 * TextToSpeech 的初始化是非同步的（要等 onInit 回呼），而且引擎建立成本高，
 * 因此惰性建立一次後就留著重複使用；由 [ExecutionService] 在服務停止時 [shutdown]。
 */
object TtsSpeaker {

    /** 引擎初始化逾時：超過就當作這個裝置沒有可用的 TTS */
    private const val INIT_TIMEOUT_MS = 5_000L

    /** 單次朗讀逾時：避免超長文字讓整個例行程序卡住 */
    private const val SPEAK_TIMEOUT_MS = 30_000L

    private val lock = Any()
    private var engine: TextToSpeech? = null
    private var ready: CompletableDeferred<Boolean>? = null
    private val utteranceCounter = AtomicLong(0)

    /** 進行中的朗讀：utteranceId → 完成訊號 */
    private val pending = mutableMapOf<String, CompletableDeferred<Boolean>>()

    /**
     * 朗讀 [text]，等到朗讀完成（或逾時）才返回。
     * 失敗時丟出例外，由 RoutineExecutor 記為失敗。
     */
    suspend fun speak(context: Context, text: String) {
        require(text.isNotBlank()) { "朗讀內容是空的" }

        val tts = awaitEngine(context.applicationContext)
        val utteranceId = "routina-${utteranceCounter.incrementAndGet()}"
        val done = CompletableDeferred<Boolean>()
        synchronized(lock) { pending[utteranceId] = done }

        val queued = tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
        if (queued != TextToSpeech.SUCCESS) {
            synchronized(lock) { pending.remove(utteranceId) }
            error("TTS 無法朗讀（引擎回傳錯誤）")
        }

        val success = try {
            withTimeout(SPEAK_TIMEOUT_MS) { done.await() }
        } catch (t: TimeoutCancellationException) {
            synchronized(lock) { pending.remove(utteranceId) }
            error("朗讀逾時（超過 ${SPEAK_TIMEOUT_MS / 1000} 秒）")
        }
        if (!success) error("TTS 朗讀失敗")
    }

    /** 取得已初始化完成的引擎；初始化失敗或逾時時丟出例外 */
    private suspend fun awaitEngine(context: Context): TextToSpeech {
        val signal = synchronized(lock) {
            ready ?: CompletableDeferred<Boolean>().also { fresh ->
                ready = fresh
                // TextToSpeech 的回呼走主執行緒，建立動作也放在主執行緒最穩妥
                createEngineOnMain(context, fresh)
            }
        }

        val ok = try {
            withTimeout(INIT_TIMEOUT_MS) { signal.await() }
        } catch (t: TimeoutCancellationException) {
            // 讓下一次執行能重新初始化，而不是永遠卡在同一個等不到的訊號
            synchronized(lock) { if (ready === signal) ready = null }
            error("TTS 初始化逾時（超過 ${INIT_TIMEOUT_MS / 1000} 秒）")
        }
        if (!ok) error("此裝置沒有可用的語音合成引擎")
        return synchronized(lock) { engine } ?: error("語音合成引擎已被釋放")
    }

    private fun createEngineOnMain(context: Context, signal: CompletableDeferred<Boolean>) {
        // 從 IO 協程呼叫時不能阻塞等待主執行緒，改以「建立完成後由 onInit 通知」的形式
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            runCatching {
                val created = TextToSpeech(context) { status ->
                    val ok = status == TextToSpeech.SUCCESS
                    if (!ok) synchronized(lock) { engine = null }
                    signal.complete(ok)
                }
                created.setOnUtteranceProgressListener(ProgressListener)
                synchronized(lock) { engine = created }
            }.onFailure {
                synchronized(lock) { engine = null }
                signal.complete(false)
            }
        }
    }

    /** 服務停止時釋放引擎（下次朗讀會重新初始化） */
    fun shutdown() {
        val target = synchronized(lock) {
            val current = engine
            engine = null
            ready = null
            pending.values.forEach { it.complete(false) }
            pending.clear()
            current
        } ?: return
        runCatching { target.stop() }
        runCatching { target.shutdown() }
    }

    private object ProgressListener : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) = finish(utteranceId, true)

        // 舊版系統仍會呼叫這個已標記為 deprecated 的版本，必須保留
        @Suppress("OVERRIDE_DEPRECATION")
        override fun onError(utteranceId: String?) = finish(utteranceId, false)

        override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId, false)

        private fun finish(utteranceId: String?, success: Boolean) {
            val id = utteranceId ?: return
            val signal = synchronized(lock) { pending.remove(id) } ?: return
            signal.complete(success)
        }
    }
}
