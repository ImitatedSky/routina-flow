package com.routina.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.routina.app.model.Action
import com.routina.app.model.Routine
import com.routina.app.model.Trigger

/**
 * 積木色表（唯一來源）。
 *
 * 25 種觸發 × 49 種動作時「一個型別一個獨立色相」已超出人眼可辨識的範圍，
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
    val TriggerBatteryFull = Color(0xFF63A066)
    val TriggerPowerSave = Color(0xFF74AD76)

    // ---------- 觸發：連線（青） ----------
    val TriggerWifiConnected = Color(0xFF105154)
    val TriggerWifiDisconnected = Color(0xFF177078)
    val TriggerBtConnected = Color(0xFF1B818C)
    val TriggerBtDisconnected = Color(0xFF1F94A2)
    val TriggerHeadsetPlugged = Color(0xFF2CA1AC)
    val TriggerHeadsetUnplugged = Color(0xFF54B7C3)
    val TriggerAirplaneMode = Color(0xFF43B0BD)
    val TriggerNfcTag = Color(0xFF65BEC9)

    // ---------- 觸發：系統與應用（藍灰） ----------
    val TriggerDeviceBoot = Color(0xFF313D42)
    val TriggerDndChanged = Color(0xFF3B464B)
    val TriggerScreenOn = Color(0xFF44545C)
    val TriggerNotificationPosted = Color(0xFF4A585F)
    val TriggerScreenOff = Color(0xFF546670)
    val TriggerAppState = Color(0xFF5A6C74)
    val TriggerScreenUnlocked = Color(0xFF67797F)

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
    val ActionDial = Color(0xFF8EBDE5)
    val ActionSendSms = Color(0xFFA0C8EA)

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
    val ActionWifiToggle = Color(0xFFD79C31)
    val ActionAutoRotate = Color(0xFFD9A23A)
    val ActionScreenTimeout = Color(0xFFDDAE4C)
    val ActionGetLocation = Color(0xFFE1B95E)
    val ActionSnapshotSettings = Color(0xFFBA5A22)
    val ActionRestoreSettings = Color(0xFFDDA135)

    // ---------- 動作：媒體與擷取（紫） ----------
    val ActionTakePhoto = Color(0xFF652F86)
    val ActionBurstPhoto = Color(0xFF823995)
    val ActionRecordAudio = Color(0xFF9E5AA9)

    // ---------- 動作：流程（灰） ----------
    val ActionWait = Color(0xFF616161)
    val ActionClipboard = Color(0xFF757575)

    // ---------- 動作：變數（藍灰，與流程灰家族相鄰） ----------
    val ActionText = Color(0xFF546E7A)
    val ActionSetVariable = Color(0xFF67818C)

    // ---------- 動作：流程控制（棕，與判斷/迴圈語意區隔於其他家族） ----------
    val ActionControl = Color(0xFF6D4C41)

    // ---------- 動作：執行程序（青，「呼叫另一個程序」與流程控制區隔） ----------
    val ActionRunRoutine = Color(0xFF00796B)

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
    is Trigger.BatteryFull -> RoutinaColors.TriggerBatteryFull
    is Trigger.PowerSave -> RoutinaColors.TriggerPowerSave
    is Trigger.WifiConnected -> RoutinaColors.TriggerWifiConnected
    is Trigger.WifiDisconnected -> RoutinaColors.TriggerWifiDisconnected
    is Trigger.BtConnected -> RoutinaColors.TriggerBtConnected
    is Trigger.BtDisconnected -> RoutinaColors.TriggerBtDisconnected
    is Trigger.HeadsetPlugged -> RoutinaColors.TriggerHeadsetPlugged
    is Trigger.HeadsetUnplugged -> RoutinaColors.TriggerHeadsetUnplugged
    is Trigger.AirplaneMode -> RoutinaColors.TriggerAirplaneMode
    is Trigger.NfcTag -> RoutinaColors.TriggerNfcTag
    is Trigger.DndChanged -> RoutinaColors.TriggerDndChanged
    is Trigger.NotificationPosted -> RoutinaColors.TriggerNotificationPosted
    is Trigger.AppState -> RoutinaColors.TriggerAppState
    is Trigger.ScreenUnlocked -> RoutinaColors.TriggerScreenUnlocked
    is Trigger.ScreenOn -> RoutinaColors.TriggerScreenOn
    is Trigger.ScreenOff -> RoutinaColors.TriggerScreenOff
    is Trigger.DeviceBoot -> RoutinaColors.TriggerDeviceBoot
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
    is Action.Dial -> RoutinaColors.ActionDial
    is Action.SendSms -> RoutinaColors.ActionSendSms
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
    is Action.WifiToggle -> RoutinaColors.ActionWifiToggle
    is Action.AutoRotate -> RoutinaColors.ActionAutoRotate
    is Action.ScreenTimeout -> RoutinaColors.ActionScreenTimeout
    is Action.GetLocation -> RoutinaColors.ActionGetLocation
    is Action.SnapshotSettings -> RoutinaColors.ActionSnapshotSettings
    is Action.RestoreSettings -> RoutinaColors.ActionRestoreSettings
    is Action.TakePhoto -> RoutinaColors.ActionTakePhoto
    is Action.BurstPhoto -> RoutinaColors.ActionBurstPhoto
    is Action.RecordAudio -> RoutinaColors.ActionRecordAudio
    is Action.Wait -> RoutinaColors.ActionWait
    is Action.Clipboard -> RoutinaColors.ActionClipboard
    is Action.Text -> RoutinaColors.ActionText
    is Action.SetVariable -> RoutinaColors.ActionSetVariable
    is Action.SetGlobalVariable -> RoutinaColors.ActionSetVariable
    is Action.Calculate -> RoutinaColors.ActionSetVariable
    is Action.Expression -> RoutinaColors.ActionSetVariable
    is Action.AskInput -> RoutinaColors.ActionSetVariable
    is Action.NotifyAsk -> RoutinaColors.ActionSetVariable
    is Action.ChooseMenu -> RoutinaColors.ActionSetVariable
    // 清單動作也寫入變數，歸變數家族同色
    is Action.ListCreate, is Action.ListSplit, is Action.ListAppend,
    is Action.ListGet, is Action.ListCount -> RoutinaColors.ActionSetVariable

    is Action.JsonGet -> RoutinaColors.ActionSetVariable
    is Action.TextTransform -> RoutinaColors.ActionSetVariable
    is Action.DateFormat -> RoutinaColors.ActionSetVariable
    is Action.IfBegin, is Action.ElseIf, is Action.Else, is Action.EndIf,
    is Action.WhileBegin, is Action.EndWhile, is Action.RepeatBegin,
    is Action.EndRepeat, is Action.ForEachBegin,
    is Action.EndForEach, is Action.OnReplyBegin, is Action.NoReply,
    is Action.EndOnReply -> RoutinaColors.ActionControl
    is Action.RunRoutine -> RoutinaColors.ActionRunRoutine
}

/** 方塊主色：優先用使用者自訂的 [Routine.color]，否則用觸發家族色 */
fun routineAccent(routine: Routine): Color =
    routine.color?.let { Color(it) } ?: triggerColor(routine.trigger)

