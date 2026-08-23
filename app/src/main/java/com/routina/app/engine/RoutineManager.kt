package com.routina.app.engine

import android.content.Context
import com.routina.app.data.RoutineRepository
import com.routina.app.model.Routine
import com.routina.app.model.RunLog
import com.routina.app.model.Trigger
import com.routina.app.model.TriggerSource

/**
 * 資料異動與執行引擎的橋接：任何 routine 的新增 / 編輯 / 啟用切換 / 刪除，
 * 都必須經由這裡，才能同步更新鬧鐘排程與監測服務。
 */
object RoutineManager {

    fun save(context: Context, routine: Routine) {
        val appContext = context.applicationContext
        val repository = RoutineRepository.get(appContext)

        // 先取消舊排程（觸發條件或時間可能已變更）
        AlarmScheduler.cancel(appContext, routine.id)
        repository.upsert(routine)

        if (routine.enabled && routine.trigger is Trigger.Time) {
            AlarmScheduler.schedule(appContext, routine)
        }
        // 觸發類型可能已從區域改成別的，syncAll 會一併移除殘留的地理圍欄
        GeofenceManager.syncAll(appContext)
        MonitorService.syncWithRoutines(appContext)
    }

    fun setEnabled(context: Context, routineId: String, enabled: Boolean) {
        val appContext = context.applicationContext
        val routine = RoutineRepository.get(appContext).findById(routineId) ?: return
        save(appContext, routine.copy(enabled = enabled))
    }

    fun delete(context: Context, routineId: String) {
        val appContext = context.applicationContext
        AlarmScheduler.cancel(appContext, routineId)
        // 資料移除後就查不到這個 id，必須在此明確移除它的地理圍欄
        GeofenceManager.remove(appContext, routineId)
        RoutineRepository.get(appContext).delete(routineId)
        MonitorService.syncWithRoutines(appContext)
    }

    /**
     * 手動執行：不受啟用狀態與觸發條件限制，維持在 App 行程內直接執行
     * （不繞道前景服務——App 開著時沒有 receiver 時限問題，等待動作也照常生效）。
     *
     * @return 這次執行的紀錄；找不到 routine 時為 null
     */
    suspend fun runNow(context: Context, routineId: String): RunLog? {
        val appContext = context.applicationContext
        val routine = RoutineRepository.get(appContext).findById(routineId) ?: return null
        return RoutineExecutor.execute(
            appContext,
            routine,
            TriggerSource.MANUAL,
            persistBlocking = false
        )
    }

    /** App 啟動時對齊排程與服務狀態 */
    fun syncAll(context: Context) {
        val appContext = context.applicationContext
        runCatching { AlarmScheduler.rescheduleAll(appContext) }
        runCatching { GeofenceManager.syncAll(appContext) }
        runCatching { MonitorService.syncWithRoutines(appContext) }
    }
}
