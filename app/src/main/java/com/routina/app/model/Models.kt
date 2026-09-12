package com.routina.app.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * 觸發條件。以 kotlinx.serialization 多型序列化（JSON 內以 "type" 欄位判別）。
 */
@Serializable
sealed class Trigger {

    /**
     * 手動執行：沒有任何自動觸發條件，只透過清單卡片與編輯畫面的 ▶ 手動執行。
     *
     * 以「手動」作為一種觸發（sentinel）而非把 trigger 改為 nullable：對既有大量
     * `when(trigger)` 與非空假設破壞最小，積木隱喻也維持（永遠有一塊帽子積木，只是它說「手動」）。
     * 手動 routine 不排程、不監測、不耗電，是新建空白 routine 的預設。
     */
    @Serializable
    @SerialName("manual")
    data object Manual : Trigger()

    /**
     * 每日定時：時間 + 星期幾（1=週一 … 7=週日，ISO-8601）。空集合視為每天。
     *
     * [mode] 為日出 / 日落時忽略 [hour]/[minute]，改以當地日出日落時間加上
     * [offsetMinutes] 的偏移（負數＝提前）。兩個欄位都有預設值，舊版 JSON 照常讀入。
     */
    @Serializable
    @SerialName("time")
    data class Time(
        val hour: Int = 8,
        val minute: Int = 0,
        val daysOfWeek: Set<Int> = ALL_DAYS,
        val mode: TimeMode = TimeMode.FIXED,
        val offsetMinutes: Int = 0
    ) : Trigger()

    /** 接上電源 */
    @Serializable
    @SerialName("power_connected")
    data object PowerConnected : Trigger()

    /** 拔除電源 */
    @Serializable
    @SerialName("power_disconnected")
    data object PowerDisconnected : Trigger()

    /** 電量向下穿越門檻 */
    @Serializable
    @SerialName("battery_below")
    data class BatteryBelow(val threshold: Int = 20) : Trigger()

    /** 進入圓形區域（地理圍欄 ENTER） */
    @Serializable
    @SerialName("location_enter")
    data class LocationEnter(
        val lat: Double = 0.0,
        val lng: Double = 0.0,
        val radiusM: Int = DEFAULT_RADIUS_M,
        val label: String = ""
    ) : Trigger()

    /** 離開圓形區域（地理圍欄 EXIT） */
    @Serializable
    @SerialName("location_exit")
    data class LocationExit(
        val lat: Double = 0.0,
        val lng: Double = 0.0,
        val radiusM: Int = DEFAULT_RADIUS_M,
        val label: String = ""
    ) : Trigger()

    /** 藍牙裝置連接（[deviceAddress] 空字串＝任一裝置） */
    @Serializable
    @SerialName("bt_connected")
    data class BtConnected(
        val deviceAddress: String = "",
        val deviceName: String = ""
    ) : Trigger()

    /** 藍牙裝置斷開（[deviceAddress] 空字串＝任一裝置） */
    @Serializable
    @SerialName("bt_disconnected")
    data class BtDisconnected(
        val deviceAddress: String = "",
        val deviceName: String = ""
    ) : Trigger()

    /** 連上 Wi-Fi（[ssid] 空字串＝任一網路） */
    @Serializable
    @SerialName("wifi_connected")
    data class WifiConnected(val ssid: String = "") : Trigger()

    /** Wi-Fi 斷線 */
    @Serializable
    @SerialName("wifi_disconnected")
    data object WifiDisconnected : Trigger()

    /** 電量向上穿越門檻 */
    @Serializable
    @SerialName("battery_above")
    data class BatteryAbove(val threshold: Int = 80) : Trigger()

    /** 充電完成：電量達 100% / 狀態為 FULL（每次充電只觸發一次） */
    @Serializable
    @SerialName("battery_full")
    data object BatteryFull : Trigger()

    /** 飛航模式切換 */
    @Serializable
    @SerialName("airplane_mode")
    data class AirplaneMode(val turnedOn: Boolean = true) : Trigger()

    /** 勿擾模式切換 */
    @Serializable
    @SerialName("dnd_changed")
    data class DndChanged(val turnedOn: Boolean = true) : Trigger()

    /** 省電模式切換 */
    @Serializable
    @SerialName("power_save")
    data class PowerSave(val turnedOn: Boolean = true) : Trigger()

    /**
     * 掃描到已登錄的 NFC 標籤。
     *
     * [uid] 為標籤的硬體 UID（大寫 hex 字串），在 App 內「掃描標籤」時讀入；
     * [label] 由使用者自填（例「床頭標籤」），只用於顯示。
     */
    @Serializable
    @SerialName("nfc_tag")
    data class NfcTag(
        val uid: String = "",
        val label: String = ""
    ) : Trigger()

    /**
     * 收到通知。
     *
     * [packageName] 空字串＝任一 App；[keyword] 空字串＝不過濾內容，
     * 否則比對通知標題與內容是否包含（不分大小寫）。
     */
    @Serializable
    @SerialName("notification_posted")
    data class NotificationPosted(
        val packageName: String = "",
        val appName: String = "",
        val keyword: String = ""
    ) : Trigger()

    /** 指定 App 進入前景（[onOpen] = true）或離開前景（false） */
    @Serializable
    @SerialName("app_state")
    data class AppState(
        val packageName: String = "",
        val appName: String = "",
        val onOpen: Boolean = true
    ) : Trigger()

    /** 解鎖螢幕（ACTION_USER_PRESENT） */
    @Serializable
    @SerialName("screen_unlocked")
    data object ScreenUnlocked : Trigger()

    /** 螢幕開啟（ACTION_SCREEN_ON） */
    @Serializable
    @SerialName("screen_on")
    data object ScreenOn : Trigger()

