package com.routina.app.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.routina.app.data.RoutineRepository
import com.routina.app.engine.GeofenceManager.transitionType
import com.routina.app.model.TriggerSource

/**
 * 地理圍欄觸發入口：Play Services 以 PendingIntent 廣播送達轉換事件。
 *
 * 比照 AlarmReceiver：goAsync + finally 確保一定 finish，
 * 執行本身交給 [ExecutionService]（服務起不來時由 TriggerDispatch 降級處理）。
 */
class GeofenceReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        val pendingResult = goAsync()
        var dispatched = false

        try {
            val event = GeofencingEvent.fromIntent(intent) ?: return
            if (event.hasError()) return

            val transition = event.geofenceTransition
            if (transition != Geofence.GEOFENCE_TRANSITION_ENTER &&
                transition != Geofence.GEOFENCE_TRANSITION_EXIT
            ) {
                return
            }

            val triggeredIds = event.triggeringGeofences?.map { it.requestId } ?: return
            val repository = RoutineRepository.get(appContext)

            val matched = triggeredIds.mapNotNull { repository.findById(it) }
                // 停用中、或圍欄殘留自舊設定（觸發類型已改）→ 不執行
                .filter { it.enabled && it.trigger.transitionType() == transition }
            if (matched.isEmpty()) return

            dispatched = true
            TriggerDispatch.run(appContext, matched, TriggerSource.LOCATION) {
                pendingResult.finish()
            }
        } catch (t: Throwable) {
            // 背景觸發不得讓 App 崩潰
        } finally {
            if (!dispatched) runCatching { pendingResult.finish() }
        }
    }
}
