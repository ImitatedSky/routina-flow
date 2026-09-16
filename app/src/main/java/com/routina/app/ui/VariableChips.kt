package com.routina.app.ui

import androidx.compose.ui.Alignment
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.border
import android.content.ClipData
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.routina.app.engine.VariableResolver

/** 拖曳時放在 ClipData 裡的標籤，收到 drop 時用它確認這是變數膠囊而不是別處拖來的文字 */
private const val CHIP_CLIP_LABEL = "routina/variable-token"

/** 一個 token 在原字串與畫面文字上各自的範圍，位移對應與整段刪除都靠它 */
private data class TokenSpan(
    val rawStart: Int,
    val rawEnd: Int,
    val shownStart: Int,
    val shownEnd: Int
)

/**
 * token 在畫面上顯示的名字：把 `{{ }}` 收起來，只留看得懂的部分。
 * `var:` 前綴拿掉（區域變數是最常見的情況），`全域:`／`迴圈:` 留著——那是語意的一部分。
 */
private fun chipLabel(inner: String): String =
    if (inner.startsWith("var:")) inner.removePrefix("var:") else inner

/** 畫面文字：token 換成膠囊標籤，前後各補一個窄空白當作膠囊的左右內距 */
private fun shownTextFor(raw: String): Pair<String, List<TokenSpan>> {
    val spans = mutableListOf<TokenSpan>()
    val shown = StringBuilder()
    var cursor = 0
    for (match in VariableResolver.TOKEN.findAll(raw)) {
        shown.append(raw, cursor, match.range.first)
        val label = " " + chipLabel(match.groupValues[1]) + " "
        val shownStart = shown.length
        shown.append(label)
        spans += TokenSpan(match.range.first, match.range.last + 1, shownStart, shown.length)
        cursor = match.range.last + 1
    }
    shown.append(raw, cursor, raw.length)
    return shown.toString() to spans
}

/**
 * 把文字裡的 `{{...}}` 變成膠囊：括號收起來、只顯示變數名、整塊上色。
 *
 * 長度會變，所以要自己做位移對應。落在 token 中間的游標一律吸附到膠囊邊緣——
 * 游標進不去膠囊內部，它在編輯時就像一個不可分割的積木，這正是要的效果。
 * 底層存的仍是原本的 token 字串，資料格式完全沒動。
 */
fun tokenChipTransformation(background: Color, foreground: Color): VisualTransformation =
    VisualTransformation { text ->
        val (shown, spans) = shownTextFor(text.text)
        val builder = AnnotatedString.Builder(shown)
        spans.forEach {
            builder.addStyle(
                SpanStyle(background = background, color = foreground, fontWeight = FontWeight.Medium),
                it.shownStart,
                it.shownEnd
            )
        }
        TransformedText(
            builder.toAnnotatedString(),
            object : OffsetMapping {
                override fun originalToTransformed(offset: Int): Int {
                    val o = offset.coerceIn(0, text.text.length)
                    var shift = 0
                    for (span in spans) {
                        when {
                            o <= span.rawStart -> return (o + shift).coerceIn(0, shown.length)
                            // 落在 token 內部：吸到膠囊尾端，游標不會停在括號中間
                            o < span.rawEnd -> return span.shownEnd
                            else -> shift += (span.shownEnd - span.shownStart) - (span.rawEnd - span.rawStart)
                        }
                    }
                    return (o + shift).coerceIn(0, shown.length)
                }

                override fun transformedToOriginal(offset: Int): Int {
                    val o = offset.coerceIn(0, shown.length)
                    var shift = 0
                    for (span in spans) {
                        when {
                            o <= span.shownStart -> return (o - shift).coerceIn(0, text.text.length)
                            o < span.shownEnd -> return span.rawEnd
                            else -> shift += (span.shownEnd - span.shownStart) - (span.rawEnd - span.rawStart)
                        }
                    }
                    return (o - shift).coerceIn(0, text.text.length)
                }
            }
        )
    }

/**
 * 刪除時把整個 token 一起拿掉。
 *
 * 沒有這一步，退格只會吃掉一個 `}`，留下 `{{時間}` 這種壞掉的半截 token——
 * 畫面上膠囊忽然散成一堆括號，而且執行時代不進值。積木就該整塊消失。
 * 只處理「剛好少一個字」的情況（退格／Delete 的實際樣子），其餘編輯照原樣放行。
 */
fun atomicTokenDelete(old: String, new: String): String? {
    if (new.length != old.length - 1) return null
    // 找出第一個不同的位置＝被刪掉的那個字
    var i = 0
    while (i < new.length && new[i] == old[i]) i++
    val hit = VariableResolver.TOKEN.findAll(old).firstOrNull { i >= it.range.first && i <= it.range.last }
        ?: return null
    return old.removeRange(hit.range.first, hit.range.last + 1)
}

/**
 * 變數膠囊列：長按拖起來，拉進哪個欄位就進哪個欄位。
 *
 * 預設收合成一列（可橫向捲動），右緣固定一個「…」展開成分組的完整清單。
 * 展開是就地攤開而不是開對話框——對話框會蓋住欄位，膠囊就拖不出去了，
 * 那會讓「全部的變數」剛好變成唯一不能拖的那些。
 *
 * 也保留輕觸插入：拖曳對長文字欄好用，但欄位多的時候點一下仍然最快。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun VariableChipStrip(
    groups: List<VarTokenGroup>,
    onTapInsert: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (groups.all { it.tokens.isEmpty() }) return
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        if (expanded) {
            groups.filter { it.tokens.isNotEmpty() }.forEach { group ->
                Text(
                    text = group.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    group.tokens.forEach { token -> VariableChip(token, onTapInsert) }
                }
            }
            MoreChip(label = "收合", onClick = { expanded = false })
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    groups.flatMap { it.tokens }.forEach { token ->
                        VariableChip(token, onTapInsert)
                    }
                }
                // 固定在右緣，不跟著捲走，隨時看得到「還有更多」
                MoreChip(label = "…", onClick = { expanded = true })
            }
        }
    }
}

/** 單一個可拖曳的變數膠囊 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun VariableChip(token: VarToken, onTapInsert: (String) -> Unit) {
    Text(
        text = token.label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .dragAndDropSource {
                detectTapGestures(
                    onLongPress = {
                        startTransfer(
                            DragAndDropTransferData(
                                ClipData.newPlainText(CHIP_CLIP_LABEL, token.token)
                            )
                        )
                    },
                    onTap = { onTapInsert(token.token) }
                )
            }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

/** 展開／收合用的膠囊，樣子和變數膠囊有別，免得被誤當成可以拖的變數 */
@Composable
private fun MoreChip(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .padding(start = 6.dp)
            .clip(RoundedCornerShape(50))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

/**
 * 讓一個欄位可以接住拖過來的變數膠囊。
 *
 * [onDropToken] 收到 token 字串後自行決定插在哪（目前是插在游標處）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.variableDropTarget(onDropToken: (String) -> Unit): Modifier {
    val target = remember(onDropToken) {
        object : DragAndDropTarget {
            override fun onDrop(event: DragAndDropEvent): Boolean {
                val clip = event.toAndroidDragEvent().clipData ?: return false
                if (clip.itemCount == 0) return false
                val token = clip.getItemAt(0).text?.toString().orEmpty()
                if (token.isBlank()) return false
                onDropToken(token)
                return true
            }
        }
    }
    return this.dragAndDropTarget(
        shouldStartDragAndDrop = { event ->
            event.toAndroidDragEvent().clipDescription?.label == CHIP_CLIP_LABEL
        },
        target = target
    )
}