    /** 螢幕關閉（ACTION_SCREEN_OFF） */
    @Serializable
    @SerialName("screen_off")
    data object ScreenOff : Trigger()

    /** 插入耳機（ACTION_HEADSET_PLUG，state=1） */
    @Serializable
    @SerialName("headset_plugged")
    data object HeadsetPlugged : Trigger()

    /** 拔除耳機（ACTION_HEADSET_PLUG，state=0） */
    @Serializable
    @SerialName("headset_unplugged")
    data object HeadsetUnplugged : Trigger()

    /** 開機完成（ACTION_BOOT_COMPLETED，由 manifest 的 BootReceiver 分派，不需常駐服務） */
    @Serializable
    @SerialName("device_boot")
    data object DeviceBoot : Trigger()

    companion object {
        val ALL_DAYS: Set<Int> = setOf(1, 2, 3, 4, 5, 6, 7)

        /** 地理圍欄半徑範圍（下限為 Geofencing API 的建議最小值） */
        const val MIN_RADIUS_M = 100
        const val MAX_RADIUS_M = 1000
        const val DEFAULT_RADIUS_M = 300

        /** 日出 / 日落偏移分鐘的可設定範圍 */
        const val MIN_SUN_OFFSET_MIN = -120
        const val MAX_SUN_OFFSET_MIN = 120
    }
}

/** 定時觸發的時間模式 */
@Serializable
enum class TimeMode {
    @SerialName("fixed")
    FIXED,

    @SerialName("sunrise")
    SUNRISE,

    @SerialName("sunset")
    SUNSET
}

/**
 * 觸發條件是否需要 MonitorService 常駐監測。
 *
 * 不在此列的背景觸發各有更省的路徑：藍牙 ACL 廣播屬於隱式廣播豁免清單，
 * 由 manifest 靜態 receiver 接收；NFC 標籤由系統 dispatch 到 NfcDispatchActivity；
 * 通知則由系統綁定的 NotificationListenerService 送達；開機完成由 manifest 的
 * BootReceiver 在開機當下分派——都不需要常駐服務。
 * App 開啟／關閉沒有對應的系統廣播，只能自行輪詢使用情況，因此必須有服務承載。
 * 螢幕開關 / 解鎖、插拔耳機、充電完成則靠服務動態註冊的廣播接收，同樣需要常駐服務。
 */
val Trigger.needsMonitor: Boolean
    get() = this is Trigger.PowerConnected ||
        this is Trigger.PowerDisconnected ||
        this is Trigger.BatteryBelow ||
        this is Trigger.BatteryAbove ||
        this is Trigger.BatteryFull ||
        this is Trigger.WifiConnected ||
        this is Trigger.WifiDisconnected ||
        this is Trigger.AirplaneMode ||
        this is Trigger.DndChanged ||
        this is Trigger.PowerSave ||
        this is Trigger.AppState ||
        this is Trigger.ScreenUnlocked ||
        this is Trigger.ScreenOn ||
        this is Trigger.ScreenOff ||
        this is Trigger.HeadsetPlugged ||
        this is Trigger.HeadsetUnplugged

/**
 * 「條件結束」時對應的反向觸發；沒有結束概念的觸發為 null（不提供「離開時還原」）。
 *
 * 例如「進入區域」的結束是「離開同一個區域」、「連上 Wi-Fi」的結束是「Wi-Fi 斷線」。
 * 用途是離開時還原：分派點收到事件時，拿這個反向觸發去比對就知道哪些程序的條件結束了
 * （見 [com.routina.app.engine.RestoreOnExit]）。
 * Wi-Fi 斷線本身不帶參數，反向只能配到「連上任一 Wi-Fi」；解鎖螢幕的結束則視為螢幕關閉。
 */
val Trigger.opposite: Trigger?
    get() = when (this) {
        is Trigger.LocationEnter -> Trigger.LocationExit(lat, lng, radiusM, label)
        is Trigger.LocationExit -> Trigger.LocationEnter(lat, lng, radiusM, label)
        is Trigger.WifiConnected -> Trigger.WifiDisconnected
        Trigger.WifiDisconnected -> Trigger.WifiConnected()
        is Trigger.BtConnected -> Trigger.BtDisconnected(deviceAddress, deviceName)
        is Trigger.BtDisconnected -> Trigger.BtConnected(deviceAddress, deviceName)
        Trigger.PowerConnected -> Trigger.PowerDisconnected
        Trigger.PowerDisconnected -> Trigger.PowerConnected
        Trigger.ScreenOn -> Trigger.ScreenOff
        Trigger.ScreenOff -> Trigger.ScreenOn
        Trigger.ScreenUnlocked -> Trigger.ScreenOff
        Trigger.HeadsetPlugged -> Trigger.HeadsetUnplugged
        Trigger.HeadsetUnplugged -> Trigger.HeadsetPlugged
        is Trigger.AirplaneMode -> Trigger.AirplaneMode(!turnedOn)
        is Trigger.DndChanged -> Trigger.DndChanged(!turnedOn)
        is Trigger.PowerSave -> Trigger.PowerSave(!turnedOn)
        is Trigger.AppState -> Trigger.AppState(packageName, appName, !onOpen)
        else -> null
    }

/** 是否為藍牙裝置觸發（由靜態 ACL receiver 接收） */
val Trigger.isBluetooth: Boolean
    get() = this is Trigger.BtConnected || this is Trigger.BtDisconnected

/**
 * 藍牙觸發指定的裝置（非藍牙觸發為 null）。
 * [BtDevice.address] 為空字串代表「任一裝置」。
 */
data class BtDevice(val address: String, val name: String)

