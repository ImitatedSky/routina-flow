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
        // 清單內容或名稱可能已變，讓桌面小工具重讀
        RoutinaWidgets.refresh(appContext)
    }

    fun setEnabled(context: Context, routineId: String, enabled: Boolean) {
        val appContext = context.applicationContext
        val routine = RoutineRepository.get(appContext).findById(routineId) ?: return
        save(appContext, routine.copy(enabled = enabled))
    }

    /**
     * 調整清單先後順序。純顯示順序，不影響任何觸發排程或監測，因此只需持久化，
     * 不必重排鬧鐘 / 地理圍欄 / 監測服務。
     */
    fun reorder(context: Context, from: Int, to: Int) {
        val appContext = context.applicationContext
        RoutineRepository.get(appContext).reorder(from, to)
        // 小工具依清單順序顯示，排序後也要刷新
        RoutinaWidgets.refresh(appContext)
    }

    /**
     * 複製一個 routine：產生新 id、名稱加「複製」、**預設停用**（避免與原本同時自動觸發），
     * 觸發／動作／顏色照抄。回傳新 id；找不到來源時回 null。
     */
    fun duplicate(context: Context, routineId: String): String? {
        val appContext = context.applicationContext
        val original = RoutineRepository.get(appContext).findById(routineId) ?: return null
        val copy = original.copy(
            id = java.util.UUID.randomUUID().toString(),
            name = original.name + " 複製",
            enabled = false,
            createdAt = System.currentTimeMillis()
        )
        save(appContext, copy)
        return copy.id
    }

    fun delete(context: Context, routineId: String) {
        val appContext = context.applicationContext
        AlarmScheduler.cancel(appContext, routineId)
        // 資料移除後就查不到這個 id，必須在此明確移除它的地理圍欄
        GeofenceManager.remove(appContext, routineId)
        RoutineRepository.get(appContext).delete(routineId)
        MonitorService.syncWithRoutines(appContext)
        RoutinaWidgets.refresh(appContext)
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
