package com.routina.app.engine

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.routina.app.MainActivity
import com.routina.app.R
import com.routina.app.RoutinaApp
import com.routina.app.data.RoutineRepository
import com.routina.app.model.Routine
import com.routina.app.model.Trigger
import com.routina.app.model.TriggerSource
import com.routina.app.model.needsMonitor

/**
 * 前景監測服務：動態註冊充電 / 電量 / 系統狀態廣播與 Wi-Fi 網路回呼，
 * 並承載 App 開啟／關閉觸發的使用情況輪詢（[AppUsageWatcher]）。
 *
 * Android 8+ 禁止以 manifest 靜態註冊 ACTION_POWER_CONNECTED 等隱式廣播，
 * 因此需要一個常駐服務動態註冊。
 *
 * 只有在「存在啟用中且觸發類型需要監測的 routine」時才運行
 * （充電 / 電量 / Wi-Fi / 飛航 / 勿擾 / 省電 / App 開啟關閉）；
 * 全部停用或刪除時自動 stopSelf → 平時零常駐。
 * 藍牙、NFC 與通知觸發不在此列：分別由靜態 receiver、系統 NFC dispatch
 * 與系統綁定的通知監聽服務接收，成本為零。
 */
class MonitorService : Service() {

    private var powerReceiver: PowerReceiver? = null
    private var systemStateReceiver: SystemStateReceiver? = null
    private var wifiCallback: WifiCallback? = null
    private var screenReceiver: ScreenReceiver? = null

    /**
     * App 開啟／關閉的偵測器。只有螢幕亮著時才輪詢，因此需要一個螢幕開關的
     * 動態 receiver（ACTION_SCREEN_ON/OFF 無法靜態註冊）來啟停它。
     */
    private var appUsageWatcher: AppUsageWatcher? = null

    /** 電量門檻去重：已觸發過的 routine id（電量回到門檻另一側後移除） */
    private val firedBatteryBelow = mutableSetOf<String>()
    private val firedBatteryAbove = mutableSetOf<String>()

    /**
     * 是否已收到第一筆電量讀數。
     * 服務啟動時電量可能已在門檻以下（或以上），此時只記錄狀態、不視為「穿越」，
     * 避免每次開機或服務重啟都誤觸發。
     */
    private var batteryStateInitialized = false

    /** 目前的 Wi-Fi 網路：註冊當下已連線的網路會先記在這裡，避免啟動即誤觸發 */
    private var currentWifi: Network? = null