val Trigger.btDevice: BtDevice?
    get() = when (this) {
        is Trigger.BtConnected -> BtDevice(deviceAddress, deviceName)
        is Trigger.BtDisconnected -> BtDevice(deviceAddress, deviceName)
        else -> null
    }

/** 回填已配對裝置的選擇結果；非藍牙觸發原樣回傳 */
fun Trigger.withBtDevice(device: BtDevice): Trigger = when (this) {
    is Trigger.BtConnected -> copy(deviceAddress = device.address, deviceName = device.name)
    is Trigger.BtDisconnected -> copy(deviceAddress = device.address, deviceName = device.name)
    else -> this
}

/** 電量門檻觸發的門檻值；非電量門檻觸發為 null */
val Trigger.batteryThreshold: Int?
    get() = when (this) {
        is Trigger.BatteryBelow -> threshold
        is Trigger.BatteryAbove -> threshold
        else -> null
    }

fun Trigger.withBatteryThreshold(value: Int): Trigger = when (this) {
    is Trigger.BatteryBelow -> copy(threshold = value)
    is Trigger.BatteryAbove -> copy(threshold = value)
    else -> this
}

/**
 * 需要「開啟時／關閉時」二選一參數的觸發；非此類觸發為 null。
 * App 開啟／關閉也走這裡：參數欄與選擇器的形式完全相同（開啟時／關閉時）。
 */
val Trigger.stateTurnedOn: Boolean?
    get() = when (this) {
        is Trigger.AirplaneMode -> turnedOn
        is Trigger.DndChanged -> turnedOn
        is Trigger.PowerSave -> turnedOn
        is Trigger.AppState -> onOpen
        else -> null
    }

fun Trigger.withStateTurnedOn(turnedOn: Boolean): Trigger = when (this) {
    is Trigger.AirplaneMode -> copy(turnedOn = turnedOn)
    is Trigger.DndChanged -> copy(turnedOn = turnedOn)
    is Trigger.PowerSave -> copy(turnedOn = turnedOn)
    is Trigger.AppState -> copy(onOpen = turnedOn)
    else -> this
}

/**
 * 觸發指定的 App（[AppTarget.packageName] 為空字串代表尚未選擇，
 * 通知觸發則以空字串代表「任一 App」）。非 App 相關觸發為 null。
 */
data class AppTarget(val packageName: String, val appName: String)

val Trigger.appTarget: AppTarget?
    get() = when (this) {
        is Trigger.NotificationPosted -> AppTarget(packageName, appName)
        is Trigger.AppState -> AppTarget(packageName, appName)
        else -> null
    }

/** 回填 App 選擇結果；非 App 相關觸發原樣回傳 */
fun Trigger.withAppTarget(target: AppTarget): Trigger = when (this) {
    is Trigger.NotificationPosted ->
        copy(packageName = target.packageName, appName = target.appName)

    is Trigger.AppState ->
        copy(packageName = target.packageName, appName = target.appName)

    else -> this
}

/**
 * 區域觸發的圓形參數（非序列化，只是把兩種區域觸發的共同欄位攤平，
 * 讓引擎與 UI 不必到處重複 when 分支）。
 */
data class GeoCircle(
    val lat: Double,
    val lng: Double,
    val radiusM: Int,
    val label: String
) {
    /** 使用者是否真的選過地點（尚未選點時經緯度為 0） */
    val isConfigured: Boolean get() = lat != 0.0 || lng != 0.0
}

/** 區域觸發的圓形參數；非區域觸發為 null */
val Trigger.geoCircle: GeoCircle?
    get() = when (this) {
        is Trigger.LocationEnter -> GeoCircle(lat, lng, radiusM, label)
        is Trigger.LocationExit -> GeoCircle(lat, lng, radiusM, label)
        else -> null
    }

/** 是否為區域觸發（需要註冊地理圍欄） */
val Trigger.isLocation: Boolean get() = geoCircle != null

/**
 * 觸發參數是否已填齊。
 *
 * 沒選地點的區域觸發、沒掃過標籤的 NFC 觸發、沒選 App 的 App 觸發存起來也永遠不會觸發，
 * 編輯畫面用這個判定擋下儲存，免得使用者以為設好了卻等不到執行。
 * 通知觸發不在此列：空的來源 App 是「任一 App」，空的關鍵字是「不過濾」，都是合法設定。
 */
val Trigger.isConfigured: Boolean
    get() = when (this) {
        is Trigger.LocationEnter, is Trigger.LocationExit -> geoCircle?.isConfigured == true
        is Trigger.NfcTag -> uid.isNotBlank()
        is Trigger.AppState -> packageName.isNotBlank()
        else -> true
    }

/** 回填地圖選點結果；非區域觸發原樣回傳 */
fun Trigger.withGeoCircle(circle: GeoCircle): Trigger = when (this) {
    is Trigger.LocationEnter -> copy(
        lat = circle.lat,
        lng = circle.lng,
        radiusM = circle.radiusM,
        label = circle.label
    )

    is Trigger.LocationExit -> copy(
        lat = circle.lat,
        lng = circle.lng,
        radiusM = circle.radiusM,
        label = circle.label
    )

    else -> this
}

/**
 * 動作。
 */
@Serializable
sealed class Action {

    /** 顯示通知 */
    @Serializable
    @SerialName("notify")
    data class Notify(
        val title: String = "",
        val message: String = ""
    ) : Action()

    /** 開啟指定 App */
    @Serializable
    @SerialName("open_app")
    data class OpenApp(
        val packageName: String = "",
        val appLabel: String = ""
    ) : Action()

    /** 以瀏覽器開啟網址 */
    @Serializable
    @SerialName("open_url")
    data class OpenUrl(val url: String = "") : Action()

