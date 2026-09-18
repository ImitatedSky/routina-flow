package com.routina.app.engine

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Routina 要不要回應 NFC 標籤。
 *
 * 關掉＝停用 [NfcDispatchActivity] 這個元件，系統的標籤派送就不再找上 Routina：
 * 碰到標籤時 Routina 不會被叫起來、NFC 觸發的程序不會執行，也不會再出現在
 * 「用哪個 App 開啟」的選擇器裡。系統的 NFC 開關不動，其他 App 照用
 * （App 沒有權限開關系統 NFC，那是系統／預載 App 才有的權限）。
 *
 * 前景讀取不受影響：標籤庫的掃描與寫入是畫面自己開 reader mode 讀的，不經過這個元件。
 *
 * 狀態直接存在系統的元件啟用狀態裡（PackageManager 會持久化，跨重開機與改版都在），
 * 不另外存一份偏好——兩份會漂移，而真正決定行為的是元件狀態那一份。
 */
object NfcDispatch {

    // Compose 讀得到：設定畫面切換後開關要立刻反映
    var enabled by mutableStateOf(true)
        private set

    /** App 啟動時讀一次真實狀態 */
    fun load(context: Context) {
        enabled = runCatching {
            // DEFAULT＝照 manifest（有註冊 intent-filter＝啟用），只有明確 DISABLED 才算關
            context.packageManager.getComponentEnabledSetting(component(context)) !=
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }.getOrDefault(true)
    }

    fun setEnabled(context: Context, on: Boolean) {
        val state = if (on) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        // 設不動就維持原狀，不要讓畫面顯示成已經改掉
        val changed = runCatching {
            context.packageManager.setComponentEnabledSetting(
                component(context),
                state,
                PackageManager.DONT_KILL_APP
            )
        }.isSuccess
        if (changed) enabled = on
    }

    private fun component(context: Context) =
        ComponentName(context.applicationContext, NfcDispatchActivity::class.java)
}
