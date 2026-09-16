package com.routina.app.ui.blocks

import com.routina.app.ui.theme.blockTint
import com.routina.app.ui.theme.blockInk
import com.routina.app.ui.theme.blockAccent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.routina.app.model.Action
import com.routina.app.model.AppTarget
import com.routina.app.model.Routine
import com.routina.app.model.Trigger
import com.routina.app.model.btDevice
import com.routina.app.model.geoCircle
import com.routina.app.ui.actionBlockLabel
import com.routina.app.ui.actionParamText
import com.routina.app.ui.appTargetLabel
import com.routina.app.ui.rememberAppIcon
import com.routina.app.ui.btDeviceLabel
import com.routina.app.ui.geoCircleLabel
import com.routina.app.ui.keywordLabel
import com.routina.app.ui.nfcTagLabel
import com.routina.app.ui.onOffLabel
import com.routina.app.ui.ssidLabel
import com.routina.app.ui.timeModeLabel
import com.routina.app.ui.weekdaysLabel
import com.routina.app.ui.theme.actionColor
import com.routina.app.ui.theme.blockContentColor
import com.routina.app.ui.theme.triggerColor
import sh.calvin.reorderable.ReorderableScope

/**
 * 積木的幾何規格。所有積木都是單純的圓角色塊（無拼圖凹凸、無帽子圓弧），
 * 差別只在顏色、文字與尺寸（編輯器版／清單縮小版）。
 */
@Immutable
data class BlockMetrics(
    /** 圓角半徑 */
    val corner: Dp,
    /** 積木最小高度 */
    val minHeight: Dp,
    val labelSize: TextUnit,
    val paramSize: TextUnit,
    val paramPaddingH: Dp,
    val paramPaddingV: Dp,
    /** 參數欄圓角：與積木圓角同心（積木圓角 − 垂直內距） */
    val paramCorner: Dp,
    val contentPaddingH: Dp,
    val contentPaddingV: Dp,
    val contentSpacing: Dp
)

/** 編輯器用（design.md 幾何規格） */
val BlockMetricsNormal = BlockMetrics(
    corner = 12.dp,
    minHeight = 48.dp,
    labelSize = 14.sp,
    paramSize = 13.sp,
    paramPaddingH = 10.dp,
    paramPaddingV = 4.dp,
    paramCorner = 6.dp,
    contentPaddingH = 14.dp,
    contentPaddingV = 8.dp,
    contentSpacing = 6.dp
)

/** 清單卡片的縮小唯讀版 */
val BlockMetricsCompact = BlockMetrics(
    corner = 10.dp,
    minHeight = 34.dp,
    labelSize = 12.sp,
    paramSize = 11.sp,
    paramPaddingH = 8.dp,
    paramPaddingV = 2.dp,
    paramCorner = 5.dp,
    contentPaddingH = 10.dp,
    contentPaddingV = 4.dp,
    contentSpacing = 4.dp
)

fun blockMetrics(compact: Boolean): BlockMetrics =
    if (compact) BlockMetricsCompact else BlockMetricsNormal

/** 堆疊時的垂直間距(同一區塊內) */
val BlockStackSpacing = 6.dp

/**
 * 區塊之間的額外間距:進入「如果」之前、離開「結束如果」之後各加這麼多。
 *
 * 分組最基本的條件是組內要比組間近;原本組內組外都一樣,間距對分組的貢獻是零。
 */
val BlockGroupSpacing = 10.dp

/** 左緣家族色條寬度 */
val BlockAccentWidth = 4.dp

/** 邊框色＝積木色加深 25% */
fun blockBorderColor(color: Color): Color = lerp(color, Color.Black, 0.25f)

/**
 * 參數欄預設底色。飽和積木（觸發帽子）仍用白底深字；
 * 一般動作積木由呼叫端傳入家族色的更淡版本，避免白色形狀成為列上最刺眼的元素。
 */
val BlockParamBackground = Color.White.copy(alpha = 0.9f)

/** 參數欄文字色 */
val BlockParamContent = Color(0xFF333333)

