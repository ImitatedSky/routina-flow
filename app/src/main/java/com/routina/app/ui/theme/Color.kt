package com.routina.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.routina.app.model.Action
import com.routina.app.model.Trigger

/**
 * 積木色表（唯一來源）。
 *
 * 17 種觸發 × 15 種動作時「一個型別一個獨立色相」已超出人眼可辨識的範圍，
 * 因此改為**功能分組色系**：一組一色相、組內只差色階，分組與調色盤的分組小標一致。
 *
 * 使用者明確回饋過：**文字才是主要辨識依據，顏色只負責分組輔助**。
 * 所以組內色階由深到淺依調色盤上的排列順序遞淺，相鄰兩塊仍分辨得出來就夠了，
 * 不需要（也不應該）讓每塊積木都長得完全不同。
 *
 * 所有積木、調色盤、清單卡片的縮小預覽都只從這裡取色，不在別處硬編顏色。
 */
object RoutinaColors {

    // ---------- 觸發：手動（中性灰藍） ----------
    /** 手動執行：無自動觸發，以中性色與其餘觸發家族區隔 */
    val TriggerManual = Color(0xFF4E5560)

    // ---------- 觸發：時間（靛） ----------
    val TriggerTime = Color(0xFF3949AB)

    // ---------- 觸發：電源（綠） ----------
    val TriggerPowerConnected = Color(0xFF1B5E20)
    val TriggerPowerDisconnected = Color(0xFF2E7D32)
    val TriggerBatteryBelow = Color(0xFF388E3C)
    val TriggerBatteryAbove = Color(0xFF43A047)
    val TriggerPowerSave = Color(0xFF66BB6A)

    // ---------- 觸發：連線（青） ----------
    val TriggerWifiConnected = Color(0xFF006064)
    val TriggerWifiDisconnected = Color(0xFF00838F)
    val TriggerBtConnected = Color(0xFF0097A7)
    val TriggerBtDisconnected = Color(0xFF00ACC1)
    val TriggerAirplaneMode = Color(0xFF26C6DA)
    val TriggerNfcTag = Color(0xFF4DD0E1)

    // ---------- 觸發：系統與應用（藍灰） ----------
    val TriggerDndChanged = Color(0xFF37474F)
    val TriggerNotificationPosted = Color(0xFF455A64)
    val TriggerAppState = Color(0xFF546E7A)

    // ---------- 觸發：位置（紫） ----------
    val TriggerLocationEnter = Color(0xFF4527A0)
    val TriggerLocationExit = Color(0xFF5E35B1)

    // ---------- 動作：通知與 App（藍） ----------
    val ActionNotify = Color(0xFF0D47A1)
    val ActionOpenApp = Color(0xFF1565C0)
    val ActionOpenUrl = Color(0xFF1976D2)
    val ActionHttp = Color(0xFF1E88E5)
    val ActionSetAlarm = Color(0xFF42A5F5)
    val ActionShare = Color(0xFF64B5F6)

    // ---------- 動作：聲音（紅） ----------
    val ActionVolume = Color(0xFFB71C1C)
    val ActionRinger = Color(0xFFC62828)
    val ActionSpeak = Color(0xFFD32F2F)
    val ActionMediaKey = Color(0xFFE53935)
    val ActionPlaySound = Color(0xFFEF5350)

    // ---------- 動作：裝置（橘） ----------
    val ActionFlashlight = Color(0xFFE65100)
    val ActionVibrate = Color(0xFFEF6C00)
    val ActionBrightness = Color(0xFFF57C00)
    val ActionDnd = Color(0xFFFB8C00)
    val ActionBluetooth = Color(0xFFFFA000)

    // ---------- 動作：媒體與擷取（紫） ----------
    val ActionTakePhoto = Color(0xFF6A1B9A)
    val ActionBurstPhoto = Color(0xFF8E24AA)
    val ActionRecordAudio = Color(0xFFAB47BC)

    // ---------- 動作：流程（灰） ----------
    val ActionWait = Color(0xFF616161)
    val ActionClipboard = Color(0xFF757575)

    // ---------- 執行結果 ----------
    val Success = Color(0xFF2E7D32)
    val Failure = Color(0xFFC62828)

    // ---------- 語意色（卡片狀態晶片） ----------
    /** 成功 / 正常（綠）：上次執行全部成功 */
    val Good = Color(0xFF2E7D32)