    /**
     * 分享：透過系統分享選單（ACTION_SEND）把文字（連結或任意文字）送出，
     * 由使用者當下選擇要分享到哪個 App／給誰。
     *
     * 背景觸發時系統禁止背景啟動 Activity，沿用「開啟 App／網址」的 launchOrNotify 降級：
     * 改發一則可點擊通知，點擊後才跳出分享選單（見 RoutineExecutor）。
     * 文字為空時記為失敗（無可分享內容）。
     */
    @Serializable
    @SerialName("share")
    data class Share(val text: String = "") : Action()

    /**
     * 設定音量（0–100%）。[stream] 有預設值，舊版只設媒體音量的 JSON 照常讀入。
     * [percentExpr] 非空時執行期解析（數字或變數）覆寫 [percent]，見 resolveNum。
     */
    @Serializable
    @SerialName("media_volume")
    data class MediaVolume(
        val percent: Int = 50,
        val stream: VolumeStream = VolumeStream.MEDIA,
        val percentExpr: String = ""
    ) : Action()

    /** 切換響鈴模式 */
    @Serializable
    @SerialName("ringer_mode")
    data class RingerMode(val mode: RingerModeType = RingerModeType.NORMAL) : Action()

    /** 開啟／關閉藍牙（Android 13+ 只能發通知帶出系統確認） */
    @Serializable
    @SerialName("bluetooth")
    data class Bluetooth(val enable: Boolean = true) : Action()

    /**
     * 開啟／關閉 Wi-Fi。
     *
     * Android 10 起系統禁止第三方 App 直接切換 Wi-Fi，只能把使用者帶到系統的 Wi-Fi 面板／
     * 設定頁自行切換（誠實降級，絕不記成假成功）。[on] 只表達使用者意圖，用於文案顯示。
     */
    @Serializable
    @SerialName("wifi_toggle")
    data class WifiToggle(val on: Boolean = true) : Action()

    /** 手電筒開／關 */
    @Serializable
    @SerialName("flashlight")
    data class Flashlight(val on: Boolean = true) : Action()

    /** 以系統 TTS 朗讀文字 */
    @Serializable
    @SerialName("speak")
    data class Speak(val text: String = "") : Action()

    /** 震動指定毫秒。[millisExpr] 非空時執行期解析覆寫 [millis]。 */
    @Serializable
    @SerialName("vibrate")
    data class Vibrate(
        val millis: Int = 500,
        val millisExpr: String = ""
    ) : Action()

    /** 勿擾模式開／關 */
    @Serializable
    @SerialName("dnd")
    data class Dnd(val on: Boolean = true) : Action()

    /** 螢幕亮度（0–100%），需要「修改系統設定」權限。[percentExpr] 非空時執行期解析覆寫 [percent]。 */
    @Serializable
    @SerialName("brightness")
    data class Brightness(
        val percent: Int = 50,
        val percentExpr: String = ""
    ) : Action()

    /** 自動旋轉開／關，與螢幕亮度一樣需要「修改系統設定」權限 */
    @Serializable
    @SerialName("auto_rotate")
    data class AutoRotate(val on: Boolean = true) : Action()

    /**
     * 螢幕逾時：多久沒操作就自動關螢幕，需要「修改系統設定」權限。
     * [secondsExpr] 非空時執行期解析覆寫 [seconds]。
     */
    @Serializable
    @SerialName("screen_timeout")
    data class ScreenTimeout(
        val seconds: Int = 30,
        val secondsExpr: String = ""
    ) : Action()

    /**
     * 撥號：帶號碼開啟系統撥號畫面（ACTION_DIAL），由使用者自己按下通話鍵，
     * 因此不需要通話權限。[number] 可含變數 token。
     */
    @Serializable
    @SerialName("dial")
    data class Dial(val number: String = "") : Action()

    /**
     * 傳簡訊：開啟簡訊 App 並預先填好收件人與內容（ACTION_SENDTO），由使用者自己按送出。
     * 刻意不要求 SEND_SMS 權限（該權限受 Play 政策嚴格限制，也不該替使用者直接發訊）。
     * [number]／[message] 可含變數 token。
     */
    @Serializable
    @SerialName("send_sms")
    data class SendSms(
        val number: String = "",
        val message: String = ""
    ) : Action()

    /**
     * 取得目前位置：向定位服務要一次座標，依 [format] 存進具名變數 [variableName]
     * （後續以 `{{var:名稱}}` 引用）。需要位置權限。
     */
    @Serializable
    @SerialName("get_location")
    data class GetLocation(
        val variableName: String = "",
        val format: LocationFormat = LocationFormat.LAT_LNG
    ) : Action()
    /**
     * 記住目前設定：把當下可調整的裝置設定（各串流音量、響鈴模式、勿擾、螢幕亮度與亮度模式）
     * 拍成一張快照存起來，供之後的「回復設定」還原。搭配既有的進入／離開類觸發
     * （例如進入區域 → 記住目前設定＋靜音；離開區域 → 回復設定），就能自己組出
     * Samsung 情境模式 / Tasker exit task 的「條件結束時還原」效果，不必改動一次觸發的模型。
     */
    @Serializable
    @SerialName("snapshot_settings")
    data object SnapshotSettings : Action()

    /**
     * 回復設定：把「記住目前設定」拍下的快照重新套回去，沿用各設定動作的同一套機制與權限降級
     * （勿擾 / 亮度缺權限時略過該項並註明，絕不崩潰）。尚未有任何快照時記為失敗。
     */
    @Serializable
    @SerialName("restore_settings")
    data object RestoreSettings : Action()

    /** HTTP 請求（webhook）：GET 或 POST，body 為純文字 */
    @Serializable
    @SerialName("http")
    data class Http(
        val url: String = "",
        val method: String = METHOD_GET,
        val body: String = ""
    ) : Action()