/** 幽靈積木的虛線圓角外框；描邊往內縮半個線寬，剛好落在積木範圍內 */
private fun Modifier.dashedBlockOutline(
    corner: Dp,
    color: Color,
    width: Dp = 1.5.dp
): Modifier = drawBehind {
    val stroke = width.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(stroke / 2f, stroke / 2f),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(corner.toPx()),
        style = Stroke(
            width = stroke,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 6.dp.toPx()))
        )
    )
}

/** 觸發積木上可獨立點擊的參數欄 */
enum class TriggerParam {
    TIME,
    DAYS,
    THRESHOLD,
    LOCATION,
    BT_DEVICE,
    SSID,
    STATE,

    /** NFC 標籤：帶出掃描對話框 */
    NFC_TAG,

    /** 通知觸發的來源 App 與關鍵字（同一個對話框設定，兩個欄位都導向它） */
    NOTIFICATION_FILTER,

    /** App 開啟／關閉的目標 App */
    APP
}

/**
 * 積木底座:家族色的淡底 + 左緣家族色條 + 內容 padding。
 *
 * 只用「底色」一種深度機制:不加外框、靜止時不給陰影。原本同時疊了飽和填色、
 * 加深 25% 的外框與陰影三種,三者都在說「這是一個獨立表面」,疊起來就是貼紙感。
 *
 * [solid] 為 true 時改用飽和實心色——留給觸發帽子積木,一頁一個 hero,
 * 讓整份清單裡唯一被強調的東西是流程的起點。
 */
