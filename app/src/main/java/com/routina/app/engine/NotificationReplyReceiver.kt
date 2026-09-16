package com.routina.app.engine

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.routina.app.R
import com.routina.app.RoutinaApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 接住通知上的直接回覆，把內容交回正在等待的例行程序。
 *
 * 「通知詢問」動作發出通知後掛在 [InputBridge] 上等待；使用者在通知的輸入框送出後，
 * 系統把文字放進這個廣播的 RemoteInput 結果裡，這裡取出來 [InputBridge.deliver] 回去，
 * 執行器就接著跑下一個動作。
 *
 * exported=false：只有本 App 自己建立的 PendingIntent 能觸發，外部程式無法偽造一則回覆
 * 來把任意字串塞進別人的流程變數裡。
 */
class NotificationReplyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val requestId = intent.getLongExtra(EXTRA_REQUEST_ID, -1L)
        if (requestId < 0) return

        val reply = RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(KEY_REPLY)
            ?.toString()
            .orEmpty()

        // 空白回覆當作沒回答，交 null 讓動作記為失敗，不要把空字串存進變數
        InputBridge.deliver(requestId, reply.ifBlank { null })

        // 不等待型的通知（顯示通知＋可回覆）：主流程早就跑完，這裡把「收到回覆時」區塊補跑。
        // 用 goAsync 讓系統知道還沒處理完，行程不會在動作跑到一半被收掉。
        if (reply.isNotBlank()) {
            val pending = PendingReplies.take(context, requestId)
            if (pending != null) {
                val appContext = context.applicationContext
                val result = goAsync()
                scope.launch {
                    try {
                        RoutineExecutor.runReplyBody(appContext, pending, reply)
                    } finally {
                        result.finish()
                    }
                }
            }
        }

        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (notificationId >= 0) {
            dismiss(context, notificationId)
        }
    }

    /**
     * 收掉那則詢問通知。
     *
     * 回覆送出後系統會把通知留在「已回覆」的過渡狀態，這時直接 cancel 會和系統的重貼打架、
     * 通知反而賴著不走。先用同一個 id 蓋上一則沒有輸入框、且 [setTimeoutAfter] 立刻到期的通知
     * 把那個狀態收掉，再取消，才會真的消失。
     */
    private fun dismiss(context: Context, notificationId: Int) {
        val manager = NotificationManagerCompat.from(context)
        val placeholder = NotificationCompat.Builder(context, RoutinaApp.CHANNEL_ACTIONS)
            .setSmallIcon(R.drawable.ic_notification)
            .setTimeoutAfter(1)
            .build()
        manager.notify(notificationId, placeholder)
        manager.cancel(notificationId)
    }

    companion object {
        /**
         * 補跑回覆區塊用的 scope：刻意不綁任何元件生命週期。
         * 廣播接收器的 onReceive 一回傳就結束，掛在它身上的協程會被取消
         * （與 [RunFromOutside] 同樣的理由）。
         */
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        const val KEY_REPLY = "routina_reply"
        private const val EXTRA_REQUEST_ID = "request_id"
        private const val EXTRA_NOTIFICATION_ID = "notification_id"

        /**
         * 建立回覆用的 PendingIntent。
         *
         * FLAG_MUTABLE 是必要的：系統要把使用者打的字寫進這個 intent 才送出，
         * 不可變的話 RemoteInput 拿不到任何結果。
         * requestCode 用 requestId，多個流程同時在等時各自的 PendingIntent 不會互相覆蓋。
         */
        fun replyPendingIntent(
            context: Context,
            requestId: Long,
            notificationId: Int
        ): PendingIntent {
            val intent = Intent(context, NotificationReplyReceiver::class.java)
                .putExtra(EXTRA_REQUEST_ID, requestId)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            return PendingIntent.getBroadcast(
                context,
                requestId.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
        }
    }
}
