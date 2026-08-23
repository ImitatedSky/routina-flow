package com.routina.app.engine

import android.content.Context
import com.routina.app.model.Routine
import com.routina.app.model.TriggerSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 背景觸發的統一入口。
 *
 * 所有觸發路徑（AlarmReceiver / GeofenceReceiver / MonitorService / BtAclReceiver）
 * 都改走這裡：先交給 [ExecutionService]（解除廣播接收器的時限），
 * 系統擋下背景啟動前景服務時再降級為背景執行緒同步執行並跳過等待動作。
 */
object TriggerDispatch {

    /**
     * 降級執行用的 scope。刻意獨立於任何元件生命週期：
     * 走到這裡代表前景服務已經起不來，只能趁行程還活著把動作做完。
     */
    private val fallbackScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * 執行 [routines]（已由呼叫端過濾出符合觸發條件者）。
     *
     * @param onFinished 全部工作交付（或降級執行完成）後呼叫一次；
     * 供 BroadcastReceiver 決定何時 `PendingResult.finish()`。
     */
    fun run(
        context: Context,
        routines: List<Routine>,
        source: TriggerSource,
        onFinished: (() -> Unit)? = null
    ) {
        val appContext = context.applicationContext
        val degraded = routines.filterNot { ExecutionService.start(appContext, it.id, source) }

        if (degraded.isEmpty()) {
            onFinished?.invoke()
            return
        }

        fallbackScope.launch {
            degraded.forEach { routine ->
                runCatching {
                    RoutineExecutor.execute(
                        context = appContext,
                        routine = routine,
                        source = source,
                        // 行程隨時可能在接收器返回後被回收 → 紀錄必須同步落地
                        persistBlocking = true,
                        allowWait = false,
                        note = ExecutionService.DEGRADED_NOTE
                    )
                }
            }
            onFinished?.invoke()
        }
    }

    /** 單一 routine 的便捷版 */
    fun run(
        context: Context,
        routine: Routine,
        source: TriggerSource,
        onFinished: (() -> Unit)? = null
    ) = run(context, listOf(routine), source, onFinished)
}