    /** 警告（琥珀）：上次執行部分失敗 */
    val Warn = Color(0xFFB26A00)

    /** 淺色積木上的文字色（白字對比不足時使用） */
    val OnLightBlock = Color(0xFF333333)
}

fun triggerColor(trigger: Trigger): Color = when (trigger) {
    is Trigger.Manual -> RoutinaColors.TriggerManual
    is Trigger.Time -> RoutinaColors.TriggerTime
    is Trigger.PowerConnected -> RoutinaColors.TriggerPowerConnected
    is Trigger.PowerDisconnected -> RoutinaColors.TriggerPowerDisconnected
    is Trigger.BatteryBelow -> RoutinaColors.TriggerBatteryBelow
    is Trigger.BatteryAbove -> RoutinaColors.TriggerBatteryAbove
    is Trigger.PowerSave -> RoutinaColors.TriggerPowerSave
    is Trigger.WifiConnected -> RoutinaColors.TriggerWifiConnected
    is Trigger.WifiDisconnected -> RoutinaColors.TriggerWifiDisconnected
    is Trigger.BtConnected -> RoutinaColors.TriggerBtConnected
    is Trigger.BtDisconnected -> RoutinaColors.TriggerBtDisconnected
    is Trigger.AirplaneMode -> RoutinaColors.TriggerAirplaneMode
    is Trigger.NfcTag -> RoutinaColors.TriggerNfcTag
    is Trigger.DndChanged -> RoutinaColors.TriggerDndChanged
    is Trigger.NotificationPosted -> RoutinaColors.TriggerNotificationPosted
    is Trigger.AppState -> RoutinaColors.TriggerAppState
    is Trigger.LocationEnter -> RoutinaColors.TriggerLocationEnter
    is Trigger.LocationExit -> RoutinaColors.TriggerLocationExit
}

fun actionColor(action: Action): Color = when (action) {
    is Action.Notify -> RoutinaColors.ActionNotify
    is Action.OpenApp -> RoutinaColors.ActionOpenApp
    is Action.OpenUrl -> RoutinaColors.ActionOpenUrl
    is Action.Share -> RoutinaColors.ActionShare
    is Action.Http -> RoutinaColors.ActionHttp
    is Action.SetAlarm -> RoutinaColors.ActionSetAlarm
    is Action.MediaVolume -> RoutinaColors.ActionVolume
    is Action.RingerMode -> RoutinaColors.ActionRinger
    is Action.Speak -> RoutinaColors.ActionSpeak
    is Action.MediaKey -> RoutinaColors.ActionMediaKey
    is Action.PlaySound -> RoutinaColors.ActionPlaySound
    is Action.Flashlight -> RoutinaColors.ActionFlashlight
    is Action.Vibrate -> RoutinaColors.ActionVibrate
    is Action.Brightness -> RoutinaColors.ActionBrightness
    is Action.Dnd -> RoutinaColors.ActionDnd
    is Action.Bluetooth -> RoutinaColors.ActionBluetooth
    is Action.TakePhoto -> RoutinaColors.ActionTakePhoto
    is Action.BurstPhoto -> RoutinaColors.ActionBurstPhoto
    is Action.RecordAudio -> RoutinaColors.ActionRecordAudio
    is Action.Wait -> RoutinaColors.ActionWait
    is Action.Clipboard -> RoutinaColors.ActionClipboard
}

/**
 * 積木上的文字色：白字與深字之中挑對比較高的那個。
 *
 * 積木以文字為主要辨識依據，所以對比不能靠「記得哪幾塊是淺色」來維護。
 * 這裡直接用 WCAG 相對亮度算出兩種候選文字色的對比率再取較高者，
 * 色表怎麼調（例如整組色階往淺色移）都不必回頭改判定條件。
 */
fun blockContentColor(fill: Color): Color =
    if (contrastRatio(fill, RoutinaColors.OnLightBlock) >= contrastRatio(fill, Color.White)) {
        RoutinaColors.OnLightBlock
    } else {
        Color.White
    }

/** WCAG 對比率（1:1 – 21:1） */
private fun contrastRatio(a: Color, b: Color): Float {
    val lighter = maxOf(a.luminance(), b.luminance()) + 0.05f
    val darker = minOf(a.luminance(), b.luminance()) + 0.05f
    return lighter / darker
}