@Composable
fun BlockSurface(
    fill: Color,
    modifier: Modifier = Modifier,
    metrics: BlockMetrics = BlockMetricsNormal,
    border: Color = blockBorderColor(fill),
    dashed: Boolean = false,
    solid: Boolean = false,
    endPadding: Dp = metrics.contentPaddingH,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(metrics.contentSpacing),
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val shape = RoundedCornerShape(metrics.corner)
    val dark = isSystemInDarkTheme()
    val accent = blockAccent(fill, dark)
    val accentWidth = with(LocalDensity.current) { BlockAccentWidth.toPx() }

    var blockModifier = modifier
        .fillMaxWidth()
        .heightIn(min = metrics.minHeight)
        .clip(shape)
    blockModifier = when {
        dashed -> blockModifier.dashedBlockOutline(metrics.corner, border)
        solid -> blockModifier.background(fill)
        else -> blockModifier
            .background(blockTint(fill, dark))
            // 色條畫在裁切之後,左端自然跟著圓角走,不必另外做形狀
            .drawBehind { drawRect(accent, size = Size(accentWidth, size.height)) }
    }
    if (onClick != null) blockModifier = blockModifier.clickable(onClick = onClick)

    Row(
        modifier = blockModifier.padding(
            start = metrics.contentPaddingH,
            end = endPadding,
            top = metrics.contentPaddingV,
            bottom = metrics.contentPaddingV
        ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = horizontalArrangement,
        content = content
    )
}

/**
 * 積木上的粗體標籤文字。
 *
 * [color] 預設白色；淺色積木（省電模式、螢幕亮度）由呼叫端傳入 blockContentColor
 * 算出的深色，確保對比足夠——積木以文字為主要辨識依據。
 */
@Composable
fun BlockLabel(
    text: String,
    metrics: BlockMetrics = BlockMetricsNormal,
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Text(
        text = text,
        color = color,
        fontSize = metrics.labelSize,
        // 繁中字型家族只宣告 weight 400，Bold 會被系統演算法「假粗」把筆畫塗糊；
        // Medium 在舊版落回 Regular（乾淨）、Android 16 才是真 Medium。
        // 中文的層級改用字級與顏色深淺表達，不靠字重。
        fontWeight = FontWeight.Medium,
        // M3 的字距是為拉丁字調的，套在中文上會鬆散
        letterSpacing = 0.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}

/**
 * 積木內嵌的參數欄。
 *
 * [leadingIcon] 非 null 時在文字前顯示小圖示（例如所選 App 的圖示）；載不到圖示時傳 null，
 * 只顯示文字即可（fallback，不破壞既有呼叫）。
 */
@Composable
fun ParamField(
    text: String,
    metrics: BlockMetrics = BlockMetricsNormal,
    modifier: Modifier = Modifier,
    leadingIcon: ImageBitmap? = null,
    paramBackground: Color = BlockParamBackground,
    paramText: Color = BlockParamContent,
    onClick: (() -> Unit)? = null
) {
    var fieldModifier = modifier
        // 圓角與積木同心（12dp 積木、上下各留 8dp）而非全圓藥丸：
        // 全圓形狀塞在圓角方塊裡是最不同心的組合，也是「到處都是藥丸」的廉價感來源
        .clip(RoundedCornerShape(metrics.paramCorner))
        .background(paramBackground)
    if (onClick != null) fieldModifier = fieldModifier.clickable(onClick = onClick)

    Row(
        modifier = fieldModifier.padding(metrics.paramPaddingH, metrics.paramPaddingV),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (leadingIcon != null) {
            val iconSize = if (metrics.paramSize.value <= 11f) 14.dp else 18.dp
            Image(
                bitmap = leadingIcon,
                contentDescription = null,
                modifier = Modifier
                    .size(iconSize)
                    .clip(RoundedCornerShape(4.dp))
            )
        }
        Text(
            text = text,
            color = paramText,
            fontSize = metrics.paramSize,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 觸發條件積木（堆疊最上面那塊） */
@Composable
fun TriggerBlock(
    trigger: Trigger,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onBodyClick: (() -> Unit)? = null,
    onParamClick: ((TriggerParam) -> Unit)? = null
) {
    val metrics = blockMetrics(compact)
    val fill = triggerColor(trigger)
    // 觸發帽子是整份流程唯一保留飽和實心色的積木（一頁一個 hero）
    val content = blockContentColor(fill)
    BlockSurface(
        fill = fill,
        modifier = modifier,
        metrics = metrics,
        solid = true,
        onClick = onBodyClick
    ) {
        // 手動帽子積木不加「當」前綴、也沒有白色參數欄，只顯示用途說明
        if (trigger !is Trigger.Manual) {
            BlockLabel("當", metrics, color = content)
        }
        when (trigger) {
            is Trigger.Manual -> {
                BlockLabel("手動執行", metrics, color = content)
                BlockLabel("點 ▶ 執行", metrics, color = content.copy(alpha = 0.7f))
            }

            is Trigger.Time -> {
                ParamField(
                    text = weekdaysLabel(trigger.daysOfWeek),
                    metrics = metrics,
                    modifier = Modifier.weight(1f, fill = false),
                    onClick = onParamClick?.let { { it(TriggerParam.DAYS) } }
                )
                BlockLabel("到", metrics, color = content)
                ParamField(
                    text = timeModeLabel(trigger),
                    metrics = metrics,
                    onClick = onParamClick?.let { { it(TriggerParam.TIME) } }
                )
            }

            is Trigger.PowerConnected -> BlockLabel("接上電源", metrics, color = content)

            is Trigger.PowerDisconnected -> BlockLabel("拔除電源", metrics, color = content)

            is Trigger.BatteryBelow -> ThresholdParam("電量降到", "以下", trigger.threshold, metrics, content, onParamClick)

            is Trigger.BatteryAbove -> ThresholdParam("電量升到", "以上", trigger.threshold, metrics, content, onParamClick)

            is Trigger.BatteryFull -> BlockLabel("充電完成", metrics, color = content)

            is Trigger.LocationEnter -> LocationParam("進入", trigger, metrics, content, onParamClick)

            is Trigger.LocationExit -> LocationParam("離開", trigger, metrics, content, onParamClick)

            is Trigger.BtConnected -> BtParam("連接藍牙", trigger, metrics, content, onParamClick)

            is Trigger.BtDisconnected -> BtParam("斷開藍牙", trigger, metrics, content, onParamClick)

            is Trigger.WifiConnected -> {
                BlockLabel("連上 Wi-Fi", metrics, color = content)
                ParamField(
                    text = ssidLabel(trigger.ssid),
                    metrics = metrics,
                    modifier = Modifier.weight(1f, fill = false),
                    onClick = onParamClick?.let { { it(TriggerParam.SSID) } }
                )
            }

            is Trigger.WifiDisconnected -> BlockLabel("Wi-Fi 斷線", metrics, color = content)

            is Trigger.AirplaneMode -> StateParam("飛航模式", trigger.turnedOn, metrics, content, onParamClick)

            is Trigger.DndChanged -> StateParam("勿擾模式", trigger.turnedOn, metrics, content, onParamClick)

            is Trigger.PowerSave -> StateParam("省電模式", trigger.turnedOn, metrics, content, onParamClick)

            is Trigger.NfcTag -> {
                BlockLabel("掃到", metrics, color = content)
                ParamField(
                    text = nfcTagLabel(trigger),
                    metrics = metrics,
                    modifier = Modifier.weight(1f, fill = false),
                    onClick = onParamClick?.let { { it(TriggerParam.NFC_TAG) } }
                )
            }

            is Trigger.NotificationPosted -> {
                BlockLabel("收到", metrics, color = content)
                ParamField(
                    text = appTargetLabel(
                        AppTarget(trigger.packageName, trigger.appName),
                        blankLabel = "任一 App"
                    ),
                    metrics = metrics,
                    modifier = Modifier.weight(1f, fill = false),
                    leadingIcon = rememberAppIcon(trigger.packageName),
                    onClick = onParamClick?.let { { it(TriggerParam.NOTIFICATION_FILTER) } }
                )
                BlockLabel("通知", metrics, color = content)
                ParamField(
                    text = keywordLabel(trigger.keyword),
                    metrics = metrics,
                    modifier = Modifier.weight(1f, fill = false),
                    onClick = onParamClick?.let { { it(TriggerParam.NOTIFICATION_FILTER) } }
                )
            }

            is Trigger.AppState -> {
                BlockLabel("App", metrics, color = content)
                ParamField(
                    text = appTargetLabel(
                        AppTarget(trigger.packageName, trigger.appName),
                        blankLabel = "選擇 App"
                    ),
                    metrics = metrics,
                    modifier = Modifier.weight(1f, fill = false),
                    leadingIcon = rememberAppIcon(trigger.packageName),
                    onClick = onParamClick?.let { { it(TriggerParam.APP) } }
                )
                ParamField(
                    text = onOffLabel(trigger.onOpen),
                    metrics = metrics,
                    onClick = onParamClick?.let { { it(TriggerParam.STATE) } }
                )
            }

            is Trigger.HeadsetPlugged -> BlockLabel("插入耳機", metrics, color = content)

            is Trigger.HeadsetUnplugged -> BlockLabel("拔除耳機", metrics, color = content)

            is Trigger.ScreenUnlocked -> BlockLabel("解鎖螢幕", metrics, color = content)

            is Trigger.ScreenOn -> BlockLabel("螢幕開啟", metrics, color = content)

            is Trigger.ScreenOff -> BlockLabel("螢幕關閉", metrics, color = content)

            is Trigger.DeviceBoot -> BlockLabel("開機完成", metrics, color = content)
        }
    }
}

/** 電量門檻的「電量降到 [80%] 以下」欄位 */
@Composable
private fun RowScope.ThresholdParam(
    prefix: String,
    suffix: String,
    threshold: Int,
    metrics: BlockMetrics,
    content: Color,
    onParamClick: ((TriggerParam) -> Unit)?
) {
    BlockLabel(prefix, metrics, color = content)
    ParamField(
        text = "$threshold%",
        metrics = metrics,
        onClick = onParamClick?.let { { it(TriggerParam.THRESHOLD) } }
    )
    BlockLabel(suffix, metrics, color = content)
}

/** 區域觸發的「進入／離開 [地點] 區域」欄位 */
@Composable
private fun RowScope.LocationParam(
    verb: String,
    trigger: Trigger,
    metrics: BlockMetrics,
    content: Color,
    onParamClick: ((TriggerParam) -> Unit)?
) {
    val circle = trigger.geoCircle ?: return
    BlockLabel(verb, metrics, color = content)
    ParamField(
        text = geoCircleLabel(circle),
        metrics = metrics,
        modifier = Modifier.weight(1f, fill = false),
        onClick = onParamClick?.let { { it(TriggerParam.LOCATION) } }
    )
    BlockLabel("區域", metrics, color = content)
}

/** 藍牙觸發的「連接／斷開藍牙 [裝置]」欄位 */
@Composable
private fun RowScope.BtParam(
    verb: String,
    trigger: Trigger,
    metrics: BlockMetrics,
    content: Color,
    onParamClick: ((TriggerParam) -> Unit)?
) {
    val device = trigger.btDevice ?: return
    BlockLabel(verb, metrics, color = content)
    ParamField(
        text = btDeviceLabel(device),
        metrics = metrics,
        modifier = Modifier.weight(1f, fill = false),
        onClick = onParamClick?.let { { it(TriggerParam.BT_DEVICE) } }
    )
}

/** 系統狀態切換的「飛航模式 [開啟時]」欄位 */
@Composable
private fun RowScope.StateParam(
    name: String,
    turnedOn: Boolean,
    metrics: BlockMetrics,
    content: Color,
    onParamClick: ((TriggerParam) -> Unit)?
) {
    BlockLabel(name, metrics, color = content)
    ParamField(
        text = onOffLabel(turnedOn),
        metrics = metrics,
        onClick = onParamClick?.let { { it(TriggerParam.STATE) } }
    )
}

/**
 * 動作積木。
 *
 * 編輯畫面（[showControls] = true 並提供 [reorderableScope]）右側顯示「≡ 拖曳握把 + ✕ 移除」：
 * 拖握把或長按積木本體即可拿起排序，[isDragging] 為 true 時積木浮起（陰影 + 微放大）。
 * 點積木本體＝編輯參數（tap 與長按拖曳不衝突）。清單縮小唯讀版不傳這些參數，維持純預覽、不可拖曳。
 */
@Composable
fun ActionBlock(
    action: Action,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showControls: Boolean = false,
    isDragging: Boolean = false,
    reorderableScope: ReorderableScope? = null,
    onBodyClick: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null
) {
    val metrics = blockMetrics(compact)
    val fill = actionColor(action)
    val dark = isSystemInDarkTheme()
    val content = blockInk(fill, dark)
    val accentColor = blockAccent(fill, dark)
    // 「開啟 App」動作在參數欄顯示所選 App 的小圖示；其他動作或未選 App 時為 null（只顯示文字）
    val paramIcon = (action as? Action.OpenApp)?.packageName?.let { rememberAppIcon(it) }

    val haptic = LocalHapticFeedback.current
    // 握把與長按本體共用同一個互動來源，一起反映「正在被拖曳」的狀態
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(metrics.corner)
    // 拿起時浮起：陰影加深、輕微放大；放開後平滑彈回
    val elevation by animateDpAsState(
        if (isDragging) 8.dp else 0.dp,
        label = "actionBlockElevation"
    )
    val scale by animateFloatAsState(
        if (isDragging) 1.03f else 1f,
        label = "actionBlockScale"
    )

    var blockModifier = modifier
    if (reorderableScope != null) {
        // 長按積木本體即可拿起（Scratch 感）；單點仍交給 BlockSurface 的 clickable＝編輯參數
        blockModifier = with(reorderableScope) {
            blockModifier.longPressDraggableHandle(
                onDragStarted = { haptic.performHapticFeedback(HapticFeedbackType.LongPress) },
                interactionSource = interactionSource
            )
        }
    }
    blockModifier = blockModifier
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .shadow(elevation, shape, clip = false)

    BlockSurface(
        fill = fill,
        modifier = blockModifier,
        metrics = metrics,
        endPadding = if (showControls) 2.dp else metrics.contentPaddingH,
        onClick = onBodyClick
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(metrics.contentSpacing)
        ) {
            BlockLabel(actionBlockLabel(action), metrics, color = content)
            // 參數欄為空（流程標記如「結束如果／否則」）時不畫空白膠囊，維持乾淨
            val param = actionParamText(action)
            if (param.isNotBlank()) {
                ParamField(
                    text = param,
                    metrics = metrics,
                    modifier = Modifier.weight(1f, fill = false),
                    leadingIcon = paramIcon,
                    // 底已經是家族淡色，參數欄再淡一階即可分出層次；
                    // 用白色會讓它變成整列最刺眼的東西
                    paramBackground = accentColor.copy(alpha = 0.14f),
                    paramText = content,
                    onClick = onBodyClick
                )
            }
        }
        // 排序改成長按積木本體拿起（見上方 longPressDraggableHandle），不再另放拖曳握把，減少視覺雜訊
        if (showControls) {
            BlockIconButton(Icons.Filled.Close, "移除動作", content, onRemove)
        }
    }
}

/** 虛線幽靈積木：「＋ 加入動作」 */
@Composable
fun GhostBlock(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String = "＋ 加入動作"
) {
    BlockSurface(
        fill = Color.Transparent,
        modifier = modifier,
        border = MaterialTheme.colorScheme.outline,
        dashed = true,
        horizontalArrangement = Arrangement.Center,
        onClick = onClick
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = BlockMetricsNormal.labelSize,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * 動作之間 / 清單開頭的插入點：一條淡線 + 中央「＋」小圓，點擊即在該索引插入新動作。
 * 刻意做得低調（淡色、細），只在需要「插到這裡」時才被注意到，不與積木本身搶視覺。
 */
@Composable
fun InsertPoint(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 24.dp)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        InsertLine(tint, Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "在此插入動作",
                tint = tint.copy(alpha = 0.8f),
                modifier = Modifier.size(14.dp)
            )
        }
        InsertLine(tint, Modifier.weight(1f))
    }
}

@Composable
private fun InsertLine(tint: Color, modifier: Modifier) {
    Box(
        modifier = modifier
            .height(1.5.dp)
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.25f))
    )
}

/** 清單預覽超出顯示上限時的灰積木 */
@Composable
fun MoreActionsBlock(count: Int, modifier: Modifier = Modifier) {
    val metrics = BlockMetricsCompact
    BlockSurface(
        fill = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
        metrics = metrics,
        border = MaterialTheme.colorScheme.outline
    ) {
        Text(
            text = "⋯還有 $count 個動作",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = metrics.labelSize,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** 積木堆疊：由上而下等距排列，不重疊 */
@Composable
fun BlockStack(
    blocks: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(BlockStackSpacing)
    ) {
        blocks.forEach { block -> block() }
    }
}

/** 清單卡片用的縮小唯讀堆疊：觸發積木 + 最多 [maxActions] 塊動作 + 「還有 N 個」灰積木 */
@Composable
fun RoutinePreviewStack(
    routine: Routine,
    modifier: Modifier = Modifier,
    maxActions: Int = 3
) {
    val shown = routine.actions.take(maxActions)
    val hidden = routine.actions.size - shown.size
    val blocks = buildList<@Composable () -> Unit> {
        add { TriggerBlock(trigger = routine.trigger, compact = true) }
        shown.forEach { action ->
            add { ActionBlock(action = action, compact = true) }
        }
        if (hidden > 0) add { MoreActionsBlock(hidden) }
    }
    BlockStack(blocks = blocks, modifier = modifier)
}

/** 積木上的半透明小圖示（顏色跟隨積木文字色）；[onClick] 為 null 代表 disabled */
@Composable
private fun BlockIconButton(
    icon: ImageVector,
    contentDescription: String,
    content: Color,
    onClick: (() -> Unit)?
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(enabled = onClick != null) { onClick?.invoke() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = content.copy(alpha = if (onClick != null) 0.7f else 0.25f),
            modifier = Modifier.size(20.dp)
        )
    }
}
