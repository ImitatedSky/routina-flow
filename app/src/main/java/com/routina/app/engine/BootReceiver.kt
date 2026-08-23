package com.routina.app.engine

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 開機、App 更新、時區變更或系統時間被修改後，恢復所有排程與監測服務。
 *
 * 鬧鐘以絕對時間（RTC）排定，時區或時間一旦改變，既有排程就會落在錯誤的當地時間，
 * 必須重新計算。地理圍欄則會在重開機時被系統整批清除，同樣必須重新註冊。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED -> {
                val appContext = context.applicationContext
                runCatching { AlarmScheduler.rescheduleAll(appContext) }
                runCatching { GeofenceManager.syncAll(appContext) }
                runCatching { MonitorService.syncWithRoutines(appContext) }
            }
        }
    }
}