    /** 播放控制：以系統媒體按鍵事件送出 */
    @Serializable
    @SerialName("media_key")
    data class MediaKey(val key: String = KEY_PLAY_PAUSE) : Action()

    /** 等待 N 秒後再執行後續動作。[secondsExpr] 非空時執行期解析覆寫 [seconds]。 */
    @Serializable
    @SerialName("wait")
    data class Wait(
        val seconds: Int = 3,
        val secondsExpr: String = ""
    ) : Action()

    /**
     * 把文字複製到系統剪貼簿。
     *
     * Android 10 起系統只允許前景 App 寫入剪貼簿，背景寫入在部分版本／廠牌會被
     * 靜默忽略（見 RoutineExecutor 的說明），因此背景觸發只能盡力而為並誠實記錄。
     */
    @Serializable
    @SerialName("clipboard")
    data class Clipboard(val text: String = "") : Action()

    /**
     * 拍照：以選定鏡頭（[lensBack] true＝後鏡頭）擷取一張相片存入相簿。
     *
     * Android 9+ 禁止背景存取相機，只有前景（手動執行）或掛上 camera 類前景服務
     * 才能拍照；背景觸發被系統擋下時記為失敗並誠實註明（見 RoutineExecutor）。
     */
    @Serializable
    @SerialName("take_photo")
    data class TakePhoto(
        val lensBack: Boolean = true,
        @SerialName("notify") val notify: Boolean = true,
        /** true＝另存一份到公開相簿（其他 App 可讀）；預設 false＝只存 App 私有空間 */
        val shareToGallery: Boolean = false
    ) : Action()

    /**
     * 連拍：以選定鏡頭連續擷取 [count] 張、每張間隔 [intervalMs] 毫秒。
     * [countExpr]/[intervalExpr] 非空時執行期解析（數字或變數）覆寫對應的數值。
     */
    @Serializable
    @SerialName("burst_photo")
    data class BurstPhoto(
        val lensBack: Boolean = true,
        val count: Int = 3,
        val intervalMs: Int = 500,
        @SerialName("notify") val notify: Boolean = true,
        val countExpr: String = "",
        val intervalExpr: String = "",
        /** true＝另存一份到公開相簿（其他 App 可讀）；預設 false＝只存 App 私有空間 */
        val shareToGallery: Boolean = false
    ) : Action()

    /**
     * 錄音：錄製 [seconds] 秒音訊存檔（受背景麥克風限制，同拍照）。
     * [secondsExpr] 非空時執行期解析覆寫 [seconds]。
     */
    @Serializable
    @SerialName("record_audio")
    data class RecordAudio(
        val seconds: Int = 5,
        @SerialName("notify") val notify: Boolean = true,
        val secondsExpr: String = "",
        /** true＝另存一份到公開音樂資料夾（其他 App 可讀）；預設 false＝只存 App 私有空間 */
        val shareToGallery: Boolean = false
    ) : Action()

    /** 播放系統音效（[type]：NOTIFICATION／ALARM／RINGTONE），無額外權限需求 */
    @Serializable
    @SerialName("play_sound")
    data class PlaySound(val type: String = SOUND_NOTIFICATION) : Action()

    /** 以系統時鐘 App 設定一個 [hour]:[minute] 的鬧鐘，標籤為 [label] */
    @Serializable
    @SerialName("set_alarm")
    data class SetAlarm(
        val hour: Int = 8,
        val minute: Int = 0,
        val label: String = ""
    ) : Action()

    /**
     * 文字：把一段（可含變數 token 的）文字設為輸出，
     * 作為後續動作以 `{{result}}` 引用的資料來源。
     */
    @Serializable
    @SerialName("text")
    data class Text(val template: String = "") : Action()

    /**
     * 設定變數：把一段（可含變數 token 的）文字解析後存成具名變數，
     * 供後續動作以 `{{var:名稱}}` 引用。
     */
    @Serializable
    @SerialName("set_variable")
    data class SetVariable(
        val name: String = "",
        val template: String = ""
    ) : Action()

    /**
     * 設定全域變數：把一段（可含變數 token 的）文字解析後存成**跨程序、可持久化**的全域變數，
     * 之後任何程序都能以 `{{全域:名稱}}` 引用。與 [SetVariable]（只存活於單次執行）不同，
     * 全域變數寫入後會落地保存（見 [com.routina.app.data.RoutineRepository.applyGlobals]）。
     */
    @Serializable
    @SerialName("set_global")
    data class SetGlobalVariable(
        val name: String = "",
        val template: String = ""
    ) : Action()

    /**
     * 計算：把 `左 [運算子] 右` 的算術結果存進具名變數 [name]（供後續以 `{{var:名稱}}` 引用）。
     * [left]／[right] 可含變數 token（例如 `{{var:count}}`、`{{迴圈:次數}}`），執行時先代入再運算。
     * 整數結果顯示為整數，非整數保留小數；除以 0 記為失敗。
     */
    @Serializable
    @SerialName("calculate")
    data class Calculate(
        val name: String = "",
        val left: String = "",
        val op: MathOp = MathOp.ADD,
        val right: String = ""
    ) : Action()

    /**
     * 運算式：一行 Python 式的變數指派 `名稱 = 值`（存進具名變數，後續以 `{{var:名稱}}` 引用）。
     * 等號右邊若含算術運算子（+ - * / %）就當數學算（變數直接寫名字，如 `count + 1`，缺的當 0）；
     * 否則整段當文字存起來。`{{...}}` token 仍可用；用引號 `"..."` 可強制當文字。
     */
    @Serializable
    @SerialName("expression")
    data class Expression(val text: String = "") : Action()

