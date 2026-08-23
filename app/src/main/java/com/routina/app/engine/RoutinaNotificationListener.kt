package com.routina.app.engine

import android.app.Notification
import android.content.Context
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import com.routina.app.data.RoutineRepository
import com.routina.app.model.Routine
import com.routina.app.model.Trigger
import com.routina.app.model.TriggerSource

/**
 * 通知觸發入口。
 *
 * 服務由系統在「通知存取權」開啟後綁定、需要時啟動 → 通知觸發不需要任何常駐服務。
 * 這裡只做比對，動作一律交給 [TriggerDispatch]（監聽服務的行程不適合跑長時間動作，
 * 也不該被動作的例外影響）。
 */
class RoutinaNotificationListener : NotificationListenerService() {

    /** 去重用：notification key → 上次觸發的時間（elapsedRealtime） */
    private val lastFired = HashMap<String, Long>()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        // 系統回呼不得讓 App 崩潰（通知的 extras 由別的 App 產生，內容無法預期）
        runCatching { handle(sbn ?: return) }
    }

    private fun handle(sbn: StatusBarNotification) {
        val source = sbn.packageName ?: return
        // 自家通知一律忽略：否則「顯示通知」動作會回頭觸發自己，形成無限迴圈
        if (source == packageName) return

        val notification = sbn.notification ?: return
        // 常駐通知（音樂播放器、前景服務）與群組摘要不是「剛剛收到一則通知」
        if (notification.flags and IGNORED_FLAGS != 0) return

        val content = contentOf(notification)
        val matched = matchingRoutines(source, content)
        if (matched.isEmpty()) return

        // 同一則通知常被更新多次（進度、群組合併），5 秒內只算一次
        if (!allowFiring(sbn.key)) return

        // 觸發情境：讓動作能引用 {{通知標題}} / {{通知內容}} / {{通知來源App}}
        val triggerContext = buildMap {
            extraText(notification, Notification.EXTRA_TITLE)
                .takeIf { it.isNotBlank() }?.let { put("通知標題", it) }
            val body = extraText(notification, Notification.EXTRA_TEXT)
                .ifBlank { extraText(notification, Notification.EXTRA_BIG_TEXT) }
            if (body.isNotBlank()) put("通知內容", body)
            put("通知來源App", appLabelOf(source))
        }

        TriggerDispatch.run(this, matched, TriggerSource.NOTIFICATION, triggerContext = triggerContext)
    }

    /** 讀取通知 extras 的單一文字欄（內容由其他 App 產生，一律不得崩潰） */
    private fun extraText(notification: Notification, key: String): String = runCatching {
        notification.extras?.getCharSequence(key)?.toString()?.trim() ?: ""
    }.getOrDefault("")

    /** 來源 App 的顯示名稱；讀不到時退回套件名 */
    private fun appLabelOf(packageName: String): String = runCatching {
        val pm = packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    private fun matchingRoutines(source: String, content: String): List<Routine> =
        RoutineRepository.get(this).routines.value.filter { routine ->
            val trigger = routine.trigger
            if (!routine.enabled || trigger !is Trigger.NotificationPosted) return@filter false
            val sourceMatches = trigger.packageName.isBlank() || trigger.packageName == source
            val keyword = trigger.keyword.trim()
            val keywordMatches = keyword.isEmpty() || content.contains(keyword, ignoreCase = true)
            sourceMatches && keywordMatches
        }

    /** 標題與內容合起來當比對範圍（使用者說的「關鍵字」不會分得清標題或內文） */
    private fun contentOf(notification: Notification): String = runCatching {
        val extras = notification.extras ?: return ""
        CONTENT_KEYS
            .mapNotNull { extras.getCharSequence(it)?.toString() }
            .joinToString("\n")
    }.getOrDefault("")

    private fun allowFiring(key: String?): Boolean {
        val id = key ?: return true
        val now = SystemClock.elapsedRealtime()
        synchronized(lastFired) {
            lastFired.entries.removeAll { now - it.value > DEDUP_WINDOW_MS }
            val previous = lastFired[id]
            if (previous != null && now - previous < DEDUP_WINDOW_MS) return false
            lastFired[id] = now
        }
        return true
    }

    companion object {
        private const val DEDUP_WINDOW_MS = 5_000L

        private val IGNORED_FLAGS =
            Notification.FLAG_ONGOING_EVENT or Notification.FLAG_GROUP_SUMMARY

        private val CONTENT_KEYS = listOf(
            Notification.EXTRA_TITLE,
            Notification.EXTRA_TEXT,
            Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_SUB_TEXT
        )

        /**
         * 是否已取得「通知存取權」。
         * 未授權時系統根本不會綁定服務 → 含通知觸發的程序不會觸發，UI 顯示引導卡。
         */
        fun isEnabled(context: Context): Boolean = runCatching {
            NotificationManagerCompat.getEnabledListenerPackages(context)
                .contains(context.packageName)
        }.getOrDefault(false)
    }
}