    /** 勿擾 / 省電的最後已知狀態：廣播可能重複送達，只在真的改變時觸發 */
    private var lastDndOn: Boolean? = null
    private var lastPowerSaveOn: Boolean? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // 進不了前景（例如背景啟動限制）就必須馬上收攤，
        // 否則 5 秒後系統會丟 ForegroundServiceDidNotStartInTimeException 讓 App 閃退。
        if (!startForegroundSafely()) {
            stopSelf()
            return
        }
        registerPowerReceiver()
        registerSystemStateReceiver()
        registerWifiCallback()
        startAppUsageWatcher()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!startForegroundSafely()) {
            stopSelf()
            return START_NOT_STICKY
        }
        // 沒有任何需要監測的 routine → 立即停止服務
        if (!hasMonitoredRoutines(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        // 程序清單可能剛剛才變動（新增 / 啟用切換）→ 重新判定要不要輪詢使用情況
        appUsageWatcher?.sync()
        return START_STICKY
    }

    override fun onDestroy() {
        powerReceiver?.let { runCatching { unregisterReceiver(it) } }
        powerReceiver = null
        systemStateReceiver?.let { runCatching { unregisterReceiver(it) } }
        systemStateReceiver = null
        screenReceiver?.let { runCatching { unregisterReceiver(it) } }
        screenReceiver = null
        wifiCallback?.let { callback ->
            runCatching {
                getSystemService(ConnectivityManager::class.java)
                    ?.unregisterNetworkCallback(callback)
            }
        }
        wifiCallback = null
        appUsageWatcher?.release()
        appUsageWatcher = null
        super.onDestroy()
    }

    // ---------- 註冊 ----------

    private fun registerPowerReceiver() {
        if (powerReceiver != null) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        powerReceiver = PowerReceiver().also { registerNotExported(it, filter) }
    }

    private fun registerSystemStateReceiver() {
        if (systemStateReceiver != null) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED)
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        // 記下目前狀態當基準，避免註冊後系統立刻補送一次廣播就誤觸發
        lastDndOn = readDndOn()
        lastPowerSaveOn = readPowerSaveOn()
        systemStateReceiver = SystemStateReceiver().also { registerNotExported(it, filter) }
    }

    /**
     * 建立 App 開啟／關閉的偵測器，並註冊螢幕開關 receiver 控制它的啟停。
     * 目前的螢幕狀態要先讀進來當初始值（服務也可能在螢幕熄滅時才啟動）。
     */
    private fun startAppUsageWatcher() {
        if (appUsageWatcher != null) return
        val watcher = AppUsageWatcher(this)
        appUsageWatcher = watcher

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        screenReceiver = ScreenReceiver().also { registerNotExported(it, filter) }

        watcher.setScreenOn(readScreenOn())
        watcher.sync()
    }

    private fun registerNotExported(receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
    }

    /**
     * Wi-Fi 連線 / 斷線：用 NetworkCallback 而非已被系統移除的 NETWORK_STATE_CHANGED 廣播。
     *
     * 註冊時先把「目前已連線的 Wi-Fi」記為 currentWifi：系統在註冊後會立刻補送一次
     * onAvailable/onCapabilitiesChanged，先記下來才不會把既有連線當成新連上。
     */
    private fun registerWifiCallback() {
        if (wifiCallback != null) return
        val manager = getSystemService(ConnectivityManager::class.java) ?: return
        currentWifi = runCatching { activeWifiNetwork(manager) }.getOrNull()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        val callback = WifiCallback()
        val registered = runCatching { manager.registerNetworkCallback(request, callback) }
        if (registered.isSuccess) wifiCallback = callback
    }

    @Suppress("DEPRECATION")
    private fun activeWifiNetwork(manager: ConnectivityManager): Network? {
        val active = manager.activeNetwork
        if (active != null && manager.isWifi(active)) return active
        // Wi-Fi 不是預設網路時（例如同時有 VPN）改掃一遍全部網路
        return manager.allNetworks.firstOrNull { manager.isWifi(it) }
    }

    private fun ConnectivityManager.isWifi(network: Network): Boolean =
        getNetworkCapabilities(network)?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

    /** @return 是否成功進入前景 */
    private fun startForegroundSafely(): Boolean =
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    buildNotification(),
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, buildNotification())
            }
        }.isSuccess

    private fun buildNotification(): Notification {
        val pending = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, RoutinaApp.CHANNEL_MONITOR)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Routina 正在監測")
            .setContentText("監測電量、Wi-Fi、系統狀態與 App 使用情況以觸發例行程序")
            .setContentIntent(pending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .build()
    }

    // ---------- 廣播接收 ----------

    /** 動態註冊的充電 / 電量廣播接收器 */
    private inner class PowerReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED ->
                    runMatching(TriggerSource.POWER) { it is Trigger.PowerConnected }

                Intent.ACTION_POWER_DISCONNECTED ->
                    runMatching(TriggerSource.POWER) { it is Trigger.PowerDisconnected }

                Intent.ACTION_BATTERY_CHANGED -> handleBatteryChanged(intent)
            }
        }
    }

    /** 飛航 / 勿擾 / 省電模式切換 */
    private inner class SystemStateReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_AIRPLANE_MODE_CHANGED -> {
                    val on = intent.getBooleanExtra("state", readAirplaneOn())
                    runMatching(TriggerSource.SYSTEM) {
                        it is Trigger.AirplaneMode && it.turnedOn == on
                    }
                }

                NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> {
                    val on = readDndOn()
                    if (on == lastDndOn) return
                    lastDndOn = on
                    runMatching(TriggerSource.SYSTEM) {
                        it is Trigger.DndChanged && it.turnedOn == on
                    }
                }

                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                    val on = readPowerSaveOn()
                    if (on == lastPowerSaveOn) return
                    lastPowerSaveOn = on
                    runMatching(TriggerSource.SYSTEM) {
                        it is Trigger.PowerSave && it.turnedOn == on
                    }
                }
            }
        }
    }

    /** 螢幕亮 / 暗：只有亮著時才輪詢使用情況（App 觸發的耗電控制） */
    private inner class ScreenReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_ON -> appUsageWatcher?.setScreenOn(true)
                Intent.ACTION_SCREEN_OFF -> appUsageWatcher?.setScreenOn(false)
            }
        }
    }

    /** Wi-Fi 連線 / 斷線回呼 */
    private inner class WifiCallback : ConnectivityManager.NetworkCallback() {

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (!capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return
            // 同一個網路的能力更新（訊號、驗證狀態…）不算重新連上
            if (network == currentWifi) return
            currentWifi = network
            handleWifiConnected(readSsid(capabilities))
        }

        override fun onLost(network: Network) {
            if (network != currentWifi) return
            currentWifi = null
            runMatching(TriggerSource.WIFI) { it is Trigger.WifiDisconnected }
        }
    }

    // ---------- 觸發判定 ----------

    /**
     * Wi-Fi 已連線。
     *
     * 指定了 SSID 但讀不到目前 SSID（Android 9+ 需要定位權限且定位服務開啟）時
     * **不觸發**——寧可不執行，也不要在使用者不知道連到哪個網路的情況下亂執行；
     * 清單卡片會顯示需要定位權限的警示。
     */
    private fun handleWifiConnected(ssid: String?) {
        runMatching(TriggerSource.WIFI) { trigger ->
            val wanted = (trigger as? Trigger.WifiConnected)?.ssid ?: return@runMatching false
            when {
                wanted.isBlank() -> true
                ssid == null -> false
                else -> ssid == wanted
            }
        }
    }

    /**
     * 電量門檻。
     *
     * - 低於：電量降至門檻（含）以下時觸發一次，回升至門檻以上後重置
     * - 高於：電量升至門檻（含）以上時觸發一次，回落至門檻以下後重置
     *
     * 兩者的已觸發狀態各自獨立。
     */
    private fun handleBatteryChanged(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return
        val percent = level * 100 / scale

        val seeding = !batteryStateInitialized
        batteryStateInitialized = true

        val triggered = mutableListOf<Routine>()
        RoutineRepository.get(this).routines.value.forEach { routine ->
            when (val trigger = routine.trigger) {
                is Trigger.BatteryBelow -> evaluateThreshold(
                    routine = routine,
                    crossed = percent <= trigger.threshold,
                    fired = firedBatteryBelow,
                    seeding = seeding,
                    triggered = triggered
                )

                is Trigger.BatteryAbove -> evaluateThreshold(
                    routine = routine,
                    crossed = percent >= trigger.threshold,
                    fired = firedBatteryAbove,
                    seeding = seeding,
                    triggered = triggered
                )

                else -> Unit
            }
        }
        if (triggered.isNotEmpty()) {
            TriggerDispatch.run(this, triggered, TriggerSource.BATTERY)
        }
    }

    private fun evaluateThreshold(
        routine: Routine,
        crossed: Boolean,
        fired: MutableSet<String>,
        seeding: Boolean,
        triggered: MutableList<Routine>
    ) {
        val alreadyFired = routine.id in fired
        if (crossed) {
            // 停用中的 routine 既不執行，也不留下已觸發旗標（否則重新啟用後會被誤判為已觸發）
            if (!routine.enabled) return
            if (alreadyFired) return
            fired += routine.id
            // 首筆讀數只建立基準狀態，不算穿越
            if (!seeding) triggered += routine
        } else if (alreadyFired) {
            // 電量回到門檻另一側 → 重置，可再次觸發
            fired -= routine.id
        }
    }

    private fun runMatching(source: TriggerSource, predicate: (Trigger) -> Boolean) {
        val matched = RoutineRepository.get(this).routines.value
            .filter { it.enabled && predicate(it.trigger) }
        if (matched.isEmpty()) return
        TriggerDispatch.run(this, matched, source)
    }

    // ---------- 系統狀態讀取 ----------

    private fun readAirplaneOn(): Boolean = runCatching {
        Settings.Global.getInt(contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1
    }.getOrDefault(false)

    private fun readDndOn(): Boolean = runCatching {
        val filter = getSystemService(NotificationManager::class.java)?.currentInterruptionFilter
        filter != null &&
            filter != NotificationManager.INTERRUPTION_FILTER_ALL &&
            filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
    }.getOrDefault(false)

    private fun readPowerSaveOn(): Boolean = runCatching {
        getSystemService(PowerManager::class.java)?.isPowerSaveMode == true
    }.getOrDefault(false)

    /** 螢幕是否亮著（讀不到時當成亮著：寧可多輪詢幾次也不要漏掉觸發） */
    private fun readScreenOn(): Boolean = runCatching {
        getSystemService(PowerManager::class.java)?.isInteractive != false
    }.getOrDefault(true)

    /**
     * 目前連線的 Wi-Fi SSID。
     *
     * Android 12+ 由 NetworkCapabilities.transportInfo 取得（舊 API 已被限制），
     * 更早的版本沿用 WifiManager.connectionInfo。
     * 未取得定位權限、或定位服務關閉時系統會回 `<unknown ssid>` → 視為讀不到。
     */
    private fun readSsid(capabilities: NetworkCapabilities?): String? {
        val raw = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (capabilities?.transportInfo as? WifiInfo)?.ssid
        } else {
            @Suppress("DEPRECATION")
            getSystemService(WifiManager::class.java)?.connectionInfo?.ssid
        }
        // 系統回傳的 SSID 會帶雙引號（十六進位形式則不帶）
        val value = raw?.trim()?.removeSurrounding("\"")?.trim()
        return value?.takeIf { it.isNotBlank() && it != UNKNOWN_SSID }
    }

    companion object {
        private const val NOTIFICATION_ID = 42

        /** 無定位權限 / 定位關閉時系統回傳的佔位字串 */
        private const val UNKNOWN_SSID = "<unknown ssid>"

        /** 是否存在啟用中且需要監測的 routine */
        fun hasMonitoredRoutines(context: Context): Boolean =
            RoutineRepository.get(context).routines.value
                .any { it.enabled && it.trigger.needsMonitor }

        /**
         * 依目前 routine 狀態啟動或停止監測服務。
         * 新增 / 編輯 / 啟用切換 / 刪除 / 開機後都應呼叫。
         */
        fun syncWithRoutines(context: Context) {
            val appContext = context.applicationContext
            val intent = Intent(appContext, MonitorService::class.java)
            if (hasMonitoredRoutines(appContext)) {
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        appContext.startForegroundService(intent)
                    } else {
                        appContext.startService(intent)
                    }
                }
            } else {
                runCatching { appContext.stopService(intent) }
            }
        }
    }
}
