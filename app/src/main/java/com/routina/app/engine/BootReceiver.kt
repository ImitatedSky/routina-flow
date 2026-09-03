package com.routina.app.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.routina.app.data.RoutineRepository
import com.routina.app.model.Trigger
import com.routina.app.model.TriggerSource

/**
 * 開機、App 更新、時區變更或系統時間被修改後，恢復所有排程與監測服務。
 *
 * 鬧鐘以絕對時間（RTC）排定，時區或時間一旦改變，既有排程就會落在錯誤的當地時間，
 * 必須重新計算。地理圍欄則會在重開機時被系統整批清除，同樣必須重新註冊。
 *
 * 開機完成（ACTION_BOOT_COMPLETED）另外分派「開機完成」觸發：這類觸發沒有常駐服務，
 * 就靠這個 manifest 靜態 receiver 在開機當下執行一次。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> {
                runCatching { AlarmScheduler.rescheduleAll(appContext) }
                runCatching { GeofenceManager.syncAll(appContext) }
                runCatching { MonitorService.syncWithRoutines(appContext) }
            }
        }
        // 只有開機完成才算「開機」；時區 / 時間變更、App 更新不觸發
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            dispatchDeviceBoot(appContext)
        }
    }

    /** 分派「開機完成」觸發（沿用 BtAclReceiver 的 goAsync 模式，解除接收器時限） */
    private fun dispatchDeviceBoot(appContext: Context) {
        val matched = RoutineRepository.get(appContext).routines.value
            .filter { it.enabled && it.trigger is Trigger.DeviceBoot }
        if (matched.isEmpty()) return

        val pendingResult = goAsync()
        var finished = false
        try {
            finished = true
            TriggerDispatch.run(appContext, matched, TriggerSource.SYSTEM) {
                pendingResult.finish()
            }
        } catch (t: Throwable) {
            // 背景觸發不得讓 App 崩潰
        } finally {
            if (!finished) runCatching { pendingResult.finish() }
        }
    }
}
