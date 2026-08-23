package com.routina.app.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.routina.app.data.RoutineRepository
import com.routina.app.model.Routine
import com.routina.app.model.Trigger
import com.routina.app.model.TriggerSource
import java.util.Calendar

/**
 * 定時觸發入口：把執行交給 [ExecutionService]，並鏈式排下一次鬧鐘。
 */
class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val routineId = intent.getStringExtra(EXTRA_ROUTINE_ID) ?: return
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        var dispatched = false

        try {
            val routine = RoutineRepository.get(appContext).findById(routineId)
            if (routine != null && routine.enabled) {
                // 先保住鏈式排程：執行動作若拋出例外，這個 routine 也不會永久停擺
                runCatching { AlarmScheduler.schedule(appContext, routine) }
                if (matchesToday(routine)) {
                    dispatched = true
                    TriggerDispatch.run(appContext, routine, TriggerSource.SCHEDULE) {
                        pendingResult.finish()
                    }
                }
            }
        } catch (t: Throwable) {
            // 背景觸發不得讓 App 崩潰
        } finally {
            if (!dispatched) runCatching { pendingResult.finish() }
        }
    }

    /**
     * 執行前重新驗證今天是否符合星期條件。
     * 鬧鐘可能因為裝置休眠、時區變更或系統延遲而在非預期的日期送達。
     */
    private fun matchesToday(routine: Routine): Boolean {
        val trigger = routine.trigger as? Trigger.Time ?: return true
        val days = trigger.daysOfWeek.ifEmpty { Trigger.ALL_DAYS }
        return AlarmScheduler.isoDayOfWeek(Calendar.getInstance()) in days
    }

    companion object {
        const val EXTRA_ROUTINE_ID = "routine_id"
    }
}
