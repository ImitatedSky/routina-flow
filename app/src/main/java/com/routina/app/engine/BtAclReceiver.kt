package com.routina.app.engine

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.routina.app.data.RoutineRepository
import com.routina.app.model.Trigger
import com.routina.app.model.TriggerSource

/**
 * 藍牙裝置連接 / 斷開觸發入口。
 *
 * `ACTION_ACL_CONNECTED` / `ACTION_ACL_DISCONNECTED` 屬於 Android 8+ 隱式廣播限制的
 * 豁免清單，可以用 manifest 靜態註冊 → 藍牙觸發不需要任何常駐服務，成本為零。
 *
 * 比對依據是裝置位址（觸發參數為空字串代表任一裝置）：位址不需要任何權限就能讀，
 * 只有裝置「名稱」在 Android 12+ 需要 BLUETOOTH_CONNECT，因此未授權時仍能正確觸發，
 * 只是紀錄裡的名稱會降級成位址。
 */
class BtAclReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val connected = when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> true
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> false
            else -> return
        }

        val appContext = context.applicationContext
        val pendingResult = goAsync()
        var finished = false

        try {
            val address = deviceAddress(intent) ?: return
            // 連接／斷開也可能是別的程序的「條件結束」→ 先還原它們觸發前的設定
            RestoreOnExit.onEvent(appContext) { it.matches(connected, address) }

            val matched = RoutineRepository.get(appContext).routines.value
                .filter { it.enabled && it.trigger.matches(connected, address) }
            if (matched.isEmpty()) return

            finished = true
            TriggerDispatch.run(appContext, matched, TriggerSource.BLUETOOTH) {
                pendingResult.finish()
            }
        } catch (t: Throwable) {
            // 背景觸發不得讓 App 崩潰
        } finally {
            if (!finished) runCatching { pendingResult.finish() }
        }
    }

    /** 觸發類型與裝置是否相符（觸發參數為空字串＝任一裝置） */
    private fun Trigger.matches(connected: Boolean, address: String): Boolean {
        val wanted = when {
            this is Trigger.BtConnected && connected -> deviceAddress
            this is Trigger.BtDisconnected && !connected -> deviceAddress
            else -> return false
        }
        return wanted.isBlank() || wanted.equals(address, ignoreCase = true)
    }

    private fun deviceAddress(intent: Intent): String? {
        val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE) as? BluetoothDevice
        }
        return device?.address?.takeIf { it.isNotBlank() }
    }

    companion object {
        /** 讀取裝置名稱在 Android 12+ 需要 BLUETOOTH_CONNECT；未授權時 UI 只能顯示位址 */
        fun canReadDeviceName(context: Context): Boolean {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
            return runCatching {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
        }
    }
}