    // ---- 清單 ----
    // 清單就是一段「一行一個項目」的純文字，存在一般變數裡（`{{var:名稱}}` 照常引用）。
    // 讀取時去掉每行前後空白、略過空行；寫入時以換行接起來。

    /** 建立清單：把多行文字（一行一個項目，可含 token）正規化後存成清單變數 [variableName] */
    @Serializable
    @SerialName("list_create")
    data class ListCreate(
        val items: String = "",
        val variableName: String = ""
    ) : Action()

    /** 切割成清單：把 [input] 依 [delimiter] 切開，存成清單變數 [variableName] */
    @Serializable
    @SerialName("list_split")
    data class ListSplit(
        val input: String = "",
        val delimiter: String = ",",
        val variableName: String = ""
    ) : Action()

    /** 加入清單項目：把 [item] 接到清單變數 [variableName] 的最後（變數沒設過視為空清單） */
    @Serializable
    @SerialName("list_append")
    data class ListAppend(
        val variableName: String = "",
        val item: String = ""
    ) : Action()

    /**
     * 取清單項目：取清單變數 [listVariable] 的第 [index] 項（1 起算，可含 token
     * 例如 `{{迴圈:次數}}`）存進 [variableName]；不是數字或超出範圍記為失敗。
     */
    @Serializable
    @SerialName("list_get")
    data class ListGet(
        val listVariable: String = "",
        val index: String = "1",
        val variableName: String = ""
    ) : Action()

    /** 清單長度：把清單變數 [listVariable] 的項目數存進 [variableName] */
    @Serializable
    @SerialName("list_count")
    data class ListCount(
        val listVariable: String = "",
        val variableName: String = ""
    ) : Action()

    /**
     * 從 JSON 取值：把 [source]（JSON 文字，通常是 HTTP 動作的 `{{result}}`）依 [path]
     * 取出一個值，存進具名變數 [variableName]（後續以 `{{var:名稱}}` 引用）。
     *
     * [path] 為 `data.items[0].name` 這類點／中括號路徑。取到陣列時輸出「一行一個項目」
     * （全 App 清單變數的共同格式），取到物件則輸出它的 JSON 文字；路徑不存在記為失敗。
     */
    @Serializable
    @SerialName("json_get")
    data class JsonGet(
        val source: String = "",
        val path: String = "",
        val variableName: String = ""
    ) : Action()

    /**
     * 文字處理：對 [input]（可含 token）做一次 [op] 轉換，結果存進具名變數 [variableName]。
     * [arg1]／[arg2] 的意義依 [op] 而定（見 [TextOp]），用不到的操作留空即可。
     */
    @Serializable
    @SerialName("text_transform")
    data class TextTransform(
        val input: String = "",
        val op: TextOp = TextOp.TRIM,
        val arg1: String = "",
        val arg2: String = "",
        val variableName: String = ""
    ) : Action()

    /**
     * 日期時間：把「現在」加上 [offsetDays] 天、[offsetMinutes] 分（負數＝往前）後，
     * 依 java.time 的 [pattern] 格式化存進具名變數 [variableName]。格式不合法記為失敗。
     */
    @Serializable
    @SerialName("date_format")
    data class DateFormat(
        val variableName: String = "",
        val pattern: String = "yyyy/MM/dd HH:mm",
        val offsetMinutes: Int = 0,
        val offsetDays: Int = 0
    ) : Action()

    /**
     * 詢問輸入：執行到這裡時暫停，跳出對話框請使用者輸入一段文字，
     * 把答案存進具名變數 [variableName]（後續以 `{{var:名稱}}` 引用）。
     * [prompt] 可含變數 token；[defaultValue] 預先填入輸入框（也可含 token）。
     */
    @Serializable
    @SerialName("ask_input")
    data class AskInput(
        val prompt: String = "",
        val variableName: String = "",
        val defaultValue: String = ""
    ) : Action()

    /**
     * 選單選擇：執行到這裡時暫停，跳出對話框列出 [options] 讓使用者點選一個，
     * 把選中的文字存進具名變數 [variableName]（後續以 `{{var:名稱}}` 引用）。
     * [prompt] 與每個選項都可含變數 token。
     */
    @Serializable
    @SerialName("choose_menu")
    data class ChooseMenu(
        val prompt: String = "",
        val options: List<String> = emptyList(),
        val variableName: String = ""
    ) : Action()

    // ---- 流程控制（配對標記）----
    // 扁平清單用配對的 begin/end 標記表達層級，由 RoutineExecutor 的直譯器解讀；
    // dispatch 時皆為 no-op（流程由直譯器處理）。對不成對的標記直譯器保持穩健、不崩潰。

    /** 如果：[condition] 成立才執行到下一個 否則如果／否則／結束如果 之間的動作 */
    @Serializable
    @SerialName("if_begin")
    data class IfBegin(val condition: Condition = Condition()) : Action()

    /** 否則如果：前面的條件都不成立且 [condition] 成立時執行 */
    @Serializable
    @SerialName("else_if")
    data class ElseIf(val condition: Condition = Condition()) : Action()

    /** 否則：前面的條件都不成立時執行 */
    @Serializable
    @SerialName("else")
    data object Else : Action()

    /** 結束如果 */
    @Serializable
    @SerialName("end_if")
    data object EndIf : Action()

    /** 一直重複…當：只要 [condition] 成立就重複執行到 結束重複 之間的動作（有次數上限保護） */
    @Serializable
    @SerialName("while_begin")
    data class WhileBegin(val condition: Condition = Condition()) : Action()

    /** 結束重複（對應 一直重複…當） */
    @Serializable
    @SerialName("end_while")
    data object EndWhile : Action()

