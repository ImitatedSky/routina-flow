package com.routina.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * 已安裝 App 的圖示載入與快取（package → ImageBitmap）。
 *
 * 「開啟 App」動作、通知觸發、App 開啟／關閉觸發的選擇器與積木參數欄共用這裡，
 * 讓三處以同一份快取顯示圖示。快取純記憶體、不寫檔：避免 App 選擇清單捲動時
 * 重複解碼，也讓積木參數欄的小圖示即時可用。
 *
 * 圖示以固定像素解碼一次，實際顯示大小交給 Image 縮放（清單 32dp、積木 16–20dp）。
 * 任何 App 查不到圖示都記入 [missingIcons] 不再重試，呼叫端 fallback 只顯示名稱。
 */
private const val ICON_LOAD_PX = 96

private val iconCache = ConcurrentHashMap<String, ImageBitmap>()
private val missingIcons: MutableSet<String> = ConcurrentHashMap.newKeySet()

/** 在 IO 執行緒載入指定 package 的 App 圖示；失敗回傳 null（呼叫端 fallback 名稱） */
suspend fun loadAppIcon(context: Context, packageName: String): ImageBitmap? {
    if (packageName.isBlank()) return null
    iconCache[packageName]?.let { return it }
    if (packageName in missingIcons) return null

    val appContext = context.applicationContext
    return withContext(Dispatchers.IO) {
        val bitmap = runCatching {
            appContext.packageManager
                .getApplicationIcon(packageName)
                .toBitmap(width = ICON_LOAD_PX, height = ICON_LOAD_PX)
                .asImageBitmap()
        }.getOrNull()
        if (bitmap != null) iconCache[packageName] = bitmap else missingIcons += packageName
        bitmap
    }
}

/**
 * 非同步載入並記住某個 App 的圖示。
 *
 * 快取命中時（例如清單捲回或積木重繪）立即回傳；否則先回傳 null（呼叫端只顯示名稱），
 * 載入完成後再更新。空 package 一律回 null。
 */
@Composable
fun rememberAppIcon(packageName: String): ImageBitmap? {
    val context = LocalContext.current
    val state: State<ImageBitmap?> = produceState(
        initialValue = iconCache[packageName],
        key1 = packageName
    ) {
        if (packageName.isNotBlank() && value == null) {
            value = loadAppIcon(context, packageName)
        }
    }
    return state.value
}