/** 自訂方塊顏色的預選盤（取各功能家族的代表色） */
val RoutinePalette: List<Color> = listOf(
    RoutinaColors.TriggerTime,
    RoutinaColors.TriggerBatteryBelow,
    RoutinaColors.TriggerBtConnected,
    RoutinaColors.TriggerLocationEnter,
    RoutinaColors.ActionNotify,
    RoutinaColors.ActionVolume,
    RoutinaColors.ActionFlashlight,
    RoutinaColors.ActionBurstPhoto,
    RoutinaColors.ActionText,
    RoutinaColors.TriggerManual
)

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

/**
 * 積木底色：家族色去飽和、拉到固定明度階。
 *
 * 不直接用家族原色鋪滿，是因為 49 種動作全部滿版飽和時，等於沒有任何一塊被強調
 * （M3 的語彙裡那是 primary/onPrimary，一整排就是一整頁 FAB），
 * 縮排與分組的訊號會被壓過去。家族辨識改由左緣的色條承擔（見 [blockAccent]）。
 *
 * 明度固定而非依原色深淺，是為了讓整份清單勻稱：原本家族內從 L*31 到 L*79 的跨距
 * 會讓同一家族的兩端看起來像兩個顏色。
 */
fun blockTint(fill: Color, dark: Boolean): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(fill.toArgb(), hsv)
    return if (dark) {
        Color.hsv(hsv[0], (hsv[1] * 0.55f).coerceAtMost(0.34f), 0.22f)
    } else {
        Color.hsv(hsv[0], (hsv[1] * 0.30f).coerceAtMost(0.15f), 0.965f)
    }
}

/**
 * 積木文字色：同色相的深（淺色模式）／淺（深色模式）版本。
 *
 * 因為底色的明度是固定的，這個對比也就固定，不需要再依底色深淺在白字與深字之間翻轉
 * ——同一個家族內文字顏色不一致是很明顯的「沒設計過」訊號。
 */
fun blockInk(fill: Color, dark: Boolean): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(fill.toArgb(), hsv)
    return if (dark) {
        Color.hsv(hsv[0], (hsv[1] * 0.45f).coerceAtMost(0.30f), 0.92f)
    } else {
        Color.hsv(hsv[0], (hsv[1] * 0.85f).coerceAtMost(0.80f), 0.36f)
    }
}

/** 家族色條：左緣那一道仍用原本的飽和家族色，顏色面積小但辨識度完整保留 */
fun blockAccent(fill: Color, dark: Boolean): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(fill.toArgb(), hsv)
    return if (dark) {
        Color.hsv(hsv[0], hsv[1].coerceAtMost(0.75f), (hsv[2] * 1.15f).coerceAtMost(0.90f))
    } else {
        Color.hsv(hsv[0], hsv[1].coerceAtLeast(0.35f), (hsv[2] * 0.92f).coerceIn(0.35f, 0.82f))
    }
}

/** WCAG 對比率（1:1 – 21:1） */
private fun contrastRatio(a: Color, b: Color): Float {
    val lighter = maxOf(a.luminance(), b.luminance()) + 0.05f
    val darker = minOf(a.luminance(), b.luminance()) + 0.05f
    return lighter / darker
}