    /** 重複 N 次：[countExpr] 非空則以變數解析出次數，否則用 [count]。以 {{迴圈:次數}} 取得目前第幾次 */
    @Serializable
    @SerialName("repeat_begin")
    data class RepeatBegin(val count: Int = 3, val countExpr: String = "") : Action()

    /** 結束重複 N 次 */
    @Serializable
    @SerialName("end_repeat")
    data object EndRepeat : Action()

    /**
     * 逐項重複：把 [listSource] 解析後的清單（換行或逗號分隔）每個項目各跑一遍 結束逐項 之間的動作。
     * [listSource] 可含 token（如 `{{result}}`、`{{var:待辦}}`），執行時先解析再切割。
     * 每次把目前項目存進具名變數 [itemVariable]（以 `{{var:名稱}}` 取得），
     * 並以 `{{迴圈:次數}}` 取得目前是第幾項（沿用重複迴圈的計數鍵）。
     */
    @Serializable
    @SerialName("foreach_begin")
    data class ForEachBegin(
        val listSource: String = "",
        val itemVariable: String = "item"
    ) : Action()

    /** 結束逐項 */
    @Serializable
    @SerialName("end_foreach")
    data object EndForEach : Action()

    /**
     * 執行另一個程序：跑到這塊時，把 [routineId] 指向的程序的動作**當場跑一遍**
     * （共用同一個執行情境，變數與 `{{result}}` 會串接流動）。[routineName] 只作顯示用，
     * 目標被改名或刪除仍以 [routineId] 為準。執行時會擋循環呼叫（見 RoutineExecutor）。
     */
    @Serializable
    @SerialName("run_routine")
    data class RunRoutine(
        val routineId: String = "",
        val routineName: String = ""
    ) : Action()

    companion object {
        const val METHOD_GET = "GET"
        const val METHOD_POST = "POST"
        val HTTP_METHODS = listOf(METHOD_GET, METHOD_POST)

        const val KEY_PLAY_PAUSE = "PLAY_PAUSE"
        const val KEY_NEXT = "NEXT"
        const val KEY_PREVIOUS = "PREVIOUS"
        val MEDIA_KEYS = listOf(KEY_PLAY_PAUSE, KEY_NEXT, KEY_PREVIOUS)

        /**
         * 數值參數的安全範圍：直接輸入或變數解析後夾在此範圍內，擋掉荒謬值
         * （例如連拍張數誤植成長時間佔用相機）。範圍比原本的滑桿寬，涵蓋各動作的合理上下限。
         */
        val BURST_COUNT_SAFE = 1..999
        val BURST_INTERVAL_SAFE = 0..60000
        val WAIT_SECONDS_SAFE = 1..3600
        val VIBRATE_MS_SAFE = 1..10000
        val RECORD_SECONDS_SAFE = 1..3600
        val PERCENT_SAFE = 0..100

        /** 螢幕逾時秒數：下限取系統最短的 15 秒，上限 30 分鐘 */
        val SCREEN_TIMEOUT_SAFE = 15..1800

        /** 流程控制安全上限：擋掉無限迴圈與失控的巢狀執行 */
        val REPEAT_COUNT_SAFE = 0..10000
        const val WHILE_MAX_ITERATIONS = 10000
        const val MAX_ACTIONS_PER_RUN = 100000

        /** 播放音效的系統音效類型 */
        const val SOUND_NOTIFICATION = "NOTIFICATION"
        const val SOUND_ALARM = "ALARM"
        const val SOUND_RINGTONE = "RINGTONE"
        val SOUND_TYPES = listOf(SOUND_NOTIFICATION, SOUND_ALARM, SOUND_RINGTONE)
    }
}

/**
 * 一個全域變數（跨程序、可持久化）。以 [name] 為唯一鍵，[value] 為目前的值。
 * 由「設定全域變數」動作寫入，或在全域變數管理畫面手動編輯；以 `{{全域:名稱}}` 引用。
 */
