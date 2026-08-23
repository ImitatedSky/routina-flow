package com.routina.app.ui

import android.app.Application
import android.app.NotificationManager
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.routina.app.data.RoutineRepository
import com.routina.app.engine.AlarmScheduler
import com.routina.app.engine.AppUsageWatcher
import com.routina.app.engine.GeofenceManager
import com.routina.app.engine.NfcTagReader
import com.routina.app.engine.RoutinaNotificationListener
import com.routina.app.engine.RoutineExecutor
import com.routina.app.engine.RoutineManager
import com.routina.app.model.Routine
import com.routina.app.model.RunLog
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class RoutineViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RoutineRepository.get(application)

    val routines: StateFlow<List<Routine>> = repository.routines
    val logs: StateFlow<List<RunLog>> = repository.logs

    init {
        // App 啟動時對齊排程與監測服務（例如使用者曾強制停止 App）
        RoutineManager.syncAll(application)
    }

    fun findById(id: String): Routine? = repository.findById(id)

    fun save(routine: Routine) = RoutineManager.save(getApplication(), routine)

    fun setEnabled(id: String, enabled: Boolean) =
        RoutineManager.setEnabled(getApplication(), id, enabled)

    fun delete(id: String) = RoutineManager.delete(getApplication(), id)

    /** 調整首頁清單先後順序（僅顯示順序，即時持久化） */
    fun reorder(from: Int, to: Int) = RoutineManager.reorder(getApplication(), from, to)

    /**
     * 手動執行。含「等待」「朗讀」「HTTP」等長時動作時可能耗時數十秒，
     * 因此在 viewModelScope 內執行，完成後把紀錄交給 [onFinished]（主執行緒）。
     */
    fun runNow(id: String, onFinished: (RunLog?) -> Unit) {
        viewModelScope.launch {
            onFinished(RoutineManager.runNow(getApplication(), id))
        }
    }

    fun clearLogs() = repository.clearLogs()

    /** 是否具備精確鬧鐘權限（Android 12+），否則 UI 顯示降級提示 */
    fun canScheduleExactAlarms(): Boolean =
        AlarmScheduler.canScheduleExact(getApplication())

    /** 是否具備「顯示在其他應用程式上層」權限（背景啟動 App / 網址的前提） */
    fun canDrawOverlays(): Boolean = RoutineExecutor.canDrawOverlays(getApplication())

    /** 通知是否已授權（「顯示通知」動作與權限引導通知都需要） */
    fun areNotificationsEnabled(): Boolean =
        NotificationManagerCompat.from(getApplication()).areNotificationsEnabled()

    /** 裝置是否支援 Google Play Services（無 GMS 時區域觸發不可用） */
    fun isPlayServicesAvailable(): Boolean =
        GeofenceManager.isPlayServicesAvailable(getApplication())

    /** 是否具備背景位置權限（Android 10+ 的「一律允許」，註冊地理圍欄的前提） */
    fun hasBackgroundLocation(): Boolean =
        GeofenceManager.hasBackgroundLocation(getApplication())

    /** 是否具備前景精確位置權限（讀取 Wi-Fi SSID 的前提） */
    fun hasForegroundLocation(): Boolean =
        GeofenceManager.hasForegroundLocation(getApplication())

    /** 是否具備「修改系統設定」權限（螢幕亮度動作的前提） */
    fun canWriteSettings(): Boolean = RoutineExecutor.canWriteSettings(getApplication())

    /** 是否具備「通知存取權」（通知觸發的前提；未授權時系統不會綁定監聽服務） */
    fun hasNotificationAccess(): Boolean =
        RoutinaNotificationListener.isEnabled(getApplication())

    /** 是否具備「使用情況存取權」（App 開啟／關閉觸發的前提） */
    fun hasUsageAccess(): Boolean = AppUsageWatcher.hasUsageAccess(getApplication())

    /** 裝置是否有 NFC 硬體（沒有時 NFC 觸發不可用） */
    fun isNfcAvailable(): Boolean = NfcTagReader.isAvailable(getApplication())

    /** NFC 是否已開啟（有硬體但關著時掃描不會發生） */
    fun isNfcEnabled(): Boolean = NfcTagReader.isEnabled(getApplication())

    /** 是否有可用於日出日落計算的座標（沿用任一已設定的區域觸發地點） */
    fun hasSunLocation(): Boolean = AlarmScheduler.sunLocation(getApplication()) != null

    /** 日出日落計算用座標（卡片狀態晶片計算定時觸發下次執行時沿用） */
    fun sunLocation(): AlarmScheduler.SunLocation? = AlarmScheduler.sunLocation(getApplication())

    /** 是否具備「勿擾模式存取權」（勿擾 / 響鈴模式動作的前提） */
    fun hasDndAccess(): Boolean = runCatching {
        getApplication<Application>()
            .getSystemService(NotificationManager::class.java)
            ?.isNotificationPolicyAccessGranted == true
    }.getOrDefault(false)

    /** 權限狀態改變（例如剛授權精確鬧鐘或背景位置）後重新對齊排程、地理圍欄與監測服務 */
    fun syncAll() = RoutineManager.syncAll(getApplication())
}
