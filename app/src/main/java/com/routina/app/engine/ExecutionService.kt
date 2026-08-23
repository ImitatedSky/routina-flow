package com.routina.app.engine

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.routina.app.MainActivity
import com.routina.app.R
import com.routina.app.RoutinaApp
import com.routina.app.data.RoutineRepository
import com.routina.app.model.TriggerSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 短生命週期前景執行服務。
 *
 * 由觸發條件（定時、地理圍欄、充電/電量、藍牙、Wi-Fi、系統狀態）啟動的執行一律走這裡：
 * BroadcastReceiver 只有約 10 秒的執行時間，而「等待」「朗讀」「HTTP」都可能更久，
 * 因此把動作搬進前景服務執行，完成後立刻 stopSelf → 平時零常駐。
 *
 * 多個 routine 同時觸發時全部進入同一個佇列，由同一個服務實例依序執行
 * （避免例如兩個程序同時搶音量、手電筒而互相打斷）。
 */
class ExecutionService : Service() {

    /** 待執行佇列（以 routineId + 來源為單位） */
    private val queue = ArrayDeque<Pending>()

    /** 是否已有協程在消化佇列 */
    private var draining = false

    /** 是否成功進入前景；false 時降級執行（跳過等待動作） */
    private var inForeground = false

    /** 最新一次 onStartCommand 的 startId：只有它才有權停止服務 */
    @Volatile
    private var latestStartId = 0

    /**
     * 執行用的 scope 刻意不在 onDestroy 取消：
     * 服務是「執行完就停」的模型，取消 scope 會把最後一筆執行砍掉一半。
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private data class Pending(val routineId: String, val source: TriggerSource)

    /**
     * 拍照／錄音動作用的前景服務類型切換。
     *
     * 平時服務以 specialUse 類型運行；執行拍照／錄音前把它切到 camera／microphone 類型
     * （Android 9+ 存取相機麥克風的前提）。從未進得了前景（[inForeground] 為 false），
     * 或 Android 14+ 背景啟動被系統擋下（startForeground 丟例外）時回傳 false，
     * 引擎據此記為背景受限失敗；做完動作 [restore] 還原回 specialUse。
     */
    private val captureSwitch = object : RoutineExecutor.ForegroundTypeSwitch {
        override fun switchTo(type: RoutineExecutor.CaptureFgsType): Boolean {
            if (!inForeground) return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
            val fgsType = when (type) {
                RoutineExecutor.CaptureFgsType.CAMERA ->
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA

                RoutineExecutor.CaptureFgsType.MICROPHONE ->
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            return runCatching {
                startForeground(NOTIFICATION_ID, buildNotification(), fgsType)
            }.isSuccess
        }

        override fun restore() {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        buildNotification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, buildNotification())
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        inForeground = startForegroundSafely()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        latestStartId = startId
        // onCreate 沒進得了前景就再試一次（例如剛好卡在背景啟動限制的邊緣）
        if (!inForeground) inForeground = startForegroundSafely()

        val routineId = intent?.getStringExtra(EXTRA_ROUTINE_ID)
        if (routineId == null) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val source = parseSource(intent.getStringExtra(EXTRA_SOURCE))

        synchronized(queue) { queue.addLast(Pending(routineId, source)) }
        drain()
        // 不要 START_STICKY：服務被系統殺掉後重啟一個空 intent 沒有任何意義
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // TTS 引擎佔資源，服務結束就釋放（下次執行會重新惰性初始化）
        runCatching { TtsSpeaker.shutdown() }
        super.onDestroy()
    }

    /** 啟動（或沿用）唯一一個佇列消化協程 */
    private fun drain() {
        synchronized(queue) {
            if (draining) return
            draining = true
        }
        // 進不了前景時必須立刻放掉服務身分：以 startForegroundService 起動卻沒有在 5 秒內
        // 進入前景，系統會丟 ForegroundServiceDidNotStartInTimeException 讓 App 閃退。
        // 工作仍由未被取消的 scope 在背景做完（等待動作被跳過並於紀錄註明）。
        if (!inForeground) stopSelf()
        scope.launch {
            while (true) {
                val next = synchronized(queue) {
                    queue.removeFirstOrNull() ?: run {
                        draining = false
                        null
                    }
                } ?: break
                runCatching { runOne(next) }
            }
            // 期間若有新的 onStartCommand 進來，latestStartId 已更新 → 這次停止會被忽略
            if (inForeground) stopSelf(latestStartId)
        }
    }

    private suspend fun runOne(pending: Pending) {
        val routine = RoutineRepository.get(this).findById(pending.routineId) ?: return
        RoutineExecutor.execute(
            context = this,
            routine = routine,
            source = pending.source,
            // 服務存活期間行程不會被回收，紀錄交給背景協程寫入即可
            persistBlocking = !inForeground,
            allowWait = inForeground,
            note = if (inForeground) null else DEGRADED_NOTE,
            // 進得了前景時才提供類型切換；降級（背景）執行時傳 null，
            // 拍照/錄音會因無法取得前景相機/麥克風而記為受限失敗
            fgsSwitch = if (inForeground) captureSwitch else null
        )
    }

    /** @return 是否成功進入前景 */
    private fun startForegroundSafely(): Boolean =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        }.isSuccess

    private fun buildNotification(): Notification {
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, RoutinaApp.CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Routina 正在執行例行程序")
            .setContentText("執行完畢後會自動結束")
            .setContentIntent(pending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .build()
    }

    private fun parseSource(name: String?): TriggerSource =
        TriggerSource.entries.firstOrNull { it.name == name } ?: TriggerSource.MANUAL

    companion object {
        private const val NOTIFICATION_ID = 43
        private const val EXTRA_ROUTINE_ID = "routine_id"
        private const val EXTRA_SOURCE = "source"

        /** 降級執行時寫進 RunLog 的註記 */
        const val DEGRADED_NOTE = "前景執行服務無法啟動，已於接收器內降級執行（等待動作被跳過）"

        /**
         * 交由前景服務執行一個 routine。
         *
         * @return 是否成功把工作交給服務。false 代表系統擋下了背景啟動前景服務
         * （Android 12+ 的 ForegroundServiceStartNotAllowedException），
         * 呼叫端應改走降級路徑。
         */
        fun start(context: Context, routineId: String, source: TriggerSource): Boolean {
            val appContext = context.applicationContext
            val intent = Intent(appContext, ExecutionService::class.java)
                .putExtra(EXTRA_ROUTINE_ID, routineId)
                .putExtra(EXTRA_SOURCE, source.name)
            return runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    appContext.startForegroundService(intent)
                } else {
                    appContext.startService(intent)
                }
            }.isSuccess
        }
    }
}
