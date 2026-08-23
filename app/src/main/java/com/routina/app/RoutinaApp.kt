package com.routina.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

class RoutinaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels(this)
    }

    companion object {
        /** 「顯示通知」動作使用 */
        const val CHANNEL_ACTIONS = "routina_actions"

        /** 前景監測服務與前景執行服務使用（低重要度，不打擾） */
        const val CHANNEL_MONITOR = "routina_monitor"

        /** 權限引導等系統提示 */
        const val CHANNEL_ALERTS = "routina_alerts"

        /** 背景無法直接啟動 App / 網址時，改以可點擊開啟的通知呈現 */
        const val CHANNEL_LAUNCH = "routina_launch"

        /** 拍照 / 連拍 / 錄音完成後，顯示結果並可點擊開啟檔案的通知 */
        const val CHANNEL_CAPTURE = "routina_capture"

        fun createNotificationChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return

            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ACTIONS,
                    "例行程序通知",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "例行程序執行「顯示通知」動作時發出的通知" }
            )

            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_MONITOR,
                    "背景監測與執行",
                    NotificationManager.IMPORTANCE_MIN
                ).apply {
                    description =
                        "監測電量／Wi-Fi／系統狀態／App 使用情況，以及在背景執行例行程序時的通知"
                }
            )

            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ALERTS,
                    "權限提醒",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "需要你授權才能完成動作時的提醒" }
            )

            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_LAUNCH,
                    "開啟提示",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply { description = "背景無法直接開啟 App 或網址時，改以通知提供捷徑" }
            )

            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_CAPTURE,
                    "擷取結果",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = "拍照 / 連拍 / 錄音完成後顯示結果，點擊可用系統檢視器開啟檔案" }
            )
        }
    }
}
