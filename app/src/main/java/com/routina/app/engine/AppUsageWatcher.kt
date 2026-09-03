package com.routina.app.engine

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import com.routina.app.data.RoutineRepository
import com.routina.app.model.Routine
import com.routina.app.model.Trigger
import com.routina.app.model.TriggerSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 「App 開啟 / 關閉」觸發的偵測器，由 [MonitorService] 持有。
 *
 * Android 沒有「某個 App 進入前景」的廣播，只能讀使用情況統計
 * （[UsageStatsManager.queryEvents]）自行判斷，所以耗電控制是這個類別的重點：
 *
 * - 只有（螢幕亮著）且（存在啟用中的 App 觸發程序）且（已取得使用情況存取權）時才輪詢
 * - 輪詢間隔 3 秒，每次只查最近一小段區間，不掃描歷史
 * - 螢幕熄滅或程序全部停用時立刻停止輪詢（由 [setScreenOn] / [sync] 驅動）
 *
 * 觸發判定只看「前景 App 換人」這一個事件：換成目標 App＝開啟，
 * 從目標 App 換成別的＝關閉。同一個 App 持續在前景不會重複觸發。
 */
class AppUsageWatcher(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var pollJob: Job? = null
    private var screenOn = true

    /** 目前的前景 App（null＝還沒建立基準） */
    private var foregroundPackage: String? = null

    /** 已處理過的最後一筆事件時間，避免查詢區間重疊時重複處理 */
    private var lastEventAt = 0L

    fun setScreenOn(on: Boolean) {
        if (screenOn == on) return
        screenOn = on
        sync()
    }

    /** 依目前條件（螢幕、程序、權限）決定要不要輪詢。程序或權限變動後都應呼叫。 */
    fun sync() {
        if (screenOn && hasAppStateRoutines(appContext) && hasUsageAccess(appContext)) {
            start()
        } else {
            stop()
        }
    }

    fun release() {
        stop()
        scope.cancel()
    }

    private fun start() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            // 先把「使用者早就開著的 App」記成基準，否則一開始輪詢就會誤判成剛剛被開啟
            foregroundPackage = runCatching { latestForeground() }.getOrNull()
            lastEventAt = System.currentTimeMillis()
            while (isActive) {
                delay(POLL_INTERVAL_MS)
                runCatching { poll() }
            }
        }
    }

    private fun stop() {
        pollJob?.cancel()
        pollJob = null
        foregroundPackage = null
        lastEventAt = 0L
    }

    /**
     * 讀取上一小段區間的前景切換事件。
     *
     * 查詢區間刻意比輪詢間隔長一倍：系統回報事件有時會慢個一兩秒，
     * 區間重疊加上 [lastEventAt] 去重，比「剛好接續」更不容易漏掉切換。
     */
    private fun poll() {
        val manager = appContext.getSystemService(UsageStatsManager::class.java) ?: return
        val now = System.currentTimeMillis()
        val events = manager.queryEvents(now - QUERY_WINDOW_MS, now) ?: return
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType != FOREGROUND_EVENT) continue
            if (event.timeStamp <= lastEventAt) continue
            lastEventAt = event.timeStamp
            val current = event.packageName ?: continue
            if (current == foregroundPackage) continue
            val previous = foregroundPackage
            foregroundPackage = current
            fire(previous, current)
        }
    }

    /** 目前的前景 App：取最近一段時間內最後一筆前景事件 */
    private fun latestForeground(): String? {
        val manager = appContext.getSystemService(UsageStatsManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        val events = manager.queryEvents(now - SEED_WINDOW_MS, now) ?: return null
        val event = UsageEvents.Event()
        var latest: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == FOREGROUND_EVENT) latest = event.packageName
        }
        return latest
    }

    private fun fire(previous: String?, current: String) {
        // 離開前景／進入前景也可能是別的程序的「條件結束」→ 先還原它們觸發前的設定
        RestoreOnExit.onEvent(appContext) { it.matchesForeground(previous, current) }

        val matched = matchingRoutines(previous, current)
        if (matched.isEmpty()) return
        TriggerDispatch.run(appContext, matched, TriggerSource.APP)
    }

    private fun matchingRoutines(previous: String?, current: String): List<Routine> =
        RoutineRepository.get(appContext).routines.value
            .filter { it.enabled && it.trigger.matchesForeground(previous, current) }

    /** 這次前景切換是否對得上該觸發（還沒選 App 的不比對，否則會對每個 App 都成立） */
    private fun Trigger.matchesForeground(previous: String?, current: String): Boolean {
        if (this !is Trigger.AppState || packageName.isBlank()) return false
        return if (onOpen) packageName == current else packageName == previous
    }

    companion object {
        /** 輪詢間隔（design.md 定的 3 秒：足以「開啟 App 後幾秒內執行」又不至於耗電） */
        private const val POLL_INTERVAL_MS = 3_000L
        private const val QUERY_WINDOW_MS = POLL_INTERVAL_MS * 2
        private const val SEED_WINDOW_MS = 60_000L

        /**
         * 前景事件型別。Android 10 起正式名稱為 ACTIVITY_RESUMED，
         * 更早的版本是 MOVE_TO_FOREGROUND（兩者語意相同）。
         */
        private val FOREGROUND_EVENT =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                UsageEvents.Event.ACTIVITY_RESUMED
            } else {
                @Suppress("DEPRECATION")
                UsageEvents.Event.MOVE_TO_FOREGROUND
            }

        /** 是否存在啟用中的 App 開啟／關閉程序 */
        fun hasAppStateRoutines(context: Context): Boolean =
            RoutineRepository.get(context).routines.value
                .any { it.enabled && it.trigger is Trigger.AppState }

        /**
         * 是否已取得「使用情況存取權」。
         *
         * 這是特殊權限，只能由使用者在系統設定裡開啟；未授權時 queryEvents 一律回空清單
         * （不會丟例外），所以必須主動檢查才知道要不要顯示引導卡。
         */
        fun hasUsageAccess(context: Context): Boolean = runCatching {
            val manager = context.getSystemService(AppOpsManager::class.java)
                ?: return@runCatching false
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                manager.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                manager.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        }.getOrDefault(false)
    }
}