@Serializable
data class GlobalVar(
    val name: String,
    val value: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 流程控制的判斷式：`左 [運算子] 右`。[left]／[right] 都可含變數 token，執行時先解析再比較
 * （見 [com.routina.app.engine.ConditionEvaluator]）。空條件（皆空、EQUALS）＝恆成立。
 */
@Serializable
data class Condition(
    val left: String = "",
    val op: CompareOp = CompareOp.EQUALS,
    val right: String = ""
)

/** 判斷式的運算子。EMPTY／NOT_EMPTY 只看 [Condition.left]（忽略 right）。 */
@Serializable
enum class CompareOp {
    @SerialName("eq") EQUALS,
    @SerialName("neq") NOT_EQUALS,
    @SerialName("gt") GREATER,
    @SerialName("gte") GREATER_EQUAL,
    @SerialName("lt") LESS,
    @SerialName("lte") LESS_EQUAL,
    @SerialName("contains") CONTAINS,
    @SerialName("not_contains") NOT_CONTAINS,
    @SerialName("empty") IS_EMPTY,
    @SerialName("not_empty") IS_NOT_EMPTY,
    @SerialName("true") IS_TRUE,
    @SerialName("false") IS_FALSE
}

/** 是否需要右值：為空／不為空／為真／為假只看左值，UI 會隱藏右值欄、顯示時也省略右值 */
val CompareOp.usesRightOperand: Boolean
    get() = this !in setOf(
        CompareOp.IS_EMPTY, CompareOp.IS_NOT_EMPTY, CompareOp.IS_TRUE, CompareOp.IS_FALSE
    )

/** 「計算」動作的算術運算子 */
@Serializable
enum class MathOp {
    @SerialName("add") ADD,
    @SerialName("sub") SUBTRACT,
    @SerialName("mul") MULTIPLY,
    @SerialName("div") DIVIDE,
    @SerialName("mod") MODULO
}

/**
 * 「文字處理」動作的操作。
 *
 * 取代 REPLACE：arg1＝要找的文字、arg2＝換成什麼。
 * 擷取 SUBSTRING：arg1＝起、arg2＝迄（第幾個字，含頭含尾，超出範圍會夾回合法範圍）。
 * 正規式擷取 REGEX_EXTRACT：arg1＝pattern、arg2＝取第幾組（預設 0＝整段），不符得到空字串。
 * 其餘操作用不到參數。
 */
@Serializable
enum class TextOp {
    @SerialName("upper") UPPER,
    @SerialName("lower") LOWER,
    @SerialName("trim") TRIM,
    @SerialName("replace") REPLACE,
    @SerialName("substring") SUBSTRING,
    @SerialName("length") LENGTH,
    @SerialName("regex_extract") REGEX_EXTRACT
}

/** 是否用得到 arg1／arg2；用不到的操作在編輯畫面不顯示參數欄，免得使用者以為要填 */
val TextOp.usesArgs: Boolean
    get() = this in setOf(TextOp.REPLACE, TextOp.SUBSTRING, TextOp.REGEX_EXTRACT)

/** 「取得目前位置」存進變數的格式 */
@Serializable
enum class LocationFormat {
    @SerialName("lat_lng")
    LAT_LNG,

    @SerialName("lat")
    LAT,

    @SerialName("lng")
    LNG
}

@Serializable
enum class RingerModeType {
    @SerialName("normal")
    NORMAL,

    @SerialName("vibrate")
    VIBRATE,

    @SerialName("silent")
    SILENT
}

/** 音量串流 */
@Serializable
enum class VolumeStream {
    @SerialName("media")
    MEDIA,

    @SerialName("ring")
    RING,

    @SerialName("alarm")
    ALARM,

    @SerialName("notification")
    NOTIFICATION
}

/** 是否為等待動作（前景服務外的降級路徑會跳過） */
val Action.isWait: Boolean get() = this is Action.Wait

/**
 * 一個例行程序：一個觸發條件 + 一至多個動作。
 */
@Serializable
data class Routine(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val enabled: Boolean = true,
    val trigger: Trigger = Trigger.Time(),
    val actions: List<Action> = emptyList(),
    /** 自訂方塊顏色（ARGB）；null＝依觸發家族色（舊資料相容） */
    val color: Int? = null,
    /** 觸發上限次數；null＝無限（舊資料相容）。達到後自動停用。手動測試不計入。 */
    val maxRuns: Int? = null,
    /** 已由觸發實際執行的次數（手動測試不計）。用來與 [maxRuns] 比對。 */
    val runCount: Int = 0,
    /** 結束日期（epoch millis）；到期後不再觸發並自動停用。null＝無期限（舊資料相容）。 */
    val expiresAt: Long? = null,
    /**
     * 離開時還原：觸發執行前先記下裝置設定，等條件結束（[Trigger.opposite]）時還原回去。
     * 只有反向觸發存在的觸發類型才提供這個選項（見 [com.routina.app.engine.RestoreOnExit]）。
     */
    val restoreOnExit: Boolean = false,
    /**
     * 執行條件：觸發發生時，這些條件全部成立才真的執行動作。
     *
     * 觸發決定「什麼時候檢查」，這裡決定「現在到底要不要做」
     * （例如：插耳機時播放音樂，但只在平日）。
     * 空清單＝沒有限制（舊資料相容）；手動執行一律不受限制，才能隨時測試。
     */
    val constraints: List<Condition> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
) {
    /** AlarmManager PendingIntent 的 requestCode：由 id 推導，穩定且不衝突。 */
    val alarmRequestCode: Int get() = id.hashCode()
}

/**
 * NFC 標籤庫的一筆記錄：掃描時把 UID 與 NDEF 內容存起來，
 * 之後可以複製到別張標籤，或設成觸發。
 */
@Serializable
data class NfcRecord(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val uid: String = "",
    /** 標籤的 NDEF 內容(base64,可原樣寫回別張標籤);空＝這張只有 UID、沒有 NDEF 內容 */
    val ndefBase64: String = "",
    /** 給人看的內容摘要(網址/文字…) */
    val summary: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

/** 觸發來源 */
@Serializable
enum class TriggerSource {
    @SerialName("schedule")
    SCHEDULE,

    @SerialName("power")
    POWER,

    @SerialName("battery")
    BATTERY,

    @SerialName("location")
    LOCATION,

    @SerialName("bluetooth")
    BLUETOOTH,

    @SerialName("wifi")
    WIFI,

    @SerialName("system")
    SYSTEM,

    @SerialName("nfc")
    NFC,

    @SerialName("notification")
    NOTIFICATION,

    @SerialName("app")
    APP,

    @SerialName("manual")
    MANUAL
}

/** 單一動作的執行結果 */
@Serializable
data class ActionResult(
    val description: String,
    val success: Boolean,
    val error: String? = null
)

/** 一次執行的紀錄 */
@Serializable
data class RunLog(
    val id: String = UUID.randomUUID().toString(),
    val routineId: String = "",
    val routineName: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val source: TriggerSource = TriggerSource.MANUAL,
    val results: List<ActionResult> = emptyList(),
    /** 執行環境的補充說明（例如前景執行服務啟不起來而降級） */
    val note: String? = null
) {
    val allSucceeded: Boolean get() = results.isNotEmpty() && results.all { it.success }
    val allFailed: Boolean get() = results.isNotEmpty() && results.none { it.success }
    val failureCount: Int get() = results.count { !it.success }
}
