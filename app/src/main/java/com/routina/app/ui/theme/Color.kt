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

    // 家族色相不變，但整體降飽和約 32%，畫面較柔和不刺眼；組內色階差保留。

    // ---------- 觸發：手動（中性灰藍） ----------
    /** 手動執行：無自動觸發，以中性色與其餘觸發家族區隔 */
    val TriggerManual = Color(0xFF51565D)

    // ---------- 觸發：時間（靛） ----------
    val TriggerTime = Color(0xFF4B5699)

    // ---------- 觸發：電源（綠） ----------
    val TriggerPowerConnected = Color(0xFF265329)
    val TriggerPowerDisconnected = Color(0xFF3B703D)
    val TriggerBatteryBelow = Color(0xFF468048)
    val TriggerBatteryAbove = Color(0xFF529155)
    val TriggerPowerSave = Color(0xFF74AD76)

    // ---------- 觸發：連線（青） ----------
    val TriggerWifiConnected = Color(0xFF105154)
    val TriggerWifiDisconnected = Color(0xFF177078)
    val TriggerBtConnected = Color(0xFF1B818C)
    val TriggerBtDisconnected = Color(0xFF1F94A2)
    val TriggerAirplaneMode = Color(0xFF43B0BD)
    val TriggerNfcTag = Color(0xFF65BEC9)

    // ---------- 觸發：系統與應用（藍灰） ----------
    val TriggerDndChanged = Color(0xFF3B464B)
    val TriggerNotificationPosted = Color(0xFF4A585F)
    val TriggerAppState = Color(0xFF5A6C74)

    // ---------- 觸發：位置（紫） ----------
    val TriggerLocationEnter = Color(0xFF4F3A8D)
    val TriggerLocationExit = Color(0xFF65499D)

    // ---------- 動作：通知與 App（藍） ----------
    val ActionNotify = Color(0xFF254C89)
    val ActionOpenApp = Color(0xFF3067A5)
    val ActionOpenUrl = Color(0xFF3776B4)
    val ActionHttp = Color(0xFF3E86C5)
    val ActionSetAlarm = Color(0xFF5FA2D8)
    val ActionShare = Color(0xFF7BB2DF)

    // ---------- 動作：聲音（紅） ----------
    val ActionVolume = Color(0xFF9E3535)
    val ActionRinger = Color(0xFFAD4141)
    val ActionSpeak = Color(0xFFB94949)
    val ActionMediaKey = Color(0xFFC95451)
    val ActionPlaySound = Color(0xFFD66B69)

    // ---------- 動作：裝置（橘） ----------
    val ActionFlashlight = Color(0xFFC15C25)
    val ActionVibrate = Color(0xFFC97026)
    val ActionBrightness = Color(0xFFCE7C27)
    val ActionDnd = Color(0xFFD38728)
    val ActionBluetooth = Color(0xFFD69629)

    // ---------- 動作：媒體與擷取（紫） ----------
    val ActionTakePhoto = Color(0xFF652F86)
    val ActionBurstPhoto = Color(0xFF823995)
    val ActionRecordAudio = Color(0xFF9E5AA9)

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
